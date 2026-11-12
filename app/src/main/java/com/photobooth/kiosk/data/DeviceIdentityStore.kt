package com.photobooth.kiosk.data

import android.content.Context

/**
 * Persists the enrolled device id and the booth server URL.
 * The private key and the OpenRouter key are never stored here.
 */
class DeviceIdentityStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): DeviceIdentity = DeviceIdentity(
        baseUrl = prefs.getString(KEY_BASE_URL, null)?.trim().orEmpty()
            .ifEmpty { DeviceIdentity.DEFAULT_BASE_URL },
        deviceId = prefs.getString(KEY_DEVICE_ID, "").orEmpty().trim(),
    )

    fun saveBaseUrl(baseUrl: String) {
        prefs.edit()
            .putString(KEY_BASE_URL, normalize(baseUrl))
            .apply()
    }

    fun saveDeviceId(deviceId: String) {
        prefs.edit()
            .putString(KEY_DEVICE_ID, deviceId.trim())
            .apply()
    }

    /** Local-only unenroll: forgets the device id and keeps the server URL. */
    fun clearDevice() {
        prefs.edit()
            .remove(KEY_DEVICE_ID)
            .apply()
    }

    private fun normalize(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return trimmed.ifEmpty { DeviceIdentity.DEFAULT_BASE_URL }
    }

    private companion object {
        const val PREFS_NAME = "booth_device"
        const val KEY_BASE_URL = "base_url"
        const val KEY_DEVICE_ID = "device_id"
    }
}

data class DeviceIdentity(
    val baseUrl: String = DEFAULT_BASE_URL,
    val deviceId: String = "",
) {
    val enrolled: Boolean get() = deviceId.startsWith("dev_")

    companion object {
        const val DEFAULT_BASE_URL = "https://110-238-85-51.sslip.io"
    }
}
