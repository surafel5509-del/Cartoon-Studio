package com.cartoonstudio.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cartoonstudio.core.common.Ids
import com.cartoonstudio.core.common.Log
import com.cartoonstudio.core.common.ProgressReporter
import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.core.undo.Command
import com.cartoonstudio.core.undo.UndoStack
import com.cartoonstudio.data.assetstore.AssetInstancer
import com.cartoonstudio.data.assetstore.AssetRepository
import com.cartoonstudio.domain.animation.AnimationClip
import com.cartoonstudio.domain.camera.Camera
import com.cartoonstudio.domain.audio.AudioRole
import com.cartoonstudio.domain.audio.AudioTrack
import com.cartoonstudio.domain.drawing.Brush
import com.cartoonstudio.domain.drawing.Rgba
import com.cartoonstudio.domain.drawing.Stroke
import com.cartoonstudio.domain.export.ExportSettings
import com.cartoonstudio.domain.model.AssetCategory
import com.cartoonstudio.domain.model.AssetDescriptor
import com.cartoonstudio.domain.model.AssetFilter
import com.cartoonstudio.domain.model.Layer
import com.cartoonstudio.domain.model.LayerKind
import com.cartoonstudio.domain.model.OnionSkinSettings
import com.cartoonstudio.domain.model.Project
import com.cartoonstudio.domain.model.ProjectFactory
import com.cartoonstudio.domain.model.Scene
import com.cartoonstudio.feature.drawing.CanvasViewState
import com.cartoonstudio.feature.drawing.DrawingTool
import com.cartoonstudio.feature.export.ExportProgress
import com.cartoonstudio.platform.android.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The single owner of editing state.
 *
 * Sub-feature composables are stateless: they receive plain domain types and
 * emit intents, and every intent funnels through [execute] so that undo,
 * autosave and the "unsaved changes" flag can never drift out of sync with the
 * document.
 */
class EditorViewModel(
    private val container: AppContainer,
    initialProject: Project,
) : ViewModel() {

    private val undoStack = UndoStack<Project>(limit = 250)

    private val internalState = MutableStateFlow(
        EditorUiState(
            project = initialProject,
            selectedLayerId = initialProject.activeScene?.layers?.firstOrNull { it.kind == LayerKind.Drawing }?.id
                ?: initialProject.activeScene?.layers?.firstOrNull()?.id,
        ),
    )
    val state: StateFlow<EditorUiState> = internalState.asStateFlow()

    val assetRepository: AssetRepository get() = container.assetRepository

    private var playbackJob: Job? = null
    private var autosaveJob: Job? = null
    private var exportJob: Job? = null

    init {
        startAutosave()
    }

    // ------------------------------------------------------------- mutations

    /** Validate → apply → record → notify. */
    private fun execute(command: Command<Project>) {
        val current = internalState.value.project
        val result = undoStack.execute(current, command)
        if (!result.applied) {
            showMessage(result.rejection ?: "That change is not allowed here")
            return
        }
        internalState.update {
            it.copy(
                project = result.state,
                history = undoStack.state(),
                hasUnsavedChanges = true,
            )
        }
    }

    fun undo() {
        val reverted = undoStack.undo(internalState.value.project)
        internalState.update {
            it.copy(project = reverted, history = undoStack.state(), hasUnsavedChanges = true)
        }
    }

    fun redo() {
        val reapplied = undoStack.redo(internalState.value.project)
        internalState.update {
            it.copy(project = reapplied, history = undoStack.state(), hasUnsavedChanges = true)
        }
    }

    // --------------------------------------------------------------- session

    fun selectTool(tool: DrawingTool) = internalState.update { it.copy(tool = tool) }

    fun selectBrush(brush: Brush) = internalState.update { it.copy(brush = brush) }

    fun selectColor(color: Rgba) = internalState.update { current ->
        current.copy(
            color = color,
            recentColors = (listOf(color) + current.recentColors.filterNot { it == color }).take(12),
        )
    }

    fun selectLayer(layerId: String?) = internalState.update { it.copy(selectedLayerId = layerId) }

    fun selectPanel(panel: EditorPanel) = internalState.update {
        it.copy(activePanel = panel, panelVisible = true)
    }

    fun togglePanel() = internalState.update { it.copy(panelVisible = !it.panelVisible) }

    fun toggleTimeline() = internalState.update { it.copy(timelineVisible = !it.timelineVisible) }

    fun setView(view: CanvasViewState) = internalState.update { it.copy(view = view) }

    fun setOnionSkin(settings: OnionSkinSettings) = internalState.update { it.copy(onionSkin = settings) }

    fun toggleOnionSkin() = internalState.update {
        it.copy(onionSkin = it.onionSkin.copy(enabled = !it.onionSkin.enabled))
    }

    fun setAssetFilter(filter: AssetFilter) = internalState.update { it.copy(assetFilter = filter) }

    fun dismissMessage() = internalState.update { it.copy(message = null) }

    private fun showMessage(text: String) = internalState.update {
        it.copy(message = EditorMessage(System.currentTimeMillis(), text))
    }

    // -------------------------------------------------------------- playback

    fun seek(frame: Frame) {
        val last = internalState.value.lastFrame
        internalState.update { it.copy(frame = Frame(frame.index.coerceIn(0, last.index))) }
    }

    fun step(delta: Int) = seek(Frame(internalState.value.frame.index + delta))

    fun jumpToStart() = seek(Frame.ZERO)

    fun jumpToEnd() = seek(internalState.value.lastFrame)

    fun toggleLoop() = internalState.update { it.copy(loopPlayback = !it.loopPlayback) }

    /**
     * Playback advances on wall-clock time rather than one frame per
     * composition, so a heavy scene plays at the right speed by dropping
     * frames instead of running in slow motion.
     */
    fun togglePlayback() {
        if (internalState.value.isPlaying) {
            stopPlayback()
            return
        }
        internalState.update { it.copy(isPlaying = true) }
        playbackJob = viewModelScope.launch {
            val fps = internalState.value.frameRate.fps.coerceAtLeast(1.0)
            val frameNanos = (1_000_000_000.0 / fps).toLong()
            var previous = System.nanoTime()
            var accumulator = 0L
            while (isActive) {
                delay(4)
                val now = System.nanoTime()
                accumulator += now - previous
                previous = now
                if (accumulator < frameNanos) continue

                val advance = (accumulator / frameNanos).toInt()
                accumulator %= frameNanos

                val current = internalState.value
                val next = current.frame.index + advance
                val last = current.lastFrame.index
                when {
                    next <= last -> internalState.update { it.copy(frame = Frame(next)) }
                    current.loopPlayback -> internalState.update {
                        it.copy(frame = Frame(if (last <= 0) 0 else next % (last + 1)))
                    }
                    else -> {
                        internalState.update { it.copy(frame = Frame(last), isPlaying = false) }
                        return@launch
                    }
                }
            }
        }
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        internalState.update { it.copy(isPlaying = false) }
    }

    // --------------------------------------------------------------- drawing

    fun commitStroke(stroke: Stroke) {
        val current = internalState.value
        val layerId = current.selectedLayerId
        if (layerId == null || !current.canDraw) {
            showMessage("Select a drawing layer first")
            return
        }
        execute(EditorCommands.addStroke(current.project, layerId, current.frame, stroke))
    }

    fun addBlankCel() = withDrawingLayer { project, layerId, frame ->
        EditorCommands.addBlankCel(project, layerId, frame)
    }

    fun duplicateCel() = withDrawingLayer { project, layerId, frame ->
        EditorCommands.duplicateCel(project, layerId, frame)
    }

    fun clearCel() = withDrawingLayer { project, layerId, frame ->
        EditorCommands.clearCel(project, layerId, frame)
    }

    private fun withDrawingLayer(build: (Project, String, Frame) -> Command<Project>) {
        val current = internalState.value
        val layerId = current.selectedLayerId ?: run {
            showMessage("Select a layer first")
            return
        }
        execute(build(current.project, layerId, current.frame))
    }

    // ---------------------------------------------------------------- layers

    fun addDrawingLayer() {
        val project = internalState.value.project
        val layer = ProjectFactory.createDrawingLayer("Drawing ${(project.activeScene?.layers?.size ?: 0) + 1}")
        execute(EditorCommands.addLayer(project, layer))
        selectLayer(layer.id)
    }

    fun addGroupLayer() {
        val project = internalState.value.project
        execute(EditorCommands.addLayer(project, ProjectFactory.createGroupLayer()))
    }

    fun addTextLayer(text: String = "Title") {
        val project = internalState.value.project
        execute(EditorCommands.addLayer(project, ProjectFactory.createTextLayer(text, Vec2.ZERO)))
    }

    fun deleteLayer(layerId: String) {
        execute(EditorCommands.removeLayer(internalState.value.project, layerId))
        if (internalState.value.selectedLayerId == layerId) selectLayer(null)
    }

    fun duplicateLayer(layerId: String) =
        execute(EditorCommands.duplicateLayer(internalState.value.project, layerId))

    fun moveLayer(layerId: String, delta: Int) =
        execute(EditorCommands.moveLayer(internalState.value.project, layerId, delta))

    fun updateLayer(layer: Layer, label: String = "Edit Layer", mergeKey: String? = null) =
        execute(EditorCommands.updateLayer(internalState.value.project, layer, label, mergeKey))

    fun toggleLayerVisibility(layerId: String) = mutateLayer(layerId, "Toggle Visibility") {
        it.copy(visible = !it.visible)
    }

    fun toggleLayerLock(layerId: String) = mutateLayer(layerId, "Toggle Lock") {
        it.copy(locked = !it.locked)
    }

    private fun mutateLayer(layerId: String, label: String, transform: (Layer) -> Layer) {
        val project = internalState.value.project
        val layer = project.activeScene?.layer(layerId) ?: return
        execute(EditorCommands.updateLayer(project, transform(layer), label))
    }

    // ------------------------------------------------------------- animation

    fun toggleKeyframe(layerId: String, frame: Frame) =
        execute(EditorCommands.toggleKeyframe(internalState.value.project, layerId, frame))

    fun moveKeyframe(layerId: String, from: Frame, to: Frame) =
        execute(EditorCommands.moveKeyframe(internalState.value.project, layerId, from, to))

    fun applyClip(clip: AnimationClip) {
        val current = internalState.value
        val layerId = current.selectedLayerId ?: run {
            showMessage("Select a character layer first")
            return
        }
        execute(EditorCommands.applyClip(current.project, layerId, clip, current.frame))
    }

    // ----------------------------------------------------------------- shots

    fun selectScene(sceneId: String) = internalState.update {
        it.copy(
            project = it.project.copy(activeSceneId = sceneId),
            frame = Frame.ZERO,
            selectedLayerId = it.project.scene(sceneId)?.layers?.firstOrNull()?.id,
        )
    }

    fun addScene() {
        val project = internalState.value.project
        val scene = ProjectFactory.createScene(
            name = "Shot ${project.scenes.size + 1}",
            settings = project.settings,
        )
        execute(EditorCommands.addScene(project, scene))
    }

    fun deleteScene(sceneId: String) {
        if (internalState.value.project.scenes.size <= 1) {
            showMessage("A project needs at least one shot")
            return
        }
        execute(EditorCommands.removeScene(internalState.value.project, sceneId))
    }

    fun duplicateScene(sceneId: String) {
        val project = internalState.value.project
        val source = project.scene(sceneId) ?: return
        val copy = source.copy(
            id = Ids.next("scene"),
            name = "${source.name} copy",
            shotNumber = project.scenes.size + 1,
        )
        execute(EditorCommands.addScene(project, copy))
    }

    fun moveScene(sceneId: String, delta: Int) =
        execute(EditorCommands.moveScene(internalState.value.project, sceneId, delta))

    fun updateScene(scene: Scene) =
        execute(EditorCommands.updateScene(internalState.value.project, scene))

    fun updateCamera(camera: Camera) {
        val scene = internalState.value.scene ?: return
        execute(
            EditorCommands.updateScene(
                internalState.value.project,
                scene.copy(camera = camera),
                label = "Camera",
            ),
        )
    }

    // ----------------------------------------------------------------- audio

    fun addAudioTrack(role: AudioRole) {
        val scene = internalState.value.scene ?: return
        val track = AudioTrack(id = Ids.next("audio"), name = role.name, role = role)
        updateScene(scene.copy(audioTracks = scene.audioTracks + track))
    }

    fun updateAudioTrack(track: AudioTrack) {
        val scene = internalState.value.scene ?: return
        updateScene(
            scene.copy(audioTracks = scene.audioTracks.map { if (it.id == track.id) track else it }),
        )
    }

    fun toggleAudioMute(trackId: String) {
        val scene = internalState.value.scene ?: return
        scene.audioTracks.firstOrNull { it.id == trackId }?.let {
            updateAudioTrack(it.copy(muted = !it.muted))
        }
    }

    fun toggleAudioSolo(trackId: String) {
        val scene = internalState.value.scene ?: return
        scene.audioTracks.firstOrNull { it.id == trackId }?.let {
            updateAudioTrack(it.copy(solo = !it.solo))
        }
    }

    // ---------------------------------------------------------------- assets

    /** Places a catalog asset into the active shot at the canvas centre. */
    fun placeAsset(descriptor: AssetDescriptor) {
        val current = internalState.value
        val repository = container.assetRepository
        val layer: Layer? = when (descriptor.category) {
            AssetCategory.Character ->
                repository.character(descriptor.id)?.let {
                    AssetInstancer.instantiateCharacter(it, Vec2.ZERO)
                }

            AssetCategory.CharacterAnimation -> {
                repository.clipForDescriptor(descriptor.id)?.let { applyClip(it) }
                null
            }

            else -> repository.artwork(descriptor.id)?.let {
                AssetInstancer.instantiateArtwork(it, Vec2.ZERO)
            }
        }

        if (layer == null) {
            if (descriptor.category != AssetCategory.CharacterAnimation) {
                showMessage("${descriptor.name} cannot be placed in a scene")
            }
            return
        }

        repository.markUsed(descriptor.id)
        execute(EditorCommands.addLayer(current.project, layer))
        selectLayer(layer.id)
        showMessage("Added ${descriptor.name}")
    }

    fun toggleAssetFavorite(descriptor: AssetDescriptor) {
        container.assetRepository.toggleFavorite(descriptor.id)
        // Re-emit so the browser recomputes its page with the new flag.
        internalState.update { it.copy(assetFilter = it.assetFilter.copy()) }
    }

    // -------------------------------------------------------- save and export

    fun save(onSaved: () -> Unit = {}) {
        val project = internalState.value.project
        internalState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            container.projectRepository.save(project)
                .onSuccess {
                    internalState.update { it.copy(isSaving = false, hasUnsavedChanges = false) }
                    onSaved()
                }
                .onFailure { error ->
                    internalState.update { it.copy(isSaving = false) }
                    showMessage(error.message ?: "Could not save")
                }
        }
    }

    /**
     * Periodic crash-recovery snapshot.
     *
     * Autosave writes to a separate file and never touches the primary
     * document, so a crash mid-write can never corrupt the saved project.
     */
    private fun startAutosave() {
        autosaveJob = viewModelScope.launch {
            while (isActive) {
                val settings = container.preferences.settings.first()
                if (!settings.autosaveEnabled) {
                    delay(IDLE_POLL_MILLIS)
                    continue
                }
                delay(settings.autosaveIntervalSeconds.coerceIn(5, 300) * 1000L)
                val current = internalState.value
                if (!current.hasUnsavedChanges) continue
                container.projectRepository.autosave(current.project)
                    .onFailure { Log.w(TAG, "Autosave failed: ${it.message}") }
            }
        }
    }

    fun setExportSettings(settings: ExportSettings) =
        internalState.update { it.copy(exportSettings = settings) }

    fun showExportSheet(show: Boolean) = internalState.update {
        it.copy(showExportSheet = show, exportProgress = ExportProgress.Idle)
    }

    fun startExport() {
        val current = internalState.value
        exportJob?.cancel()
        internalState.update {
            it.copy(exportProgress = ExportProgress.Running(0f, "Preparing…"))
        }
        exportJob = viewModelScope.launch {
            val reporter = ProgressReporter { fraction, label ->
                internalState.update { it.copy(exportProgress = ExportProgress.Running(fraction, label)) }
            }
            container.exportEngine.export(current.project, current.exportSettings, reporter)
                .onSuccess { result ->
                    internalState.update { it.copy(exportProgress = ExportProgress.Finished(result)) }
                }
                .onFailure { error ->
                    internalState.update {
                        it.copy(exportProgress = ExportProgress.Failed(error.message ?: "Export failed"))
                    }
                }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        internalState.update { it.copy(exportProgress = ExportProgress.Idle) }
    }

    override fun onCleared() {
        stopPlayback()
        autosaveJob?.cancel()
        exportJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val TAG = "EditorViewModel"
        private const val IDLE_POLL_MILLIS = 5_000L

        fun factory(container: AppContainer, project: Project): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EditorViewModel(container, project) as T
            }
    }
}
