package com.cartoonstudio.feature.compositing

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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cartoonstudio.core.common.Ids
import com.cartoonstudio.domain.model.BlendMode
import com.cartoonstudio.domain.model.Effect
import com.cartoonstudio.domain.model.Layer
import com.cartoonstudio.domain.model.MaskMode
import com.cartoonstudio.ui.components.OptionRow
import com.cartoonstudio.ui.components.SectionHeader
import com.cartoonstudio.ui.components.SliderRow
import com.cartoonstudio.ui.designsystem.spacing

/** Actions available on a layer row. */
data class LayerCallbacks(
    val onSelect: (String) -> Unit = {},
    val onToggleVisible: (String) -> Unit = {},
    val onToggleLocked: (String) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onDuplicate: (String) -> Unit = {},
    val onMoveUp: (String) -> Unit = {},
    val onMoveDown: (String) -> Unit = {},
    val onUpdate: (Layer) -> Unit = {},
)

/** Photoshop-style layer stack: top of the list is the front-most layer. */
@Composable
fun LayersPanel(
    layers: List<Layer>,
    selectedLayerId: String?,
    callbacks: LayerCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SectionHeader("Layers · ${layers.size}")
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(layers.size) { index ->
                val layer = layers[index]
                LayerRow(
                    layer = layer,
                    selected = layer.id == selectedLayerId,
                    callbacks = callbacks,
                )
            }
        }
    }
}

@Composable
private fun LayerRow(layer: Layer, selected: Boolean, callbacks: LayerCallbacks) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
            )
            .clickable { callbacks.onSelect(layer.id) }
            .padding(horizontal = MaterialTheme.spacing.small, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { callbacks.onToggleVisible(layer.id) }, modifier = Modifier.size(32.dp)) {
            Icon(
                if (layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = "Toggle visibility",
                modifier = Modifier.size(18.dp),
                tint = if (layer.visible) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outline,
            )
        }
        IconButton(onClick = { callbacks.onToggleLocked(layer.id) }, modifier = Modifier.size(32.dp)) {
            Icon(
                if (layer.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = "Toggle lock",
                modifier = Modifier.size(16.dp),
                tint = if (layer.locked) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.outline,
            )
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                layer.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(layer.kind.displayName)
                    if (layer.isAnimated) append(" · animated")
                    if (layer.blendMode != BlendMode.Normal) append(" · ${layer.blendMode.displayName}")
                    if (layer.effects.isNotEmpty()) append(" · ${layer.effects.size} fx")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { callbacks.onMoveUp(layer.id) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.ArrowUpward, "Move up", modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = { callbacks.onMoveDown(layer.id) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.ArrowDownward, "Move down", modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = { callbacks.onDuplicate(layer.id) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.ContentCopy, "Duplicate", modifier = Modifier.size(15.dp))
        }
        IconButton(onClick = { callbacks.onDelete(layer.id) }, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Filled.Delete, "Delete",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Opacity, blending, masking and the non-destructive effect stack. */
@Composable
fun LayerPropertiesPanel(
    layer: Layer?,
    onUpdate: (Layer) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (layer == null) {
        com.cartoonstudio.ui.components.EmptyState(
            icon = Icons.Filled.AutoAwesome,
            title = "No layer selected",
            message = "Pick a layer to adjust opacity, blending and effects.",
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(layer.name)
        SliderRow(
            label = "Opacity",
            value = layer.opacity,
            onValueChange = { onUpdate(layer.copy(opacity = it)) },
            valueFormatter = { "${(it * 100).toInt()}%" },
        )
        SectionHeader("Blend Mode")
        OptionRow(
            options = BlendMode.entries.toList(),
            selected = layer.blendMode,
            onSelect = { onUpdate(layer.copy(blendMode = it)) },
            labelOf = { it.displayName },
        )
        SectionHeader("Mask")
        OptionRow(
            options = MaskMode.entries.toList(),
            selected = layer.maskMode,
            onSelect = { onUpdate(layer.copy(maskMode = it)) },
            labelOf = { it.name },
        )
        SectionHeader("Transform")
        SliderRow(
            label = "Rotation",
            value = layer.transform.rotationDegrees,
            onValueChange = { onUpdate(layer.copy(transform = layer.transform.copy(rotationDegrees = it))) },
            valueRange = -180f..180f,
            valueFormatter = { "${it.toInt()}°" },
        )
        SliderRow(
            label = "Scale",
            value = layer.transform.scale.x,
            onValueChange = {
                onUpdate(
                    layer.copy(
                        transform = layer.transform.copy(
                            scale = com.cartoonstudio.core.math.Vec2(it, it),
                        ),
                    ),
                )
            },
            valueRange = 0.05f..4f,
            valueFormatter = { "%.2f×".format(it) },
        )
        EffectsSection(layer, onUpdate)
    }
}

@Composable
private fun EffectsSection(layer: Layer, onUpdate: (Layer) -> Unit) {
    SectionHeader("Effects · ${layer.effects.size}")
    OptionRow(
        options = EffectKind.entries.toList(),
        selected = null,
        onSelect = { kind ->
            onUpdate(layer.copy(effects = layer.effects + kind.create()))
        },
        labelOf = { "+ ${it.displayName}" },
    )
    Column(modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.small)) {
        layer.effects.forEach { effect ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.large, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(effect.displayName, style = MaterialTheme.typography.bodySmall)
                IconButton(
                    onClick = {
                        onUpdate(layer.copy(effects = layer.effects.filterNot { it.id == effect.id }))
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.Delete, "Remove effect",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            when (effect) {
                is Effect.GaussianBlur -> SliderRow(
                    "Radius", effect.radius,
                    { value ->
                        onUpdate(layer.copy(effects = layer.effects.replace(effect.copy(radius = value))))
                    },
                    valueRange = 0f..60f,
                    valueFormatter = { "${it.toInt()} px" },
                )
                is Effect.Glow -> SliderRow(
                    "Intensity", effect.intensity,
                    { value ->
                        onUpdate(layer.copy(effects = layer.effects.replace(effect.copy(intensity = value))))
                    },
                )
                is Effect.ColorAdjust -> {
                    SliderRow(
                        "Brightness", effect.brightness,
                        { value ->
                            onUpdate(
                                layer.copy(effects = layer.effects.replace(effect.copy(brightness = value))),
                            )
                        },
                        valueRange = -1f..1f,
                    )
                    SliderRow(
                        "Saturation", effect.saturation,
                        { value ->
                            onUpdate(
                                layer.copy(effects = layer.effects.replace(effect.copy(saturation = value))),
                            )
                        },
                        valueRange = 0f..3f,
                    )
                }
                else -> Unit
            }
        }
    }
}

private fun List<Effect>.replace(effect: Effect): List<Effect> =
    map { if (it.id == effect.id) effect else it }

/** Effect types offered in the add-effect row. */
enum class EffectKind(val displayName: String) {
    Blur("Blur"),
    Glow("Glow"),
    Shadow("Shadow"),
    Outline("Outline"),
    Color("Color"),
    Tint("Tint");

    fun create(): Effect {
        val id = Ids.next("fx")
        return when (this) {
            Blur -> Effect.GaussianBlur(id)
            Glow -> Effect.Glow(id)
            Shadow -> Effect.DropShadow(id)
            Outline -> Effect.Outline(id)
            Color -> Effect.ColorAdjust(id)
            Tint -> Effect.Tint(id)
        }
    }
}
