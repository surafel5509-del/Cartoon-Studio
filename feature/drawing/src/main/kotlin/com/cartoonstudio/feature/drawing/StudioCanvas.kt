package com.cartoonstudio.feature.drawing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.cartoonstudio.core.math.Matrix3
import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.domain.drawing.Brush
import com.cartoonstudio.domain.drawing.Cel
import com.cartoonstudio.domain.drawing.Rgba
import com.cartoonstudio.domain.drawing.Stroke
import com.cartoonstudio.domain.drawing.StrokeBuilder
import com.cartoonstudio.domain.model.ProjectSettings
import com.cartoonstudio.domain.model.Scene
import com.cartoonstudio.engine.rendering.RenderGraphBuilder
import com.cartoonstudio.engine.rendering.RenderOverlayOptions
import com.cartoonstudio.engine.scene.EvaluationOptions
import com.cartoonstudio.engine.scene.SceneEvaluator
import com.cartoonstudio.platform.graphics.CanvasProjection
import com.cartoonstudio.platform.graphics.CanvasRenderer

/** Everything the canvas needs to draw one frame of the editor. */
data class CanvasContent(
    val scene: Scene,
    val settings: ProjectSettings,
    val frame: Frame,
    val overlays: RenderOverlayOptions,
)

/** Gesture callbacks the editor reacts to. */
data class CanvasCallbacks(
    val onStrokeCommitted: (Stroke) -> Unit = {},
    val onViewChanged: (CanvasViewState) -> Unit = {},
    val onTap: (Vec2) -> Unit = {},
    val onColorPicked: (Rgba) -> Unit = {},
)

/**
 * The drawing surface.
 *
 * Rendering goes through the shared engine pipeline — evaluate the scene,
 * build a render graph, replay it on the native canvas — so the preview is
 * pixel-identical to an export. Live input is drawn as an extra overlay
 * stroke so the in-progress line appears with zero document churn.
 */
@Composable
fun StudioCanvas(
    content: CanvasContent,
    tool: DrawingTool,
    brush: Brush,
    color: Rgba,
    view: CanvasViewState,
    callbacks: CanvasCallbacks,
    modifier: Modifier = Modifier,
    clipResolver: (String) -> com.cartoonstudio.domain.animation.AnimationClip? = { null },
) {
    val renderer = remember { CanvasRenderer() }
    val evaluator = remember(clipResolver) { SceneEvaluator(clipResolver) }
    val density = LocalDensity.current

    var liveStroke by remember { mutableStateOf<Stroke?>(null) }
    var shapeStart by remember { mutableStateOf<Vec2?>(null) }
    val currentContent by rememberUpdatedState(content)
    val currentView by rememberUpdatedState(view)
    val currentTool by rememberUpdatedState(tool)
    val currentBrush by rememberUpdatedState(brush)
    val currentColor by rememberUpdatedState(color)
    val currentCallbacks by rememberUpdatedState(callbacks)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F))
            .pointerInput(tool) {
                if (currentTool == DrawingTool.Pan) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        currentCallbacks.onViewChanged(
                            currentView.zoomedBy(zoom).pannedBy(pan.x, pan.y),
                        )
                    }
                }
            }
            .pointerInput(tool, brush, color) {
                if (currentTool.drawsStrokes) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull()
                                ?: continue
                            if (!down.pressed) continue

                            val viewMatrix = matrixFor(currentContent.settings, size.width.toFloat(), size.height.toFloat(), currentView)
                            val toDocument: (Offset) -> Vec2 = { offset ->
                                CanvasProjection.screenToDocument(Vec2(offset.x, offset.y), viewMatrix)
                            }

                            val brushForTool = if (currentTool == DrawingTool.Eraser) {
                                currentBrush.copy(
                                    operation = com.cartoonstudio.domain.drawing.DrawingOperation.Erase,
                                )
                            } else {
                                currentBrush
                            }

                            val startPoint = toDocument(down.position)
                            val startTime = System.currentTimeMillis()

                            if (currentTool.isShape) {
                                shapeStart = startPoint
                            }

                            val builder = StrokeBuilder(
                                brush = brushForTool,
                                color = currentColor,
                                strokeId = com.cartoonstudio.core.common.Ids.next("stroke"),
                            )
                            if (currentTool.isFreehand) {
                                builder.begin(startPoint, down.pressure.coerceIn(0.05f, 1f), startTime)
                                liveStroke = builder.finish()
                            }
                            down.consume()

                            var dragging = true
                            while (dragging) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull() ?: break
                                val position = toDocument(change.position)

                                if (change.pressed) {
                                    if (currentTool.isFreehand) {
                                        builder.extend(
                                            position,
                                            change.pressure.coerceIn(0.05f, 1f),
                                            System.currentTimeMillis(),
                                        )
                                        liveStroke = builder.finish()
                                    } else {
                                        liveStroke = ShapeTools.build(
                                            currentTool, shapeStart ?: startPoint, position,
                                            brushForTool, currentColor,
                                        )
                                    }
                                    change.consume()
                                } else {
                                    dragging = false
                                    val finished = if (currentTool.isFreehand) {
                                        builder.finish()
                                    } else {
                                        ShapeTools.build(
                                            currentTool, shapeStart ?: startPoint, position,
                                            brushForTool, currentColor,
                                        )
                                    }
                                    liveStroke = null
                                    shapeStart = null
                                    if (finished != null && finished.points.size > 1) {
                                        currentCallbacks.onStrokeCommitted(finished)
                                    }
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(tool) {
                if (!currentTool.drawsStrokes && currentTool != DrawingTool.Pan) {
                    detectTapGestures { offset ->
                        val viewMatrix = matrixFor(
                            currentContent.settings, size.width.toFloat(), size.height.toFloat(), currentView,
                        )
                        currentCallbacks.onTap(
                            CanvasProjection.screenToDocument(Vec2(offset.x, offset.y), viewMatrix),
                        )
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val viewportWidth = size.width
            val viewportHeight = size.height
            val fitMatrix = matrixFor(content.settings, viewportWidth, viewportHeight, view)

            val evaluated = evaluator.evaluate(
                scene = content.scene,
                frame = content.frame,
                options = EvaluationOptions(
                    viewportWidth = content.settings.canvasWidth.toFloat(),
                    viewportHeight = content.settings.canvasHeight.toFloat(),
                    applyCamera = false,
                    cullOffscreen = false,
                ),
            )

            val graph = RenderGraphBuilder(renderScale = density.density).build(
                scene = evaluated.copy(viewMatrix = fitMatrix),
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                overlays = content.overlays.copy(
                    canvasBounds = content.settings.canvasBounds,
                ),
            )

            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                renderer.render(native, graph)

                // Live input is drawn outside the document, so an in-progress
                // stroke never enters the undo history until it is committed.
                liveStroke?.let { stroke ->
                    val geometry = com.cartoonstudio.engine.drawing.StrokeTessellator
                        .tessellate(stroke, density.density)
                    if (!geometry.isEmpty) {
                        renderer.render(
                            native,
                            graph.copy(
                                commands = listOf(
                                    com.cartoonstudio.engine.rendering.RenderCommand.FillPath(
                                        transform = fitMatrix,
                                        outline = geometry.outline,
                                        argb = stroke.color.argb,
                                        erase = stroke.isErase,
                                    ),
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun matrixFor(
    settings: ProjectSettings,
    viewportWidth: Float,
    viewportHeight: Float,
    view: CanvasViewState,
): Matrix3 = CanvasProjection.fitMatrix(
    settings = settings,
    viewportWidth = viewportWidth,
    viewportHeight = viewportHeight,
    userZoom = view.zoom,
    userPan = Vec2(view.panX, view.panY),
)
