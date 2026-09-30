package com.cartoonstudio.domain.animation

import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.core.time.FrameRange
import kotlinx.serialization.Serializable

/** Broad motion categories used for browsing and filtering the clip library. */
@Serializable
enum class ClipCategory {
    Locomotion, Idle, Gesture, Emotion, Action, Combat, Reaction, Dance, Facial, Camera, Effect, Custom;

    val displayName: String
        get() = name.replaceFirstChar { it.uppercase() }
}

/**
 * A reusable, named block of animation.
 *
 * Clips are authored once (in a content pack or by the user) and then
 * instanced onto any compatible object. Because a clip stores normalised
 * channel curves and not absolute values, the same "Walk" clip retargets onto
 * characters of different proportions.
 */
@Serializable
data class AnimationClip(
    val id: String,
    val name: String,
    val category: ClipCategory = ClipCategory.Custom,
    /** Channel curves, keyed from frame 0 of the clip. */
    val tracks: PropertyTracks = PropertyTracks.Empty,
    /** Per-bone curves for rigged characters: bone id -> channels. */
    val boneTracks: Map<String, PropertyTracks> = emptyMap(),
    val lengthInFrames: Int = 24,
    val loopable: Boolean = true,
    val tags: List<String> = emptyList(),
    val builtIn: Boolean = true,
    /** Rig signature this clip was authored against; used for compatibility. */
    val rigProfile: String? = null,
    val thumbnailRef: String? = null,
) {
    val range: FrameRange get() = FrameRange.ofLength(Frame.ZERO, lengthInFrames)

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return name.lowercase().contains(q) ||
            category.name.lowercase().contains(q) ||
            tags.any { it.lowercase().contains(q) }
    }
}

/** How a clip's values combine with the layers beneath it. */
@Serializable
enum class ClipBlendMode {
    /** Replaces the underlying value. */
    Override,
    /** Adds on top of the underlying value — used for "Walk + Wave". */
    Additive,
}

/**
 * A placement of an [AnimationClip] on a timeline.
 *
 * Instances are cheap: retiming, looping and blending are all evaluated, never
 * baked, so the source clip stays reusable.
 */
@Serializable
data class ClipInstance(
    val id: String,
    val clipId: String,
    val startFrame: Frame = Frame.ZERO,
    /** Playback rate; 2.0 plays the clip twice as fast. */
    val speed: Float = 1f,
    val loopCount: Int = 1,
    val weight: Float = 1f,
    val blendMode: ClipBlendMode = ClipBlendMode.Override,
    /** Cross-fade in/out lengths in frames. */
    val blendInFrames: Int = 0,
    val blendOutFrames: Int = 0,
    val enabled: Boolean = true,
    /** Restricts the instance to specific bones, e.g. only the upper body. */
    val boneMask: List<String> = emptyList(),
) {
    fun lengthFor(clip: AnimationClip): Int {
        val single = (clip.lengthInFrames / speed.coerceAtLeast(0.01f)).toInt().coerceAtLeast(1)
        return single * loopCount.coerceAtLeast(1)
    }

    fun rangeFor(clip: AnimationClip): FrameRange = FrameRange.ofLength(startFrame, lengthFor(clip))

    /** Weight at [frame] including cross-fades, 0 when the instance is inactive. */
    fun weightAt(frame: Frame, clip: AnimationClip): Float {
        if (!enabled) return 0f
        val range = rangeFor(clip)
        if (frame !in range) return 0f
        val local = frame.index - startFrame.index
        val length = range.lengthInFrames
        var w = weight
        if (blendInFrames > 0 && local < blendInFrames) {
            w *= local.toFloat() / blendInFrames
        }
        if (blendOutFrames > 0 && local > length - blendOutFrames) {
            w *= (length - local).toFloat() / blendOutFrames
        }
        return w.coerceIn(0f, 1f)
    }

    /** Maps a timeline frame into clip-local frame space. */
    fun localFrame(frame: Frame, clip: AnimationClip): Frame {
        val elapsed = ((frame.index - startFrame.index) * speed).toInt()
        if (clip.lengthInFrames <= 0) return Frame.ZERO
        return Frame(elapsed.mod(clip.lengthInFrames))
    }
}

/**
 * A stack of clip instances evaluated bottom-to-top.
 *
 * This is what makes layered performance possible: a base `Walk` override
 * layer, plus an additive `Wave` upper-body layer, plus an additive facial
 * `Talk` layer.
 */
@Serializable
data class AnimationLayerStack(
    val instances: List<ClipInstance> = emptyList(),
) {
    fun withInstance(instance: ClipInstance) = copy(instances = instances + instance)

    fun withoutInstance(instanceId: String) =
        copy(instances = instances.filterNot { it.id == instanceId })

    fun updateInstance(instanceId: String, transform: (ClipInstance) -> ClipInstance) =
        copy(instances = instances.map { if (it.id == instanceId) transform(it) else it })

    companion object {
        val Empty = AnimationLayerStack()
    }
}
