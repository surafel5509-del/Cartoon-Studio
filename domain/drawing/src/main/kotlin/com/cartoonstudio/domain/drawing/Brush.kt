package com.cartoonstudio.domain.drawing

import kotlinx.serialization.Serializable

/** How a brush reacts to stylus pressure and speed. */
@Serializable
enum class BrushDynamics { None, Pressure, Velocity, PressureAndVelocity }

/** The shape a stroke is stamped or tessellated with. */
@Serializable
enum class BrushShape { Round, Flat, Chisel, Calligraphy, Textured, Airbrush }

/** Compositing behaviour of a drawing operation. */
@Serializable
enum class DrawingOperation { Paint, Erase, Smudge, Blur, Fill }

/**
 * A reusable brush preset.
 *
 * Brushes are pure data so they can be persisted inside a project, shared in a
 * content pack, or serialised into an undo command.
 */
@Serializable
data class Brush(
    val id: String,
    val name: String,
    val shape: BrushShape = BrushShape.Round,
    val operation: DrawingOperation = DrawingOperation.Paint,
    /** Diameter in canvas units at full pressure. */
    val size: Float = 12f,
    /** 0 = fully soft edge, 1 = hard edge. */
    val hardness: Float = 0.85f,
    val opacity: Float = 1f,
    /** Paint accumulation per stamp, used by airbrush style tools. */
    val flow: Float = 1f,
    /** Distance between stamps as a fraction of [size]. */
    val spacing: Float = 0.08f,
    val dynamics: BrushDynamics = BrushDynamics.Pressure,
    /** Minimum width as a fraction of [size] when pressure is at its lowest. */
    val minimumWidthFraction: Float = 0.25f,
    /** Input smoothing strength, 0 = raw input, 1 = heavily stabilised. */
    val stabilization: Float = 0.35f,
    /** Random size/angle variation for textured, organic media. */
    val jitter: Float = 0f,
    val angleDegrees: Float = 0f,
    val builtIn: Boolean = true,
) {
    fun widthFor(pressure: Float): Float = when (dynamics) {
        BrushDynamics.None -> size
        else -> size * (minimumWidthFraction + (1f - minimumWidthFraction) * pressure.coerceIn(0f, 1f))
    }

    companion object {
        val Ink = Brush("brush_ink", "Ink Pen", BrushShape.Round, size = 6f, hardness = 1f, stabilization = 0.5f)
        val Pencil = Brush(
            "brush_pencil", "Pencil", BrushShape.Textured, size = 4f, hardness = 0.6f,
            opacity = 0.9f, jitter = 0.15f, stabilization = 0.2f,
        )
        val Marker = Brush("brush_marker", "Marker", BrushShape.Chisel, size = 22f, hardness = 0.95f, opacity = 0.85f)
        val Paint = Brush("brush_paint", "Paint Brush", BrushShape.Flat, size = 32f, hardness = 0.5f, flow = 0.8f)
        val Airbrush = Brush(
            "brush_airbrush", "Airbrush", BrushShape.Airbrush, size = 48f, hardness = 0.05f,
            opacity = 0.35f, flow = 0.25f, spacing = 0.04f,
        )
        val Calligraphy = Brush(
            "brush_calligraphy", "Calligraphy", BrushShape.Calligraphy, size = 18f,
            angleDegrees = 45f, hardness = 1f,
        )
        val Eraser = Brush(
            "brush_eraser", "Eraser", BrushShape.Round, operation = DrawingOperation.Erase,
            size = 24f, hardness = 0.9f,
        )
        val SoftEraser = Brush(
            "brush_eraser_soft", "Soft Eraser", BrushShape.Round, operation = DrawingOperation.Erase,
            size = 48f, hardness = 0.15f, opacity = 0.6f,
        )
        val Smudge = Brush("brush_smudge", "Smudge", operation = DrawingOperation.Smudge, size = 30f, hardness = 0.4f)

        val builtIn: List<Brush> = listOf(
            Ink, Pencil, Marker, Paint, Airbrush, Calligraphy, Eraser, SoftEraser, Smudge,
        )
    }
}
