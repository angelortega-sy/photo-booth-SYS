package com.photobooth.kiosk.data

import android.content.Context

/** Selected image model. The OpenRouter key and the device private key never live here. */
data class AiSettings(
    val modelId: String = "",
)

class AiSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AiSettings = AiSettings(
        modelId = prefs.getString(KEY_MODEL, "").orEmpty().trim(),
    )

    fun saveModel(modelId: String) {
        prefs.edit()
            .putString(KEY_MODEL, modelId.trim())
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "ai_backend"
        const val KEY_MODEL = "model_id"
    }
}
