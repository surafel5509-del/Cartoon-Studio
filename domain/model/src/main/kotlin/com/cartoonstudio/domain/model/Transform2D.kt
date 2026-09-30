package com.cartoonstudio.domain.model

import com.cartoonstudio.core.math.Matrix3
import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.core.math.toRadians
import kotlinx.serialization.Serializable

/**
 * Object transform in parent space.
 *
 * Stored as decomposed channels (rather than a baked matrix) because that is
 * what the animation system keyframes and what artists manipulate directly.
 */
@Serializable
data class Transform2D(
    val position: Vec2 = Vec2.ZERO,
    val pivot: Vec2 = Vec2.ZERO,
    val rotationDegrees: Float = 0f,
    val scale: Vec2 = Vec2.ONE,
    val skew: Vec2 = Vec2.ZERO,
) {
    fun toMatrix(): Matrix3 = Matrix3.compose(
        position = position,
        pivot = pivot,
        rotationRadians = rotationDegrees.toRadians(),
        scaleFactor = scale,
        skewFactor = skew,
    )

    fun translated(delta: Vec2) = copy(position = position + delta)

    fun rotated(deltaDegrees: Float) = copy(rotationDegrees = rotationDegrees + deltaDegrees)

    fun scaledBy(factor: Float) = copy(scale = scale * factor)

    /** Mirrors horizontally — the single most used character operation. */
    fun flippedHorizontally() = copy(scale = Vec2(-scale.x, scale.y))

    fun flippedVertically() = copy(scale = Vec2(scale.x, -scale.y))

    val isIdentity: Boolean
        get() = position == Vec2.ZERO && rotationDegrees == 0f && scale == Vec2.ONE && skew == Vec2.ZERO

    companion object {
        val Identity = Transform2D()
    }
}
