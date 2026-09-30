package com.cartoonstudio.feature.editor

import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.core.time.FrameRate
import com.cartoonstudio.core.undo.HistoryState
import com.cartoonstudio.domain.drawing.Brush
import com.cartoonstudio.domain.drawing.Rgba
import com.cartoonstudio.domain.export.ExportSettings
import com.cartoonstudio.domain.model.AssetFilter
import com.cartoonstudio.domain.model.Layer
import com.cartoonstudio.domain.model.LayerKind
import com.cartoonstudio.domain.model.OnionSkinSettings
import com.cartoonstudio.domain.model.Project
import com.cartoonstudio.domain.model.Scene
import com.cartoonstudio.feature.drawing.CanvasViewState
import com.cartoonstudio.feature.drawing.DrawingTool
import com.cartoonstudio.feature.export.ExportProgress

/** Which inspector is docked on the right-hand side. */
enum class EditorPanel(val title: String) {
    Layers("Layers"),
    Brush("Brush"),
    Color("Color"),
    Assets("Assets"),
    Motion("Motion"),
    Shots("Shots"),
    Audio("Audio"),
    Properties("Properties"),
}

/** Transient message shown in a snackbar. */
data class EditorMessage(val id: Long, val text: String)

/**
 * The complete, immutable state of an editing session.
 *
 * The document ([project]) is the only part that is persisted or undone;
 * everything else is session state that is deliberately thrown away when the
 * editor closes, which keeps saved files portable between devices.
 */
data class EditorUiState(
    val project: Project,
    val isLoading: Boolean = false,
    val frame: Frame = Frame.ZERO,
    val isPlaying: Boolean = false,
    val loopPlayback: Boolean = true,
    val tool: DrawingTool = DrawingTool.Brush,
    val brush: Brush = Brush.builtIn.first(),
    val color: Rgba = Rgba.Ink,
    val recentColors: List<Rgba> = emptyList(),
    val selectedLayerId: String? = null,
    val activePanel: EditorPanel = EditorPanel.Layers,
    val panelVisible: Boolean = true,
    val timelineVisible: Boolean = true,
    val view: CanvasViewState = CanvasViewState.Fit,
    val onionSkin: OnionSkinSettings = OnionSkinSettings.Default,
    val assetFilter: AssetFilter = AssetFilter(),
    val history: HistoryState = HistoryState(),
    val exportSettings: ExportSettings = ExportSettings(),
    val exportProgress: ExportProgress = ExportProgress.Idle,
    val showExportSheet: Boolean = false,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val message: EditorMessage? = null,
) {
    val scene: Scene? get() = project.activeScene

    val frameRate: FrameRate get() = project.settings.frameRate

    val selectedLayer: Layer?
        get() {
            val scene = scene ?: return null
            val id = selectedLayerId ?: return null
            return scene.layers.firstNotNullOfOrNull { it.findById(id) }
        }

    val lastFrame: Frame get() = scene?.lastFrame ?: Frame.ZERO

    val canDraw: Boolean
        get() = selectedLayer?.let { it.kind == LayerKind.Drawing && !it.locked } ?: false
}
