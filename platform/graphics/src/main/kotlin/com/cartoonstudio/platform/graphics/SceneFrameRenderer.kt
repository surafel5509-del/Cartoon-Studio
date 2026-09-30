package com.cartoonstudio.platform.graphics

import android.graphics.Bitmap
import android.graphics.Canvas
import com.cartoonstudio.core.math.Matrix3
import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.domain.animation.AnimationClip
import com.cartoonstudio.domain.model.ProjectSettings
import com.cartoonstudio.domain.model.Scene
import com.cartoonstudio.engine.compositing.Compositor
import com.cartoonstudio.engine.rendering.RenderGraph
import com.cartoonstudio.engine.rendering.RenderGraphBuilder
import com.cartoonstudio.engine.rendering.RenderOverlayOptions
import com.cartoonstudio.engine.scene.EvaluationOptions
import com.cartoonstudio.engine.scene.SceneEvaluator

/**
 * Renders scene frames into bitmaps.
 *
 * Used by export, thumbnail generation and the project browser cover images.
 * Preview goes through the same evaluator and command list, so an exported
 * frame matches the editor exactly.
 */
class SceneFrameRenderer(
    clipResolver: (String) -> AnimationClip? = { null },
    imageResolver: ImageResolver = ImageResolver.None,
) {

    private val evaluator = SceneEvaluator(clipResolver)
    private val renderer = CanvasRenderer(imageResolver)

    /** Builds the command list for a frame without rasterising it. */
    fun graphFor(
        scene: Scene,
        settings: ProjectSettings,
        frame: Frame,
        widthPx: Int,
        heightPx: Int,
        overlays: RenderOverlayOptions = RenderOverlayOptions.None,
        applyCamera: Boolean = true,
    ): RenderGraph {
        val scale = widthPx.toFloat() / settings.canvasWidth.toFloat()
        val evaluated = evaluator.evaluate(
            scene = scene,
            frame = frame,
            options = EvaluationOptions(
                viewportWidth = settings.canvasWidth.toFloat(),
                viewportHeight = settings.canvasHeight.toFloat(),
                applyCamera = applyCamera,
            ),
        )
        val builder = RenderGraphBuilder(renderScale = scale)
        val graph = builder.build(
            scene = evaluated,
            viewportWidth = widthPx.toFloat(),
            viewportHeight = heightPx.toFloat(),
            overlays = overlays.copy(canvasBounds = overlays.canvasBounds ?: settings.canvasBounds),
        )
        // Output resolution can differ from the document canvas, so scale the
        // whole graph rather than re-evaluating the scene at a different size.
        return graph.copy(
            commands = Compositor.optimize(graph.commands),
            viewMatrix = Matrix3.scale(scale) * graph.viewMatrix,
        )
    }

    fun renderToBitmap(
        scene: Scene,
        settings: ProjectSettings,
        frame: Frame,
        widthPx: Int,
        heightPx: Int,
        transparent: Boolean = false,
        reuse: Bitmap? = null,
    ): Bitmap {
        val bitmap = if (reuse != null && !reuse.isRecycled &&
            reuse.width == widthPx && reuse.height == heightPx
        ) {
            reuse
        } else {
            Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        }

        val canvas = Canvas(bitmap)
        bitmap.eraseColor(0)

        val graph = graphFor(scene, settings, frame, widthPx, heightPx)
        val scale = widthPx.toFloat() / settings.canvasWidth.toFloat()
        canvas.save()
        canvas.scale(scale, scale)
        renderer.render(
            canvas,
            graph.copy(
                viewMatrix = graph.viewMatrix,
                commands = if (transparent) {
                    graph.commands.filterNot { it is com.cartoonstudio.engine.rendering.RenderCommand.Clear }
                } else {
                    graph.commands
                },
            ),
        )
        canvas.restore()
        return bitmap
    }

    /** Small cover image for the project browser. */
    fun renderThumbnail(
        scene: Scene,
        settings: ProjectSettings,
        frame: Frame = Frame.ZERO,
        maxWidth: Int = 480,
    ): Bitmap {
        val scale = (maxWidth.toFloat() / settings.canvasWidth).coerceAtMost(1f)
        val width = (settings.canvasWidth * scale).toInt().coerceAtLeast(16)
        val height = (settings.canvasHeight * scale).toInt().coerceAtLeast(16)
        return renderToBitmap(scene, settings, frame, width, height)
    }

    val stats: RenderStats get() = renderer.stats
}

/** Maps a screen point into document space for hit testing and drawing. */
object CanvasProjection {

    fun screenToDocument(
        point: Vec2,
        viewMatrix: Matrix3,
    ): Vec2 = viewMatrix.inverted()?.transform(point) ?: point

    fun documentToScreen(point: Vec2, viewMatrix: Matrix3): Vec2 = viewMatrix.transform(point)

    /**
     * View matrix that fits [settings] inside a viewport, honouring user pan
     * and zoom on top of the fit.
     */
    fun fitMatrix(
        settings: ProjectSettings,
        viewportWidth: Float,
        viewportHeight: Float,
        userZoom: Float = 1f,
        userPan: Vec2 = Vec2.ZERO,
        padding: Float = 24f,
    ): Matrix3 {
        val available = Vec2(
            (viewportWidth - padding * 2f).coerceAtLeast(1f),
            (viewportHeight - padding * 2f).coerceAtLeast(1f),
        )
        val fit = minOf(
            available.x / settings.canvasWidth.toFloat(),
            available.y / settings.canvasHeight.toFloat(),
        )
        val scale = fit * userZoom.coerceIn(0.05f, 16f)
        val offset = Vec2(
            (viewportWidth - settings.canvasWidth * scale) / 2f,
            (viewportHeight - settings.canvasHeight * scale) / 2f,
        )
        return Matrix3.translation(offset + userPan) * Matrix3.scale(scale)
    }
}
