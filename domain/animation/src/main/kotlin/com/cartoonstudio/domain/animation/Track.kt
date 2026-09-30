package com.cartoonstudio.domain.animation

import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.core.time.FrameRange
import kotlinx.serialization.Serializable

/**
 * Well-known animatable channels.
 *
 * Using stable string paths (rather than an enum) lets content packs and rigs
 * introduce custom channels without changing the document schema.
 */
object PropertyPath {
    const val POSITION_X = "transform.position.x"
    const val POSITION_Y = "transform.position.y"
    const val ROTATION = "transform.rotation"
    const val SCALE_X = "transform.scale.x"
    const val SCALE_Y = "transform.scale.y"
    const val SKEW_X = "transform.skew.x"
    const val SKEW_Y = "transform.skew.y"
    const val PIVOT_X = "transform.pivot.x"
    const val PIVOT_Y = "transform.pivot.y"
    const val OPACITY = "render.opacity"
    const val VISIBILITY = "render.visible"
    const val CAMERA_ZOOM = "camera.zoom"
    const val CAMERA_X = "camera.x"
    const val CAMERA_Y = "camera.y"
    const val CAMERA_ROTATION = "camera.rotation"
    const val AUDIO_GAIN = "audio.gain"
    const val CEL_INDEX = "drawing.cel"

    val transformChannels = listOf(
        POSITION_X, POSITION_Y, ROTATION, SCALE_X, SCALE_Y, SKEW_X, SKEW_Y, OPACITY,
    )

    fun displayName(path: String): String = when (path) {
        POSITION_X -> "Position X"
        POSITION_Y -> "Position Y"
        ROTATION -> "Rotation"
        SCALE_X -> "Scale X"
        SCALE_Y -> "Scale Y"
        SKEW_X -> "Skew X"
        SKEW_Y -> "Skew Y"
        PIVOT_X -> "Pivot X"
        PIVOT_Y -> "Pivot Y"
        OPACITY -> "Opacity"
        VISIBILITY -> "Visible"
        CAMERA_ZOOM -> "Zoom"
        CAMERA_X -> "Camera X"
        CAMERA_Y -> "Camera Y"
        CAMERA_ROTATION -> "Camera Roll"
        AUDIO_GAIN -> "Gain"
        CEL_INDEX -> "Drawing"
        else -> path.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}

/** What happens outside the authored key range. */
@Serializable
enum class ExtrapolationMode { Hold, Loop, PingPong, Continue }

/**
 * An ordered list of keyframes for one property channel.
 *
 * Keys are kept sorted by frame; all mutating helpers preserve that invariant.
 */
@Serializable
data class Track(
    val property: String,
    val keys: List<Keyframe> = emptyList(),
    val enabled: Boolean = true,
    val preExtrapolation: ExtrapolationMode = ExtrapolationMode.Hold,
    val postExtrapolation: ExtrapolationMode = ExtrapolationMode.Hold,
) {
    val isEmpty: Boolean get() = keys.isEmpty()
    val isAnimated: Boolean get() = keys.size > 1

    val range: FrameRange
        get() = if (keys.isEmpty()) FrameRange.EMPTY
        else FrameRange(keys.first().frame, keys.last().frame + 1)

    fun keyAt(frame: Frame): Keyframe? = keys.firstOrNull { it.frame == frame }

    fun hasKeyAt(frame: Frame): Boolean = keyAt(frame) != null

    fun nextKeyAfter(frame: Frame): Keyframe? = keys.firstOrNull { it.frame > frame }

    fun previousKeyBefore(frame: Frame): Keyframe? = keys.lastOrNull { it.frame < frame }

    /** Inserts or replaces the key at [frame]. */
    fun withKey(key: Keyframe): Track {
        val without = keys.filterNot { it.frame == key.frame }
        return copy(keys = (without + key).sorted())
    }

    fun withKeyAt(frame: Frame, value: Float, interpolation: Interpolation = Interpolation.EaseInOut): Track =
        withKey(Keyframe(frame, value, interpolation))

    fun withoutKeyAt(frame: Frame): Track = copy(keys = keys.filterNot { it.frame == frame })

    fun withKeyMoved(from: Frame, to: Frame): Track {
        val key = keyAt(from) ?: return this
        return withoutKeyAt(from).withKey(key.copy(frame = to))
    }

    fun shifted(deltaFrames: Int): Track =
        copy(keys = keys.map { it.copy(frame = it.frame + deltaFrames) })

    fun scaled(factor: Float, pivot: Frame = Frame.ZERO): Track = copy(
        keys = keys.map {
            val offset = (it.frame.index - pivot.index) * factor
            it.copy(frame = Frame(pivot.index + offset.toInt()))
        }.distinctBy { it.frame.index }.sorted(),
    )

    fun withInterpolation(interpolation: Interpolation): Track =
        copy(keys = keys.map { it.copy(interpolation = interpolation) })

    /** Value bounds across all keys, used to auto-fit the curve editor. */
    fun valueBounds(): ClosedFloatingPointRange<Float> {
        if (keys.isEmpty()) return 0f..1f
        val min = keys.minOf { it.value }
        val max = keys.maxOf { it.value }
        return if (min == max) (min - 1f)..(max + 1f) else min..max
    }
}

/** All animated channels attached to one document object. */
@Serializable
data class PropertyTracks(
    val tracks: Map<String, Track> = emptyMap(),
) {
    val isEmpty: Boolean get() = tracks.isEmpty()
    val isAnimated: Boolean get() = tracks.values.any { it.isAnimated }

    val animatedProperties: List<String> get() = tracks.keys.sorted()

    fun track(property: String): Track? = tracks[property]

    fun withTrack(track: Track): PropertyTracks = copy(tracks = tracks + (track.property to track))

    fun withoutTrack(property: String): PropertyTracks = copy(tracks = tracks - property)

    fun withKey(
        property: String,
        frame: Frame,
        value: Float,
        interpolation: Interpolation = Interpolation.EaseInOut,
    ): PropertyTracks {
        val existing = tracks[property] ?: Track(property)
        return withTrack(existing.withKeyAt(frame, value, interpolation))
    }

    fun withoutKey(property: String, frame: Frame): PropertyTracks {
        val existing = tracks[property] ?: return this
        val updated = existing.withoutKeyAt(frame)
        return if (updated.isEmpty) withoutTrack(property) else withTrack(updated)
    }

    fun hasKeyAt(frame: Frame): Boolean = tracks.values.any { it.hasKeyAt(frame) }

    fun keyedFrames(): List<Frame> =
        tracks.values.flatMap { track -> track.keys.map { it.frame } }.distinct().sortedBy { it.index }

    fun range(): FrameRange {
        val frames = keyedFrames()
        if (frames.isEmpty()) return FrameRange.EMPTY
        return FrameRange(frames.first(), frames.last() + 1)
    }

    fun shifted(deltaFrames: Int): PropertyTracks =
        copy(tracks = tracks.mapValues { (_, track) -> track.shifted(deltaFrames) })

    companion object {
        val Empty = PropertyTracks()
    }
}
