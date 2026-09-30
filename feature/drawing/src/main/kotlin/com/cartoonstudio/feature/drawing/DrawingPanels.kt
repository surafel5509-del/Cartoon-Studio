package com.cartoonstudio.feature.drawing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cartoonstudio.domain.drawing.Brush
import com.cartoonstudio.domain.drawing.BrushDynamics
import com.cartoonstudio.domain.drawing.Palette
import com.cartoonstudio.domain.drawing.Rgba
import com.cartoonstudio.ui.components.ColorSwatch
import com.cartoonstudio.ui.components.OptionRow
import com.cartoonstudio.ui.components.SectionHeader
import com.cartoonstudio.ui.components.SliderRow
import com.cartoonstudio.ui.components.ToolButton
import com.cartoonstudio.ui.designsystem.spacing

/** Vertical tool rail shown beside the canvas. */
@Composable
fun ToolRail(
    selected: DrawingTool,
    onSelect: (DrawingTool) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(56.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = MaterialTheme.spacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tiny),
    ) {
        DrawingTool.drawingTools.forEach { tool ->
            ToolButton(tool.icon, tool.displayName, tool == selected, { onSelect(tool) })
        }
        com.cartoonstudio.ui.components.PanelDivider(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 12.dp),
            vertical = false,
        )
        DrawingTool.selectionTools.forEach { tool ->
            ToolButton(tool.icon, tool.displayName, tool == selected, { onSelect(tool) })
        }
        com.cartoonstudio.ui.components.PanelDivider(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 12.dp),
            vertical = false,
        )
        DrawingTool.utilityTools.forEach { tool ->
            ToolButton(tool.icon, tool.displayName, tool == selected, { onSelect(tool) })
        }
        com.cartoonstudio.ui.components.PanelDivider(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 12.dp),
            vertical = false,
        )
        ToolButton(Icons.Filled.Undo, "Undo", false, onUndo, enabled = canUndo)
        ToolButton(Icons.Filled.Redo, "Redo", false, onRedo, enabled = canRedo)
    }
}

/** Brush presets, size, opacity and dynamics. */
@Composable
fun BrushPanel(
    brush: Brush,
    customBrushes: List<Brush>,
    onBrushSelected: (Brush) -> Unit,
    onBrushChanged: (Brush) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = Brush.builtIn + customBrushes
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader("Brush")
        OptionRow(
            options = presets,
            selected = presets.firstOrNull { it.id == brush.id },
            onSelect = onBrushSelected,
            labelOf = { it.name },
        )
        SliderRow(
            label = "Size",
            value = brush.size,
            onValueChange = { onBrushChanged(brush.copy(size = it)) },
            valueRange = 1f..160f,
            valueFormatter = { "${it.toInt()} px" },
        )
        SliderRow(
            label = "Opacity",
            value = brush.opacity,
            onValueChange = { onBrushChanged(brush.copy(opacity = it)) },
            valueFormatter = { "${(it * 100).toInt()}%" },
        )
        SliderRow(
            label = "Hardness",
            value = brush.hardness,
            onValueChange = { onBrushChanged(brush.copy(hardness = it)) },
            valueFormatter = { "${(it * 100).toInt()}%" },
        )
        SliderRow(
            label = "Smoothing",
            value = brush.stabilization,
            onValueChange = { onBrushChanged(brush.copy(stabilization = it)) },
            valueRange = 0f..0.95f,
            valueFormatter = { "${(it * 100).toInt()}%" },
        )
        SectionHeader("Pressure")
        OptionRow(
            options = BrushDynamics.entries.toList(),
            selected = brush.dynamics,
            onSelect = { onBrushChanged(brush.copy(dynamics = it)) },
            labelOf = { it.name },
        )
    }
}

/** Palette picker with recent colours. */
@Composable
fun ColorPanel(
    color: Rgba,
    palettes: List<Palette>,
    recentColors: List<Rgba>,
    onColorSelected: (Rgba) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader("Color", trailing = {
            Text(color.toHex(), style = MaterialTheme.typography.labelSmall)
        })
        if (recentColors.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.large),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                recentColors.take(8).forEach { recent ->
                    ColorSwatch(
                        color = Color(recent.argb),
                        selected = recent.argb == color.argb,
                        onClick = { onColorSelected(recent) },
                        size = 26.dp,
                    )
                }
            }
        }
        palettes.forEach { palette ->
            SectionHeader(palette.name)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(44.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightForRows(palette.colors.size),
                contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.large),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                userScrollEnabled = false,
            ) {
                items(palette.colors) { swatch ->
                    ColorSwatch(
                        color = Color(swatch.argb),
                        selected = swatch.argb == color.argb,
                        onClick = { onColorSelected(swatch) },
                    )
                }
            }
        }
    }
}

private fun Modifier.heightForRows(itemCount: Int): Modifier {
    val rows = ((itemCount + 5) / 6).coerceAtLeast(1)
    return this.height((rows * 44 + (rows - 1) * 8 + 8).dp)
}
