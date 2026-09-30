package com.cartoonstudio.engine.drawing

import com.cartoonstudio.core.math.Vec2
import com.cartoonstudio.domain.drawing.Brush
import com.cartoonstudio.domain.drawing.Rgba
import com.cartoonstudio.domain.drawing.Stroke
import com.cartoonstudio.domain.drawing.StrokePoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrokeGeometryTest {

    private fun stroke(vararg points: Pair<Float, Float>, brush: Brush = Brush.Ink) = Stroke(
        id = "stroke_test",
        brush = brush,
        color = Rgba.Ink,
        points = points.map { StrokePoint(it.first, it.second) },
    )

    @Test
    fun `a single point produces no geometry`() {
        assertTrue(StrokeTessellator.tessellate(stroke(0f to 0f)).isEmpty)
    }

    @Test
    fun `a straight line produces a closed ribbon outline`() {
        val geometry = StrokeTessellator.tessellate(stroke(0f to 0f, 100f to 0f))
        assertTrue(geometry.centerLine.size >= 2)
        // Two sides of the ribbon, so the outline has twice the samples.
        assertEquals(geometry.samples.size * 2, geometry.outline.size)
        assertTrue(geometry.averageWidth > 0f)
    }

    @Test
    fun `bounds include the stroke width`() {
        val brush = Brush.Ink.copy(size = 20f)
        val geometry = StrokeTessellator.tessellate(stroke(0f to 0f, 100f to 0f, brush = brush))
        assertTrue(geometry.bounds.height >= 10f, "bounds ${geometry.bounds} ignored brush width")
        assertTrue(geometry.bounds.width >= 100f)
    }

    @Test
    fun `hit testing respects the stroke path`() {
        val line = stroke(0f to 0f, 100f to 0f, brush = Brush.Ink.copy(size = 10f))
        assertTrue(StrokeHitTester.hits(line, Vec2(50f, 0f)))
        assertTrue(!StrokeHitTester.hits(line, Vec2(50f, 400f)))
    }

    @Test
    fun `lasso selection returns only enclosed strokes`() {
        val inside = stroke(10f to 10f, 20f to 20f)
        val outside = stroke(500f to 500f, 520f to 520f)
        val polygon = listOf(Vec2(0f, 0f), Vec2(100f, 0f), Vec2(100f, 100f), Vec2(0f, 100f))
        val selected = StrokeHitTester.strokesInPolygon(listOf(inside, outside), polygon)
        assertEquals(listOf(inside), selected)
    }

    @Test
    fun `tessellation is deterministic for the same input`() {
        val input = stroke(0f to 0f, 30f to 40f, 90f to 10f)
        val first = StrokeTessellator.tessellate(input)
        val second = StrokeTessellator.tessellate(input)
        assertEquals(first.centerLine, second.centerLine)
        assertEquals(first.outline, second.outline)
    }
}
