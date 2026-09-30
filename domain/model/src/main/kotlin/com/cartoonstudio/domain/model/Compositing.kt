package com.cartoonstudio.domain.model

import com.cartoonstudio.domain.drawing.Rgba
import kotlinx.serialization.Serializable

/**
 * Layer blend modes.
 *
 * The set is limited to modes that map onto Android's `BlendMode`/`PorterDuff`
 * support, so preview and export always agree.
 */
@Serializable
enum class BlendMode {
    Normal, Multiply, Screen, Overlay, Darken, Lighten, ColorDodge, ColorBurn,
    HardLight, SoftLight, Difference, Exclusion, Hue, Saturation, Color, Luminosity, Add;

    val displayName: String
        get() = name.replace(Regex("([a-z])([A-Z])"), "$1 $2")

    companion object {
        /** Modes safe on every supported API level. */
        val common = listOf(Normal, Multiply, Screen, Overlay, Darken, Lighten, Add, Difference)
    }
}

/** How a layer participates in masking with its neighbours. */
@Serializable
enum class MaskMode {
    None,
    /** This layer's alpha clips the layer directly below it. */
    ClipBelow,
    /** This layer is clipped by the layer above it. */
    ClippedByAbove,
    /** Inverted alpha mask. */
    InvertedClipBelow,
    /** Used purely as a mask and never drawn itself. */
    MatteOnly,
}

/** Non-destructive layer effects, evaluated by the compositor. */
@Serializable
sealed interface Effect {
    val id: String
    val enabled: Boolean
    val displayName: String

    @Serializable
    data class GaussianBlur(
        override val id: String,
        val radius: Float = 8f,
        override val enabled: Boolean = true,
    ) : Effect {
        override val displayName: String get() = "Blur"
    }

    @Serializable
    data class Glow(
        override val id: String,
        val radius: Float = 16f,
        val intensity: Float = 0.8f,
        val color: Rgba = Rgba.of(255, 236, 160),
        override val enabled: Boolean = true,
    ) : Effect {
        override val displayName: String get() = "Glow"
    }

    @Serializable
    data class DropShadow(
        override val id: String,
        val offsetX: Float = 6f,
        val offsetY: Float = 8f,
        val radius: Float = 10f,
        val color: Rgba = Rgba.of(0, 0, 0, 120),
        override val enabled: Boolean = true,
    ) : Effect {
        override val displayName: String get() = "Drop Shadow"
    }

    @Serializable
    data class Outline(
        override val id: String,
        val width: Float = 4f,
        val color: Rgba = Rgba.Ink,
        override val enabled: Boolean = true,
    ) : Effect {
        override val displayName: String get() = "Outline"
    }

    @Serializable
    data class ColorAdjust(
        override val id: String,
        val brightness: Float = 0f,
        val contrast: Float = 1f,
        val saturation: Float = 1f,
        val hueShiftDegrees: Float = 0f,
        override val enabled: Boolean = true,
    ) : Effect {
        override val displayName: String get() = "Color Adjust"
    }

    @Serializable
    data class Tint(
        override val id: String,
        val color: Rgba = Rgba.White,
        val amount: Float = 0.5f,
        override val enabled: Boolean = true,
    ) : Effect {
        override val displayName: String get() = "Tint"
    }
}

/** Particle emitter description used by the VFX system. */
@Serializable
data class ParticleEmitter(
    val id: String,
    val preset: ParticlePreset = ParticlePreset.Sparkle,
    val rate: Float = 24f,
    val lifetimeFrames: Int = 30,
    val speed: Float = 90f,
    val spreadDegrees: Float = 30f,
    val directionDegrees: Float = -90f,
    val gravity: Float = 120f,
    val startSize: Float = 12f,
    val endSize: Float = 2f,
    val startColor: Rgba = Rgba.of(255, 236, 160),
    val endColor: Rgba = Rgba.of(255, 120, 60, 0),
    val seed: Int = 1337,
)

@Serializable
enum class ParticlePreset {
    Sparkle, Smoke, Fire, Dust, Rain, Snow, Bubbles, Confetti, Leaves, Stars, Magic, Explosion;

    val displayName: String get() = name
}

/** Onion skinning configuration for frame-by-frame drawing. */
@Serializable
data class OnionSkinSettings(
    val enabled: Boolean = false,
    val framesBefore: Int = 2,
    val framesAfter: Int = 1,
    val opacityBefore: Float = 0.35f,
    val opacityAfter: Float = 0.25f,
    val tintBefore: Rgba = Rgba.of(255, 80, 80),
    val tintAfter: Rgba = Rgba.of(80, 160, 255),
    val tintEnabled: Boolean = true,
) {
    companion object {
        val Default = OnionSkinSettings()
    }
}
