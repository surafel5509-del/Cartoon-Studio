package com.cartoonstudio.feature.editor

import com.cartoonstudio.core.common.Ids
import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.core.undo.Command
import com.cartoonstudio.core.undo.SnapshotCommand
import com.cartoonstudio.domain.animation.AnimationClip
import com.cartoonstudio.domain.animation.Interpolation
import com.cartoonstudio.domain.animation.PropertyPath
import com.cartoonstudio.domain.drawing.Cel
import com.cartoonstudio.domain.drawing.Stroke
import com.cartoonstudio.domain.model.Layer
import com.cartoonstudio.domain.model.LayerContent
import com.cartoonstudio.domain.model.Project
import com.cartoonstudio.domain.model.Scene
import com.cartoonstudio.data.assetstore.AssetInstancer

/**
 * Every document mutation as a pure command.
 *
 * Commands are built from the current document and describe the whole
 * transition, so undo/redo is exact and the autosave layer can snapshot at any
 * point without coordinating with the UI.
 */
object EditorCommands {

    /** Wraps a pure transform of the document into an undoable command. */
    fun edit(
        label: String,
        project: Project,
        mergeKey: String? = null,
        transform: (Project) -> Project,
    ): Command<Project> = SnapshotCommand(label, project, transform(project), mergeKey)

    /** Edits the active scene, leaving the rest of the project untouched. */
    fun editScene(
        label: String,
        project: Project,
        mergeKey: String? = null,
        transform: (Scene) -> Scene,
    ): Command<Project> = edit(label, project, mergeKey) { it.updateActiveScene(transform) }

    // ---------------------------------------------------------------- drawing

    /**
     * Adds a stroke to the cel exposed at [frame], creating the cel when the
     * layer has nothing on that frame yet.
     */
    fun addStroke(project: Project, layerId: String, frame: Frame, stroke: Stroke): Command<Project> =
        editScene("Draw", project) { scene ->
            scene.updateLayer(layerId) { layer ->
                val drawing = layer.content as? LayerContent.Drawing ?: return@updateLayer layer
                val existing = drawing.cels[frame.index]
                    ?: drawing.celAt(frame)?.copy(id = Ids.next("cel"))
                    ?: Cel(id = Ids.next("cel"))
                layer.copy(content = drawing.withCel(frame, existing.withStroke(stroke)))
            }
        }

    fun eraseStrokes(
        project: Project,
        layerId: String,
        frame: Frame,
        strokeIds: Set<String>,
    ): Command<Project> = editScene("Erase", project) { scene ->
        scene.updateLayer(layerId) { layer ->
            val drawing = layer.content as? LayerContent.Drawing ?: return@updateLayer layer
            val cel = drawing.celAt(frame) ?: return@updateLayer layer
            layer.copy(
                content = drawing.withCel(
                    frame,
                    cel.copy(strokes = cel.strokes.filterNot { it.id in strokeIds }),
                ),
            )
        }
    }

    /** Creates a blank drawing on [frame] — the "new key drawing" action. */
    fun addBlankCel(project: Project, layerId: String, frame: Frame): Command<Project> =
        editScene("New Drawing", project) { scene ->
            scene.updateLayer(layerId) { layer ->
                val drawing = layer.content as? LayerContent.Drawing ?: return@updateLayer layer
                layer.copy(content = drawing.withCel(frame, Cel(id = Ids.next("cel"))))
            }
        }

    /** Copies the visible cel onto [frame] so it can be modified independently. */
    fun duplicateCel(project: Project, layerId: String, frame: Frame): Command<Project> =
        editScene("Duplicate Drawing", project) { scene ->
            scene.updateLayer(layerId) { layer ->
                val drawing = layer.content as? LayerContent.Drawing ?: return@updateLayer layer
                val source = drawing.celAt(frame) ?: return@updateLayer layer
                layer.copy(content = drawing.withCel(frame, source.copy(id = Ids.next("cel"))))
            }
        }

    fun clearCel(project: Project, layerId: String, frame: Frame): Command<Project> =
        editScene("Clear Drawing", project) { scene ->
            scene.updateLayer(layerId) { layer ->
                val drawing = layer.content as? LayerContent.Drawing ?: return@updateLayer layer
                layer.copy(content = drawing.withoutCel(frame))
            }
        }

    // ----------------------------------------------------------------- layers

    fun addLayer(project: Project, layer: Layer): Command<Project> =
        editScene("Add ${layer.kind.displayName}", project) { it.withLayerAdded(layer) }

    fun removeLayer(project: Project, layerId: String): Command<Project> =
        editScene("Delete Layer", project) { it.withoutLayer(layerId) }

    fun duplicateLayer(project: Project, layerId: String): Command<Project> =
        editScene("Duplicate Layer", project) { scene ->
            val source = scene.layer(layerId) ?: return@editScene scene
            val index = scene.layers.indexOfFirst { it.id == layerId }.coerceAtLeast(0)
            scene.withLayerAdded(reid(source).copy(name = "${source.name} copy"), index)
        }

    fun updateLayer(
        project: Project,
        layer: Layer,
        label: String = "Edit Layer",
        mergeKey: String? = null,
    ): Command<Project> = editScene(label, project, mergeKey) { it.withLayer(layer) }

    fun moveLayer(project: Project, layerId: String, delta: Int): Command<Project> =
        editScene("Reorder Layer", project) { scene ->
            val index = scene.layers.indexOfFirst { it.id == layerId }
            if (index < 0) return@editScene scene
            scene.withLayerMoved(index, (index + delta).coerceIn(0, scene.layers.lastIndex))
        }

    private fun reid(layer: Layer): Layer {
        val copy = layer.copy(id = Ids.next("layer"))
        val group = copy.content as? LayerContent.Group ?: return copy
        return copy.copy(content = group.copy(children = group.children.map { reid(it) }))
    }

    // -------------------------------------------------------------- animation

    /** Toggles a transform keyframe on the whole transform channel set. */
    fun toggleKeyframe(project: Project, layerId: String, frame: Frame): Command<Project> =
        editScene("Toggle Keyframe", project) { scene ->
            scene.updateLayer(layerId) { layer ->
                if (layer.tracks.hasKeyAt(frame)) {
                    var tracks = layer.tracks
                    PropertyPath.transformChannels.forEach { tracks = tracks.withoutKey(it, frame) }
                    layer.copy(tracks = tracks)
                } else {
                    var tracks = layer.tracks
                    val transform = layer.transform
                    val values = mapOf(
                        PropertyPath.POSITION_X to transform.position.x,
                        PropertyPath.POSITION_Y to transform.position.y,
                        PropertyPath.ROTATION to transform.rotationDegrees,
                        PropertyPath.SCALE_X to transform.scale.x,
                        PropertyPath.SCALE_Y to transform.scale.y,
                        PropertyPath.OPACITY to layer.opacity,
                    )
                    values.forEach { (property, value) ->
                        tracks = tracks.withKey(property, frame, value, Interpolation.EaseInOut)
                    }
                    layer.copy(tracks = tracks)
                }
            }
        }

    fun moveKeyframe(project: Project, layerId: String, from: Frame, to: Frame): Command<Project> =
        editScene("Move Keyframe", project, mergeKey = "movekey:$layerId") { scene ->
            scene.updateLayer(layerId) { layer ->
                var tracks = layer.tracks
                layer.tracks.tracks.values.forEach { track ->
                    if (track.hasKeyAt(from)) tracks = tracks.withTrack(track.withKeyMoved(from, to))
                }
                layer.copy(tracks = tracks)
            }
        }

    fun setInterpolation(
        project: Project,
        layerId: String,
        property: String,
        interpolation: Interpolation,
    ): Command<Project> = editScene("Set Easing", project) { scene ->
        scene.updateLayer(layerId) { layer ->
            val track = layer.tracks.track(property) ?: return@updateLayer layer
            layer.copy(tracks = layer.tracks.withTrack(track.withInterpolation(interpolation)))
        }
    }

    /** Applies a reusable motion clip to a rigged character layer. */
    fun applyClip(
        project: Project,
        layerId: String,
        clip: AnimationClip,
        startFrame: Frame,
    ): Command<Project> = editScene("Apply ${clip.name}", project) { scene ->
        val layer = scene.layer(layerId) ?: return@editScene scene
        scene.withLayer(AssetInstancer.applyClip(layer, clip, startFrame))
    }

    // ------------------------------------------------------------------ scene

    fun updateScene(project: Project, scene: Scene, label: String = "Edit Shot"): Command<Project> =
        edit(label, project) { it.withScene(scene) }

    fun addScene(project: Project, scene: Scene): Command<Project> =
        edit("Add Shot", project) { it.withSceneAdded(scene).copy(activeSceneId = scene.id) }

    fun removeScene(project: Project, sceneId: String): Command<Project> =
        edit("Delete Shot", project) { it.withoutScene(sceneId) }

    fun moveScene(project: Project, sceneId: String, delta: Int): Command<Project> =
        edit("Reorder Shots", project) { current ->
            val index = current.scenes.indexOfFirst { it.id == sceneId }
            if (index < 0) return@edit current
            current.withSceneMoved(index, (index + delta).coerceIn(0, current.scenes.lastIndex))
        }
}
