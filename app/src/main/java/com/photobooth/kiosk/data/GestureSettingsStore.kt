package com.photobooth.kiosk.data

import android.content.Context

/**
 * Persists the kiosk gesture preferences.
 *
 * The open-palm capture gesture loads a MediaPipe model and runs on every
 * analyzed frame, so it stays optional: the admin can turn it off for events
 * where guests are too far away or the light is too poor for hand detection.
 */
class GestureSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** When true, showing an open palm to the camera starts the countdown. */
    var palmCaptureEnabled: Boolean
        get() = prefs.getBoolean(KEY_PALM_CAPTURE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_PALM_CAPTURE, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "gesture_settings"
        const val KEY_PALM_CAPTURE = "palm_capture_enabled"
    }
}
