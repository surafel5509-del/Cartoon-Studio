package com.cartoonstudio.core.math

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Cubic bezier easing solver used by the animation engine.
 *
 * Control points are expressed in normalized (0..1) time/value space, matching
 * the CSS `cubic-bezier()` convention so authored curves are portable.
 */
class CubicBezierEasing(
    private val x1: Float,
    private val y1: Float,
    private val x2: Float,
    private val y2: Float,
) {

    fun transform(fraction: Float): Float {
        if (fraction <= 0f) return 0f
        if (fraction >= 1f) return 1f
        val t = solveForT(fraction)
        return bezier(t, y1, y2)
    }

    private fun solveForT(x: Float): Float {
        // Newton-Raphson with a bisection fallback keeps this stable for
        // aggressive curves authored by users in the graph editor.
        var t = x
        repeat(NEWTON_ITERATIONS) {
            val error = bezier(t, x1, x2) - x
            if (abs(error) < PRECISION) return t
            val derivative = bezierDerivative(t, x1, x2)
            if (abs(derivative) < 1e-6f) return@repeat
            t -= error / derivative
        }
        var low = 0f
        var high = 1f
        var mid = x
        repeat(BISECTION_ITERATIONS) {
            mid = (low + high) * 0.5f
            val value = bezier(mid, x1, x2)
            if (abs(value - x) < PRECISION) return mid
            if (value < x) low = mid else high = mid
        }
        return mid
    }

    private fun bezier(t: Float, p1: Float, p2: Float): Float {
        val inv = 1f - t
        return 3f * inv * inv * t * p1 + 3f * inv * t * t * p2 + t * t * t
    }

    private fun bezierDerivative(t: Float, p1: Float, p2: Float): Float {
        val inv = 1f - t
        return 3f * inv * inv * p1 + 6f * inv * t * (p2 - p1) + 3f * t * t * (1f - p2)
    }

    companion object {
        private const val NEWTON_ITERATIONS = 8
        private const val BISECTION_ITERATIONS = 24
        private const val PRECISION = 1e-5f
    }
}

/** Named easing functions exposed in the timeline UI. */
object Easings {
    val Linear = CubicBezierEasing(0f, 0f, 1f, 1f)
    val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    val Smooth = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    val Snappy = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)
    val Anticipate = CubicBezierEasing(0.6f, -0.28f, 0.735f, 0.045f)
    val Overshoot = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)

    /** Classic elastic settle, used for cartoon "bounce back" motion. */
    fun elasticOut(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val p = 0.3f
        return 2f.pow(-10f * t) * sin((t - p / 4f) * (2f * Math.PI.toFloat()) / p) + 1f
    }

    /** Bouncing ball falloff, used for squash-and-stretch presets. */
    fun bounceOut(t: Float): Float {
        val n1 = 7.5625f
        val d1 = 2.75f
        return when {
            t < 1f / d1 -> n1 * t * t
            t < 2f / d1 -> { val x = t - 1.5f / d1; n1 * x * x + 0.75f }
            t < 2.5f / d1 -> { val x = t - 2.25f / d1; n1 * x * x + 0.9375f }
            else -> { val x = t - 2.625f / d1; n1 * x * x + 0.984375f }
        }
    }

    fun sineInOut(t: Float): Float = -(cos(Math.PI.toFloat() * t) - 1f) / 2f
}
