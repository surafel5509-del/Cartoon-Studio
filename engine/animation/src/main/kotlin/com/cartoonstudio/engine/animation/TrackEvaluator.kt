package com.cartoonstudio.engine.animation

import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.domain.animation.ExtrapolationMode
import com.cartoonstudio.domain.animation.Interpolation
import com.cartoonstudio.domain.animation.PropertyTracks
import com.cartoonstudio.domain.animation.Track
import kotlin.math.abs

/**
 * Deterministic curve evaluation.
 *
 * Given the same track and frame this always returns the same value — no
 * device state, no floating point frame numbers, no hidden caches. Preview and
 * offline export therefore produce identical motion.
 */
object TrackEvaluator {

    /** Evaluates [track] at [frame], falling back to [defaultValue] when empty. */
    fun evaluate(track: Track, frame: Frame, defaultValue: Float = 0f): Float {
        if (!track.enabled || track.keys.isEmpty()) return defaultValue
        val keys = track.keys
        if (keys.size == 1) return keys.first().value

        val first = keys.first()
        val last = keys.last()

        if (frame.index <= first.frame.index) {
            return extrapolate(track, frame, before = true, defaultValue)
        }
        if (frame.index >= last.frame.index) {
            return extrapolate(track, frame, before = false, defaultValue)
        }

        // Binary search for the segment containing the frame.
        var low = 0
        var high = keys.size - 1
        while (high - low > 1) {
            val mid = (low + high) / 2
            if (keys[mid].frame.index <= frame.index) low = mid else high = mid
        }

        val start = keys[low]
        val end = keys[high]
        val span = (end.frame.index - start.frame.index).toFloat()
        if (span <= 0f) return end.value
        if (start.interpolation == Interpolation.Hold) return start.value

        val t = ((frame.index - start.frame.index) / span).coerceIn(0f, 1f)
        val eased = start.easedFraction(t)
        return start.value + (end.value - start.value) * eased
    }

    private fun extrapolate(track: Track, frame: Frame, before: Boolean, defaultValue: Float): Float {
        val keys = track.keys
        if (keys.isEmpty()) return defaultValue
        val mode = if (before) track.preExtrapolation else track.postExtrapolation
        val anchor = if (before) keys.first() else keys.last()
        val range = track.range
        val length = (keys.last().frame.index - keys.first().frame.index).coerceAtLeast(1)

        return when (mode) {
            ExtrapolationMode.Hold -> anchor.value
            ExtrapolationMode.Loop -> {
                val offset = (frame.index - keys.first().frame.index).mod(length)
                evaluate(track, Frame(keys.first().frame.index + offset), defaultValue)
            }
            ExtrapolationMode.PingPong -> {
                val raw = (frame.index - keys.first().frame.index).mod(length * 2)
                val offset = if (raw <= length) raw else length * 2 - raw
                evaluate(track, Frame(keys.first().frame.index + offset), defaultValue)
            }
            ExtrapolationMode.Continue -> {
                val neighbour = if (before) keys.getOrNull(1) else keys.getOrNull(keys.size - 2)
                if (neighbour == null || range.isEmpty) return anchor.value
                val slopeSpan = abs(neighbour.frame.index - anchor.frame.index).coerceAtLeast(1)
                val slope = (neighbour.value - anchor.value) / slopeSpan
                anchor.value + slope * (frame.index - anchor.frame.index)
            }
        }
    }

    /** Evaluates every channel, returning only the ones that are animated. */
    fun evaluateAll(tracks: PropertyTracks, frame: Frame): Map<String, Float> {
        if (tracks.isEmpty) return emptyMap()
        val result = HashMap<String, Float>(tracks.tracks.size)
        for ((property, track) in tracks.tracks) {
            if (!track.enabled || track.keys.isEmpty()) continue
            result[property] = evaluate(track, frame)
        }
        return result
    }

    /** Value of one named channel, or [defaultValue] when it is not animated. */
    fun value(tracks: PropertyTracks, property: String, frame: Frame, defaultValue: Float): Float {
        val track = tracks.track(property) ?: return defaultValue
        return evaluate(track, frame, defaultValue)
    }

    /**
     * Samples a track across a range — used by the curve editor to draw the
     * spline and by the export planner to detect static spans it can skip.
     */
    fun sample(track: Track, from: Frame, to: Frame, step: Int = 1): List<Pair<Frame, Float>> {
        if (to.index < from.index) return emptyList()
        val result = ArrayList<Pair<Frame, Float>>()
        var index = from.index
        while (index <= to.index) {
            val frame = Frame(index)
            result += frame to evaluate(track, frame)
            index += step.coerceAtLeast(1)
        }
        return result
    }
}
