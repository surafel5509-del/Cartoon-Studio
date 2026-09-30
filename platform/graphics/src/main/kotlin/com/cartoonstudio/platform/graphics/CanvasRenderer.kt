package com.cartoonstudio.platform.graphics

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Typeface
import com.cartoonstudio.core.math.Rect2
import com.cartoonstudio.domain.model.Effect
import com.cartoonstudio.engine.rendering.OverlayKind
import com.cartoonstudio.engine.rendering.RenderCommand
import com.cartoonstudio.engine.rendering.RenderGraph
import com.cartoonstudio.engine.rendering.TextAlignment
import kotlin.math.cos
import kotlin.math.sin

/** Supplies bitmaps for [RenderCommand.DrawImage]; returns null when absent. */
fun interface ImageResolver {
    fun resolve(assetId: String): android.graphics.Bitmap?

    companion object {
        val None = ImageResolver { null }
    }
}

/** Statistics for the performance HUD. */
data class RenderStats(
    var drawCalls: Int = 0,
    var offscreenLayers: Int = 0,
    var skipped: Int = 0,
    var lastFrameMillis: Long = 0L,
) {
    fun reset() {
        drawCalls = 0
        offscreenLayers = 0
        skipped = 0
    }
}

/**
 * Replays an engine [RenderGraph] onto an Android canvas.
 *
 * The same renderer serves the interactive preview (through Compose's native
 * canvas) and offline export (through a bitmap canvas), which is what
 * guarantees that what the artist sees is what gets rendered.
 */
class CanvasRenderer(
    private val imageResolver: ImageResolver = ImageResolver.None,
) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val scratchMatrix = Matrix()
    private val scratchPath = Path()

    val stats = RenderStats()

    fun render(canvas: Canvas, graph: RenderGraph) {
        val started = System.nanoTime()
        stats.reset()
        var layerDepth = 0

        for (command in graph.commands) {
            when (command) {
                is RenderCommand.Clear -> canvas.drawColor(command.argb, PorterDuff.Mode.SRC)

                is RenderCommand.BeginLayer -> {
                    val paint = Paint().apply {
                        alpha = (command.opacity.coerceIn(0f, 1f) * 255).toInt()
                        xfermode = AndroidConversions.toXfermode(command.blendMode)
                        applyEffects(this, command.effects)
                    }
                    canvas.saveLayer(null, paint)
                    layerDepth++
                    stats.offscreenLayers++
                }

                is RenderCommand.EndLayer -> {
                    if (layerDepth > 0) {
                        canvas.restore()
                        layerDepth--
                    }
                }

                is RenderCommand.FillPath -> {
                    if (command.outline.size < 3) {
                        stats.skipped++
                    } else {
                        withTransform(canvas, command.transform) {
                            val path = AndroidConversions.toSmoothPath(command.outline, true, scratchPath)
                            fillPaint.color = command.argb
                            fillPaint.isAntiAlias = command.antiAlias
                            fillPaint.xfermode = if (command.erase) ERASE_MODE else null
                            canvas.drawPath(path, fillPaint)
                            fillPaint.xfermode = null
                            stats.drawCalls++
                        }
                    }
                }

                is RenderCommand.StrokePath -> withTransform(canvas, command.transform) {
                    val path = AndroidConversions.toPath(command.points, command.closed, scratchPath)
                    strokePaint.color = command.argb
                    strokePaint.strokeWidth = command.width
                    strokePaint.strokeCap = if (command.roundCap) Paint.Cap.ROUND else Paint.Cap.BUTT
                    canvas.drawPath(path, strokePaint)
                    stats.drawCalls++
                }

                is RenderCommand.FillRect -> withTransform(canvas, command.transform) {
                    fillPaint.color = command.argb
                    fillPaint.shader = null
                    canvas.drawRect(
                        command.rect.left, command.rect.top, command.rect.right, command.rect.bottom,
                        fillPaint,
                    )
                    stats.drawCalls++
                }

                is RenderCommand.GradientRect -> withTransform(canvas, command.transform) {
                    val radians = Math.toRadians(command.angleDegrees.toDouble())
                    val halfWidth = command.rect.width / 2f
                    val halfHeight = command.rect.height / 2f
                    val center = command.rect.center
                    val dx = cos(radians).toFloat() * halfWidth
                    val dy = sin(radians).toFloat() * halfHeight
                    fillPaint.shader = LinearGradient(
                        center.x - dx, center.y - dy, center.x + dx, center.y + dy,
                        command.startArgb, command.endArgb, Shader.TileMode.CLAMP,
                    )
                    canvas.drawRect(
                        command.rect.left, command.rect.top, command.rect.right, command.rect.bottom,
                        fillPaint,
                    )
                    fillPaint.shader = null
                    stats.drawCalls++
                }

                is RenderCommand.FillCircle -> withTransform(canvas, command.transform) {
                    fillPaint.color = command.argb
                    fillPaint.shader = null
                    canvas.drawCircle(command.center.x, command.center.y, command.radius, fillPaint)
                    stats.drawCalls++
                }

                is RenderCommand.DrawText -> withTransform(canvas, command.transform) {
                    drawText(canvas, command)
                    stats.drawCalls++
                }

                is RenderCommand.DrawImage -> withTransform(canvas, command.transform) {
                    val bitmap = imageResolver.resolve(command.assetId)
                    if (bitmap == null) {
                        stats.skipped++
                    } else {
                        imagePaint.alpha = (command.opacity.coerceIn(0f, 1f) * 255).toInt()
                        val destination = command.destination
                        if (destination == null) {
                            canvas.drawBitmap(
                                bitmap, -bitmap.width / 2f, -bitmap.height / 2f, imagePaint,
                            )
                        } else {
                            canvas.drawBitmap(
                                bitmap, null, AndroidConversions.toRectF(destination), imagePaint,
                            )
                        }
                        stats.drawCalls++
                    }
                }

                is RenderCommand.Overlay -> withTransform(canvas, command.transform) {
                    drawOverlay(canvas, command)
                    stats.drawCalls++
                }
            }
        }

        // Defensive: never leave the canvas in an unbalanced state.
        while (layerDepth > 0) {
            canvas.restore()
            layerDepth--
        }
        stats.lastFrameMillis = (System.nanoTime() - started) / 1_000_000
    }

    private inline fun withTransform(canvas: Canvas, transform: com.cartoonstudio.core.math.Matrix3, block: () -> Unit) {
        canvas.save()
        canvas.concat(AndroidConversions.toMatrix(transform, scratchMatrix))
        block()
        canvas.restore()
    }

    private fun drawText(canvas: Canvas, command: RenderCommand.DrawText) {
        textPaint.color = command.argb
        textPaint.textSize = command.fontSizePx
        textPaint.letterSpacing = command.letterSpacing
        textPaint.typeface = Typeface.create(
            Typeface.DEFAULT,
            when {
                command.bold && command.italic -> Typeface.BOLD_ITALIC
                command.bold -> Typeface.BOLD
                command.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            },
        )
        textPaint.textAlign = when (command.alignment) {
            TextAlignment.Start -> Paint.Align.LEFT
            TextAlignment.Center -> Paint.Align.CENTER
            TextAlignment.End -> Paint.Align.RIGHT
        }

        val lines = command.text.split('\n')
        val lineHeight = command.fontSizePx * command.lineHeight
        val startY = -(lines.size - 1) * lineHeight / 2f + command.fontSizePx * 0.35f

        lines.forEachIndexed { index, line ->
            val y = startY + index * lineHeight
            if (command.outlineWidth > 0f) {
                textPaint.style = Paint.Style.STROKE
                textPaint.strokeWidth = command.outlineWidth
                textPaint.color = command.outlineArgb
                canvas.drawText(line, 0f, y, textPaint)
                textPaint.style = Paint.Style.FILL
                textPaint.color = command.argb
            }
            canvas.drawText(line, 0f, y, textPaint)
        }
    }

    private fun drawOverlay(canvas: Canvas, command: RenderCommand.Overlay) {
        strokePaint.color = command.argb
        strokePaint.strokeWidth = command.width
        fillPaint.color = command.argb
        fillPaint.shader = null

        when (command.kind) {
            OverlayKind.Joint, OverlayKind.Pivot -> command.points.forEach {
                canvas.drawCircle(it.x, it.y, command.width, fillPaint)
            }
            OverlayKind.SelectionBox -> {
                val path = AndroidConversions.toPath(command.points, true, scratchPath)
                canvas.drawPath(path, strokePaint)
            }
            else -> {
                val path = AndroidConversions.toPath(command.points, false, scratchPath)
                canvas.drawPath(path, strokePaint)
            }
        }
    }

    private fun applyEffects(paint: Paint, effects: List<Effect>) {
        effects.forEach { effect ->
            when (effect) {
                is Effect.GaussianBlur -> if (effect.radius > 0f) {
                    paint.maskFilter = BlurMaskFilter(effect.radius, BlurMaskFilter.Blur.NORMAL)
                }
                is Effect.Glow -> if (effect.radius > 0f) {
                    paint.maskFilter = BlurMaskFilter(effect.radius, BlurMaskFilter.Blur.OUTER)
                }
                is Effect.DropShadow -> paint.setShadowLayer(
                    effect.radius.coerceAtLeast(0.1f), effect.offsetX, effect.offsetY, effect.color.argb,
                )
                is Effect.ColorAdjust -> paint.colorFilter = ColorMatrixColorFilter(
                    colorMatrixFor(effect),
                )
                is Effect.Tint -> paint.colorFilter = ColorMatrixColorFilter(
                    ColorMatrix().apply {
                        val amount = effect.amount.coerceIn(0f, 1f)
                        setScale(
                            1f - amount + amount * effect.color.red / 255f,
                            1f - amount + amount * effect.color.green / 255f,
                            1f - amount + amount * effect.color.blue / 255f,
                            1f,
                        )
                    },
                )
                is Effect.Outline -> Unit
            }
        }
    }

    private fun colorMatrixFor(effect: Effect.ColorAdjust): ColorMatrix {
        val matrix = ColorMatrix()
        matrix.setSaturation(effect.saturation.coerceIn(0f, 4f))

        val contrast = effect.contrast.coerceIn(0f, 4f)
        val brightness = effect.brightness.coerceIn(-1f, 1f) * 255f
        val translate = (1f - contrast) * 127.5f + brightness
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        matrix.postConcat(contrastMatrix)
        return matrix
    }

    private companion object {
        val ERASE_MODE = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
}

/** Convenience holder describing the surface being drawn into. */
data class Viewport(
    val widthPx: Float,
    val heightPx: Float,
) {
    val bounds: Rect2 get() = Rect2.fromSize(widthPx, heightPx)
}
