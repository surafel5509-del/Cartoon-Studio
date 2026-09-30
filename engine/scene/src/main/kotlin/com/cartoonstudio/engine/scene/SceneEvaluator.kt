package com.cartoonstudio.engine.scene

import com.cartoonstudio.core.math.Matrix3
import com.cartoonstudio.core.math.Rect2
import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.domain.animation.AnimationClip
import com.cartoonstudio.domain.animation.PropertyPath
import com.cartoonstudio.domain.camera.Camera
import com.cartoonstudio.domain.model.Layer
import com.cartoonstudio.domain.model.LayerContent
import com.cartoonstudio.domain.model.Scene
import com.cartoonstudio.domain.model.Transform2D
import com.cartoonstudio.engine.animation.ClipStackEvaluator
import com.cartoonstudio.engine.animation.TrackEvaluator
import com.cartoonstudio.engine.rigging.RigSolver
import com.cartoonstudio.engine.rigging.SolvedSkeleton

/** Options that change what the evaluator emits (preview vs. export). */
data class EvaluationOptions(
    val viewportWidth: Float,
    val viewportHeight: Float,
    /** Applies the scene camera; export always does, some editor views do not. */
    val applyCamera: Boolean = true,
    /** Skips layers that fall entirely outside the camera frustum. */
    val cullOffscreen: Boolean = true,
    /** Renders layers hidden in the editor (used by "isolate" previews). */
    val includeHidden: Boolean = false,
)

/**
 * Turns document state into a flat, renderable frame description.
 *
 * `Project State -> Scene Evaluation -> Render Graph -> Graphics Backend`
 *
 * This is the single shared contract between the interactive preview and the
 * offline export renderer.
 */
class SceneEvaluator(
    private val clipResolver: (String) -> AnimationClip? = { null },
) {

    private val clipStackEvaluator = ClipStackEvaluator(clipResolver)

    fun evaluate(scene: Scene, frame: Frame, options: EvaluationOptions): EvaluatedScene {
        val camera = evaluateCamera(scene.camera, frame)
        val viewMatrix = if (options.applyCamera) {
            camera.viewMatrix(options.viewportWidth, options.viewportHeight)
        } else {
            Matrix3.IDENTITY
        }

        val frustum = if (options.cullOffscreen && options.applyCamera) {
            camera.frustum(options.viewportWidth, options.viewportHeight).inflate(CULL_MARGIN)
        } else {
            null
        }

        val output = ArrayList<EvaluatedLayer>()
        var bounds = Rect2.INVALID

        // Layers are stored top-to-bottom (as shown in the layer panel), so the
        // draw order is the reverse.
        for (layer in scene.layers.asReversed()) {
            evaluateLayer(
                layer = layer,
                frame = frame,
                parentMatrix = Matrix3.IDENTITY,
                parentOpacity = 1f,
                camera = camera,
                options = options,
                soloActive = scene.hasSolo,
                depth = 0,
                output = output,
            )
        }

        val visible = if (frustum == null) output else output.filter { evaluated ->
            val local = evaluated.source.localBounds(frame)
            local.isEmpty || evaluated.worldMatrix.transform(local).intersects(frustum)
        }

        visible.forEach { evaluated ->
            val local = evaluated.source.localBounds(frame)
            if (!local.isEmpty) bounds = bounds.union(evaluated.worldMatrix.transform(local))
        }

        return EvaluatedScene(
            sceneId = scene.id,
            frame = frame,
            camera = camera,
            viewMatrix = viewMatrix,
            layers = visible,
            backgroundArgb = scene.backgroundColor.argb,
            contentBounds = if (bounds == Rect2.INVALID) Rect2.EMPTY else bounds,
        )
    }

    private fun evaluateLayer(
        layer: Layer,
        frame: Frame,
        parentMatrix: Matrix3,
        parentOpacity: Float,
        camera: Camera,
        options: EvaluationOptions,
        soloActive: Boolean,
        depth: Int,
        output: MutableList<EvaluatedLayer>,
    ) {
        if (!options.includeHidden) {
            if (!layer.visible || !layer.existsAt(frame)) return
            if (soloActive && !layer.solo && !layer.walkAny { it.solo }) return
        }

        val channels = animatedChannels(layer, frame)
        val transform = applyChannels(layer.transform, channels)
        val parallaxMatrix = parallaxMatrixFor(layer, camera)
        val worldMatrix = parentMatrix * parallaxMatrix * transform.toMatrix()

        val opacity = (parentOpacity * layer.opacity *
            (channels[PropertyPath.OPACITY] ?: 1f)).coerceIn(0f, 1f)

        when (val content = layer.content) {
            is LayerContent.Group -> {
                // A group contributes no geometry of its own; it composes children.
                for (child in content.children.asReversed()) {
                    evaluateLayer(
                        layer = child,
                        frame = frame,
                        parentMatrix = worldMatrix,
                        parentOpacity = opacity,
                        camera = camera,
                        options = options,
                        soloActive = soloActive,
                        depth = depth + 1,
                        output = output,
                    )
                }
            }

            is LayerContent.Drawing -> {
                val celIndexOverride = channels[PropertyPath.CEL_INDEX]?.toInt()
                val celFrame = celIndexOverride?.let { Frame(it) } ?: frame
                output += baseEvaluated(layer, content, worldMatrix, opacity, depth)
                    .copy(cel = content.celAt(celFrame))
            }

            is LayerContent.Character -> {
                val skeleton = content.skeleton
                val solved = if (skeleton == null) {
                    SolvedSkeleton.Empty
                } else {
                    val fromClips = clipStackEvaluator.evaluateBones(content.motion, frame)
                    val authored = content.boneTracks.mapValues { (_, tracks) ->
                        TrackEvaluator.evaluateAll(tracks, frame)
                    }
                    val merged = HashMap<String, Map<String, Float>>()
                    fromClips.forEach { (boneId, values) -> merged[boneId] = values }
                    authored.forEach { (boneId, values) ->
                        merged[boneId] = (merged[boneId].orEmpty() + values)
                    }
                    RigSolver.solve(skeleton, merged)
                }
                output += baseEvaluated(layer, content, worldMatrix, opacity, depth)
                    .copy(skeleton = solved)
            }

            else -> output += baseEvaluated(layer, content, worldMatrix, opacity, depth)
        }
    }

    private fun baseEvaluated(
        layer: Layer,
        content: LayerContent,
        worldMatrix: Matrix3,
        opacity: Float,
        depth: Int,
    ) = EvaluatedLayer(
        layerId = layer.id,
        name = layer.name,
        source = layer,
        content = content,
        worldMatrix = worldMatrix,
        opacity = opacity,
        blendMode = layer.blendMode,
        maskMode = layer.maskMode,
        effects = layer.effects.filter { it.enabled },
        depth = depth,
    )

    private fun animatedChannels(layer: Layer, frame: Frame): Map<String, Float> {
        val authored = TrackEvaluator.evaluateAll(layer.tracks, frame)
        val character = layer.content as? LayerContent.Character ?: return authored
        val fromClips = clipStackEvaluator.evaluate(character.motion, frame)
        if (fromClips.isEmpty()) return authored
        return fromClips + authored
    }

    private fun applyChannels(base: Transform2D, channels: Map<String, Float>): Transform2D {
        if (channels.isEmpty()) return base
        return base.copy(
            position = Vec2(
                channels[PropertyPath.POSITION_X] ?: base.position.x,
                channels[PropertyPath.POSITION_Y] ?: base.position.y,
            ),
            pivot = Vec2(
                channels[PropertyPath.PIVOT_X] ?: base.pivot.x,
                channels[PropertyPath.PIVOT_Y] ?: base.pivot.y,
            ),
            rotationDegrees = channels[PropertyPath.ROTATION] ?: base.rotationDegrees,
            scale = Vec2(
                channels[PropertyPath.SCALE_X] ?: base.scale.x,
                channels[PropertyPath.SCALE_Y] ?: base.scale.y,
            ),
            skew = Vec2(
                channels[PropertyPath.SKEW_X] ?: base.skew.x,
                channels[PropertyPath.SKEW_Y] ?: base.skew.y,
            ),
        )
    }

    /**
     * Multi-plane parallax: a layer with factor < 1 lags behind the camera and
     * therefore reads as distant.
     */
    private fun parallaxMatrixFor(layer: Layer, camera: Camera): Matrix3 {
        val parallax = layer.parallax
        if (!parallax.enabled || parallax.factor == 1f) return Matrix3.IDENTITY
        val offset = camera.position * (1f - parallax.factor)
        return Matrix3.translation(offset)
    }

    private fun evaluateCamera(camera: Camera, frame: Frame): Camera {
        if (camera.tracks.isEmpty) return camera
        val tracks = camera.tracks
        return camera.copy(
            position = Vec2(
                TrackEvaluator.value(tracks, PropertyPath.CAMERA_X, frame, camera.position.x),
                TrackEvaluator.value(tracks, PropertyPath.CAMERA_Y, frame, camera.position.y),
            ),
            zoom = TrackEvaluator.value(tracks, PropertyPath.CAMERA_ZOOM, frame, camera.zoom),
            rotationDegrees = TrackEvaluator.value(
                tracks, PropertyPath.CAMERA_ROTATION, frame, camera.rotationDegrees,
            ),
        )
    }

    private companion object {
        const val CULL_MARGIN = 64f
    }
}

/** True when [predicate] matches this layer or any descendant. */
fun Layer.walkAny(predicate: (Layer) -> Boolean): Boolean {
    if (predicate(this)) return true
    return children.any { it.walkAny(predicate) }
}
