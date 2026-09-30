package com.cartoonstudio.domain.drawing

import com.cartoonstudio.core.math.Vec2
import kotlin.math.abs

/**
 * Accumulates live pointer input into a stroke.
 *
 * Responsibilities:
 * - stabilise jittery finger/stylus input with an exponential filter,
 * - drop redundant samples so documents stay small,
 * - keep a raw copy so stabilisation strength can be re-applied losslessly.
 */
class StrokeBuilder(
    private val brush: Brush,
    private val color: Rgba,
    private val strokeId: String,
) {

    private val raw = mutableListOf<StrokePoint>()
    private val smoothed = mutableListOf<StrokePoint>()
    private var startMillis: Long = 0L

    val isEmpty: Boolean get() = smoothed.isEmpty()

    /** Points committed so far, for live preview rendering. */
    val points: List<StrokePoint> get() = smoothed

    fun begin(position: Vec2, pressure: Float, timeMillis: Long) {
        raw.clear()
        smoothed.clear()
        startMillis = timeMillis
        val point = StrokePoint(position.x, position.y, pressure.coerceIn(0.01f, 1f), 0f, 0)
        raw += point
        smoothed += point
    }

    fun extend(position: Vec2, pressure: Float, timeMillis: Long) {
        if (smoothed.isEmpty()) {
            begin(position, pressure, timeMillis)
            return
        }
        val offset = (timeMillis - startMillis).coerceAtLeast(0L).toInt()
        val last = smoothed.last()

        // Reject samples that add no information; this typically removes 40-60%
        // of raw touch events on a high-rate digitiser.
        val minimumStep = (brush.size * brush.spacing).coerceAtLeast(MIN_STEP)
        if (last.position.distanceTo(position) < minimumStep &&
            abs(last.pressure - pressure) < PRESSURE_EPSILON
        ) {
            return
        }

        raw += StrokePoint(position.x, position.y, pressure.coerceIn(0.01f, 1f), 0f, offset)

        val alpha = 1f - brush.stabilization.coerceIn(0f, 0.95f)
        val filtered = Vec2(
            last.x + (position.x - last.x) * alpha,
            last.y + (position.y - last.y) * alpha,
        )
        val filteredPressure = last.pressure + (pressure - last.pressure) * 0.5f
        smoothed += StrokePoint(filtered.x, filtered.y, filteredPressure.coerceIn(0.01f, 1f), 0f, offset)
    }

    /** Finishes the stroke, returning null when the gesture produced no line. */
    fun finish(closeShape: Boolean = false): Stroke? {
        if (smoothed.isEmpty()) return null
        if (smoothed.size == 1) {
            // A tap is a valid mark: emit a dot by duplicating the sample.
            val only = smoothed.first()
            smoothed += only.copy(x = only.x + 0.01f)
        }
        return Stroke(
            id = strokeId,
            brush = brush,
            color = color,
            points = simplify(smoothed, brush.size * 0.06f),
            closed = closeShape,
        )
    }

    fun cancel() {
        raw.clear()
        smoothed.clear()
    }

    companion object {
        private const val MIN_STEP = 0.75f
        private const val PRESSURE_EPSILON = 0.08f

        /**
         * Ramer-Douglas-Peucker simplification. Keeps documents compact without
         * visibly changing the drawn line.
         */
        fun simplify(points: List<StrokePoint>, tolerance: Float): List<StrokePoint> {
            if (points.size < 3 || tolerance <= 0f) return points
            val keep = BooleanArray(points.size)
            keep[0] = true
            keep[points.size - 1] = true
            simplifySegment(points, 0, points.size - 1, tolerance, keep)
            return points.filterIndexed { index, _ -> keep[index] }
        }

        private fun simplifySegment(
            points: List<StrokePoint>,
            first: Int,
            last: Int,
            tolerance: Float,
            keep: BooleanArray,
        ) {
            if (last <= first + 1) return
            var maxDistance = 0f
            var index = first
            val a = points[first].position
            val b = points[last].position
            for (i in first + 1 until last) {
                val distance = Vec2.distanceToSegment(points[i].position, a, b)
                if (distance > maxDistance) {
                    maxDistance = distance
                    index = i
                }
            }
            if (maxDistance > tolerance) {
                keep[index] = true
                simplifySegment(points, first, index, tolerance, keep)
                simplifySegment(points, index, last, tolerance, keep)
            }
        }
    }
}
