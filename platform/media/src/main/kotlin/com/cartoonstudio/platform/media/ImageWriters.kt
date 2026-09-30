package com.cartoonstudio.platform.media

import android.graphics.Bitmap
import com.cartoonstudio.core.common.AppError
import com.cartoonstudio.core.common.Outcome
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes individual frames as PNG files. */
object PngWriter {

    fun write(bitmap: Bitmap, target: File, quality: Int = 100): Outcome<Long> = try {
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, quality, stream)
            stream.flush()
        }
        Outcome.success(target.length())
    } catch (t: Throwable) {
        Outcome.failure(AppError.Io("Could not write ${target.name}", t))
    }

    fun toBytes(bitmap: Bitmap): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }
}

/**
 * Packs a project directory into a single portable `.cstudio` archive.
 *
 * Archives are plain zips so they can be inspected, backed up and restored
 * without the app — an explicit anti-lock-in decision.
 */
object ProjectArchiver {

    fun archive(projectRoot: File, target: File): Outcome<Long> = try {
        target.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zip ->
            projectRoot.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val relative = file.relativeTo(projectRoot).path.replace(File.separatorChar, '/')
                    zip.putNextEntry(ZipEntry(relative))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        Outcome.success(target.length())
    } catch (t: Throwable) {
        Outcome.failure(AppError.Io("Could not create the archive", t))
    }

    fun extract(archive: File, destination: File): Outcome<Unit> = try {
        destination.mkdirs()
        java.util.zip.ZipInputStream(archive.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = File(destination, entry.name)
                // Guard against path traversal in untrusted archives.
                if (!outFile.canonicalPath.startsWith(destination.canonicalPath)) {
                    return Outcome.failure(AppError.Invalid("Archive contains an unsafe path"))
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        Outcome.success(Unit)
    } catch (t: Throwable) {
        Outcome.failure(AppError.Io("Could not extract the archive", t))
    }
}
