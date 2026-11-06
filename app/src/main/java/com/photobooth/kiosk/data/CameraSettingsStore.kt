package com.photobooth.kiosk.data

import android.content.Context

/**
 * Persisted camera settings for the kiosk. The USB camera is often mounted
 * rotated 90 degrees, so the preview and captured photo need the same extra
 * rotation.
 */
class CameraSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var rotationDegrees: Int
        get() = prefs.getInt(KEY_ROTATION, DEFAULT_ROTATION).normalized()
        set(value) = prefs.edit()
            .putInt(KEY_ROTATION, value.normalized())
            .putInt(KEY_VERSION, CURRENT_VERSION)
            .apply()

    init {
        // A vertical USB webcam was shown 90° clockwise while the old default was 0°.
        val version = prefs.getInt(KEY_VERSION, 1)
        if (version < CURRENT_VERSION) {
            val stored = if (prefs.contains(KEY_ROTATION)) prefs.getInt(KEY_ROTATION, 0) else 0
            val corrected = if (stored == 0) DEFAULT_ROTATION else stored.normalized()
            prefs.edit()
                .putInt(KEY_ROTATION, corrected)
                .putInt(KEY_VERSION, CURRENT_VERSION)
                .apply()
        }
    }

    private fun Int.normalized(): Int = ((this % 360) + 360) % 360

    private companion object {
        const val PREFS_NAME = "camera_settings"
        const val KEY_ROTATION = "rotation_degrees"
        const val KEY_VERSION = "rotation_version"
        const val CURRENT_VERSION = 2
        const val DEFAULT_ROTATION = 270
    }
}
