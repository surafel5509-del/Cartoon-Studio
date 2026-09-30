package com.cartoonstudio.feature.animation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.cartoonstudio.core.time.Frame
import com.cartoonstudio.data.assetstore.AnimationLibrary
import com.cartoonstudio.domain.animation.AnimationClip
import com.cartoonstudio.domain.animation.ClipCategory
import com.cartoonstudio.domain.animation.Interpolation
import com.cartoonstudio.domain.animation.PropertyPath
import com.cartoonstudio.domain.animation.Track
import com.cartoonstudio.engine.animation.TrackEvaluator
import com.cartoonstudio.ui.components.OptionRow
import com.cartoonstudio.ui.components.SectionHeader
import com.cartoonstudio.ui.designsystem.spacing

/**
 * The reusable motion library.
 *
 * Any clip can be applied to any character built on the same rig profile,
 * which is what makes "Walk", "Wave" and "Talk" reusable across the whole
 * cast instead of being re-animated per character.
 */
@Composable
fun ClipLibraryPanel(
    onApplyClip: (AnimationClip) -> Unit,
    modifier: Modifier = Modifier,
) {
    var category by remember { mutableStateOf<ClipCategory?>(null) }
    val clips = remember(category) {
        category?.let { AnimationLibrary.inCategory(it) } ?: AnimationLibrary.clips
    }

    Column(modifier = modifier.fillMaxSize()) {
        SectionHeader("Motion Library · ${AnimationLibrary.clips.size} clips")
        OptionRow(
            options = listOf<ClipCategory?>(null) + ClipCategory.entries.toList(),
            selected = category,
            onSelect = { category = it },
            labelOf = { it?.displayName ?: "All" },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            items(clips.size) { index ->
                val clip = clips[index]
                ClipRow(clip = clip, onApply = { onApplyClip(clip) })
            }
        }
    }
}

@Composable
private fun ClipRow(clip: AnimationClip, onApply: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onApply),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.DirectionsRun, contentDescription = null)
            Column(modifier = Modifier.weight(1f).padding(horizontal = MaterialTheme.spacing.medium)) {
                Text(clip.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${clip.category.displayName} · ${clip.lengthInFrames}f" +
                        if (clip.loopable) " · loops" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onApply) {
                Icon(Icons.Filled.Add, contentDescription = "Apply ${clip.name}")
            }
        }
    }
}

/**
 * Curve (graph) editor.
 *
 * Draws the evaluated spline for a property track so timing can be judged
 * visually, with keyframes marked on the curve.
 */
@Composable
fun CurveEditor(
    track: Track?,
    durationFrames: Int,
    currentFrame: Frame,
    modifier: Modifier = Modifier,
) {
    val curveColor = MaterialTheme.colorScheme.secondary
    val keyColor = MaterialTheme.colorScheme.tertiary
    val playheadColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            track?.let { "Curve · ${PropertyPath.displayName(it.property)}" } ?: "Curve",
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = MaterialTheme.spacing.large)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val frames = durationFrames.coerceAtLeast(2)
            val stepX = size.width / (frames - 1)

            for (line in 0..4) {
                val y = size.height * line / 4f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            if (track != null && track.keys.isNotEmpty()) {
                val bounds = track.valueBounds()
                val span = (bounds.endInclusive - bounds.start).takeIf { it > 0.0001f } ?: 1f
                fun toY(value: Float) = size.height - ((value - bounds.start) / span) * size.height

                var previous: Offset? = null
                for (index in 0 until frames) {
                    val value = TrackEvaluator.evaluate(track, Frame(index))
                    val point = Offset(index * stepX, toY(value))
                    previous?.let { drawLine(curveColor, it, point, strokeWidth = 2f) }
                    previous = point
                }
                track.keys.forEach { key ->
                    drawCircle(
                        color = keyColor,
                        radius = 5f,
                        center = Offset(key.frame.index * stepX, toY(key.value)),
                    )
                }
            }

            val playheadX = currentFrame.index * stepX
            drawLine(playheadColor, Offset(playheadX, 0f), Offset(playheadX, size.height), strokeWidth = 2f)
        }
    }
}

/** Easing presets offered when a keyframe is selected. */
@Composable
fun InterpolationPicker(
    selected: Interpolation,
    onSelect: (Interpolation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader("Easing")
        OptionRow(
            options = Interpolation.entries.toList(),
            selected = selected,
            onSelect = onSelect,
            labelOf = { it.name },
        )
    }
}
