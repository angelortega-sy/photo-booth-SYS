package com.photobooth.kiosk.ui.attract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttractStateTest {

    @Test
    fun `welcome idle for the delay shows the attract`() {
        assertTrue(shouldShowAttract(showWelcome = true, showAdmin = false, hasSession = false, idleMs = ATTRACT_DELAY_MS))
    }

    @Test
    fun `welcome before the delay does not`() {
        assertFalse(shouldShowAttract(showWelcome = true, showAdmin = false, hasSession = false, idleMs = ATTRACT_DELAY_MS - 1))
    }

    @Test
    fun `admin never shows the attract`() {
        assertFalse(shouldShowAttract(showWelcome = true, showAdmin = true, hasSession = false, idleMs = 10 * ATTRACT_DELAY_MS))
    }

    @Test
    fun `an active session never shows the attract`() {
        assertFalse(shouldShowAttract(showWelcome = false, showAdmin = false, hasSession = true, idleMs = 10 * ATTRACT_DELAY_MS))
    }

    @Test
    fun `a custom delay from admin is respected`() {
        assertFalse(
            shouldShowAttract(
                showWelcome = true,
                showAdmin = false,
                hasSession = false,
                idleMs = ATTRACT_DELAY_MS,
                delayMs = 60_000L,
            ),
        )
        assertTrue(
            shouldShowAttract(
                showWelcome = true,
                showAdmin = false,
                hasSession = false,
                idleMs = 60_000L,
                delayMs = 60_000L,
            ),
        )
    }

    @Test
    fun `a stored delay outside the slider range is clamped`() {
        assertEquals(ATTRACT_DELAY_MIN_MS, clampAttractDelay(0L))
        assertEquals(ATTRACT_DELAY_MAX_MS, clampAttractDelay(999_000L))
        assertEquals(45_000L, clampAttractDelay(45_000L))
    }
}
