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
        get() = prefs.getInt(KEY_ROTATION, 0).normalized()
        set(value) = prefs.edit().putInt(KEY_ROTATION, value.normalized()).apply()

    private fun Int.normalized(): Int = ((this % 360) + 360) % 360

    private companion object {
        const val PREFS_NAME = "camera_settings"
        const val KEY_ROTATION = "rotation_degrees"
    }
}
