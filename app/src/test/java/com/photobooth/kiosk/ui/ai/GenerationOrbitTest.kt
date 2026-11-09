package com.photobooth.kiosk.ui.ai

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

class GenerationOrbitTest {
    @Test
    fun `orbit points remain on the requested radius across phases`() {
        val centerX = 120f
        val centerY = 80f
        val radius = 36f

        listOf(0f, (PI / 2).toFloat(), PI.toFloat(), (PI * 1.5).toFloat()).forEach { phase ->
            val point = GenerationOrbit.point(centerX, centerY, radius, phase)

            assertEquals(radius, hypot(point.x - centerX, point.y - centerY), 0.001f)
        }
    }
}
