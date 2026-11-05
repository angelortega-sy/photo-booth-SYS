package com.photobooth.kiosk.data

import android.content.Context

/**
 * Selected image model, plus the URL and optional kiosk token used by the
 * single-photo flow. The OpenRouter key and the device private key never live here.
 */
data class AiSettings(
    val baseUrl: String = "",
    val kioskToken: String = "",
    val modelId: String = "",
)

class AiSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AiSettings = AiSettings(
        baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty().trim(),
        kioskToken = prefs.getString(KEY_TOKEN, "").orEmpty().trim(),
        modelId = prefs.getString(KEY_MODEL, "").orEmpty().trim(),
    )

    fun save(settings: AiSettings) {
        prefs.edit()
            .putString(KEY_BASE_URL, settings.baseUrl.trim())
            .putString(KEY_TOKEN, settings.kioskToken.trim())
            .putString(KEY_MODEL, settings.modelId.trim())
            .apply()
    }

    fun saveModel(modelId: String) {
        prefs.edit()
            .putString(KEY_MODEL, modelId.trim())
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "ai_backend"
        const val KEY_BASE_URL = "base_url"
        const val KEY_TOKEN = "kiosk_token"
        const val KEY_MODEL = "model_id"
    }
}
