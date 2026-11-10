package com.photobooth.kiosk.ui.ai

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * Rising sparkles for the generation screen, in unit coordinates (0..1 of the
 * screen). Each sparkle loops a whole number of times per animation cycle so the
 * restart of the infinite transition is invisible.
 */
internal object GenerationSparkles {
    data class Sparkle(val x: Float, val y: Float, val alpha: Float, val scale: Float)

    private const val GOLDEN = 0.618034f
    private const val WOBBLE = 0.025f

    fun at(index: Int, progress: Float): Sparkle {
        val cycles = 1 + index % 3
        val travel = fract(progress * cycles + fract(index * 0.271f))
        val y = 1f - travel
        val wobble = sin((travel + index * 0.5f) * 2f * PI.toFloat()) * WOBBLE
        val x = (fract(index * GOLDEN) * (1f - 2 * WOBBLE) + WOBBLE + wobble).coerceIn(0f, 1f)
        return Sparkle(
            x = x,
            y = y,
            alpha = alphaAt(y),
            scale = 0.5f + fract(index * 0.73f) * 0.9f,
        )
    }

    fun alphaAt(y: Float): Float = sin(PI.toFloat() * y.coerceIn(0f, 1f)).coerceIn(0f, 1f)

    private fun fract(value: Float): Float = value - floor(value)
}
