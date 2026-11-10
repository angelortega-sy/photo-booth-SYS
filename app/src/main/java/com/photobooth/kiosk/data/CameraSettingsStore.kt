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
        // Version 2 rewrote every stored 0 to the old 270 default, which broke the
        // devices whose camera needed no correction. From version 3 on the angle is
        // only changed from Admin and only applies to the external USB camera, so
        // this migration must not touch the stored value.
        if (prefs.getInt(KEY_VERSION, 1) < CURRENT_VERSION) {
            prefs.edit().putInt(KEY_VERSION, CURRENT_VERSION).apply()
        }
    }

    private fun Int.normalized(): Int = ((this % 360) + 360) % 360

    private companion object {
        const val PREFS_NAME = "camera_settings"
        const val KEY_ROTATION = "rotation_degrees"
        const val KEY_VERSION = "rotation_version"
        const val CURRENT_VERSION = 3
        // Mount correction for a sideways webcam. Built-in cameras are described by
        // CameraX, so the default has to be zero.
        const val DEFAULT_ROTATION = 0
    }
}
