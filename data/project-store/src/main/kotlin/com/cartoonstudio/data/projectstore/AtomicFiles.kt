package com.cartoonstudio.data.projectstore

import com.cartoonstudio.core.common.AppError
import com.cartoonstudio.core.common.Log
import com.cartoonstudio.core.common.Outcome
import java.io.File
import java.io.FileOutputStream

/**
 * Atomic file replacement.
 *
 * A project save must never leave a half-written document on disk: the app can
 * be killed at any moment on Android. Every write goes to a sibling temp file,
 * is flushed to storage, and only then replaces the target.
 */
object AtomicFiles {

    private const val TAG = "AtomicFiles"

    fun writeText(target: File, content: String): Outcome<Unit> = write(target, content.toByteArray())

    fun write(target: File, bytes: ByteArray): Outcome<Unit> {
        val parent = target.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return Outcome.failure(AppError.Io("Could not create ${parent.name}"))
        }
        val temp = File(target.absolutePath + ".tmp")
        val backup = File(target.absolutePath + ".bak")
        return try {
            FileOutputStream(temp).use { stream ->
                stream.write(bytes)
                stream.flush()
                // fsync: without this the rename can land before the data does.
                stream.fd.sync()
            }
            if (target.exists()) {
                backup.delete()
                target.renameTo(backup)
            }
            if (!temp.renameTo(target)) {
                // Fall back to a copy when rename fails (e.g. across mounts).
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            backup.delete()
            Outcome.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "Atomic write failed for ${target.name}", t)
            temp.delete()
            // Restore the previous good copy so the project stays openable.
            if (!target.exists() && backup.exists()) backup.renameTo(target)
            Outcome.failure(AppError.Io("Could not save ${target.name}", t))
        }
    }

    fun readText(source: File): Outcome<String> = try {
        when {
            source.exists() -> Outcome.success(source.readText())
            File(source.absolutePath + ".bak").exists() -> {
                Log.w(TAG, "Recovering ${source.name} from backup")
                Outcome.success(File(source.absolutePath + ".bak").readText())
            }
            else -> Outcome.failure(AppError.NotFound(source.name))
        }
    } catch (t: Throwable) {
        Outcome.failure(AppError.Io("Could not read ${source.name}", t))
    }

    fun deleteRecursively(target: File): Outcome<Unit> = try {
        if (target.exists() && !target.deleteRecursively()) {
            Outcome.failure(AppError.Io("Could not delete ${target.name}"))
        } else {
            Outcome.success(Unit)
        }
    } catch (t: Throwable) {
        Outcome.failure(AppError.Io("Could not delete ${target.name}", t))
    }
}
