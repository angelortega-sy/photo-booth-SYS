package com.photobooth.kiosk.data

import android.content.Context
import com.photobooth.kiosk.ui.attract.ATTRACT_DELAY_MS
import com.photobooth.kiosk.ui.attract.clampAttractDelay

/**
 * Admin choices for the attract (idle) video: which clip plays over the welcome
 * screen and after how many seconds of no interaction it takes over.
 *
 * The video id is stored as `asset:<file>` for a clip bundled in the APK or
 * `file:<file>` for one imported from the device, so a missing import can fall
 * back to the bundled clip instead of showing a black screen.
 */
class AttractSettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun videoId(): String = prefs.getString(KEY_VIDEO_ID, null)
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_VIDEO_ID

    fun setVideoId(videoId: String) {
        prefs.edit().putString(KEY_VIDEO_ID, videoId).apply()
    }

    fun idleMs(): Long = clampAttractDelay(prefs.getLong(KEY_IDLE_MS, ATTRACT_DELAY_MS))

    fun setIdleMs(idleMs: Long) {
        prefs.edit().putLong(KEY_IDLE_MS, clampAttractDelay(idleMs)).apply()
    }

    companion object {
        private const val PREFS_NAME = "attract_settings"
        private const val KEY_VIDEO_ID = "video_id"
        private const val KEY_IDLE_MS = "idle_ms"

        const val DEFAULT_VIDEO_ID = "asset:attract-loop.mp4"
    }
}
