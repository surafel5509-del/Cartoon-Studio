package com.cartoonstudio.core.math

import kotlinx.serialization.Serializable

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Immutable 2D vector / point in canvas space. */
@Serializable
data class Vec2(val x: Float, val y: Float) {

    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vec2(x * scalar, y * scalar)
    operator fun times(other: Vec2) = Vec2(x * other.x, y * other.y)
    operator fun div(scalar: Float) = Vec2(x / scalar, y / scalar)
    operator fun unaryMinus() = Vec2(-x, -y)

    val length: Float get() = hypot(x, y)
    val lengthSquared: Float get() = x * x + y * y

    fun normalized(): Vec2 {
        val len = length
        return if (len <= EPSILON) ZERO else Vec2(x / len, y / len)
    }

    fun distanceTo(other: Vec2): Float = hypot(other.x - x, other.y - y)

    fun dot(other: Vec2): Float = x * other.x + y * other.y

    /** 2D analogue of the cross product; sign indicates turn direction. */
    fun cross(other: Vec2): Float = x * other.y - y * other.x

    /** Angle of the vector in radians, measured from the +X axis. */
    fun angle(): Float = atan2(y, x)

    fun rotated(radians: Float): Vec2 {
        val c = cos(radians)
        val s = sin(radians)
        return Vec2(x * c - y * s, x * s + y * c)
    }

    fun rotatedAround(pivot: Vec2, radians: Float): Vec2 = (this - pivot).rotated(radians) + pivot

    /** Perpendicular vector (rotated 90 degrees counter-clockwise). */
    fun perpendicular(): Vec2 = Vec2(-y, x)

    fun lerp(other: Vec2, t: Float): Vec2 = Vec2(x + (other.x - x) * t, y + (other.y - y) * t)

    fun isFinite(): Boolean = x.isFinite() && y.isFinite()

    fun approximately(other: Vec2, tolerance: Float = EPSILON): Boolean =
        abs(x - other.x) <= tolerance && abs(y - other.y) <= tolerance

    companion object {
        const val EPSILON = 1e-5f
        val ZERO = Vec2(0f, 0f)
        val ONE = Vec2(1f, 1f)
        val UNIT_X = Vec2(1f, 0f)
        val UNIT_Y = Vec2(0f, 1f)

        fun fromAngle(radians: Float, length: Float = 1f) =
            Vec2(cos(radians) * length, sin(radians) * length)

        /** Distance from [point] to the segment [a]..[b]. */
        fun distanceToSegment(point: Vec2, a: Vec2, b: Vec2): Float {
            val ab = b - a
            val lengthSquared = ab.lengthSquared
            if (lengthSquared <= EPSILON) return point.distanceTo(a)
            val t = ((point - a).dot(ab) / lengthSquared).coerceIn(0f, 1f)
            return point.distanceTo(a + ab * t)
        }

        fun centroid(points: List<Vec2>): Vec2 {
            if (points.isEmpty()) return ZERO
            var sx = 0f
            var sy = 0f
            for (p in points) {
                sx += p.x
                sy += p.y
            }
            return Vec2(sx / points.size, sy / points.size)
        }
    }
}

fun Float.lerpTo(target: Float, t: Float): Float = this + (target - this) * t

fun Float.clamp01(): Float = coerceIn(0f, 1f)

fun Float.toRadians(): Float = (this * kotlin.math.PI / 180.0).toFloat()

fun Float.toDegrees(): Float = (this * 180.0 / kotlin.math.PI).toFloat()

internal fun safeSqrt(value: Float): Float = if (value <= 0f) 0f else sqrt(value)
