package com.cartoonstudio.engine.animation

import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.domain.animation.AnimationClip
import com.cartoonstudio.domain.animation.AnimationLayerStack
import com.cartoonstudio.domain.animation.ClipBlendMode
import com.cartoonstudio.domain.animation.PropertyTracks

/**
 * Blends a stack of clip instances into a single set of channel values.
 *
 * This is what makes layered performance work: a base `Walk` (Override), plus
 * an additive `Wave` restricted to the arm bones, plus an additive facial
 * `Talk` layer — all evaluated at one frame, bottom to top.
 */
class ClipStackEvaluator(private val clipResolver: (String) -> AnimationClip?) {

    /** Object-level channels (transform, opacity, ...). */
    fun evaluate(stack: AnimationLayerStack, frame: Frame): Map<String, Float> {
        if (stack.instances.isEmpty()) return emptyMap()
        val accumulated = HashMap<String, Float>()

        for (instance in stack.instances) {
            val clip = clipResolver(instance.clipId) ?: continue
            val weight = instance.weightAt(frame, clip)
            if (weight <= 0f) continue
            val local = instance.localFrame(frame, clip)
            val values = TrackEvaluator.evaluateAll(clip.tracks, local)
            mix(accumulated, values, weight, instance.blendMode)
        }
        return accumulated
    }

    /** Per-bone channels for rigged characters. */
    fun evaluateBones(stack: AnimationLayerStack, frame: Frame): Map<String, MutableMap<String, Float>> {
        val result = HashMap<String, MutableMap<String, Float>>()
        for (instance in stack.instances) {
            val clip = clipResolver(instance.clipId) ?: continue
            val weight = instance.weightAt(frame, clip)
            if (weight <= 0f) continue
            val local = instance.localFrame(frame, clip)
            for ((boneId, tracks) in clip.boneTracks) {
                if (instance.boneMask.isNotEmpty() && boneId !in instance.boneMask) continue
                val channelValues = TrackEvaluator.evaluateAll(tracks, local)
                if (channelValues.isEmpty()) continue
                val target = result.getOrPut(boneId) { HashMap() }
                mix(target, channelValues, weight, instance.blendMode)
            }
        }
        return result
    }

    private fun mix(
        target: MutableMap<String, Float>,
        values: Map<String, Float>,
        weight: Float,
        mode: ClipBlendMode,
    ) {
        for ((property, value) in values) {
            val existing = target[property]
            target[property] = when {
                existing == null -> if (mode == ClipBlendMode.Additive) value * weight else value * weight
                mode == ClipBlendMode.Additive -> existing + value * weight
                else -> existing + (value - existing) * weight
            }
        }
    }

    companion object {
        /** Merges evaluated clip output over directly authored channel tracks. */
        fun merge(
            authored: PropertyTracks,
            frame: Frame,
            clipValues: Map<String, Float>,
        ): Map<String, Float> {
            val base = TrackEvaluator.evaluateAll(authored, frame)
            if (clipValues.isEmpty()) return base
            if (base.isEmpty()) return clipValues
            val result = HashMap(clipValues)
            // Directly authored keys win: they are the animator's explicit intent.
            result.putAll(base)
            return result
        }
    }
}
