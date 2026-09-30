package com.cartoonstudio.engine.drawing

import com.cartoonstudio.core.math.Rect2
import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.domain.drawing.Brush
import com.cartoonstudio.domain.drawing.BrushDynamics
import com.cartoonstudio.domain.drawing.Stroke
import com.cartoonstudio.domain.drawing.StrokePoint
import kotlin.math.abs

/** One sample of a tessellated stroke: centre point plus half width. */
data class RibbonSample(val position: Vec2, val halfWidth: Float, val tangent: Vec2)

/**
 * A stroke converted into renderable geometry.
 *
 * [outline] is a closed polygon suitable for a single filled path — this is
 * what gives pressure-sensitive strokes their tapered look while still being
 * one cheap draw call.
 */
data class StrokeGeometry(
    val centerLine: List<Vec2>,
    val outline: List<Vec2>,
    val samples: List<RibbonSample>,
    val bounds: Rect2,
    val averageWidth: Float,
) {
    val isEmpty: Boolean get() = centerLine.size < 2

    companion object {
        val Empty = StrokeGeometry(emptyList(), emptyList(), emptyList(), Rect2.EMPTY, 0f)
    }
}

/**
 * Converts stroke point lists into smooth, variable-width geometry.
 *
 * The centre line is resampled through a Catmull-Rom spline so hand-drawn
 * lines stay smooth at any zoom level, then offset along the normal by the
 * per-sample half width to build the outline.
 */
object StrokeTessellator {

    /**
     * @param scale render scale; higher values produce more subdivisions so
     *   zoomed-in artwork stays smooth without wasting work when zoomed out.
     */
    fun tessellate(stroke: Stroke, scale: Float = 1f): StrokeGeometry {
        val points = stroke.points
        if (points.size < 2) return StrokeGeometry.Empty

        val resampled = resample(points, stroke.brush, scale)
        if (resampled.size < 2) return StrokeGeometry.Empty

        val samples = ArrayList<RibbonSample>(resampled.size)
        for (i in resampled.indices) {
            val point = resampled[i]
            val previous = resampled[(i - 1).coerceAtLeast(0)]
            val next = resampled[(i + 1).coerceAtMost(resampled.size - 1)]
            val tangent = (next.position - previous.position).normalized()
            val width = widthFor(stroke.brush, point, previous)
            samples += RibbonSample(point.position, width * 0.5f, tangent)
        }

        val left = ArrayList<Vec2>(samples.size)
        val right = ArrayList<Vec2>(samples.size)
        for (sample in samples) {
            val normal = sample.tangent.perpendicular()
            left += sample.position + normal * sample.halfWidth
            right += sample.position - normal * sample.halfWidth
        }

        val outline = ArrayList<Vec2>(left.size + right.size)
        outline.addAll(left)
        outline.addAll(right.asReversed())

        val centerLine = samples.map { it.position }
        return StrokeGeometry(
            centerLine = centerLine,
            outline = outline,
            samples = samples,
            bounds = Rect2.bounding(outline).inflate(1f),
            averageWidth = samples.sumOf { it.halfWidth.toDouble() }.toFloat() / samples.size * 2f,
        )
    }

    private fun widthFor(brush: Brush, point: StrokePoint, previous: StrokePoint): Float {
        val base = when (brush.dynamics) {
            BrushDynamics.None -> brush.size
            BrushDynamics.Pressure -> brush.widthFor(point.pressure)
            BrushDynamics.Velocity -> {
                val deltaTime = (point.timeOffsetMillis - previous.timeOffsetMillis).coerceAtLeast(1)
                val speed = point.position.distanceTo(previous.position) / deltaTime
                // Faster strokes get thinner, like a real nib being dragged.
                brush.size * (1f - (speed * VELOCITY_SENSITIVITY).coerceIn(0f, 0.7f))
            }
            BrushDynamics.PressureAndVelocity -> {
                val deltaTime = (point.timeOffsetMillis - previous.timeOffsetMillis).coerceAtLeast(1)
                val speed = point.position.distanceTo(previous.position) / deltaTime
                brush.widthFor(point.pressure) * (1f - (speed * VELOCITY_SENSITIVITY).coerceIn(0f, 0.5f))
            }
        }
        return base.coerceAtLeast(MIN_WIDTH)
    }

    /**
     * Catmull-Rom resampling. Produces evenly spaced samples that follow the
     * original input without overshooting.
     */
    private fun resample(points: List<StrokePoint>, brush: Brush, scale: Float): List<StrokePoint> {
        if (points.size < 3) return points
        val targetSpacing = (brush.size * brush.spacing).coerceIn(0.6f, 12f) / scale.coerceAtLeast(0.1f)
        val result = ArrayList<StrokePoint>(points.size * 2)
        result += points.first()

        for (i in 0 until points.size - 1) {
            val p0 = points[(i - 1).coerceAtLeast(0)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[(i + 2).coerceAtMost(points.size - 1)]

            val segmentLength = p1.position.distanceTo(p2.position)
            val steps = (segmentLength / targetSpacing).toInt().coerceIn(1, MAX_STEPS_PER_SEGMENT)
            for (step in 1..steps) {
                val t = step.toFloat() / steps
                val position = catmullRom(p0.position, p1.position, p2.position, p3.position, t)
                result += StrokePoint(
                    x = position.x,
                    y = position.y,
                    pressure = p1.pressure + (p2.pressure - p1.pressure) * t,
                    tilt = p1.tilt,
                    timeOffsetMillis = (p1.timeOffsetMillis +
                        (p2.timeOffsetMillis - p1.timeOffsetMillis) * t).toInt(),
                )
            }
        }
        return result
    }

    private fun catmullRom(p0: Vec2, p1: Vec2, p2: Vec2, p3: Vec2, t: Float): Vec2 {
        val t2 = t * t
        val t3 = t2 * t
        return (p1 * 2f +
            (p2 - p0) * t +
            (p0 * 2f - p1 * 5f + p2 * 4f - p3) * t2 +
            (p1 * 3f - p0 - p2 * 3f + p3) * t3) * 0.5f
    }

    private const val MIN_WIDTH = 0.4f
    private const val MAX_STEPS_PER_SEGMENT = 32
    private const val VELOCITY_SENSITIVITY = 0.25f
}

/** Hit testing and selection helpers shared by the drawing tools. */
object StrokeHitTester {

    /** True when [point] is within [tolerance] of the stroke's painted area. */
    fun hits(stroke: Stroke, point: Vec2, tolerance: Float = 0f): Boolean {
        val points = stroke.points
        if (points.isEmpty()) return false
        if (points.size == 1) {
            return points[0].position.distanceTo(point) <= stroke.brush.size * 0.5f + tolerance
        }
        for (i in 1 until points.size) {
            val halfWidth = stroke.brush.widthFor(points[i].pressure) * 0.5f + tolerance
            val distance = Vec2.distanceToSegment(point, points[i - 1].position, points[i].position)
            if (distance <= halfWidth) return true
        }
        return false
    }

    /** Strokes whose painted area intersects the lasso polygon. */
    fun strokesInPolygon(strokes: List<Stroke>, polygon: List<Vec2>): List<Stroke> {
        if (polygon.size < 3) return emptyList()
        val bounds = Rect2.bounding(polygon)
        return strokes.filter { stroke ->
            val strokeBounds = stroke.bounds()
            strokeBounds.intersects(bounds) &&
                stroke.points.any { containsPoint(polygon, it.position) }
        }
    }

    /** Even-odd point-in-polygon test. */
    fun containsPoint(polygon: List<Vec2>, point: Vec2): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            if ((a.y > point.y) != (b.y > point.y)) {
                val denominator = b.y - a.y
                if (abs(denominator) > 1e-6f) {
                    val x = a.x + (point.y - a.y) / denominator * (b.x - a.x)
                    if (point.x < x) inside = !inside
                }
            }
            j = i
        }
        return inside
    }
}
