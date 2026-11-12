package com.photobooth.kiosk.ui.attract

/** Default idle time on the welcome screen before the attract video takes over. */
internal const val ATTRACT_DELAY_MS = 25_000L

/** Allowed range for the idle delay picked in Admin. */
internal const val ATTRACT_DELAY_MIN_MS = 10_000L
internal const val ATTRACT_DELAY_MAX_MS = 120_000L
internal const val ATTRACT_DELAY_STEP_MS = 5_000L

/** Time the finished video stays as a poster before it plays again. */
internal const val ATTRACT_REPLAY_DELAY_MS = 90_000L

/**
 * Touches are swallowed for this long after the video is dismissed, so the
 * second tap of a double tap does not fall through to the welcome CTA.
 */
internal const val ATTRACT_TAP_GRACE_MS = 400L

/** Keeps a stored delay inside the range the admin slider offers. */
internal fun clampAttractDelay(delayMs: Long): Long =
    delayMs.coerceIn(ATTRACT_DELAY_MIN_MS, ATTRACT_DELAY_MAX_MS)

internal fun shouldShowAttract(
    showWelcome: Boolean,
    showAdmin: Boolean,
    hasSession: Boolean,
    idleMs: Long,
    delayMs: Long = ATTRACT_DELAY_MS,
): Boolean = showWelcome && !showAdmin && !hasSession && idleMs >= clampAttractDelay(delayMs)
