package com.cartoonstudio.core.time

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Rational frame rate.
 *
 * Persistent animation state never stores floating point frame numbers, so the
 * rate is kept as an exact rational (e.g. 24000/1001 for 23.976 fps) and all
 * conversions happen through this type.
 */
@Serializable
data class FrameRate(val numerator: Int, val denominator: Int = 1) {

    init {
        require(numerator > 0) { "frame rate numerator must be positive" }
        require(denominator > 0) { "frame rate denominator must be positive" }
    }

    val fps: Double get() = numerator.toDouble() / denominator.toDouble()

    val frameDurationMillis: Double get() = 1000.0 * denominator / numerator

    /** True for broadcast rates that are not whole numbers. */
    val isDropFrameCandidate: Boolean get() = denominator != 1

    fun framesForMillis(millis: Long): Int = floor(millis / frameDurationMillis).toInt()

    fun millisForFrames(frames: Int): Long = (frames * frameDurationMillis).roundToLong()

    override fun toString(): String =
        if (denominator == 1) "$numerator fps" else String.format("%.3f fps", fps)

    companion object {
        val FPS_12 = FrameRate(12)
        val FPS_24 = FrameRate(24)
        val FPS_25 = FrameRate(25)
        val FPS_30 = FrameRate(30)
        val FPS_48 = FrameRate(48)
        val FPS_50 = FrameRate(50)
        val FPS_60 = FrameRate(60)
        val FPS_23_976 = FrameRate(24000, 1001)
        val FPS_29_97 = FrameRate(30000, 1001)

        val PRESETS = listOf(FPS_12, FPS_24, FPS_23_976, FPS_25, FPS_29_97, FPS_30, FPS_48, FPS_50, FPS_60)

        val Default = FPS_24
    }
}

/**
 * An integral frame index on a timeline. Frame 0 is the first frame.
 */
@Serializable
@JvmInline
value class Frame(val index: Int) : Comparable<Frame> {

    operator fun plus(other: Frame) = Frame(index + other.index)
    operator fun minus(other: Frame) = Frame(index - other.index)
    operator fun plus(delta: Int) = Frame(index + delta)
    operator fun minus(delta: Int) = Frame(index - delta)
    operator fun times(factor: Int) = Frame(index * factor)

    override fun compareTo(other: Frame): Int = index.compareTo(other.index)

    fun coerceIn(min: Frame, max: Frame) = Frame(index.coerceIn(min.index, max.index))

    fun toMillis(rate: FrameRate): Long = rate.millisForFrames(index)

    fun toSeconds(rate: FrameRate): Double = index / rate.fps

    companion object {
        val ZERO = Frame(0)
        fun fromMillis(millis: Long, rate: FrameRate) = Frame(rate.framesForMillis(millis))
        fun fromSeconds(seconds: Double, rate: FrameRate) = Frame((seconds * rate.fps).roundToInt())
    }
}

/** Half-open frame range `[start, endExclusive)`. */
@Serializable
data class FrameRange(val start: Frame, val endExclusive: Frame) {

    val lengthInFrames: Int get() = (endExclusive.index - start.index).coerceAtLeast(0)
    val isEmpty: Boolean get() = lengthInFrames == 0
    val lastFrame: Frame get() = Frame((endExclusive.index - 1).coerceAtLeast(start.index))

    operator fun contains(frame: Frame): Boolean =
        frame.index >= start.index && frame.index < endExclusive.index

    fun clamp(frame: Frame): Frame =
        Frame(frame.index.coerceIn(start.index, (endExclusive.index - 1).coerceAtLeast(start.index)))

    fun shifted(delta: Int) = FrameRange(start + delta, endExclusive + delta)

    fun intersects(other: FrameRange): Boolean =
        start.index < other.endExclusive.index && other.start.index < endExclusive.index

    fun durationMillis(rate: FrameRate): Long = rate.millisForFrames(lengthInFrames)

    /** Normalized position of [frame] inside the range, clamped to 0..1. */
    fun fractionOf(frame: Frame): Float {
        if (lengthInFrames <= 1) return 0f
        return ((frame.index - start.index).toFloat() / (lengthInFrames - 1).toFloat()).coerceIn(0f, 1f)
    }

    fun frames(): IntRange = start.index until endExclusive.index

    companion object {
        val EMPTY = FrameRange(Frame.ZERO, Frame.ZERO)
        fun ofLength(start: Frame, length: Int) = FrameRange(start, start + length)
    }
}

/** Named point on the timeline (beat, dialogue cue, act break, ...). */
@Serializable
data class Marker(
    val id: String,
    val frame: Frame,
    val label: String,
    val colorArgb: Int = 0xFFFFC107.toInt(),
    val kind: MarkerKind = MarkerKind.General,
)

@Serializable
enum class MarkerKind { General, Dialogue, Action, Camera, Audio, Review }

/** Formats frames as `HH:MM:SS:FF` timecode. */
object Timecode {

    fun format(frame: Frame, rate: FrameRate): String {
        val fps = rate.fps
        val totalSeconds = floor(frame.index / fps).toInt()
        val frames = (frame.index - floor(totalSeconds * fps)).toInt().coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d:%02d:%02d".format(hours, minutes, seconds, frames)
    }

    fun formatShort(frame: Frame, rate: FrameRate): String {
        val totalSeconds = frame.index / rate.fps
        val minutes = floor(totalSeconds / 60).toInt()
        val seconds = totalSeconds - minutes * 60
        return "%d:%05.2f".format(minutes, seconds)
    }

    fun formatDuration(millis: Long): String {
        val totalSeconds = abs(millis) / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
