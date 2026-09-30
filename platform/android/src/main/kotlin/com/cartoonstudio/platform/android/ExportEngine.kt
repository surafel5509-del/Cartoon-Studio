package com.cartoonstudio.platform.android

import android.content.Context
import android.os.Environment
import com.cartoonstudio.core.common.AppError
import com.cartoonstudio.core.common.DispatcherProvider
import com.cartoonstudio.core.common.Log
import com.cartoonstudio.core.common.Outcome
import com.cartoonstudio.core.common.ProgressReporter
import com.cartoonstudio.data.assetstore.AnimationLibrary
import com.cartoonstudio.data.projectstore.ProjectRepository
import com.cartoonstudio.domain.export.ExportFormat
import com.cartoonstudio.domain.export.ExportResult
import com.cartoonstudio.domain.export.ExportSettings
import com.cartoonstudio.domain.model.Project
import com.cartoonstudio.engine.export.ExportPlanner
import com.cartoonstudio.platform.graphics.SceneFrameRenderer
import com.cartoonstudio.platform.media.GifEncoder
import com.cartoonstudio.platform.media.PngWriter
import com.cartoonstudio.platform.media.ProjectArchiver
import com.cartoonstudio.platform.media.VideoCodec
import com.cartoonstudio.platform.media.VideoEncoder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Offline renderer.
 *
 * Export is a cancellable background job that reuses the exact same scene
 * evaluation and render graph as the interactive preview, so the result is
 * deterministic and matches what the artist saw.
 */
class ExportEngine(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val projectRepository: ProjectRepository,
) {

    private val frameRenderer = SceneFrameRenderer(clipResolver = AnimationLibrary::resolve)

    suspend fun export(
        project: Project,
        settings: ExportSettings,
        progress: ProgressReporter = ProgressReporter.None,
    ): Outcome<ExportResult> = withContext(dispatchers.default) {
        settings.validate()?.let { return@withContext Outcome.failure(AppError.Invalid(it)) }

        val plan = ExportPlanner.plan(project, settings)
        if (plan.isEmpty && settings.format != ExportFormat.ProjectArchive) {
            return@withContext Outcome.failure(AppError.Invalid("Nothing to export"))
        }

        val startedAt = System.currentTimeMillis()
        val stem = "${sanitize(settings.fileNameStem.ifBlank { project.name })}_${timestamp()}"

        val result = when (settings.format) {
            ExportFormat.Mp4H264 -> encodeVideo(project, settings, plan, stem, VideoCodec.H264, progress)
            ExportFormat.WebmVp9 -> encodeVideo(project, settings, plan, stem, VideoCodec.Vp9, progress)
            ExportFormat.AnimatedGif -> encodeGif(project, settings, plan, stem, progress)
            ExportFormat.PngSequence -> writeSequence(project, settings, plan, stem, progress)
            ExportFormat.SinglePng -> writeSingleFrame(project, settings, plan, stem, progress)
            ExportFormat.ProjectArchive -> archive(project, stem)
        }

        result.onSuccess {
            Log.i(TAG, "Export finished in ${System.currentTimeMillis() - startedAt} ms -> ${it.outputPath}")
        }
    }

    // -- formats ------------------------------------------------------------

    private suspend fun encodeVideo(
        project: Project,
        settings: ExportSettings,
        plan: com.cartoonstudio.engine.export.ExportPlan,
        stem: String,
        codec: VideoCodec,
        progress: ProgressReporter,
    ): Outcome<ExportResult> {
        val extension = if (codec == VideoCodec.H264) "mp4" else "webm"
        val output = outputFile("$stem.$extension")
        val encoder = VideoEncoder(
            width = VideoEncoder.alignSize(plan.widthPixels),
            height = VideoEncoder.alignSize(plan.heightPixels),
            frameRate = settings.frameRate.fps.toInt().coerceAtLeast(1),
            bitRate = settings.bitrate(project.settings),
            codec = codec,
        )

        val started = encoder.start(output)
        if (started is Outcome.Failure) return started

        var bitmap: android.graphics.Bitmap? = null
        try {
            plan.frames.forEachIndexed { index, planned ->
                currentCoroutineContext().ensureActive()
                bitmap = frameRenderer.renderToBitmap(
                    scene = planned.scene,
                    settings = project.settings,
                    frame = planned.sceneFrame,
                    widthPx = plan.widthPixels,
                    heightPx = plan.heightPixels,
                    reuse = bitmap,
                )
                val encoded = encoder.encodeFrame(bitmap!!)
                if (encoded is Outcome.Failure) {
                    encoder.release()
                    return encoded
                }
                progress.report(
                    (index + 1).toFloat() / plan.frameCount,
                    "Rendering frame ${index + 1} of ${plan.frameCount}",
                )
            }
            val finished = encoder.finish()
            if (finished is Outcome.Failure) return finished
        } finally {
            bitmap?.recycle()
        }

        return Outcome.success(
            ExportResult(
                outputPath = output.absolutePath,
                frameCount = plan.frameCount,
                widthPixels = plan.widthPixels,
                heightPixels = plan.heightPixels,
                durationMillis = plan.durationMillis,
                fileSizeBytes = output.length(),
                format = settings.format,
            )
        )
    }

    private suspend fun encodeGif(
        project: Project,
        settings: ExportSettings,
        plan: com.cartoonstudio.engine.export.ExportPlan,
        stem: String,
        progress: ProgressReporter,
    ): Outcome<ExportResult> {
        val output = outputFile("$stem.gif")
        val delay = (100.0 / settings.frameRate.fps * settings.frameStep).toInt().coerceAtLeast(2)
        val encoder = GifEncoder(plan.widthPixels, plan.heightPixels, delay, settings.loopGif)
        val started = encoder.start(output)
        if (started is Outcome.Failure) return started

        var bitmap: android.graphics.Bitmap? = null
        try {
            plan.frames.forEachIndexed { index, planned ->
                currentCoroutineContext().ensureActive()
                bitmap = frameRenderer.renderToBitmap(
                    planned.scene, project.settings, planned.sceneFrame,
                    plan.widthPixels, plan.heightPixels, reuse = bitmap,
                )
                val added = encoder.addFrame(bitmap!!)
                if (added is Outcome.Failure) {
                    encoder.release()
                    return added
                }
                progress.report((index + 1).toFloat() / plan.frameCount, "Encoding GIF frame ${index + 1}")
            }
            val finished = encoder.finish()
            if (finished is Outcome.Failure) return finished
        } finally {
            bitmap?.recycle()
        }

        return Outcome.success(
            ExportResult(
                output.absolutePath, plan.frameCount, plan.widthPixels, plan.heightPixels,
                plan.durationMillis, output.length(), settings.format,
            )
        )
    }

    private suspend fun writeSequence(
        project: Project,
        settings: ExportSettings,
        plan: com.cartoonstudio.engine.export.ExportPlan,
        stem: String,
        progress: ProgressReporter,
    ): Outcome<ExportResult> {
        val directory = File(outputDirectory(), stem).apply { mkdirs() }
        var totalBytes = 0L

        plan.frames.forEachIndexed { index, planned ->
            currentCoroutineContext().ensureActive()
            val bitmap = frameRenderer.renderToBitmap(
                planned.scene, project.settings, planned.sceneFrame,
                plan.widthPixels, plan.heightPixels, transparent = settings.transparentBackground,
            )
            val name = ExportPlanner.frameFileName(stem, index, plan.frameCount)
            val written = PngWriter.write(bitmap, File(directory, name))
            bitmap.recycle()
            if (written is Outcome.Failure) return written
            totalBytes += written.getOrElse(0L)
            progress.report((index + 1).toFloat() / plan.frameCount, "Writing $name")
        }

        return Outcome.success(
            ExportResult(
                directory.absolutePath, plan.frameCount, plan.widthPixels, plan.heightPixels,
                plan.durationMillis, totalBytes, settings.format,
            )
        )
    }

    private fun writeSingleFrame(
        project: Project,
        settings: ExportSettings,
        plan: com.cartoonstudio.engine.export.ExportPlan,
        stem: String,
        progress: ProgressReporter,
    ): Outcome<ExportResult> {
        val planned = plan.frames.firstOrNull()
            ?: return Outcome.failure(AppError.Invalid("No frame selected"))
        val bitmap = frameRenderer.renderToBitmap(
            planned.scene, project.settings, planned.sceneFrame,
            plan.widthPixels, plan.heightPixels, transparent = settings.transparentBackground,
        )
        val output = outputFile("$stem.png")
        val written = PngWriter.write(bitmap, output)
        bitmap.recycle()
        progress.report(1f, "Saved image")
        return written.map {
            ExportResult(
                output.absolutePath, 1, plan.widthPixels, plan.heightPixels, 0L, it, settings.format,
            )
        }
    }

    private fun archive(project: Project, stem: String): Outcome<ExportResult> {
        val root = projectRepository.pathsFor(project.id).root
        val output = outputFile("$stem.cstudio")
        return ProjectArchiver.archive(root, output).map { size ->
            ExportResult(
                output.absolutePath, 0, project.settings.canvasWidth, project.settings.canvasHeight,
                0L, size, ExportFormat.ProjectArchive,
            )
        }
    }

    // -- locations ----------------------------------------------------------

    /**
     * Scoped app storage. Files land in `Movies/Cartoon Studio` inside the
     * app's own external directory, so no storage permission is required on
     * any supported API level.
     */
    fun outputDirectory(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        return File(base, "Cartoon Studio").apply { mkdirs() }
    }

    private fun outputFile(name: String) = File(outputDirectory(), name)

    private fun sanitize(raw: String) = raw.trim()
        .map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
        .joinToString("")
        .take(48)
        .ifBlank { "export" }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private companion object {
        const val TAG = "ExportEngine"
    }
}
