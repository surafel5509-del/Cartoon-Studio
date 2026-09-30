package com.cartoonstudio.engine.compositing

import com.cartoonstudio.domain.model.BlendMode
import com.cartoonstudio.domain.model.Effect
import com.cartoonstudio.domain.model.MaskMode
import com.cartoonstudio.engine.rendering.RenderCommand

/** A resolved compositing step for one layer group. */
data class CompositePass(
    val opacity: Float,
    val blendMode: BlendMode,
    val maskMode: MaskMode,
    val effects: List<Effect>,
    /** True when the group must be rendered into an offscreen buffer. */
    val requiresOffscreen: Boolean,
    /** Extra padding needed so blurs and glows are not clipped. */
    val bleedPixels: Float,
)

/**
 * Decides how layer groups are composited.
 *
 * Offscreen buffers are expensive on mobile GPUs, so the compositor only
 * requests one when the result would otherwise be wrong — a layer that is
 * fully opaque, in Normal blend mode and has no effects draws straight to the
 * target.
 */
object Compositor {

    fun planFor(
        opacity: Float,
        blendMode: BlendMode,
        maskMode: MaskMode,
        effects: List<Effect>,
    ): CompositePass {
        val active = effects.filter { it.enabled }
        val needsOffscreen = opacity < 0.999f ||
            blendMode != BlendMode.Normal ||
            maskMode != MaskMode.None ||
            active.isNotEmpty()
        return CompositePass(
            opacity = opacity.coerceIn(0f, 1f),
            blendMode = blendMode,
            maskMode = maskMode,
            effects = orderEffects(active),
            requiresOffscreen = needsOffscreen,
            bleedPixels = bleedFor(active),
        )
    }

    /**
     * Effects are applied in a fixed, predictable order regardless of the
     * order they were added in the UI. Colour work happens first, then
     * geometric spreading, then shadows, so results match user expectation.
     */
    fun orderEffects(effects: List<Effect>): List<Effect> =
        effects.sortedBy { effect ->
            when (effect) {
                is Effect.ColorAdjust -> 0
                is Effect.Tint -> 1
                is Effect.Outline -> 2
                is Effect.GaussianBlur -> 3
                is Effect.Glow -> 4
                is Effect.DropShadow -> 5
            }
        }

    /** How far an effect can paint outside the layer's own bounds. */
    fun bleedFor(effects: List<Effect>): Float = effects.fold(0f) { acc, effect ->
        acc + when (effect) {
            is Effect.GaussianBlur -> effect.radius * 2f
            is Effect.Glow -> effect.radius * 2f
            is Effect.DropShadow -> effect.radius * 2f +
                maxOf(kotlin.math.abs(effect.offsetX), kotlin.math.abs(effect.offsetY))
            is Effect.Outline -> effect.width
            else -> 0f
        }
    }

    /**
     * Collapses redundant `BeginLayer`/`EndLayer` pairs.
     *
     * A group that ends up empty (every child culled) costs a full offscreen
     * allocation for nothing, so it is removed before the backend sees it.
     */
    fun optimize(commands: List<RenderCommand>): List<RenderCommand> {
        val result = ArrayList<RenderCommand>(commands.size)
        var index = 0
        while (index < commands.size) {
            val command = commands[index]
            if (command is RenderCommand.BeginLayer &&
                index + 1 < commands.size &&
                commands[index + 1] is RenderCommand.EndLayer
            ) {
                index += 2
                continue
            }
            result += command
            index++
        }
        return result
    }

    /** Estimated peak offscreen memory for a frame, in bytes. */
    fun estimateOffscreenBytes(
        commands: List<RenderCommand>,
        viewportWidth: Int,
        viewportHeight: Int,
    ): Long {
        var depth = 0
        var maxDepth = 0
        commands.forEach {
            when (it) {
                is RenderCommand.BeginLayer -> {
                    depth++
                    maxDepth = maxOf(maxDepth, depth)
                }
                is RenderCommand.EndLayer -> depth = (depth - 1).coerceAtLeast(0)
                else -> Unit
            }
        }
        return maxDepth.toLong() * viewportWidth * viewportHeight * 4L
    }
}
