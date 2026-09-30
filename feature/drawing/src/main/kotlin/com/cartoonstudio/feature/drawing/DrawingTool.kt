package com.cartoonstudio.feature.drawing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

/** Tools available on the canvas tool rail. */
enum class DrawingTool(val displayName: String, val icon: ImageVector) {
    Brush("Brush", Icons.Filled.Brush),
    Eraser("Eraser", Icons.Filled.HorizontalRule),
    Line("Line", Icons.Filled.Timeline),
    Rectangle("Rectangle", Icons.Filled.Rectangle),
    Ellipse("Ellipse", Icons.Filled.Circle),
    Lasso("Lasso Select", Icons.Filled.Gesture),
    Select("Select", Icons.Filled.CropFree),
    Transform("Transform", Icons.Filled.OpenWith),
    Eyedropper("Pick Color", Icons.Filled.Colorize),
    Pan("Pan & Zoom", Icons.Filled.PanTool);

    val isFreehand: Boolean get() = this == Brush || this == Eraser
    val isShape: Boolean get() = this == Line || this == Rectangle || this == Ellipse
    val drawsStrokes: Boolean get() = isFreehand || isShape

    companion object {
        val drawingTools = listOf(Brush, Eraser, Line, Rectangle, Ellipse)
        val selectionTools = listOf(Lasso, Select, Transform)
        val utilityTools = listOf(Eyedropper, Pan)
    }
}

/** Live canvas navigation state (session state — never persisted). */
data class CanvasViewState(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    fun zoomedBy(factor: Float) = copy(zoom = (zoom * factor).coerceIn(0.1f, 16f))
    fun pannedBy(dx: Float, dy: Float) = copy(panX = panX + dx, panY = panY + dy)

    companion object {
        val Fit = CanvasViewState()
    }
}
