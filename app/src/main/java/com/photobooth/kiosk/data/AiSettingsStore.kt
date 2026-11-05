package com.photobooth.kiosk.data

import android.content.Context

/** Backend connection settings for AI styling. Never stores the OpenRouter key. */
data class AiSettings(
    val baseUrl: String = "",
    val kioskToken: String = "",
    val modelId: String = DEFAULT_MODEL_ID,
) {
    companion object {
        const val DEFAULT_MODEL_ID = "openai/gpt-image-2.6-sunburst"
    }
}

class AiSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AiSettings = AiSettings(
        baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty().trim(),
        kioskToken = prefs.getString(KEY_TOKEN, "").orEmpty().trim(),
        modelId = prefs.getString(KEY_MODEL, AiSettings.DEFAULT_MODEL_ID)
            .orEmpty().trim().ifEmpty { AiSettings.DEFAULT_MODEL_ID },
    )

    fun save(settings: AiSettings) {
        prefs.edit()
            .putString(KEY_BASE_URL, settings.baseUrl.trim())
            .putString(KEY_TOKEN, settings.kioskToken.trim())
            .putString(KEY_MODEL, settings.modelId.trim().ifEmpty { AiSettings.DEFAULT_MODEL_ID })
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "ai_backend"
        const val KEY_BASE_URL = "base_url"
        const val KEY_TOKEN = "kiosk_token"
        const val KEY_MODEL = "model_id"
    }
}
