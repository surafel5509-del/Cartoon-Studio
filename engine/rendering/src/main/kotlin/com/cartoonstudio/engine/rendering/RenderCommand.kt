package com.cartoonstudio.engine.rendering

import com.cartoonstudio.core.math.Matrix3
import com.cartoonstudio.core.math.Rect2
import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.domain.model.BlendMode
import com.cartoonstudio.domain.model.Effect

/**
 * Backend-independent drawing instruction.
 *
 * The engine never talks to Android APIs; it emits this command list and a
 * platform backend (Canvas today, a GPU backend later) replays it. Geometry
 * stays in local space with an accompanying [Matrix3] so backends can use
 * hardware transform concatenation instead of re-tessellating.
 */
sealed interface RenderCommand {

    data class Clear(val argb: Int) : RenderCommand

    /**
     * Opens an offscreen compositing group. Required for layer opacity, blend
     * modes, masks and effects to apply to the layer as a whole.
     */
    data class BeginLayer(
        val opacity: Float,
        val blendMode: BlendMode,
        val effects: List<Effect>,
        val bounds: Rect2?,
        val isolate: Boolean = false,
    ) : RenderCommand

    data object EndLayer : RenderCommand

    /** Filled polygon; the standard representation of a tessellated stroke. */
    data class FillPath(
        val transform: Matrix3,
        val outline: List<Vec2>,
        val argb: Int,
        val antiAlias: Boolean = true,
        val erase: Boolean = false,
    ) : RenderCommand

    /** Stroked polyline; used for constant-width tools and UI overlays. */
    data class StrokePath(
        val transform: Matrix3,
        val points: List<Vec2>,
        val width: Float,
        val argb: Int,
        val closed: Boolean = false,
        val roundCap: Boolean = true,
    ) : RenderCommand

    data class FillRect(
        val transform: Matrix3,
        val rect: Rect2,
        val argb: Int,
    ) : RenderCommand

    data class GradientRect(
        val transform: Matrix3,
        val rect: Rect2,
        val startArgb: Int,
        val endArgb: Int,
        val angleDegrees: Float,
    ) : RenderCommand

    data class FillCircle(
        val transform: Matrix3,
        val center: Vec2,
        val radius: Float,
        val argb: Int,
    ) : RenderCommand

    data class DrawText(
        val transform: Matrix3,
        val text: String,
        val fontSizePx: Float,
        val argb: Int,
        val bold: Boolean,
        val italic: Boolean,
        val alignment: TextAlignment,
        val letterSpacing: Float = 0f,
        val lineHeight: Float = 1.2f,
        val outlineWidth: Float = 0f,
        val outlineArgb: Int = 0,
    ) : RenderCommand

    /** Bitmap referenced by stable asset id; the backend resolves and caches it. */
    data class DrawImage(
        val transform: Matrix3,
        val assetId: String,
        val opacity: Float,
        val destination: Rect2?,
    ) : RenderCommand

    /** Editor-only overlay (rig bones, pivots, selection); never exported. */
    data class Overlay(
        val transform: Matrix3,
        val kind: OverlayKind,
        val points: List<Vec2>,
        val argb: Int,
        val width: Float = 2f,
    ) : RenderCommand
}

enum class TextAlignment { Start, Center, End }

enum class OverlayKind { Bone, Joint, Pivot, SelectionBox, Guide, OnionGhost }

/**
 * An ordered command list for one frame plus the metadata the backend and the
 * performance HUD need.
 */
data class RenderGraph(
    val commands: List<RenderCommand>,
    val viewMatrix: Matrix3,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val backgroundArgb: Int,
) {
    val commandCount: Int get() = commands.size

    val layerGroupCount: Int get() = commands.count { it is RenderCommand.BeginLayer }

    companion object {
        fun empty(width: Float, height: Float) = RenderGraph(
            commands = emptyList(),
            viewMatrix = Matrix3.IDENTITY,
            viewportWidth = width,
            viewportHeight = height,
            backgroundArgb = 0xFFFFFFFF.toInt(),
        )
    }
}
