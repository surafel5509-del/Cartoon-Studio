package com.cartoonstudio.platform.graphics

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.cartoonstudio.core.math.Matrix3
import com.cartoonstudio.core.math.Rect2
import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.domain.model.BlendMode

/** Bridges engine math types onto the Android graphics stack. */
object AndroidConversions {

    /**
     * [Matrix3] is stored in the same row-major order Android uses, so this is
     * a direct copy with no re-ordering.
     */
    fun toMatrix(source: Matrix3, into: Matrix = Matrix()): Matrix {
        into.setValues(source.toFloatArray())
        return into
    }

    fun toRectF(rect: Rect2): RectF = RectF(rect.left, rect.top, rect.right, rect.bottom)

    fun toPath(points: List<Vec2>, closed: Boolean, into: Path = Path()): Path {
        into.reset()
        if (points.isEmpty()) return into
        into.moveTo(points[0].x, points[0].y)
        for (index in 1 until points.size) {
            into.lineTo(points[index].x, points[index].y)
        }
        if (closed) into.close()
        return into
    }

    /**
     * Smooth path through [points] using quadratic midpoint interpolation.
     * Used for tessellated stroke outlines so edges stay soft at high zoom.
     */
    fun toSmoothPath(points: List<Vec2>, closed: Boolean, into: Path = Path()): Path {
        into.reset()
        if (points.size < 3) return toPath(points, closed, into)
        into.moveTo(points[0].x, points[0].y)
        for (index in 1 until points.size - 1) {
            val current = points[index]
            val next = points[index + 1]
            into.quadTo(current.x, current.y, (current.x + next.x) / 2f, (current.y + next.y) / 2f)
        }
        val last = points.last()
        into.lineTo(last.x, last.y)
        if (closed) into.close()
        return into
    }

    /**
     * Maps document blend modes onto Porter-Duff modes available on every
     * supported API level. Modes without a direct equivalent fall back to
     * normal compositing rather than rendering incorrectly.
     */
    fun toXfermode(mode: BlendMode): PorterDuffXfermode? = when (mode) {
        BlendMode.Normal -> null
        BlendMode.Multiply -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        BlendMode.Screen -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        BlendMode.Overlay -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        BlendMode.Darken -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
        BlendMode.Lighten -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
        BlendMode.Add -> PorterDuffXfermode(PorterDuff.Mode.ADD)
        BlendMode.Difference -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
        else -> null
    }
}
