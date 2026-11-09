package com.photobooth.kiosk.ui.ai

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class GenerationCircuitPoint(
    val x: Float,
    val y: Float,
)

object GenerationCircuit {
    fun point(
        width: Float,
        height: Float,
        cornerRadius: Float,
        progress: Float,
    ): GenerationCircuitPoint {
        val radius = min(cornerRadius, min(width, height) / 2f)
        val horizontal = width - 2f * radius
        val vertical = height - 2f * radius
        val quarterArc = (PI.toFloat() / 2f) * radius
        val perimeter = 2f * (horizontal + vertical) + 4f * quarterArc
        var distance = (((progress % 1f) + 1f) % 1f) * perimeter

        if (distance <= horizontal) {
            return GenerationCircuitPoint(radius + distance, 0f)
        }
        distance -= horizontal
        if (distance <= quarterArc) {
            return arcPoint(width - radius, radius, radius, -PI.toFloat() / 2f + distance / radius)
        }
        distance -= quarterArc
        if (distance <= vertical) {
            return GenerationCircuitPoint(width, radius + distance)
        }
        distance -= vertical
        if (distance <= quarterArc) {
            return arcPoint(width - radius, height - radius, radius, distance / radius)
        }
        distance -= quarterArc
        if (distance <= horizontal) {
            return GenerationCircuitPoint(width - radius - distance, height)
        }
        distance -= horizontal
        if (distance <= quarterArc) {
            return arcPoint(radius, height - radius, radius, PI.toFloat() / 2f + distance / radius)
        }
        distance -= quarterArc
        if (distance <= vertical) {
            return GenerationCircuitPoint(0f, height - radius - distance)
        }
        distance -= vertical
        return arcPoint(radius, radius, radius, PI.toFloat() + distance / radius)
    }

    private fun arcPoint(
        centerX: Float,
        centerY: Float,
        radius: Float,
        angle: Float,
    ) = GenerationCircuitPoint(
        x = centerX + cos(angle) * radius,
        y = centerY + sin(angle) * radius,
    )
}
