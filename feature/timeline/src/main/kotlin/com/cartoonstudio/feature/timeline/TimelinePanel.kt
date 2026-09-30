package com.cartoonstudio.feature.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.core.time.FrameRate
import com.cartoonstudio.core.time.Timecode
import com.cartoonstudio.domain.model.Layer
import com.cartoonstudio.domain.model.LayerContent
import com.cartoonstudio.domain.model.Scene
import com.cartoonstudio.ui.components.ToolButton
import com.cartoonstudio.ui.designsystem.spacing

/** Everything the timeline needs to render. */
data class TimelineState(
    val scene: Scene,
    val frame: Frame,
    val frameRate: FrameRate,
    val isPlaying: Boolean,
    val loop: Boolean,
    val selectedLayerId: String?,
    val framesPerPixel: Float = 0.14f,
)

/** Timeline interactions. */
data class TimelineCallbacks(
    val onSeek: (Frame) -> Unit = {},
    val onTogglePlay: () -> Unit = {},
    val onToggleLoop: () -> Unit = {},
    val onStep: (Int) -> Unit = {},
    val onJumpToStart: () -> Unit = {},
    val onJumpToEnd: () -> Unit = {},
    val onSelectLayer: (String) -> Unit = {},
    val onToggleKeyframe: (String, Frame) -> Unit = { _, _ -> },
    val onMoveKeyframe: (String, Frame, Frame) -> Unit = { _, _, _ -> },
)

/**
 * Dope sheet and transport.
 *
 * One row per layer, keyframes as diamonds, exposed drawing cels as filled
 * blocks, plus scene markers and a draggable playhead.
 */
@Composable
fun TimelinePanel(
    state: TimelineState,
    callbacks: TimelineCallbacks,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val pixelsPerFrame = remember(state.framesPerPixel) {
        (1f / state.framesPerPixel).coerceIn(2f, 60f)
    }
    val layers = remember(state.scene) { state.scene.allLayers() }
    val totalWidthDp = with(density) {
        (state.scene.durationFrames * pixelsPerFrame).toDp()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        TransportBar(state, callbacks)

        Row(modifier = Modifier.fillMaxWidth().height(MaterialTheme.spacing.timelineHeight)) {
            // Layer name gutter
            Column(modifier = Modifier.width(150.dp).fillMaxHeight()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        "  Layers",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(layers.size) { index ->
                        val layer = layers[index]
                        LayerRowLabel(
                            layer = layer,
                            selected = layer.id == state.selectedLayerId,
                            onClick = { callbacks.onSelectLayer(layer.id) },
                        )
                    }
                }
            }

            com.cartoonstudio.ui.components.PanelDivider(modifier = Modifier.fillMaxHeight())

            // Track area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scroll)
                    .clipToBounds(),
            ) {
                Column(modifier = Modifier.width(totalWidthDp)) {
                    TimeRuler(
                        durationFrames = state.scene.durationFrames,
                        frameRate = state.frameRate,
                        pixelsPerFrame = pixelsPerFrame,
                        currentFrame = state.frame,
                        onSeek = callbacks.onSeek,
                    )
                    TrackArea(
                        layers = layers,
                        state = state,
                        pixelsPerFrame = pixelsPerFrame,
                        callbacks = callbacks,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportBar(state: TimelineState, callbacks: TimelineCallbacks) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = MaterialTheme.spacing.small, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ToolButton(Icons.Filled.KeyboardDoubleArrowLeft, "Go to start", false, callbacks.onJumpToStart)
        ToolButton(Icons.Filled.NavigateBefore, "Previous frame", false, { callbacks.onStep(-1) })
        ToolButton(
            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (state.isPlaying) "Pause" else "Play",
            state.isPlaying,
            callbacks.onTogglePlay,
        )
        ToolButton(Icons.Filled.NavigateNext, "Next frame", false, { callbacks.onStep(1) })
        ToolButton(Icons.Filled.KeyboardDoubleArrowRight, "Go to end", false, callbacks.onJumpToEnd)
        ToolButton(Icons.Filled.Repeat, "Loop", state.loop, callbacks.onToggleLoop)

        Text(
            text = Timecode.format(state.frame, state.frameRate),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = MaterialTheme.spacing.small),
        )
        Text(
            text = "frame ${state.frame.index + 1} / ${state.scene.durationFrames}  ·  " +
                "${state.frameRate.fps.toInt()} fps",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = MaterialTheme.spacing.small),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LayerRowLabel(layer: Layer, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MaterialTheme.spacing.trackHeight)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.FiberManualRecord,
            contentDescription = null,
            tint = when {
                !layer.visible -> MaterialTheme.colorScheme.outline
                layer.isAnimated -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(8.dp),
        )
        Text(
            text = "  ${layer.name}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TimeRuler(
    durationFrames: Int,
    frameRate: FrameRate,
    pixelsPerFrame: Float,
    currentFrame: Frame,
    onSeek: (Frame) -> Unit,
) {
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val playheadColor = MaterialTheme.colorScheme.primary
    val secondStep = frameRate.fps.toInt().coerceAtLeast(1)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(pixelsPerFrame, durationFrames) {
                detectTapGestures { offset ->
                    onSeek(Frame((offset.x / pixelsPerFrame).toInt().coerceIn(0, durationFrames - 1)))
                }
            }
            .pointerInput(pixelsPerFrame, durationFrames) {
                detectDragGestures { change, _ ->
                    onSeek(
                        Frame((change.position.x / pixelsPerFrame).toInt().coerceIn(0, durationFrames - 1)),
                    )
                }
            },
    ) {
        for (frameIndex in 0 until durationFrames) {
            val x = frameIndex * pixelsPerFrame
            val isSecond = frameIndex % secondStep == 0
            if (!isSecond && pixelsPerFrame < 6f) continue
            drawLine(
                color = tickColor.copy(alpha = if (isSecond) 0.8f else 0.25f),
                start = Offset(x, if (isSecond) 6f else 14f),
                end = Offset(x, size.height),
                strokeWidth = 1f,
            )
        }
        val playheadX = currentFrame.index * pixelsPerFrame
        drawLine(
            color = playheadColor,
            start = Offset(playheadX, 0f),
            end = Offset(playheadX, size.height),
            strokeWidth = 2f,
        )
    }
}

@Composable
private fun TrackArea(
    layers: List<Layer>,
    state: TimelineState,
    pixelsPerFrame: Float,
    callbacks: TimelineCallbacks,
) {
    val rowHeightDp = MaterialTheme.spacing.trackHeight
    val rowHeightPx = with(LocalDensity.current) { rowHeightDp.toPx() }
    val keyColor = MaterialTheme.colorScheme.tertiary
    val celColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val playheadColor = MaterialTheme.colorScheme.primary
    val selectedTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeightDp * layers.size.coerceAtLeast(1))
            .pointerInput(layers, pixelsPerFrame) {
                detectTapGestures { offset ->
                    val rowIndex = (offset.y / rowHeightPx).toInt()
                    val layer = layers.getOrNull(rowIndex) ?: return@detectTapGestures
                    val frame = Frame((offset.x / pixelsPerFrame).toInt().coerceAtLeast(0))
                    callbacks.onSelectLayer(layer.id)
                    callbacks.onToggleKeyframe(layer.id, frame)
                }
            },
    ) {
        layers.forEachIndexed { rowIndex, layer ->
            val top = rowIndex * rowHeightPx
            if (layer.id == state.selectedLayerId) {
                drawRect(
                    color = selectedTint,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, rowHeightPx),
                )
            }
            drawLine(
                color = gridColor,
                start = Offset(0f, top + rowHeightPx),
                end = Offset(size.width, top + rowHeightPx),
                strokeWidth = 1f,
            )

            // Exposed drawing cels
            (layer.content as? LayerContent.Drawing)?.let { drawing ->
                val exposed = drawing.exposedFrames()
                exposed.forEachIndexed { index, frameIndex ->
                    val nextFrame = exposed.getOrNull(index + 1) ?: state.scene.durationFrames
                    val x = frameIndex * pixelsPerFrame
                    val width = ((nextFrame - frameIndex) * pixelsPerFrame).coerceAtLeast(2f)
                    drawRect(
                        color = celColor,
                        topLeft = Offset(x, top + 6f),
                        size = Size(width - 1f, rowHeightPx - 12f),
                    )
                }
            }

            // Keyframe diamonds
            layer.tracks.keyedFrames().forEach { frame ->
                val centerX = frame.index * pixelsPerFrame
                val centerY = top + rowHeightPx / 2f
                val radius = 5f
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(centerX, centerY - radius)
                    lineTo(centerX + radius, centerY)
                    lineTo(centerX, centerY + radius)
                    lineTo(centerX - radius, centerY)
                    close()
                }
                drawPath(path, keyColor)
            }
        }

        // Scene markers
        state.scene.markers.forEach { marker ->
            val x = marker.frame.index * pixelsPerFrame
            drawLine(
                color = Color(marker.colorArgb),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.5f,
            )
        }

        val playheadX = state.frame.index * pixelsPerFrame
        drawLine(
            color = playheadColor,
            start = Offset(playheadX, 0f),
            end = Offset(playheadX, size.height),
            strokeWidth = 2f,
        )
    }
}
