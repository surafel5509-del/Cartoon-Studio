package com.cartoonstudio.data.projectstore

import com.cartoonstudio.core.serialization.SchemaVersion
import kotlinx.serialization.Serializable
import java.io.File

/**
 * On-disk layout of a project, as specified in `docs/formats/PROJECT_FORMAT.md`:
 *
 * ```
 * project/
 *   manifest.json
 *   project.json
 *   scenes/
 *   assets/
 *   audio/
 *   thumbnails/
 *   metadata/
 * ```
 */
class ProjectPaths(val root: File) {
    val manifest: File get() = File(root, "manifest.json")
    val document: File get() = File(root, "project.json")
    val scenes: File get() = File(root, "scenes")
    val assets: File get() = File(root, "assets")
    val audio: File get() = File(root, "audio")
    val thumbnails: File get() = File(root, "thumbnails")
    val metadata: File get() = File(root, "metadata")
    val autosave: File get() = File(metadata, "autosave.json")
    val recovery: File get() = File(metadata, "recovery.json")
    val thumbnail: File get() = File(thumbnails, "cover.png")

    fun ensureDirectories() {
        listOf(root, scenes, assets, audio, thumbnails, metadata).forEach { it.mkdirs() }
    }

    fun totalSizeBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

/**
 * Small, fast-to-read header describing a project.
 *
 * The project browser only reads manifests, so listing hundreds of projects
 * never deserialises a single full document.
 */
@Serializable
data class ProjectManifest(
    val projectId: String,
    val name: String,
    val schemaVersion: SchemaVersion = SchemaVersion.CURRENT,
    val appVersion: String = "1.0.0",
    val createdAtMillis: Long = 0L,
    val modifiedAtMillis: Long = 0L,
    val revision: Long = 0L,
    val sceneCount: Int = 0,
    val layerCount: Int = 0,
    val durationFrames: Int = 0,
    val canvasWidth: Int = 1920,
    val canvasHeight: Int = 1080,
    val frameRateFps: Double = 24.0,
    val description: String = "",
    /** Set when the last save did not complete; triggers recovery on open. */
    val dirtyShutdown: Boolean = false,
)

/** What the project browser shows for one project. */
data class ProjectSummary(
    val projectId: String,
    val name: String,
    val modifiedAtMillis: Long,
    val sceneCount: Int,
    val layerCount: Int,
    val durationFrames: Int,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val frameRateFps: Double,
    val sizeBytes: Long,
    val thumbnailPath: String?,
    val needsRecovery: Boolean,
) {
    val aspectRatio: Float get() = canvasWidth.toFloat() / canvasHeight.toFloat()

    companion object {
        fun from(manifest: ProjectManifest, paths: ProjectPaths): ProjectSummary = ProjectSummary(
            projectId = manifest.projectId,
            name = manifest.name,
            modifiedAtMillis = manifest.modifiedAtMillis,
            sceneCount = manifest.sceneCount,
            layerCount = manifest.layerCount,
            durationFrames = manifest.durationFrames,
            canvasWidth = manifest.canvasWidth,
            canvasHeight = manifest.canvasHeight,
            frameRateFps = manifest.frameRateFps,
            sizeBytes = paths.totalSizeBytes(),
            thumbnailPath = paths.thumbnail.takeIf { it.exists() }?.absolutePath,
            needsRecovery = manifest.dirtyShutdown || paths.recovery.exists(),
        )
    }
}
