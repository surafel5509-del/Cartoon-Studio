package com.cartoonstudio.feature.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cartoonstudio.core.time.Timecode
import com.cartoonstudio.data.assetstore.AnimationLibrary
import com.cartoonstudio.domain.drawing.Cel
import com.cartoonstudio.domain.model.LayerContent
import com.cartoonstudio.engine.rendering.RenderOverlayOptions
import com.cartoonstudio.feature.animation.ClipLibraryPanel
import com.cartoonstudio.feature.animation.CurveEditor
import com.cartoonstudio.feature.assets.AssetBrowserPanel
import com.cartoonstudio.feature.audio.AudioCallbacks
import com.cartoonstudio.feature.audio.AudioPanel
import com.cartoonstudio.feature.audio.AddAudioTrackRow
import com.cartoonstudio.feature.compositing.LayerCallbacks
import com.cartoonstudio.feature.compositing.LayerPropertiesPanel
import com.cartoonstudio.feature.compositing.LayersPanel
import com.cartoonstudio.feature.drawing.BrushPanel
import com.cartoonstudio.feature.drawing.CanvasCallbacks
import com.cartoonstudio.feature.drawing.CanvasContent
import com.cartoonstudio.feature.drawing.ColorPanel
import com.cartoonstudio.feature.drawing.StudioCanvas
import com.cartoonstudio.feature.drawing.ToolRail
import com.cartoonstudio.feature.export.ExportScreen
import com.cartoonstudio.feature.scenes.CameraPanel
import com.cartoonstudio.feature.scenes.SceneCallbacks
import com.cartoonstudio.feature.scenes.ScenePropertiesPanel
import com.cartoonstudio.feature.scenes.ScenesPanel
import com.cartoonstudio.feature.timeline.TimelineCallbacks
import com.cartoonstudio.feature.timeline.TimelinePanel
import com.cartoonstudio.feature.timeline.TimelineState
import com.cartoonstudio.ui.components.EmptyState
import com.cartoonstudio.ui.components.PanelDivider
import com.cartoonstudio.ui.components.ToolButton
import com.cartoonstudio.ui.designsystem.spacing

/**
 * The full editing workspace.
 *
 * Layout is a classic DCC shell — tool rail, canvas, inspector, timeline —
 * assembled from stateless sub-feature composables. All state lives in
 * [EditorViewModel]; this function only routes it.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scene = state.scene

    Column(modifier = modifier.fillMaxSize()) {
        EditorTopBar(
            title = state.project.name,
            subtitle = scene?.let {
                "${it.name} · ${Timecode.format(state.frame, state.frameRate)}"
            }.orEmpty(),
            isSaving = state.isSaving,
            hasUnsavedChanges = state.hasUnsavedChanges,
            onBack = {
                viewModel.save()
                onExit()
            },
            onSave = { viewModel.save() },
            onExport = { viewModel.showExportSheet(true) },
            onSettings = onOpenSettings,
            onToggleTimeline = viewModel::toggleTimeline,
            onTogglePanel = viewModel::togglePanel,
        )

        if (scene == null) {
            EmptyState(
                icon = Icons.Filled.Movie,
                title = "This project has no shots",
                message = "Add a shot to start animating.",
            )
            return@Column
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ToolRail(
                selected = state.tool,
                onSelect = viewModel::selectTool,
                canUndo = state.history.canUndo,
                canRedo = state.history.canRedo,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
            )
            PanelDivider()

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val overlays = remember(state.onionSkin, state.selectedLayerId, state.frame, scene) {
                    buildOverlays(state)
                }
                StudioCanvas(
                    content = CanvasContent(
                        scene = scene,
                        settings = state.project.settings,
                        frame = state.frame,
                        overlays = overlays,
                    ),
                    tool = state.tool,
                    brush = state.brush,
                    color = state.color,
                    view = state.view,
                    callbacks = remember(viewModel) {
                        CanvasCallbacks(
                            onStrokeCommitted = viewModel::commitStroke,
                            onViewChanged = viewModel::setView,
                            onColorPicked = viewModel::selectColor,
                        )
                    },
                    clipResolver = AnimationLibrary::resolve,
                    modifier = Modifier.fillMaxSize(),
                )

                state.message?.let { message ->
                    LaunchedEffect(message.id) {
                        kotlinx.coroutines.delay(2600)
                        viewModel.dismissMessage()
                    }
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(MaterialTheme.spacing.large),
                    ) { Text(message.text) }
                }
            }

            AnimatedVisibility(visible = state.panelVisible) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    PanelDivider()
                    InspectorPanel(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier
                            .width(MaterialTheme.spacing.panelWidth)
                            .fillMaxHeight(),
                    )
                    PanelRail(active = state.activePanel, onSelect = viewModel::selectPanel)
                }
            }
        }

        AnimatedVisibility(visible = state.timelineVisible) {
            Column {
                PanelDivider(vertical = false, modifier = Modifier.fillMaxWidth().height(1.dp))
                TimelinePanel(
                    state = TimelineState(
                        scene = scene,
                        frame = state.frame,
                        frameRate = state.frameRate,
                        isPlaying = state.isPlaying,
                        loop = state.loopPlayback,
                        selectedLayerId = state.selectedLayerId,
                    ),
                    callbacks = remember(viewModel) {
                        TimelineCallbacks(
                            onSeek = viewModel::seek,
                            onTogglePlay = viewModel::togglePlayback,
                            onToggleLoop = viewModel::toggleLoop,
                            onStep = viewModel::step,
                            onJumpToStart = viewModel::jumpToStart,
                            onJumpToEnd = viewModel::jumpToEnd,
                            onSelectLayer = viewModel::selectLayer,
                            onToggleKeyframe = viewModel::toggleKeyframe,
                            onMoveKeyframe = viewModel::moveKeyframe,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MaterialTheme.spacing.timelineHeight),
                )
            }
        }
    }

    if (state.showExportSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.showExportSheet(false) },
            sheetState = sheetState,
        ) {
            ExportScreen(
                project = state.project,
                settings = state.exportSettings,
                progress = state.exportProgress,
                onSettingsChange = viewModel::setExportSettings,
                onStartExport = viewModel::startExport,
                onCancel = viewModel::cancelExport,
                onShare = { viewModel.showExportSheet(false) },
                onClose = { viewModel.showExportSheet(false) },
                modifier = Modifier.fillMaxHeight(0.92f),
            )
        }
    }
}

/** Onion skin needs the neighbouring cels of the *selected* drawing layer. */
private fun buildOverlays(state: EditorUiState): RenderOverlayOptions {
    val layer = state.selectedLayer
    val drawing = layer?.content as? LayerContent.Drawing
    val onion = state.onionSkin

    val ghosts: List<Pair<Int, Cel>> = if (drawing != null && onion.enabled) {
        val current = state.frame.index
        drawing.cels
            .filterKeys { index ->
                index != current &&
                    index >= current - onion.framesBefore &&
                    index <= current + onion.framesAfter
            }
            .map { (index, cel) -> index to cel }
            .sortedBy { it.first }
    } else {
        emptyList()
    }

    return RenderOverlayOptions(
        onionSkin = onion,
        onionSkinLayerId = layer?.id,
        onionSkinCels = ghosts,
        selectedLayerIds = setOfNotNull(state.selectedLayerId),
        showSelectionBounds = true,
        showSafeArea = state.project.settings.safeAreaEnabled,
        showGrid = state.project.settings.gridEnabled,
        gridSpacing = state.project.settings.gridSpacing,
        canvasBounds = state.project.settings.canvasBounds,
    )
}

@Composable
private fun EditorTopBar(
    title: String,
    subtitle: String,
    isSaving: Boolean,
    hasUnsavedChanges: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit,
    onSettings: () -> Unit,
    onToggleTimeline: () -> Unit,
    onTogglePanel: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.small, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (isSaving) "Saving…" else subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleTimeline) {
                Icon(Icons.Filled.ViewTimeline, contentDescription = "Toggle timeline")
            }
            IconButton(onClick = onTogglePanel) {
                Icon(Icons.Filled.Tune, contentDescription = "Toggle inspector")
            }
            IconButton(onClick = onSave) {
                Icon(
                    Icons.Filled.Save,
                    contentDescription = "Save",
                    tint = if (hasUnsavedChanges) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Filled.FileDownload, contentDescription = "Export")
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = "Settings")
            }
        }
    }
}

@Composable
private fun PanelRail(active: EditorPanel, onSelect: (EditorPanel) -> Unit) {
    Column(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EditorPanel.entries.forEach { panel ->
            ToolButton(
                icon = panel.icon,
                label = panel.title,
                selected = panel == active,
                onClick = { onSelect(panel) },
            )
        }
    }
}

private val EditorPanel.icon: ImageVector
    get() = when (this) {
        EditorPanel.Layers -> Icons.Filled.Layers
        EditorPanel.Brush -> Icons.Filled.Brush
        EditorPanel.Color -> Icons.Filled.ColorLens
        EditorPanel.Assets -> Icons.Filled.AddPhotoAlternate
        EditorPanel.Motion -> Icons.Filled.Animation
        EditorPanel.Shots -> Icons.Filled.Movie
        EditorPanel.Audio -> Icons.Filled.MusicNote
        EditorPanel.Properties -> Icons.Filled.Visibility
    }

@Composable
private fun InspectorPanel(
    state: EditorUiState,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    val scene = state.scene ?: return
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        when (state.activePanel) {
            EditorPanel.Layers -> LayersPanel(
                layers = scene.layers,
                selectedLayerId = state.selectedLayerId,
                callbacks = LayerCallbacks(
                    onSelect = viewModel::selectLayer,
                    onToggleVisible = viewModel::toggleLayerVisibility,
                    onToggleLocked = viewModel::toggleLayerLock,
                    onDelete = viewModel::deleteLayer,
                    onDuplicate = viewModel::duplicateLayer,
                    onMoveUp = { viewModel.moveLayer(it, -1) },
                    onMoveDown = { viewModel.moveLayer(it, 1) },
                    onUpdate = { viewModel.updateLayer(it) },
                ),
            )

            EditorPanel.Brush -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                BrushPanel(
                    brush = state.brush,
                    customBrushes = state.project.customBrushes,
                    onBrushSelected = viewModel::selectBrush,
                    onBrushChanged = viewModel::selectBrush,
                )
            }

            EditorPanel.Color -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ColorPanel(
                    color = state.color,
                    palettes = state.project.palettes,
                    recentColors = state.recentColors,
                    onColorSelected = viewModel::selectColor,
                )
            }

            EditorPanel.Assets -> AssetBrowserPanel(
                repository = viewModel.assetRepository,
                filter = state.assetFilter,
                onFilterChanged = viewModel::setAssetFilter,
                onAssetChosen = viewModel::placeAsset,
                onToggleFavorite = viewModel::toggleAssetFavorite,
            )

            EditorPanel.Motion -> Column {
                ClipLibraryPanel(
                    onApplyClip = viewModel::applyClip,
                    modifier = Modifier.weight(1f),
                )
                CurveEditor(
                    track = state.selectedLayer?.tracks?.tracks?.values?.firstOrNull(),
                    durationFrames = scene.durationFrames,
                    currentFrame = state.frame,
                )
            }

            EditorPanel.Shots -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ScenesPanel(
                    scenes = state.project.scenes,
                    activeSceneId = state.project.activeSceneId,
                    frameRate = state.frameRate,
                    callbacks = SceneCallbacks(
                        onSelect = viewModel::selectScene,
                        onAdd = viewModel::addScene,
                        onDuplicate = viewModel::duplicateScene,
                        onDelete = viewModel::deleteScene,
                        onMoveUp = { viewModel.moveScene(it, -1) },
                        onMoveDown = { viewModel.moveScene(it, 1) },
                        onUpdate = viewModel::updateScene,
                    ),
                    modifier = Modifier.height(260.dp),
                )
                ScenePropertiesPanel(scene = scene, onUpdate = viewModel::updateScene)
                CameraPanel(camera = scene.camera, onUpdate = viewModel::updateCamera)
            }

            EditorPanel.Audio -> Column {
                AudioPanel(
                    tracks = scene.audioTracks,
                    assets = emptyMap(),
                    lipSyncTracks = scene.lipSyncTracks,
                    currentFrame = state.frame,
                    frameRate = state.frameRate,
                    durationFrames = scene.durationFrames,
                    callbacks = AudioCallbacks(
                        onToggleMute = viewModel::toggleAudioMute,
                        onToggleSolo = viewModel::toggleAudioSolo,
                        onUpdateTrack = viewModel::updateAudioTrack,
                        onSeek = viewModel::seek,
                    ),
                    modifier = Modifier.weight(1f),
                )
                AddAudioTrackRow(onAddTrack = viewModel::addAudioTrack)
            }

            EditorPanel.Properties -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                LayerPropertiesPanel(
                    layer = state.selectedLayer,
                    onUpdate = { viewModel.updateLayer(it, "Layer Properties", mergeKey = "layer:${it.id}") },
                )
            }
        }
    }
}
