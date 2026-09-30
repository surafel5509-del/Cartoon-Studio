package com.cartoonstudio.platform.media

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import com.cartoonstudio.core.common.AppError
import com.cartoonstudio.core.common.Log
import com.cartoonstudio.core.common.Outcome
import java.io.File
import java.nio.ByteBuffer

/** Codec selection for the supported output containers. */
enum class VideoCodec(val mime: String, val muxerFormat: Int) {
    H264(MediaFormat.MIMETYPE_VIDEO_AVC, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4),
    Vp9(MediaFormat.MIMETYPE_VIDEO_VP9, MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM),
}

/**
 * Hardware-accelerated video encoder.
 *
 * Frames are pushed as bitmaps and converted to the encoder's preferred YUV
 * layout. Using ByteBuffer input (rather than a GL surface) keeps the export
 * path identical to the bitmap renderer used everywhere else, so exported
 * pixels match preview pixels exactly.
 */
class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val frameRate: Int,
    private val bitRate: Int,
    private val codec: VideoCodec = VideoCodec.H264,
    private val keyFrameIntervalSeconds: Int = 1,
) {

    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var frameIndex = 0L
    private var colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
    private val bufferInfo = MediaCodec.BufferInfo()
    private var yuvBuffer: ByteArray? = null
    private var argbBuffer: IntArray? = null

    fun start(output: File): Outcome<Unit> = try {
        val codecName = selectEncoder(codec.mime)
            ?: return Outcome.failure(AppError.Unsupported("${codec.name} encoding"))

        val format = MediaFormat.createVideoFormat(codec.mime, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyFrameIntervalSeconds)
        }

        encoder = MediaCodec.createByCodecName(codecName).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        output.parentFile?.mkdirs()
        muxer = MediaMuxer(output.absolutePath, codec.muxerFormat)
        frameIndex = 0
        Outcome.success(Unit)
    } catch (t: Throwable) {
        Log.e(TAG, "Encoder start failed", t)
        release()
        Outcome.failure(AppError.Io("Could not start the video encoder", t))
    }

    fun encodeFrame(bitmap: Bitmap): Outcome<Unit> {
        val codecInstance = encoder ?: return Outcome.failure(AppError.Invalid("Encoder not started"))
        return try {
            drainEncoder(endOfStream = false)
            val inputIndex = codecInstance.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex >= 0) {
                val buffer = codecInstance.getInputBuffer(inputIndex)
                    ?: return Outcome.failure(AppError.Io("No encoder input buffer"))
                val yuv = convert(bitmap)
                buffer.clear()
                buffer.put(yuv)
                val presentationTimeUs = frameIndex * 1_000_000L / frameRate
                codecInstance.queueInputBuffer(inputIndex, 0, yuv.size, presentationTimeUs, 0)
                frameIndex++
            }
            Outcome.success(Unit)
        } catch (t: Throwable) {
            Outcome.failure(AppError.Io("Frame encoding failed", t))
        }
    }

    fun finish(): Outcome<Unit> = try {
        val codecInstance = encoder
        if (codecInstance != null) {
            val inputIndex = codecInstance.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex >= 0) {
                codecInstance.queueInputBuffer(
                    inputIndex, 0, 0,
                    frameIndex * 1_000_000L / frameRate,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
            }
            drainEncoder(endOfStream = true)
        }
        release()
        Outcome.success(Unit)
    } catch (t: Throwable) {
        release()
        Outcome.failure(AppError.Io("Could not finalise the video file", t))
    }

    fun release() {
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        runCatching { if (muxerStarted) muxer?.stop() }
        runCatching { muxer?.release() }
        encoder = null
        muxer = null
        muxerStarted = false
        trackIndex = -1
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val codecInstance = encoder ?: return
        val muxerInstance = muxer ?: return
        while (true) {
            val status = codecInstance.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                status == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return

                status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = muxerInstance.addTrack(codecInstance.outputFormat)
                        muxerInstance.start()
                        muxerStarted = true
                    }
                }

                status >= 0 -> {
                    val encoded: ByteBuffer = codecInstance.getOutputBuffer(status) ?: continue
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && muxerStarted) {
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        muxerInstance.writeSampleData(trackIndex, encoded, bufferInfo)
                    }
                    codecInstance.releaseOutputBuffer(status, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    /** ARGB bitmap -> encoder colour format (NV12 or I420). */
    private fun convert(bitmap: Bitmap): ByteArray {
        val pixelCount = width * height
        val argb = argbBuffer ?: IntArray(pixelCount).also { argbBuffer = it }
        val yuv = yuvBuffer ?: ByteArray(pixelCount * 3 / 2).also { yuvBuffer = it }

        val source = if (bitmap.width == width && bitmap.height == height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        source.getPixels(argb, 0, width, 0, 0, width, height)

        val semiPlanar = colorFormat != MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        var uvIndex = pixelCount
        var vPlane = pixelCount + pixelCount / 4

        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = argb[y * width + x]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF

                val luma = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[y * width + x] = luma.coerceIn(16, 235).toByte()

                if (y % 2 == 0 && x % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    if (semiPlanar) {
                        yuv[uvIndex++] = u.coerceIn(16, 240).toByte()
                        yuv[uvIndex++] = v.coerceIn(16, 240).toByte()
                    } else {
                        yuv[uvIndex++] = u.coerceIn(16, 240).toByte()
                        yuv[vPlane++] = v.coerceIn(16, 240).toByte()
                    }
                }
            }
        }
        if (source !== bitmap) source.recycle()
        return yuv
    }

    /** Picks a hardware encoder that supports a colour format we can produce. */
    private fun selectEncoder(mime: String): String? {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in list.codecInfos) {
            if (!info.isEncoder) continue
            if (info.supportedTypes.none { it.equals(mime, ignoreCase = true) }) continue
            val capabilities = runCatching { info.getCapabilitiesForType(mime) }.getOrNull() ?: continue
            val preferred = capabilities.colorFormats.firstOrNull {
                it == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar ||
                    it == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            }
            if (preferred != null) {
                colorFormat = preferred
                return info.name
            }
        }
        return null
    }

    companion object {
        private const val TAG = "VideoEncoder"
        private const val TIMEOUT_US = 10_000L

        /** Encoders require even dimensions and have hard size limits. */
        fun alignSize(value: Int): Int = (value / 2) * 2
    }
}
