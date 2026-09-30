package com.cartoonstudio.engine.animation

import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.domain.animation.ExtrapolationMode
import com.cartoonstudio.domain.animation.Interpolation
import com.cartoonstudio.domain.animation.Keyframe
import com.cartoonstudio.domain.animation.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackEvaluatorTest {

    private val linear = Track(
        property = "transform.position.x",
        keys = listOf(
            Keyframe(Frame(0), 0f, Interpolation.Linear),
            Keyframe(Frame(10), 100f, Interpolation.Linear),
        ),
    )

    @Test
    fun `empty track returns the default`() {
        assertEquals(7f, TrackEvaluator.evaluate(Track("x"), Frame(3), defaultValue = 7f))
    }

    @Test
    fun `linear segments interpolate proportionally`() {
        assertEquals(0f, TrackEvaluator.evaluate(linear, Frame(0)))
        assertEquals(50f, TrackEvaluator.evaluate(linear, Frame(5)))
        assertEquals(100f, TrackEvaluator.evaluate(linear, Frame(10)))
    }

    @Test
    fun `hold keys step instead of blending`() {
        val held = linear.copy(
            keys = listOf(
                Keyframe(Frame(0), 0f, Interpolation.Hold),
                Keyframe(Frame(10), 100f, Interpolation.Hold),
            ),
        )
        assertEquals(0f, TrackEvaluator.evaluate(held, Frame(9)))
        assertEquals(100f, TrackEvaluator.evaluate(held, Frame(10)))
    }

    @Test
    fun `evaluation outside the key range holds by default`() {
        assertEquals(0f, TrackEvaluator.evaluate(linear, Frame(-20)))
        assertEquals(100f, TrackEvaluator.evaluate(linear, Frame(999)))
    }

    @Test
    fun `looping extrapolation repeats the cycle`() {
        val looping = linear.copy(
            preExtrapolation = ExtrapolationMode.Loop,
            postExtrapolation = ExtrapolationMode.Loop,
        )
        assertEquals(
            TrackEvaluator.evaluate(looping, Frame(3)),
            TrackEvaluator.evaluate(looping, Frame(13)),
            absoluteTolerance = 0.001f,
        )
    }

    @Test
    fun `easing stays inside the value range and is monotonic`() {
        val eased = linear.copy(
            keys = listOf(
                Keyframe(Frame(0), 0f, Interpolation.EaseInOut),
                Keyframe(Frame(10), 100f, Interpolation.EaseInOut),
            ),
        )
        var previous = -1f
        for (index in 0..10) {
            val value = TrackEvaluator.evaluate(eased, Frame(index))
            assertTrue(value in 0f..100f, "value $value out of range at frame $index")
            assertTrue(value >= previous, "curve went backwards at frame $index")
            previous = value
        }
    }

    @Test
    fun `evaluation is deterministic`() {
        repeat(50) {
            assertEquals(50f, TrackEvaluator.evaluate(linear, Frame(5)))
        }
    }
}
