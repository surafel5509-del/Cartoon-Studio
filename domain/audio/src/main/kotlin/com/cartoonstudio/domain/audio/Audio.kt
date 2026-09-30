package com.cartoonstudio.domain.audio

import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.core.time.FrameRange
import com.cartoonstudio.core.time.FrameRate
import com.cartoonstudio.core.time.Marker
import kotlinx.serialization.Serializable

/** Role of an audio track in the mix. */
@Serializable
enum class AudioRole { Dialogue, Music, Effects, Ambience, Foley, Reference }

/**
 * A piece of audio referenced by a project.
 *
 * Audio is referenced by stable asset id; the bytes live in the project's
 * `audio/` folder or in a content pack, never at an absolute device path.
 */
@Serializable
data class AudioAsset(
    val id: String,
    val name: String,
    val role: AudioRole = AudioRole.Effects,
    val durationMillis: Long = 0L,
    val sampleRate: Int = 44_100,
    val channels: Int = 2,
    /** Relative path inside the project or content pack. */
    val relativePath: String? = null,
    /** Downsampled peaks (0..1) for waveform drawing; computed in background. */
    val waveformPeaks: List<Float> = emptyList(),
)

/** A placement of an [AudioAsset] on a timeline. */
@Serializable
data class AudioClip(
    val id: String,
    val assetId: String,
    val name: String,
    val startFrame: Frame = Frame.ZERO,
    val lengthInFrames: Int = 24,
    /** Trim into the source, in milliseconds. */
    val sourceOffsetMillis: Long = 0L,
    val gain: Float = 1f,
    val pan: Float = 0f,
    val fadeInFrames: Int = 0,
    val fadeOutFrames: Int = 0,
    val muted: Boolean = false,
) {
    val range: FrameRange get() = FrameRange.ofLength(startFrame, lengthInFrames)

    fun gainAt(frame: Frame): Float {
        if (muted || frame !in range) return 0f
        val local = frame.index - startFrame.index
        var g = gain
        if (fadeInFrames > 0 && local < fadeInFrames) g *= local.toFloat() / fadeInFrames
        if (fadeOutFrames > 0 && local > lengthInFrames - fadeOutFrames) {
            g *= (lengthInFrames - local).toFloat() / fadeOutFrames
        }
        return g.coerceIn(0f, 4f)
    }
}

/** A lane in the audio mixer. */
@Serializable
data class AudioTrack(
    val id: String,
    val name: String,
    val role: AudioRole = AudioRole.Effects,
    val clips: List<AudioClip> = emptyList(),
    val gain: Float = 1f,
    val pan: Float = 0f,
    val muted: Boolean = false,
    val solo: Boolean = false,
) {
    fun withClip(clip: AudioClip) = copy(clips = clips + clip)
    fun withoutClip(clipId: String) = copy(clips = clips.filterNot { it.id == clipId })
    fun clipsAt(frame: Frame) = clips.filter { frame in it.range }

    val lengthInFrames: Int get() = clips.maxOfOrNull { it.range.endExclusive.index } ?: 0
}

/**
 * Phoneme shapes used for lip sync.
 *
 * The set follows the widely used Preston Blair mouth chart, which most
 * cartoon mouth sheets are already drawn against.
 */
@Serializable
enum class Viseme { Rest, AI, E, O, U, MBP, FV, L, WQ, Etc;

    val description: String
        get() = when (this) {
            Rest -> "Closed / neutral"
            AI -> "Open — \"ah\", \"i\""
            E -> "Wide — \"eh\""
            O -> "Rounded — \"oh\""
            U -> "Small round — \"oo\""
            MBP -> "Lips closed — m, b, p"
            FV -> "Teeth on lip — f, v"
            L -> "Tongue up — l"
            WQ -> "Pucker — w, q"
            Etc -> "Consonant cluster"
        }
}

/** One mouth shape held over a span of frames. */
@Serializable
data class LipSyncKey(
    val frame: Frame,
    val viseme: Viseme,
    val holdFrames: Int = 1,
)

/**
 * A generated or hand-authored lip sync track bound to a dialogue clip and a
 * character layer.
 */
@Serializable
data class LipSyncTrack(
    val id: String,
    val audioClipId: String,
    val targetLayerId: String,
    val keys: List<LipSyncKey> = emptyList(),
) {
    fun visemeAt(frame: Frame): Viseme =
        keys.lastOrNull { it.frame.index <= frame.index }?.viseme ?: Viseme.Rest

    /**
     * Naive text-driven lip sync: distributes visemes evenly over the clip.
     * Real audio analysis refines this, but it already produces usable timing
     * for blocking a scene.
     */
    companion object {
        fun fromText(
            id: String,
            audioClipId: String,
            targetLayerId: String,
            text: String,
            range: FrameRange,
            @Suppress("UNUSED_PARAMETER") rate: FrameRate = FrameRate.Default,
        ): LipSyncTrack {
            val letters = text.lowercase().filter { it.isLetter() }
            if (letters.isEmpty() || range.isEmpty) {
                return LipSyncTrack(id, audioClipId, targetLayerId)
            }
            val perLetter = (range.lengthInFrames.toFloat() / letters.length).coerceAtLeast(1f)
            val keys = letters.mapIndexed { index, char ->
                LipSyncKey(
                    frame = Frame(range.start.index + (index * perLetter).toInt()),
                    viseme = visemeFor(char),
                    holdFrames = perLetter.toInt().coerceAtLeast(1),
                )
            }.distinctBy { it.frame.index }
            return LipSyncTrack(id, audioClipId, targetLayerId, keys)
        }

        fun visemeFor(char: Char): Viseme = when (char) {
            'a', 'i' -> Viseme.AI
            'e' -> Viseme.E
            'o' -> Viseme.O
            'u' -> Viseme.U
            'm', 'b', 'p' -> Viseme.MBP
            'f', 'v' -> Viseme.FV
            'l' -> Viseme.L
            'w', 'q' -> Viseme.WQ
            else -> Viseme.Etc
        }
    }
}

/** Timeline markers grouped for a scene's audio work. */
@Serializable
data class AudioMarkers(val markers: List<Marker> = emptyList())
