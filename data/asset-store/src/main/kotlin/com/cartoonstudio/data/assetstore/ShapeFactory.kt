package com.cartoonstudio.data.assetstore

import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.domain.drawing.Brush
import com.cartoonstudio.domain.drawing.Rgba
import com.cartoonstudio.domain.drawing.Stroke
import com.cartoonstudio.domain.drawing.StrokePoint
import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds vector artwork from primitives.
 *
 * All built-in library artwork is generated as real strokes rather than
 * shipped as bitmaps: it stays resolution independent, exports cleanly at 4K,
 * is fully editable by the user, and costs a few kilobytes instead of
 * megabytes of PNGs.
 */
object ShapeFactory {

    private var counter = 0

    private fun nextId(prefix: String): String {
        counter += 1
        return "${prefix}_$counter"
    }

    private val outlineBrush = Brush.Ink.copy(size = 5f, stabilization = 0f)

    fun polygon(
        points: List<Vec2>,
        fill: Rgba,
        outline: Rgba? = Rgba.Ink,
        outlineWidth: Float = 5f,
    ): Stroke = Stroke(
        id = nextId("shape"),
        brush = outlineBrush.copy(size = outlineWidth),
        color = outline ?: fill,
        points = points.map { StrokePoint(it.x, it.y) },
        closed = true,
        fill = fill,
    )

    fun ellipse(
        center: Vec2,
        radiusX: Float,
        radiusY: Float,
        fill: Rgba,
        outline: Rgba? = Rgba.Ink,
        segments: Int = 28,
        outlineWidth: Float = 5f,
    ): Stroke {
        val points = (0 until segments).map { index ->
            val angle = index.toFloat() / segments * 2f * Math.PI.toFloat()
            Vec2(center.x + cos(angle) * radiusX, center.y + sin(angle) * radiusY)
        }
        return polygon(points, fill, outline, outlineWidth)
    }

    fun circle(center: Vec2, radius: Float, fill: Rgba, outline: Rgba? = Rgba.Ink) =
        ellipse(center, radius, radius, fill, outline)

    fun roundedRect(
        center: Vec2,
        width: Float,
        height: Float,
        corner: Float,
        fill: Rgba,
        outline: Rgba? = Rgba.Ink,
    ): Stroke {
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        val r = corner.coerceAtMost(minOf(halfWidth, halfHeight))
        val points = ArrayList<Vec2>(36)
        val corners = listOf(
            Vec2(center.x + halfWidth - r, center.y - halfHeight + r) to -90f,
            Vec2(center.x + halfWidth - r, center.y + halfHeight - r) to 0f,
            Vec2(center.x - halfWidth + r, center.y + halfHeight - r) to 90f,
            Vec2(center.x - halfWidth + r, center.y - halfHeight + r) to 180f,
        )
        corners.forEach { (pivot, startDegrees) ->
            for (step in 0..6) {
                val angle = ((startDegrees + step * 15f) * Math.PI / 180f).toFloat()
                points += Vec2(pivot.x + cos(angle) * r, pivot.y + sin(angle) * r)
            }
        }
        return polygon(points, fill, outline)
    }

    /** Capsule/limb shape running from [from] to [to]. */
    fun limb(from: Vec2, to: Vec2, thickness: Float, fill: Rgba, outline: Rgba? = Rgba.Ink): Stroke {
        val direction = (to - from).normalized()
        val normal = direction.perpendicular() * (thickness / 2f)
        val points = ArrayList<Vec2>(20)
        points += from + normal
        points += to + normal
        for (step in 0..6) {
            val angle = (step / 6f) * Math.PI.toFloat()
            val rotated = normal.rotated(-angle)
            points += to + rotated
        }
        points += to - normal
        points += from - normal
        for (step in 0..6) {
            val angle = (step / 6f) * Math.PI.toFloat()
            points += from - normal.rotated(-angle)
        }
        return polygon(points, fill, outline, outlineWidth = 4f)
    }

    fun star(center: Vec2, outerRadius: Float, innerRadius: Float, pointCount: Int, fill: Rgba): Stroke {
        val points = (0 until pointCount * 2).map { index ->
            val radius = if (index % 2 == 0) outerRadius else innerRadius
            val angle = (index.toFloat() / (pointCount * 2)) * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2f
            Vec2(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
        }
        return polygon(points, fill)
    }

    fun triangle(center: Vec2, width: Float, height: Float, fill: Rgba, outline: Rgba? = Rgba.Ink) = polygon(
        listOf(
            Vec2(center.x, center.y - height / 2f),
            Vec2(center.x + width / 2f, center.y + height / 2f),
            Vec2(center.x - width / 2f, center.y + height / 2f),
        ),
        fill, outline,
    )

    /** Open line, e.g. a mouth or an eyebrow. */
    fun line(points: List<Vec2>, color: Rgba, width: Float): Stroke = Stroke(
        id = nextId("line"),
        brush = outlineBrush.copy(size = width),
        color = color,
        points = points.map { StrokePoint(it.x, it.y) },
    )

    fun arc(
        center: Vec2,
        radius: Float,
        startDegrees: Float,
        sweepDegrees: Float,
        color: Rgba,
        width: Float,
        segments: Int = 16,
    ): Stroke {
        val points = (0..segments).map { step ->
            val angle = ((startDegrees + sweepDegrees * step / segments) * Math.PI / 180f).toFloat()
            Vec2(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
        }
        return line(points, color, width)
    }
}
