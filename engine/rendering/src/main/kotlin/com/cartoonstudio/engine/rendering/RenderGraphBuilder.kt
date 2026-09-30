package com.cartoonstudio.engine.rendering

import com.cartoonstudio.core.math.Matrix3
import com.cartoonstudio.core.math.Rect2
import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.domain.drawing.Cel
import com.cartoonstudio.domain.drawing.Rgba
import com.cartoonstudio.domain.model.LayerContent
import com.cartoonstudio.domain.model.OnionSkinSettings
import com.cartoonstudio.domain.model.TextAlign
import com.cartoonstudio.engine.drawing.StrokeTessellator
import com.cartoonstudio.engine.scene.EvaluatedLayer
import com.cartoonstudio.engine.scene.EvaluatedScene

/** Extra passes the interactive editor wants but export must never include. */
data class RenderOverlayOptions(
    val onionSkin: OnionSkinSettings = OnionSkinSettings.Default,
    val onionSkinLayerId: String? = null,
    val onionSkinCels: List<Pair<Int, Cel>> = emptyList(),
    val showRig: Boolean = false,
    val selectedLayerIds: Set<String> = emptySet(),
    val showSelectionBounds: Boolean = true,
    val showSafeArea: Boolean = false,
    val showGrid: Boolean = false,
    val gridSpacing: Float = 64f,
    val canvasBounds: Rect2? = null,
) {
    companion object {
        /** Export configuration: artwork only. */
        val None = RenderOverlayOptions(
            onionSkin = OnionSkinSettings(enabled = false),
            showRig = false,
            showSelectionBounds = false,
        )
    }
}

/**
 * Converts an [EvaluatedScene] into a flat [RenderGraph].
 *
 * Export calls this with [RenderOverlayOptions.None] and the interactive
 * canvas calls it with overlays enabled — the artwork path is byte-identical
 * in both cases.
 */
class RenderGraphBuilder(private val renderScale: Float = 1f) {

    fun build(
        scene: EvaluatedScene,
        viewportWidth: Float,
        viewportHeight: Float,
        overlays: RenderOverlayOptions = RenderOverlayOptions.None,
    ): RenderGraph {
        val commands = ArrayList<RenderCommand>(scene.layers.size * 4 + 8)
        commands += RenderCommand.Clear(scene.backgroundArgb)

        overlays.canvasBounds?.let { bounds ->
            commands += RenderCommand.FillRect(scene.viewMatrix, bounds, scene.backgroundArgb)
        }

        if (overlays.showGrid) {
            appendGrid(commands, scene, overlays)
        }

        for (layer in scene.layers) {
            if (!layer.isVisible) continue
            appendLayer(commands, scene, layer, overlays)
        }

        if (overlays.onionSkin.enabled && overlays.onionSkinCels.isNotEmpty()) {
            appendOnionSkin(commands, scene, overlays)
        }

        if (overlays.showSelectionBounds && overlays.selectedLayerIds.isNotEmpty()) {
            appendSelection(commands, scene, overlays)
        }

        if (overlays.showSafeArea) {
            overlays.canvasBounds?.let { appendSafeArea(commands, scene.viewMatrix, it) }
        }

        return RenderGraph(
            commands = commands,
            viewMatrix = scene.viewMatrix,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            backgroundArgb = scene.backgroundArgb,
        )
    }

    private fun appendLayer(
        commands: MutableList<RenderCommand>,
        scene: EvaluatedScene,
        layer: EvaluatedLayer,
        overlays: RenderOverlayOptions,
    ) {
        val matrix = scene.viewMatrix * layer.worldMatrix
        val needsGroup = layer.opacity < 0.999f ||
            layer.blendMode != com.cartoonstudio.domain.model.BlendMode.Normal ||
            layer.effects.isNotEmpty()

        if (needsGroup) {
            commands += RenderCommand.BeginLayer(
                opacity = layer.opacity,
                blendMode = layer.blendMode,
                effects = layer.effects,
                bounds = null,
            )
        }

        when (val content = layer.content) {
            is LayerContent.Drawing -> layer.cel?.let { appendCel(commands, matrix, it, 1f) }

            is LayerContent.Backdrop -> {
                val bounds = overlays.canvasBounds ?: Rect2.fromSize(1920f, 1080f)
                val end = content.gradientEndColor
                commands += if (end == null) {
                    RenderCommand.FillRect(matrix, bounds, content.color.argb)
                } else {
                    RenderCommand.GradientRect(
                        matrix, bounds, content.color.argb, end.argb, content.gradientAngleDegrees,
                    )
                }
            }

            is LayerContent.Text -> commands += RenderCommand.DrawText(
                transform = matrix,
                text = content.text,
                fontSizePx = content.fontSize,
                argb = content.color.argb,
                bold = content.bold,
                italic = content.italic,
                alignment = when (content.align) {
                    TextAlign.Start -> TextAlignment.Start
                    TextAlign.Center -> TextAlignment.Center
                    TextAlign.End -> TextAlignment.End
                },
                letterSpacing = content.letterSpacing,
                lineHeight = content.lineHeight,
                outlineWidth = content.outlineWidth,
                outlineArgb = content.outlineColor.argb,
            )

            is LayerContent.AssetInstance -> commands += RenderCommand.DrawImage(
                transform = matrix,
                assetId = content.assetId,
                opacity = layer.opacity,
                destination = null,
            )

            is LayerContent.Character -> {
                // Bound artwork follows its bone; unbound artwork uses the
                // character's own transform.
                val skeleton = layer.skeleton
                if (overlays.showRig && skeleton.bones.isNotEmpty()) {
                    skeleton.bones.values.forEach { bone ->
                        commands += RenderCommand.Overlay(
                            transform = matrix,
                            kind = OverlayKind.Bone,
                            points = listOf(bone.headPosition, bone.tailPosition),
                            argb = 0xFF4DD0E1.toInt(),
                            width = 3f,
                        )
                        commands += RenderCommand.Overlay(
                            transform = matrix,
                            kind = OverlayKind.Joint,
                            points = listOf(bone.headPosition),
                            argb = 0xFFFFC107.toInt(),
                            width = 8f,
                        )
                    }
                }
                commands += RenderCommand.DrawImage(
                    transform = matrix,
                    assetId = content.packageId,
                    opacity = layer.opacity,
                    destination = null,
                )
            }

            is LayerContent.Particles -> appendParticles(commands, matrix, content, scene.frame)

            is LayerContent.NestedScene, is LayerContent.Adjustment, is LayerContent.Group -> Unit
        }

        if (needsGroup) commands += RenderCommand.EndLayer
    }

    private fun appendCel(
        commands: MutableList<RenderCommand>,
        matrix: Matrix3,
        cel: Cel,
        opacityScale: Float,
        tint: Rgba? = null,
    ) {
        for (stroke in cel.strokes) {
            val geometry = StrokeTessellator.tessellate(stroke, renderScale)
            if (geometry.isEmpty) continue
            val baseColor = tint ?: stroke.color
            val argb = if (opacityScale >= 0.999f) {
                baseColor.argb
            } else {
                baseColor.withAlphaFraction(baseColor.alphaFraction * opacityScale).argb
            }
            commands += RenderCommand.FillPath(
                transform = matrix,
                outline = geometry.outline,
                argb = argb,
                erase = stroke.isErase,
            )
        }
    }

    private fun appendOnionSkin(
        commands: MutableList<RenderCommand>,
        scene: EvaluatedScene,
        overlays: RenderOverlayOptions,
    ) {
        val settings = overlays.onionSkin
        val target = overlays.onionSkinLayerId?.let { id -> scene.layers.firstOrNull { it.layerId == id } }
        val matrix = scene.viewMatrix * (target?.worldMatrix ?: Matrix3.IDENTITY)
        val currentIndex = scene.frame.index

        for ((frameIndex, cel) in overlays.onionSkinCels) {
            val distance = frameIndex - currentIndex
            if (distance == 0) continue
            val isBefore = distance < 0
            val steps = kotlin.math.abs(distance)
            val allowed = if (isBefore) settings.framesBefore else settings.framesAfter
            if (steps > allowed) continue

            val falloff = 1f - (steps - 1).toFloat() / allowed.coerceAtLeast(1)
            val opacity = (if (isBefore) settings.opacityBefore else settings.opacityAfter) *
                falloff.coerceIn(0.2f, 1f)
            val tint = if (!settings.tintEnabled) null else {
                if (isBefore) settings.tintBefore else settings.tintAfter
            }
            appendCel(commands, matrix, cel, opacity, tint)
        }
    }

    private fun appendParticles(
        commands: MutableList<RenderCommand>,
        matrix: Matrix3,
        content: LayerContent.Particles,
        frame: Frame,
    ) {
        val particles = ParticleSimulator.simulate(content.emitter, frame)
        for (particle in particles) {
            commands += RenderCommand.FillCircle(
                transform = matrix,
                center = particle.position,
                radius = particle.size * 0.5f,
                argb = particle.argb,
            )
        }
    }

    private fun appendSelection(
        commands: MutableList<RenderCommand>,
        scene: EvaluatedScene,
        overlays: RenderOverlayOptions,
    ) {
        for (layerId in overlays.selectedLayerIds) {
            val layer = scene.layer(layerId) ?: continue
            val local = layer.source.localBounds(scene.frame)
            if (local.isEmpty) continue
            val world = layer.worldMatrix.transform(local)
            commands += RenderCommand.Overlay(
                transform = scene.viewMatrix,
                kind = OverlayKind.SelectionBox,
                points = world.corners(),
                argb = 0xFF2196F3.toInt(),
                width = 2f,
            )
        }
    }

    private fun appendSafeArea(commands: MutableList<RenderCommand>, viewMatrix: Matrix3, bounds: Rect2) {
        val actionSafe = shrink(bounds, 0.05f)
        val titleSafe = shrink(bounds, 0.1f)
        listOf(actionSafe to 0x66FFFFFF, titleSafe to 0x44FFFFFF).forEach { (rect, color) ->
            commands += RenderCommand.Overlay(
                transform = viewMatrix,
                kind = OverlayKind.Guide,
                points = rect.corners(),
                argb = color,
                width = 1.5f,
            )
        }
    }

    private fun appendGrid(
        commands: MutableList<RenderCommand>,
        scene: EvaluatedScene,
        overlays: RenderOverlayOptions,
    ) {
        val bounds = overlays.canvasBounds ?: return
        val spacing = overlays.gridSpacing.coerceAtLeast(8f)
        var x = bounds.left
        while (x <= bounds.right) {
            commands += RenderCommand.Overlay(
                transform = scene.viewMatrix,
                kind = OverlayKind.Guide,
                points = listOf(Vec2(x, bounds.top), Vec2(x, bounds.bottom)),
                argb = 0x22000000,
                width = 1f,
            )
            x += spacing
        }
        var y = bounds.top
        while (y <= bounds.bottom) {
            commands += RenderCommand.Overlay(
                transform = scene.viewMatrix,
                kind = OverlayKind.Guide,
                points = listOf(Vec2(bounds.left, y), Vec2(bounds.right, y)),
                argb = 0x22000000,
                width = 1f,
            )
            y += spacing
        }
    }

    private fun shrink(rect: Rect2, fraction: Float): Rect2 {
        val dx = rect.width * fraction
        val dy = rect.height * fraction
        return Rect2(rect.left + dx, rect.top + dy, rect.right - dx, rect.bottom - dy)
    }
}
