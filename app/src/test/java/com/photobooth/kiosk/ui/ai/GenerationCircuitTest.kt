package com.photobooth.kiosk.ui.ai

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class GenerationCircuitTest {
    @Test
    fun `tracer follows straight edges and rounded corners continuously`() {
        val width = 200f
        val height = 120f
        val radius = 20f
        val straightWidth = width - 2f * radius
        val quarterArc = (PI.toFloat() / 2f) * radius
        val perimeter = 2f * (straightWidth + height - 2f * radius) + 4f * quarterArc

        assertPoint(20f, 0f, GenerationCircuit.point(width, height, radius, 0f))
        assertPoint(180f, 0f, GenerationCircuit.point(width, height, radius, straightWidth / perimeter))
        assertPoint(200f, 20f, GenerationCircuit.point(width, height, radius, (straightWidth + quarterArc) / perimeter))
        assertPoint(200f, 100f, GenerationCircuit.point(width, height, radius, (straightWidth + quarterArc + height - 2f * radius) / perimeter))
        assertPoint(180f, 120f, GenerationCircuit.point(width, height, radius, (straightWidth + 2f * quarterArc + height - 2f * radius) / perimeter))
        assertPoint(20f, 0f, GenerationCircuit.point(width, height, radius, 1f))
    }

    private fun assertPoint(
        expectedX: Float,
        expectedY: Float,
        actual: GenerationCircuitPoint,
    ) {
        assertEquals(expectedX, actual.x, 0.001f)
        assertEquals(expectedY, actual.y, 0.001f)
    }
}
