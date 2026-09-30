package com.cartoonstudio.domain.export

import com.cartoonstudio.core.time.FrameRange
import com.cartoonstudio.core.time.FrameRate
import com.cartoonstudio.domain.model.ProjectSettings
import kotlinx.serialization.Serializable

/** Output container / codec combination. */
@Serializable
enum class ExportFormat(val displayName: String, val extension: String, val supportsAlpha: Boolean) {
    Mp4H264("MP4 · H.264", "mp4", false),
    WebmVp9("WebM · VP9", "webm", true),
    PngSequence("PNG Sequence", "png", true),
    AnimatedGif("Animated GIF", "gif", false),
    SinglePng("Single Frame (PNG)", "png", true),
    ProjectArchive("Project Archive", "cstudio", false);

    val isVideo: Boolean get() = this == Mp4H264 || this == WebmVp9
}

/** Convenience quality tiers mapped onto concrete bitrates. */
@Serializable
enum class ExportQuality(val displayName: String, val bitsPerPixelPerFrame: Float) {
    Draft("Draft", 0.05f),
    Standard("Standard", 0.10f),
    High("High", 0.18f),
    Master("Master", 0.30f),
}

/** What part of the project to render. */
@Serializable
sealed interface ExportScope {
    @Serializable
    data class SingleScene(val sceneId: String) : ExportScope

    @Serializable
    data class SceneRange(val sceneId: String, val range: FrameRange) : ExportScope

    @Serializable
    data object WholeProject : ExportScope
}

/**
 * A fully specified render request.
 *
 * Export is deterministic: the same project plus the same settings must always
 * produce byte-identical frames, so nothing here may depend on device state.
 */
@Serializable
data class ExportSettings(
    val format: ExportFormat = ExportFormat.Mp4H264,
    val scope: ExportScope = ExportScope.WholeProject,
    val quality: ExportQuality = ExportQuality.High,
    /** Output size multiplier applied to the project canvas. */
    val resolutionScale: Float = 1f,
    val frameRate: FrameRate = FrameRate.Default,
    val transparentBackground: Boolean = false,
    val includeAudio: Boolean = true,
    /** Render every Nth frame; 2 turns 24 fps into "on twos". */
    val frameStep: Int = 1,
    val fileNameStem: String = "export",
    val loopGif: Boolean = true,
) {
    fun outputWidth(settings: ProjectSettings): Int =
        alignEven((settings.canvasWidth * resolutionScale).toInt().coerceAtLeast(16))

    fun outputHeight(settings: ProjectSettings): Int =
        alignEven((settings.canvasHeight * resolutionScale).toInt().coerceAtLeast(16))

    /** Target bitrate for video encoders, derived from size, rate and quality. */
    fun bitrate(settings: ProjectSettings): Int {
        val pixels = outputWidth(settings).toLong() * outputHeight(settings).toLong()
        val perSecond = pixels * frameRate.fps * quality.bitsPerPixelPerFrame
        return perSecond.toInt().coerceIn(500_000, 80_000_000)
    }

    fun validate(): String? = when {
        resolutionScale <= 0f -> "Resolution scale must be greater than zero"
        frameStep < 1 -> "Frame step must be at least 1"
        fileNameStem.isBlank() -> "File name cannot be empty"
        else -> null
    }

    companion object {
        /** Encoders require even dimensions. */
        private fun alignEven(value: Int) = if (value % 2 == 0) value else value + 1

        val SocialVertical = ExportSettings(
            format = ExportFormat.Mp4H264,
            quality = ExportQuality.High,
            resolutionScale = 1f,
        )
        val QuickPreview = ExportSettings(
            format = ExportFormat.Mp4H264,
            quality = ExportQuality.Draft,
            resolutionScale = 0.5f,
        )
        val FrameSequence = ExportSettings(
            format = ExportFormat.PngSequence,
            quality = ExportQuality.Master,
            transparentBackground = true,
        )
    }
}

/** A named, reusable export configuration shown in the export screen. */
data class ExportPreset(
    val id: String,
    val name: String,
    val description: String,
    val settings: ExportSettings,
) {
    companion object {
        val presets = listOf(
            ExportPreset("exp_share", "Share Video", "MP4 · full size · high quality", ExportSettings.SocialVertical),
            ExportPreset("exp_preview", "Quick Preview", "MP4 · half size · fast", ExportSettings.QuickPreview),
            ExportPreset(
                "exp_frames", "Frame Sequence", "Transparent PNG per frame",
                ExportSettings.FrameSequence,
            ),
            ExportPreset(
                "exp_gif", "Animated GIF", "Looping GIF for messaging",
                ExportSettings(format = ExportFormat.AnimatedGif, resolutionScale = 0.5f, frameStep = 2),
            ),
            ExportPreset(
                "exp_archive", "Project Archive", "Back up or move the whole project",
                ExportSettings(format = ExportFormat.ProjectArchive),
            ),
        )
    }
}

/** Outcome of a completed render. */
data class ExportResult(
    val outputPath: String,
    val frameCount: Int,
    val widthPixels: Int,
    val heightPixels: Int,
    val durationMillis: Long,
    val fileSizeBytes: Long,
    val format: ExportFormat,
)
