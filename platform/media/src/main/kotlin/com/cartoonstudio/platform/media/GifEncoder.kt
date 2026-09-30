package com.cartoonstudio.platform.media

import android.graphics.Bitmap
import com.cartoonstudio.core.common.AppError
import com.cartoonstudio.core.common.Outcome
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream

/**
 * Minimal, dependency-free GIF89a writer.
 *
 * Frames are quantised to a fixed 6×6×6 colour cube plus a grey ramp, which is
 * fast, allocation-light and perfectly adequate for flat cartoon artwork.
 */
class GifEncoder(
    private val width: Int,
    private val height: Int,
    private val frameDelayCentis: Int,
    private val loop: Boolean = true,
) {

    private var stream: OutputStream? = null
    private var wroteHeader = false

    fun start(output: File): Outcome<Unit> = try {
        output.parentFile?.mkdirs()
        stream = BufferedOutputStream(output.outputStream(), 1 shl 16)
        Outcome.success(Unit)
    } catch (t: Throwable) {
        Outcome.failure(AppError.Io("Could not create the GIF file", t))
    }

    fun addFrame(bitmap: Bitmap): Outcome<Unit> {
        val out = stream ?: return Outcome.failure(AppError.Invalid("GIF encoder not started"))
        return try {
            val scaled = if (bitmap.width == width && bitmap.height == height) bitmap
            else Bitmap.createScaledBitmap(bitmap, width, height, true)

            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)
            if (scaled !== bitmap) scaled.recycle()

            val indices = ByteArray(pixels.size)
            for (i in pixels.indices) indices[i] = quantize(pixels[i])

            if (!wroteHeader) {
                writeHeader(out)
                wroteHeader = true
            }
            writeGraphicControl(out)
            writeImageDescriptor(out)
            LzwEncoder(indices, BITS_PER_PIXEL).encode(out)
            Outcome.success(Unit)
        } catch (t: Throwable) {
            Outcome.failure(AppError.Io("Could not write a GIF frame", t))
        }
    }

    fun finish(): Outcome<Unit> = try {
        stream?.let {
            it.write(0x3B) // trailer
            it.flush()
            it.close()
        }
        stream = null
        Outcome.success(Unit)
    } catch (t: Throwable) {
        Outcome.failure(AppError.Io("Could not finalise the GIF", t))
    }

    fun release() {
        runCatching { stream?.close() }
        stream = null
    }

    private fun writeHeader(out: OutputStream) {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(out, width)
        writeShort(out, height)
        // Global colour table: 256 entries, 8 bits per colour.
        out.write(0xF0 or (BITS_PER_PIXEL - 1))
        out.write(0) // background colour index
        out.write(0) // default pixel aspect ratio
        writePalette(out)
        if (loop) writeNetscapeExtension(out)
    }

    private fun writePalette(out: OutputStream) {
        val table = ByteArray(PALETTE_SIZE * 3)
        var index = 0
        for (r in 0 until CUBE) {
            for (g in 0 until CUBE) {
                for (b in 0 until CUBE) {
                    table[index * 3] = (r * 255 / (CUBE - 1)).toByte()
                    table[index * 3 + 1] = (g * 255 / (CUBE - 1)).toByte()
                    table[index * 3 + 2] = (b * 255 / (CUBE - 1)).toByte()
                    index++
                }
            }
        }
        // Remaining slots become a grey ramp for smooth shading.
        var grey = 0
        while (index < PALETTE_SIZE) {
            val value = (grey * 255 / (PALETTE_SIZE - CUBE * CUBE * CUBE - 1).coerceAtLeast(1)).coerceIn(0, 255)
            table[index * 3] = value.toByte()
            table[index * 3 + 1] = value.toByte()
            table[index * 3 + 2] = value.toByte()
            index++
            grey++
        }
        out.write(table)
    }

    private fun writeNetscapeExtension(out: OutputStream) {
        out.write(0x21)
        out.write(0xFF)
        out.write(11)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3)
        out.write(1)
        writeShort(out, 0) // repeat forever
        out.write(0)
    }

    private fun writeGraphicControl(out: OutputStream) {
        out.write(0x21)
        out.write(0xF9)
        out.write(4)
        out.write(0) // no transparency, no disposal
        writeShort(out, frameDelayCentis.coerceAtLeast(2))
        out.write(0) // transparent colour index
        out.write(0)
    }

    private fun writeImageDescriptor(out: OutputStream) {
        out.write(0x2C)
        writeShort(out, 0)
        writeShort(out, 0)
        writeShort(out, width)
        writeShort(out, height)
        out.write(0) // no local colour table, not interlaced
    }

    private fun writeShort(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    private fun quantize(argb: Int): Byte {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val ri = r * (CUBE - 1) / 255
        val gi = g * (CUBE - 1) / 255
        val bi = b * (CUBE - 1) / 255
        return (ri * CUBE * CUBE + gi * CUBE + bi).toByte()
    }

    private companion object {
        const val CUBE = 6
        const val PALETTE_SIZE = 256
        const val BITS_PER_PIXEL = 8
    }
}

/**
 * GIF LZW compressor.
 *
 * Standard variable-code-width LZW with the GIF clear/end codes and 255-byte
 * sub-block framing.
 */
private class LzwEncoder(private val pixels: ByteArray, private val colorDepth: Int) {

    private val clearCode = 1 shl colorDepth
    private val endCode = clearCode + 1
    private var codeSize = colorDepth + 1
    private var nextCode = endCode + 1

    private val accumulator = ByteArray(256)
    private var accumulatorSize = 0
    private var bitBuffer = 0
    private var bitCount = 0

    private val dictionary = HashMap<Int, Int>(4096)

    fun encode(out: OutputStream) {
        out.write(colorDepth)
        writeCode(out, clearCode)

        if (pixels.isEmpty()) {
            writeCode(out, endCode)
            flushBits(out)
            flushAccumulator(out)
            out.write(0)
            return
        }

        var prefix = pixels[0].toInt() and 0xFF
        for (index in 1 until pixels.size) {
            val suffix = pixels[index].toInt() and 0xFF
            val key = (prefix shl 8) or suffix
            val existing = dictionary[key]
            if (existing != null) {
                prefix = existing
            } else {
                writeCode(out, prefix)
                if (nextCode < 4096) {
                    dictionary[key] = nextCode++
                    if (nextCode - 1 == (1 shl codeSize) && codeSize < 12) codeSize++
                } else {
                    writeCode(out, clearCode)
                    dictionary.clear()
                    codeSize = colorDepth + 1
                    nextCode = endCode + 1
                }
                prefix = suffix
            }
        }
        writeCode(out, prefix)
        writeCode(out, endCode)
        flushBits(out)
        flushAccumulator(out)
        out.write(0) // block terminator
    }

    private fun writeCode(out: OutputStream, code: Int) {
        bitBuffer = bitBuffer or (code shl bitCount)
        bitCount += codeSize
        while (bitCount >= 8) {
            append(out, (bitBuffer and 0xFF).toByte())
            bitBuffer = bitBuffer ushr 8
            bitCount -= 8
        }
    }

    private fun flushBits(out: OutputStream) {
        if (bitCount > 0) {
            append(out, (bitBuffer and 0xFF).toByte())
            bitBuffer = 0
            bitCount = 0
        }
    }

    private fun append(out: OutputStream, value: Byte) {
        accumulator[accumulatorSize++] = value
        if (accumulatorSize == 255) flushAccumulator(out)
    }

    private fun flushAccumulator(out: OutputStream) {
        if (accumulatorSize == 0) return
        out.write(accumulatorSize)
        out.write(accumulator, 0, accumulatorSize)
        accumulatorSize = 0
    }
}
