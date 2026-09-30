package com.cartoonstudio.domain.model

import com.cartoonstudio.core.math.Rect2
import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.domain.animation.AnimationLayerStack
import com.cartoonstudio.domain.animation.PropertyTracks
import com.cartoonstudio.domain.camera.ParallaxSettings
import com.cartoonstudio.domain.drawing.Cel
import com.cartoonstudio.domain.drawing.Rgba
import com.cartoonstudio.domain.rigging.Skeleton
import kotlinx.serialization.Serializable

/** What a layer actually contains. */
@Serializable
sealed interface LayerContent {

    /**
     * Hand-drawn, frame-by-frame artwork.
     *
     * [cels] is sparse and keyed by frame index: a frame without its own cel
     * holds the previous one, which is how animating "on twos" works with no
     * duplicated data.
     */
    @Serializable
    data class Drawing(
        val cels: Map<Int, Cel> = emptyMap(),
        val onionSkin: OnionSkinSettings = OnionSkinSettings.Default,
    ) : LayerContent {

        /** The cel that should be visible at [frame] (exposure sheet lookup). */
        fun celAt(frame: Frame): Cel? {
            cels[frame.index]?.let { return it }
            val previousKey = cels.keys.filter { it <= frame.index }.maxOrNull() ?: return null
            return cels[previousKey]
        }

        fun exposedFrames(): List<Int> = cels.keys.sorted()

        fun withCel(frame: Frame, cel: Cel): Drawing = copy(cels = cels + (frame.index to cel))

        fun withoutCel(frame: Frame): Drawing = copy(cels = cels - frame.index)
    }

    /** An instance of a library asset (prop, background, vehicle, ...). */
    @Serializable
    data class AssetInstance(
        val assetId: String,
        val variantId: String? = null,
        val tintColor: Rgba? = null,
    ) : LayerContent

    /** A rigged character instance with its own motion stack. */
    @Serializable
    data class Character(
        val packageId: String,
        val skeleton: Skeleton? = null,
        val variantId: String? = null,
        val activePoseId: String? = null,
        val expressionId: String? = null,
        val motion: AnimationLayerStack = AnimationLayerStack.Empty,
        /** Per-bone animation authored directly on this instance. */
        val boneTracks: Map<String, PropertyTracks> = emptyMap(),
    ) : LayerContent

    /** Titles, credits, captions and speech bubbles. */
    @Serializable
    data class Text(
        val text: String = "Text",
        val fontSize: Float = 48f,
        val color: Rgba = Rgba.Ink,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val align: TextAlign = TextAlign.Center,
        val letterSpacing: Float = 0f,
        val lineHeight: Float = 1.2f,
        val outlineWidth: Float = 0f,
        val outlineColor: Rgba = Rgba.White,
    ) : LayerContent

    /** A folder of layers sharing a transform, opacity and effects. */
    @Serializable
    data class Group(
        val children: List<Layer> = emptyList(),
        val expanded: Boolean = true,
    ) : LayerContent

    /** Particle / VFX emitter. */
    @Serializable
    data class Particles(val emitter: ParticleEmitter) : LayerContent

    /** Another scene rendered as a layer — used for reusable backgrounds. */
    @Serializable
    data class NestedScene(val sceneId: String) : LayerContent

    /** Effect-only layer that adjusts everything beneath it. */
    @Serializable
    data class Adjustment(val note: String = "") : LayerContent

    /** Flat colour or gradient backdrop. */
    @Serializable
    data class Backdrop(
        val color: Rgba = Rgba.Paper,
        val gradientEndColor: Rgba? = null,
        val gradientAngleDegrees: Float = 90f,
    ) : LayerContent
}

@Serializable
enum class TextAlign { Start, Center, End }

/** Coarse layer type, used for icons, filtering and creation menus. */
enum class LayerKind(val displayName: String) {
    Drawing("Drawing"),
    Asset("Asset"),
    Character("Character"),
    Text("Text"),
    Group("Group"),
    Particles("Particles"),
    NestedScene("Scene"),
    Adjustment("Adjustment"),
    Backdrop("Backdrop"),
}

/**
 * A layer in a scene.
 *
 * Layers own presentation state (opacity, blending, masking, effects), their
 * transform, and their animation. Nesting happens through
 * [LayerContent.Group].
 */
@Serializable
data class Layer(
    val id: String,
    val name: String,
    val content: LayerContent,
    val transform: Transform2D = Transform2D.Identity,
    val opacity: Float = 1f,
    val blendMode: BlendMode = BlendMode.Normal,
    val maskMode: MaskMode = MaskMode.None,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val solo: Boolean = false,
    val tracks: PropertyTracks = PropertyTracks.Empty,
    val effects: List<Effect> = emptyList(),
    val parallax: ParallaxSettings = ParallaxSettings.Disabled,
    val colorTagArgb: Int? = null,
    /** Frame span in which the layer exists; null means the whole scene. */
    val inFrame: Frame? = null,
    val outFrame: Frame? = null,
) {
    val kind: LayerKind
        get() = when (content) {
            is LayerContent.Drawing -> LayerKind.Drawing
            is LayerContent.AssetInstance -> LayerKind.Asset
            is LayerContent.Character -> LayerKind.Character
            is LayerContent.Text -> LayerKind.Text
            is LayerContent.Group -> LayerKind.Group
            is LayerContent.Particles -> LayerKind.Particles
            is LayerContent.NestedScene -> LayerKind.NestedScene
            is LayerContent.Adjustment -> LayerKind.Adjustment
            is LayerContent.Backdrop -> LayerKind.Backdrop
        }

    val children: List<Layer>
        get() = (content as? LayerContent.Group)?.children.orEmpty()

    val isAnimated: Boolean
        get() = tracks.isAnimated ||
            (content as? LayerContent.Character)?.motion?.instances?.isNotEmpty() == true ||
            (content as? LayerContent.Drawing)?.cels?.size?.let { it > 1 } == true

    fun existsAt(frame: Frame): Boolean {
        val afterIn = inFrame?.let { frame.index >= it.index } ?: true
        val beforeOut = outFrame?.let { frame.index < it.index } ?: true
        return afterIn && beforeOut
    }

    fun withChildren(children: List<Layer>): Layer {
        val group = content as? LayerContent.Group ?: return this
        return copy(content = group.copy(children = children))
    }

    /** Depth-first walk over this layer and every descendant. */
    fun walk(action: (Layer) -> Unit) {
        action(this)
        children.forEach { it.walk(action) }
    }

    fun findById(layerId: String): Layer? {
        if (id == layerId) return this
        children.forEach { child -> child.findById(layerId)?.let { return it } }
        return null
    }

    /** Local-space content bounds, before this layer's own transform. */
    fun localBounds(frame: Frame): Rect2 = when (content) {
        is LayerContent.Drawing -> content.celAt(frame)?.bounds() ?: Rect2.EMPTY
        is LayerContent.Group -> content.children.fold(Rect2.INVALID) { acc, child ->
            acc.union(child.transform.toMatrix().transform(child.localBounds(frame)))
        }
        is LayerContent.Text -> Rect2.fromCenter(
            com.cartoonstudio.core.math.Vec2.ZERO,
            content.text.length * content.fontSize * 0.55f,
            content.fontSize * content.lineHeight,
        )
        else -> Rect2.fromCenter(com.cartoonstudio.core.math.Vec2.ZERO, 240f, 240f)
    }

    companion object {
        fun drawing(id: String, name: String) = Layer(id, name, LayerContent.Drawing())
        fun group(id: String, name: String, children: List<Layer> = emptyList()) =
            Layer(id, name, LayerContent.Group(children))
    }
}
