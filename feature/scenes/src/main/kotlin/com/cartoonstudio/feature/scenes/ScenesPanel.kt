package com.cartoonstudio.feature.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cartoonstudio.core.time.FrameRate
import com.cartoonstudio.core.time.Timecode
import com.cartoonstudio.domain.camera.Camera
import com.cartoonstudio.domain.camera.CameraPreset
import com.cartoonstudio.domain.model.Scene
import com.cartoonstudio.ui.components.SectionHeader
import com.cartoonstudio.ui.components.SliderRow
import com.cartoonstudio.ui.designsystem.spacing

/** Shot-list operations. */
data class SceneCallbacks(
    val onSelect: (String) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onDuplicate: (String) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onMoveUp: (String) -> Unit = {},
    val onMoveDown: (String) -> Unit = {},
    val onUpdate: (Scene) -> Unit = {},
)

/**
 * Shot list for the project.
 *
 * A "scene" here is a shot: an independent timeline with its own camera,
 * duration and layer stack, exported together as one film.
 */
@Composable
fun ScenesPanel(
    scenes: List<Scene>,
    activeSceneId: String?,
    frameRate: FrameRate,
    callbacks: SceneCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader("Shots · ${scenes.size}", modifier = Modifier.weight(1f))
            IconButton(onClick = callbacks.onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add shot")
            }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(scenes.size) { index ->
                val scene = scenes[index]
                SceneRow(
                    scene = scene,
                    index = index,
                    selected = scene.id == activeSceneId,
                    frameRate = frameRate,
                    callbacks = callbacks,
                )
            }
        }
    }
}

@Composable
private fun SceneRow(
    scene: Scene,
    index: Int,
    selected: Boolean,
    frameRate: FrameRate,
    callbacks: SceneCallbacks,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
            )
            .clickable { callbacks.onSelect(scene.id) }
            .padding(horizontal = MaterialTheme.spacing.small, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Movie,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = MaterialTheme.spacing.small)) {
            Text(
                "${index + 1}. ${scene.name}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${scene.durationFrames}f · ${Timecode.formatShort(scene.lastFrame, frameRate)} · " +
                    "${scene.allLayers().size} layers",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { callbacks.onMoveUp(scene.id) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.ArrowUpward, "Move up", modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = { callbacks.onMoveDown(scene.id) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.ArrowDownward, "Move down", modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = { callbacks.onDuplicate(scene.id) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.ContentCopy, "Duplicate", modifier = Modifier.size(15.dp))
        }
        IconButton(onClick = { callbacks.onDelete(scene.id) }, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Filled.Delete, "Delete",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Editable properties of the current shot. */
@Composable
fun ScenePropertiesPanel(
    scene: Scene,
    onUpdate: (Scene) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader("Shot Properties")
        OutlinedTextField(
            value = scene.name,
            onValueChange = { onUpdate(scene.copy(name = it)) },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.large),
        )
        SliderRow(
            label = "Duration",
            value = scene.durationFrames.toFloat(),
            onValueChange = { onUpdate(scene.copy(durationFrames = it.toInt().coerceAtLeast(1))) },
            valueRange = 1f..600f,
            valueFormatter = { "${it.toInt()} frames" },
        )
        OutlinedTextField(
            value = scene.notes,
            onValueChange = { onUpdate(scene.copy(notes = it)) },
            label = { Text("Director notes") },
            minLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
        )
    }
}

/** Camera framing controls plus the cinematic preset shelf. */
@Composable
fun CameraPanel(
    camera: Camera,
    onUpdate: (Camera) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader("Camera")
        SliderRow(
            label = "Zoom",
            value = camera.zoom,
            onValueChange = { onUpdate(camera.copy(zoom = it)) },
            valueRange = 0.2f..5f,
            valueFormatter = { "%.2f×".format(it) },
        )
        SliderRow(
            label = "Rotation",
            value = camera.rotationDegrees,
            onValueChange = { onUpdate(camera.copy(rotationDegrees = it)) },
            valueRange = -45f..45f,
            valueFormatter = { "${it.toInt()}°" },
        )
        SliderRow(
            label = "Pan X",
            value = camera.position.x,
            onValueChange = {
                onUpdate(camera.copy(position = com.cartoonstudio.core.math.Vec2(it, camera.position.y)))
            },
            valueRange = -1920f..1920f,
            valueFormatter = { "${it.toInt()} px" },
        )
        SliderRow(
            label = "Pan Y",
            value = camera.position.y,
            onValueChange = {
                onUpdate(camera.copy(position = com.cartoonstudio.core.math.Vec2(camera.position.x, it)))
            },
            valueRange = -1080f..1080f,
            valueFormatter = { "${it.toInt()} px" },
        )
        SectionHeader("Shot Presets")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny),
        ) {
            CameraPreset.builtIn.forEach { preset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onUpdate(
                                camera.copy(
                                    zoom = preset.zoom,
                                    rotationDegrees = preset.rotationDegrees,
                                    position = preset.offset,
                                ),
                            )
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(preset.name, style = MaterialTheme.typography.bodySmall)
                        Text(
                            preset.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("%.1f×".format(preset.zoom), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
