package com.cartoonstudio.domain.animation

import com.cartoonstudio.core.math.CubicBezierEasing
import com.cartoonstudio.core.math.Easings
import com.cartoonstudio.core.time.Frame
import kotlinx.serialization.Serializable

/** Interpolation applied on the segment that *starts* at a keyframe. */
@Serializable
enum class Interpolation {
    /** Value jumps at the next key — the default for frame-by-frame work. */
    Hold,
    Linear,
    EaseIn,
    EaseOut,
    EaseInOut,
    Smooth,
    Bounce,
    Elastic,
    /** Uses the keyframe's own bezier handles. */
    Bezier;

    val isSmooth: Boolean get() = this != Hold && this != Linear
}

/** Normalised bezier handles in (time, value) space for custom curves. */
@Serializable
data class BezierHandles(
    val outX: Float = 0.42f,
    val outY: Float = 0f,
    val inX: Float = 0.58f,
    val inY: Float = 1f,
) {
    companion object {
        val Default = BezierHandles()
    }
}

/**
 * A single animated value at an exact frame.
 *
 * Values are `Float`; colours, positions and rotations are animated as
 * separate channels so every track shares one evaluation path and one
 * curve editor.
 */
@Serializable
data class Keyframe(
    val frame: Frame,
    val value: Float,
    val interpolation: Interpolation = Interpolation.EaseInOut,
    val handles: BezierHandles = BezierHandles.Default,
    /** Optional label shown in the dope sheet (e.g. "contact", "breakdown"). */
    val label: String? = null,
) : Comparable<Keyframe> {

    override fun compareTo(other: Keyframe): Int = frame.index.compareTo(other.frame.index)

    fun easedFraction(t: Float): Float = when (interpolation) {
        Interpolation.Hold -> 0f
        Interpolation.Linear -> t
        Interpolation.EaseIn -> Easings.EaseIn.transform(t)
        Interpolation.EaseOut -> Easings.EaseOut.transform(t)
        Interpolation.EaseInOut -> Easings.EaseInOut.transform(t)
        Interpolation.Smooth -> Easings.Smooth.transform(t)
        Interpolation.Bounce -> Easings.bounceOut(t)
        Interpolation.Elastic -> Easings.elasticOut(t)
        Interpolation.Bezier -> CubicBezierEasing(handles.outX, handles.outY, handles.inX, handles.inY)
            .transform(t)
    }
}
