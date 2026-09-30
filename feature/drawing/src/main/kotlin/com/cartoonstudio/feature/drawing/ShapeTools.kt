package com.cartoonstudio.feature.drawing

import com.cartoonstudio.core.common.Ids
import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.domain.drawing.Brush
import com.cartoonstudio.domain.drawing.Rgba
import com.cartoonstudio.domain.drawing.Stroke
import com.cartoonstudio.domain.drawing.StrokePoint
import kotlin.math.cos
import kotlin.math.sin

/** Builds geometric strokes for the line, rectangle and ellipse tools. */
object ShapeTools {

    fun build(tool: DrawingTool, start: Vec2, end: Vec2, brush: Brush, color: Rgba): Stroke? = when (tool) {
        DrawingTool.Line -> stroke(listOf(start, end), brush, color, closed = false)
        DrawingTool.Rectangle -> stroke(
            listOf(
                start,
                Vec2(end.x, start.y),
                end,
                Vec2(start.x, end.y),
            ),
            brush, color, closed = true,
        )
        DrawingTool.Ellipse -> {
            val center = Vec2((start.x + end.x) / 2f, (start.y + end.y) / 2f)
            val radiusX = kotlin.math.abs(end.x - start.x) / 2f
            val radiusY = kotlin.math.abs(end.y - start.y) / 2f
            val points = (0 until SEGMENTS).map { index ->
                val angle = index.toFloat() / SEGMENTS * 2f * Math.PI.toFloat()
                Vec2(center.x + cos(angle) * radiusX, center.y + sin(angle) * radiusY)
            }
            stroke(points, brush, color, closed = true)
        }
        else -> null
    }

    private fun stroke(points: List<Vec2>, brush: Brush, color: Rgba, closed: Boolean): Stroke? {
        if (points.size < 2) return null
        val ordered = if (closed) points + points.first() else points
        return Stroke(
            id = Ids.next("stroke"),
            brush = brush.copy(dynamics = com.cartoonstudio.domain.drawing.BrushDynamics.None),
            color = color,
            points = ordered.map { StrokePoint(it.x, it.y) },
            closed = closed,
        )
    }

    private const val SEGMENTS = 48
}
