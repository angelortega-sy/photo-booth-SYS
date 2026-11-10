package com.photobooth.kiosk.ui.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationSparklesTest {
    @Test
    fun `sparkles stay inside the unit square`() {
        repeat(40) { index ->
            listOf(0f, 0.13f, 0.5f, 0.87f, 0.999f).forEach { progress ->
                val sparkle = GenerationSparkles.at(index, progress)
                assertTrue(sparkle.x in 0f..1f)
                assertTrue(sparkle.y in 0f..1f)
                assertTrue(sparkle.alpha in 0f..1f)
                assertTrue(sparkle.scale > 0f)
            }
        }
    }

    @Test
    fun `sparkles rise over time`() {
        val before = GenerationSparkles.at(index = 3, progress = 0.20f)
        val after = GenerationSparkles.at(index = 3, progress = 0.21f)
        assertTrue("y should decrease (move up)", after.y < before.y)
    }

    @Test
    fun `sparkles fade out at the top and bottom edges`() {
        // Alpha follows the vertical position, so edges are invisible and the wrap is seamless.
        val sparkle = GenerationSparkles.at(index = 0, progress = 0f)
        assertEquals(GenerationSparkles.alphaAt(sparkle.y), sparkle.alpha, 0.0001f)
        assertEquals(0f, GenerationSparkles.alphaAt(0f), 0.0001f)
        assertEquals(0f, GenerationSparkles.alphaAt(1f), 0.0001f)
    }

    @Test
    fun `the loop is seamless`() {
        repeat(12) { index ->
            val start = GenerationSparkles.at(index, 0f)
            val end = GenerationSparkles.at(index, 1f)
            assertEquals(start.x, end.x, 0.0001f)
            assertEquals(start.y, end.y, 0.0001f)
        }
    }
}
