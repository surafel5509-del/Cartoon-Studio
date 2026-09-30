package com.cartoonstudio.core.math

import kotlinx.serialization.Serializable

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 2D affine transform stored row-major as:
 *
 * ```
 * | a  c  tx |
 * | b  d  ty |
 * | 0  0  1  |
 * ```
 *
 * This matches the convention used by Android's `Matrix` and Compose's
 * graphics layer, which keeps the platform adapters trivial.
 */
@Serializable
data class Matrix3(
    val a: Float = 1f,
    val b: Float = 0f,
    val c: Float = 0f,
    val d: Float = 1f,
    val tx: Float = 0f,
    val ty: Float = 0f,
) {

    operator fun times(other: Matrix3): Matrix3 = Matrix3(
        a = a * other.a + c * other.b,
        b = b * other.a + d * other.b,
        c = a * other.c + c * other.d,
        d = b * other.c + d * other.d,
        tx = a * other.tx + c * other.ty + tx,
        ty = b * other.tx + d * other.ty + ty,
    )

    fun transform(point: Vec2): Vec2 = Vec2(
        a * point.x + c * point.y + tx,
        b * point.x + d * point.y + ty,
    )

    /** Transforms a direction, ignoring translation. */
    fun transformVector(vector: Vec2): Vec2 = Vec2(
        a * vector.x + c * vector.y,
        b * vector.x + d * vector.y,
    )

    fun transform(rect: Rect2): Rect2 = Rect2.bounding(rect.corners().map(::transform))

    val determinant: Float get() = a * d - b * c

    fun inverted(): Matrix3? {
        val det = determinant
        if (abs(det) < 1e-9f) return null
        val invDet = 1f / det
        return Matrix3(
            a = d * invDet,
            b = -b * invDet,
            c = -c * invDet,
            d = a * invDet,
            tx = (c * ty - d * tx) * invDet,
            ty = (b * tx - a * ty) * invDet,
        )
    }

    /** Average uniform scale, useful for level-of-detail and stroke widths. */
    fun approximateScale(): Float {
        val sx = Vec2(a, b).length
        val sy = Vec2(c, d).length
        return (sx + sy) * 0.5f
    }

    fun toFloatArray(): FloatArray = floatArrayOf(a, c, tx, b, d, ty, 0f, 0f, 1f)

    companion object {
        val IDENTITY = Matrix3()

        fun translation(delta: Vec2) = Matrix3(tx = delta.x, ty = delta.y)

        fun scale(sx: Float, sy: Float = sx) = Matrix3(a = sx, d = sy)

        fun rotation(radians: Float): Matrix3 {
            val c = cos(radians)
            val s = sin(radians)
            return Matrix3(a = c, b = s, c = -s, d = c)
        }

        fun skew(sx: Float, sy: Float) = Matrix3(c = sx, b = sy)

        /** Builds `translate(pivot) * R * S * K * translate(-pivot) * translate(position)`. */
        fun compose(
            position: Vec2 = Vec2.ZERO,
            pivot: Vec2 = Vec2.ZERO,
            rotationRadians: Float = 0f,
            scaleFactor: Vec2 = Vec2.ONE,
            skewFactor: Vec2 = Vec2.ZERO,
        ): Matrix3 {
            var m = translation(position + pivot)
            if (rotationRadians != 0f) m *= rotation(rotationRadians)
            if (skewFactor != Vec2.ZERO) m *= skew(skewFactor.x, skewFactor.y)
            if (scaleFactor != Vec2.ONE) m *= scale(scaleFactor.x, scaleFactor.y)
            return m * translation(-pivot)
        }
    }
}
