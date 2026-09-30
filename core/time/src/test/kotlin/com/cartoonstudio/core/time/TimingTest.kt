package com.cartoonstudio.core.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimingTest {

    @Test
    fun `ntsc rate keeps exact rational precision`() {
        val rate = FrameRate.FPS_29_97
        assertEquals(30000, rate.numerator)
        assertEquals(1001, rate.denominator)
        assertTrue(rate.isDropFrameCandidate)
        // One second of 29.97 media is 1001 ms of 30 frames, not 1000 ms.
        assertEquals(1001L, rate.millisForFrames(30))
    }

    @Test
    fun `frame and millisecond conversion round trips`() {
        val rate = FrameRate.FPS_24
        val frame = Frame(48)
        assertEquals(2000L, frame.toMillis(rate))
        assertEquals(frame, Frame.fromMillis(2000L, rate))
    }

    @Test
    fun `frame range is half open`() {
        val range = FrameRange.ofLength(Frame(10), 5)
        assertEquals(5, range.lengthInFrames)
        assertTrue(Frame(10) in range)
        assertTrue(Frame(14) in range)
        assertFalse(Frame(15) in range)
        assertEquals(Frame(14), range.lastFrame)
    }

    @Test
    fun `timecode formats hours minutes seconds frames`() {
        assertEquals("00:00:01:00", Timecode.format(Frame(24), FrameRate.FPS_24))
        assertEquals("00:01:00:00", Timecode.format(Frame(24 * 60), FrameRate.FPS_24))
    }

    @Test
    fun `ranges detect overlap`() {
        val a = FrameRange.ofLength(Frame(0), 10)
        val b = FrameRange.ofLength(Frame(9), 10)
        val c = FrameRange.ofLength(Frame(10), 10)
        assertTrue(a.intersects(b))
        assertFalse(a.intersects(c))
    }
}
