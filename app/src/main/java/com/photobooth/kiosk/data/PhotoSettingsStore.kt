package com.photobooth.kiosk.data

import android.content.Context

/**
 * Kiosk-wide photo handling options.
 *
 * Saving a copy of every finished photo on the device is opt-in: a booth that
 * runs for a whole event would otherwise fill its storage with guest photos.
 */
class PhotoSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveToDevice(): Boolean = prefs.getBoolean(KEY_SAVE_TO_DEVICE, false)

    fun setSaveToDevice(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAVE_TO_DEVICE, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "photo_settings"
        private const val KEY_SAVE_TO_DEVICE = "save_to_device"
    }
}
