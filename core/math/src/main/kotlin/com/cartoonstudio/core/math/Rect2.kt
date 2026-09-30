package com.cartoonstudio.core.math

import kotlinx.serialization.Serializable

import kotlin.math.max
import kotlin.math.min

/** Axis-aligned rectangle used for bounds, dirty regions and hit testing. */
@Serializable
data class Rect2(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val center: Vec2 get() = Vec2((left + right) * 0.5f, (top + bottom) * 0.5f)
    val topLeft: Vec2 get() = Vec2(left, top)
    val bottomRight: Vec2 get() = Vec2(right, bottom)
    val isEmpty: Boolean get() = width <= 0f || height <= 0f
    val area: Float get() = if (isEmpty) 0f else width * height

    fun contains(point: Vec2): Boolean =
        point.x in left..right && point.y in top..bottom

    fun intersects(other: Rect2): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun union(other: Rect2): Rect2 {
        if (isEmpty) return other
        if (other.isEmpty) return this
        return Rect2(
            min(left, other.left),
            min(top, other.top),
            max(right, other.right),
            max(bottom, other.bottom),
        )
    }

    fun intersect(other: Rect2): Rect2 = Rect2(
        max(left, other.left),
        max(top, other.top),
        min(right, other.right),
        min(bottom, other.bottom),
    )

    fun inflate(amount: Float): Rect2 =
        Rect2(left - amount, top - amount, right + amount, bottom + amount)

    fun translate(delta: Vec2): Rect2 =
        Rect2(left + delta.x, top + delta.y, right + delta.x, bottom + delta.y)

    fun corners(): List<Vec2> = listOf(
        Vec2(left, top), Vec2(right, top), Vec2(right, bottom), Vec2(left, bottom),
    )

    /** Uniform scale factor that fits this rect inside [target] without cropping. */
    fun fitScaleInto(target: Rect2): Float {
        if (isEmpty || target.isEmpty) return 1f
        return min(target.width / width, target.height / height)
    }

    companion object {
        val EMPTY = Rect2(0f, 0f, 0f, 0f)
        val INVALID = Rect2(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)

        fun fromSize(width: Float, height: Float) = Rect2(0f, 0f, width, height)

        fun fromCenter(center: Vec2, width: Float, height: Float) = Rect2(
            center.x - width / 2f, center.y - height / 2f,
            center.x + width / 2f, center.y + height / 2f,
        )

        fun bounding(points: Collection<Vec2>): Rect2 {
            if (points.isEmpty()) return EMPTY
            var l = Float.MAX_VALUE
            var t = Float.MAX_VALUE
            var r = -Float.MAX_VALUE
            var b = -Float.MAX_VALUE
            for (p in points) {
                l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y)
            }
            return Rect2(l, t, r, b)
        }
    }
}
