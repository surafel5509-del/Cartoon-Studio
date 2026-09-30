package com.cartoonstudio.domain.drawing

import com.cartoonstudio.core.math.Rect2
import com.cartoonstudio.core.math.Vec2
import kotlinx.serialization.Serializable

/** A single sampled input point along a stroke. */
@Serializable
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    /** Stylus tilt in degrees, 0 when unavailable. */
    val tilt: Float = 0f,
    /** Milliseconds since the stroke began; enables velocity dynamics and replay. */
    val timeOffsetMillis: Int = 0,
) {
    val position: Vec2 get() = Vec2(x, y)

    companion object {
        fun of(position: Vec2, pressure: Float = 1f, timeOffsetMillis: Int = 0) =
            StrokePoint(position.x, position.y, pressure, 0f, timeOffsetMillis)
    }
}

/**
 * A committed drawing stroke.
 *
 * Strokes are stored as vector point lists rather than baked pixels so that
 * artwork stays resolution independent, can be re-rendered at export
 * resolution, and remains editable (non-destructive).
 */
@Serializable
data class Stroke(
    val id: String,
    val brush: Brush,
    val color: Rgba,
    val points: List<StrokePoint>,
    /** Closed strokes are filled shapes rather than open paths. */
    val closed: Boolean = false,
    val fill: Rgba? = null,
) {
    val isErase: Boolean get() = brush.operation == DrawingOperation.Erase

    fun bounds(): Rect2 {
        if (points.isEmpty()) return Rect2.EMPTY
        val padding = brush.size * 0.5f + 1f
        return Rect2.bounding(points.map { it.position }).inflate(padding)
    }

    /** Approximate arc length; used for spacing, dashes and progress rendering. */
    fun length(): Float {
        var total = 0f
        for (i in 1 until points.size) {
            total += points[i - 1].position.distanceTo(points[i].position)
        }
        return total
    }

    fun translated(delta: Vec2): Stroke = copy(
        points = points.map { it.copy(x = it.x + delta.x, y = it.y + delta.y) },
    )

    fun transformed(transform: (Vec2) -> Vec2): Stroke = copy(
        points = points.map {
            val p = transform(it.position)
            it.copy(x = p.x, y = p.y)
        },
    )
}

/**
 * A single drawn frame of artwork ("cel" in traditional animation).
 *
 * A cel belongs to a drawing layer at a specific frame index. Frames without
 * their own cel reuse the most recent earlier cel, which is what makes
 * shooting "on twos" or "on threes" free.
 */
@Serializable
data class Cel(
    val id: String,
    val strokes: List<Stroke> = emptyList(),
    /** Optional imported raster reference (stable asset id, never a device path). */
    val imageAssetId: String? = null,
    val label: String? = null,
) {
    val isEmpty: Boolean get() = strokes.isEmpty() && imageAssetId == null

    fun bounds(): Rect2 {
        if (strokes.isEmpty()) return Rect2.EMPTY
        return strokes.fold(Rect2.INVALID) { acc, stroke -> acc.union(stroke.bounds()) }
    }

    fun withStroke(stroke: Stroke): Cel = copy(strokes = strokes + stroke)

    fun withoutStroke(strokeId: String): Cel = copy(strokes = strokes.filterNot { it.id == strokeId })
}
