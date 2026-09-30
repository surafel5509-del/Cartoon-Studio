package com.cartoonstudio.domain.model

import com.cartoonstudio.core.math.Rect2
import com.cartoonstudio.core.time.FrameRate
import com.cartoonstudio.domain.drawing.Brush
import com.cartoonstudio.domain.drawing.Palette
import com.cartoonstudio.domain.drawing.Rgba
import kotlinx.serialization.Serializable

/** Canvas / document settings shared by every scene in a project. */
@Serializable
data class ProjectSettings(
    val canvasWidth: Int = 1920,
    val canvasHeight: Int = 1080,
    val frameRate: FrameRate = FrameRate.Default,
    val backgroundColor: Rgba = Rgba.Paper,
    val safeAreaEnabled: Boolean = false,
    val gridEnabled: Boolean = false,
    val gridSpacing: Float = 64f,
    val snapToGrid: Boolean = false,
    val defaultOnionSkin: OnionSkinSettings = OnionSkinSettings.Default,
) {
    val canvasBounds: Rect2 get() = Rect2.fromSize(canvasWidth.toFloat(), canvasHeight.toFloat())

    val aspectRatio: Float get() = canvasWidth.toFloat() / canvasHeight.toFloat()

    companion object {
        val HD = ProjectSettings(1920, 1080)
        val Square = ProjectSettings(1080, 1080)
        val Vertical = ProjectSettings(1080, 1920)
        val FourK = ProjectSettings(3840, 2160)
        val Classic43 = ProjectSettings(1440, 1080)
    }
}

/** A named canvas preset shown when creating a project. */
data class CanvasPreset(
    val id: String,
    val name: String,
    val description: String,
    val settings: ProjectSettings,
) {
    companion object {
        val presets = listOf(
            CanvasPreset("preset_hd", "HD Landscape", "1920 × 1080 · 16:9", ProjectSettings.HD),
            CanvasPreset("preset_vertical", "Vertical", "1080 × 1920 · 9:16", ProjectSettings.Vertical),
            CanvasPreset("preset_square", "Square", "1080 × 1080 · 1:1", ProjectSettings.Square),
            CanvasPreset("preset_4k", "4K UHD", "3840 × 2160 · 16:9", ProjectSettings.FourK),
            CanvasPreset("preset_classic", "Classic", "1440 × 1080 · 4:3", ProjectSettings.Classic43),
        )
    }
}

/**
 * The root persistent document.
 *
 * Everything the editor can undo lives inside this immutable tree; session
 * state (selection, panels, zoom) is deliberately kept outside so a project
 * saved on one device opens identically on another.
 */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val settings: ProjectSettings = ProjectSettings.HD,
    val scenes: List<Scene> = emptyList(),
    val activeSceneId: String? = null,
    val palettes: List<Palette> = Palette.builtIn,
    val customBrushes: List<Brush> = emptyList(),
    val createdAtMillis: Long = 0L,
    val modifiedAtMillis: Long = 0L,
    /** Increments on every committed edit; used for autosave and conflict checks. */
    val revision: Long = 0L,
    val description: String = "",
) {
    val activeScene: Scene?
        get() = scenes.firstOrNull { it.id == activeSceneId } ?: scenes.firstOrNull()

    val totalFrames: Int get() = scenes.sumOf { it.durationFrames }

    val durationMillis: Long get() = settings.frameRate.millisForFrames(totalFrames)

    val layerCount: Int get() = scenes.sumOf { it.allLayers().size }

    fun scene(sceneId: String): Scene? = scenes.firstOrNull { it.id == sceneId }

    fun withScene(scene: Scene): Project =
        copy(scenes = scenes.map { if (it.id == scene.id) scene else it })

    fun withSceneAdded(scene: Scene, atIndex: Int = scenes.size): Project {
        val list = scenes.toMutableList()
        list.add(atIndex.coerceIn(0, list.size), scene)
        return copy(scenes = list, activeSceneId = activeSceneId ?: scene.id)
    }

    fun withoutScene(sceneId: String): Project {
        if (scenes.size <= 1) return this
        val remaining = scenes.filterNot { it.id == sceneId }
        return copy(
            scenes = remaining,
            activeSceneId = if (activeSceneId == sceneId) remaining.firstOrNull()?.id else activeSceneId,
        )
    }

    fun withSceneMoved(fromIndex: Int, toIndex: Int): Project {
        if (fromIndex !in scenes.indices) return this
        val list = scenes.toMutableList()
        list.add(toIndex.coerceIn(0, list.size - 1), list.removeAt(fromIndex))
        return copy(scenes = list)
    }

    fun updateActiveScene(transform: (Scene) -> Scene): Project {
        val scene = activeScene ?: return this
        return withScene(transform(scene))
    }

    fun touched(nowMillis: Long): Project = copy(modifiedAtMillis = nowMillis, revision = revision + 1)
}
