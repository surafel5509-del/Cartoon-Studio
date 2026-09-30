package com.cartoonstudio.engine.scene

import com.cartoonstudio.core.math.Matrix3
import com.cartoonstudio.core.math.Rect2
import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.domain.camera.Camera
import com.cartoonstudio.domain.drawing.Cel
import com.cartoonstudio.domain.model.BlendMode
import com.cartoonstudio.domain.model.Effect
import com.cartoonstudio.domain.model.Layer
import com.cartoonstudio.domain.model.LayerContent
import com.cartoonstudio.domain.model.MaskMode
import com.cartoonstudio.engine.rigging.SolvedSkeleton

/**
 * One layer resolved for a single frame.
 *
 * Everything the renderer needs is already computed here: the world matrix,
 * the effective opacity, which cel is exposed, and the solved rig. The
 * renderer performs no document lookups, which is what keeps preview and
 * export identical.
 */
data class EvaluatedLayer(
    val layerId: String,
    val name: String,
    val source: Layer,
    val content: LayerContent,
    val worldMatrix: Matrix3,
    val opacity: Float,
    val blendMode: BlendMode,
    val maskMode: MaskMode,
    val effects: List<Effect>,
    val cel: Cel? = null,
    val skeleton: SolvedSkeleton = SolvedSkeleton.Empty,
    val depth: Int = 0,
) {
    val isVisible: Boolean get() = opacity > 0.001f
}

/**
 * A scene resolved for a single frame, in back-to-front draw order.
 */
data class EvaluatedScene(
    val sceneId: String,
    val frame: Frame,
    val camera: Camera,
    val viewMatrix: Matrix3,
    val layers: List<EvaluatedLayer>,
    val backgroundArgb: Int,
    val contentBounds: Rect2,
) {
    val layerCount: Int get() = layers.size

    fun layer(layerId: String): EvaluatedLayer? = layers.firstOrNull { it.layerId == layerId }

    companion object {
        fun empty(sceneId: String, frame: Frame) = EvaluatedScene(
            sceneId = sceneId,
            frame = frame,
            camera = Camera(id = "camera_none"),
            viewMatrix = Matrix3.IDENTITY,
            layers = emptyList(),
            backgroundArgb = 0xFFFFFFFF.toInt(),
            contentBounds = Rect2.EMPTY,
        )
    }
}
