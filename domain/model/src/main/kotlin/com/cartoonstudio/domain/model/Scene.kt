package com.cartoonstudio.domain.model

import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.core.time.FrameRange
import com.cartoonstudio.core.time.Marker
import com.cartoonstudio.domain.audio.AudioTrack
import com.cartoonstudio.domain.audio.LipSyncTrack
import com.cartoonstudio.domain.camera.Camera
import com.cartoonstudio.domain.drawing.Rgba
import kotlinx.serialization.Serializable

/**
 * A shot / scene: the unit of production that gets storyboarded, animated and
 * rendered.
 */
@Serializable
data class Scene(
    val id: String,
    val name: String,
    val layers: List<Layer> = emptyList(),
    val camera: Camera = Camera(id = "camera_main"),
    val durationFrames: Int = 96,
    val backgroundColor: Rgba = Rgba.Paper,
    val markers: List<Marker> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val lipSyncTracks: List<LipSyncTrack> = emptyList(),
    /** Storyboard note / dialogue for this shot. */
    val notes: String = "",
    val shotNumber: Int = 1,
) {
    val range: FrameRange get() = FrameRange.ofLength(Frame.ZERO, durationFrames.coerceAtLeast(1))

    val lastFrame: Frame get() = Frame((durationFrames - 1).coerceAtLeast(0))

    /** Flattened depth-first list of every layer, including group children. */
    fun allLayers(): List<Layer> = buildList {
        layers.forEach { layer -> layer.walk { add(it) } }
    }

    fun layer(layerId: String): Layer? {
        layers.forEach { layer -> layer.findById(layerId)?.let { return it } }
        return null
    }

    val hasSolo: Boolean get() = allLayers().any { it.solo }

    /** Layers that should be drawn at [frame], honouring solo/visibility/spans. */
    fun renderableLayers(frame: Frame): List<Layer> {
        val soloActive = hasSolo
        return layers.filter { it.visible && it.existsAt(frame) && (!soloActive || it.solo || it.children.any { c -> c.solo }) }
    }

    fun withLayer(layer: Layer): Scene = copy(layers = replaceIn(layers, layer))

    fun withLayerAdded(layer: Layer, atIndex: Int = 0): Scene {
        val list = layers.toMutableList()
        list.add(atIndex.coerceIn(0, list.size), layer)
        return copy(layers = list)
    }

    fun withoutLayer(layerId: String): Scene = copy(layers = removeIn(layers, layerId))

    fun withLayerMoved(fromIndex: Int, toIndex: Int): Scene {
        if (fromIndex !in layers.indices) return this
        val list = layers.toMutableList()
        val item = list.removeAt(fromIndex)
        list.add(toIndex.coerceIn(0, list.size), item)
        return copy(layers = list)
    }

    fun updateLayer(layerId: String, transform: (Layer) -> Layer): Scene =
        copy(layers = mapIn(layers, layerId, transform))

    val audioLengthFrames: Int get() = audioTracks.maxOfOrNull { it.lengthInFrames } ?: 0

    fun markersAt(frame: Frame): List<Marker> = markers.filter { it.frame == frame }

    companion object {
        private fun replaceIn(list: List<Layer>, layer: Layer): List<Layer> = list.map {
            when {
                it.id == layer.id -> layer
                it.children.isNotEmpty() -> it.withChildren(replaceIn(it.children, layer))
                else -> it
            }
        }

        private fun removeIn(list: List<Layer>, layerId: String): List<Layer> = list
            .filterNot { it.id == layerId }
            .map { if (it.children.isNotEmpty()) it.withChildren(removeIn(it.children, layerId)) else it }

        private fun mapIn(list: List<Layer>, layerId: String, transform: (Layer) -> Layer): List<Layer> =
            list.map {
                when {
                    it.id == layerId -> transform(it)
                    it.children.isNotEmpty() -> it.withChildren(mapIn(it.children, layerId, transform))
                    else -> it
                }
            }
    }
}
