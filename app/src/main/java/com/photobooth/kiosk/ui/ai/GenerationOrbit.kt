package com.photobooth.kiosk.ui.ai

import kotlin.math.cos
import kotlin.math.sin

data class GenerationOrbitPoint(
    val x: Float,
    val y: Float,
)

object GenerationOrbit {
    fun point(
        centerX: Float,
        centerY: Float,
        radius: Float,
        phaseRadians: Float,
    ): GenerationOrbitPoint = GenerationOrbitPoint(
        x = centerX + cos(phaseRadians) * radius,
        y = centerY + sin(phaseRadians) * radius,
    )
}
