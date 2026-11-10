package com.photobooth.kiosk.data

import android.content.Context

/** Selected image model and generation preferences. The OpenRouter key never lives here. */
data class AiSettings(
    val modelId: String = "",
    /** Show the draft images the provider streams while it paints, when the model supports it. */
    val streamPreview: Boolean = false,
)

class AiSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AiSettings = AiSettings(
        modelId = prefs.getString(KEY_MODEL, "").orEmpty().trim(),
        streamPreview = prefs.getBoolean(KEY_STREAM, false),
    )

    fun saveModel(modelId: String) {
        prefs.edit()
            .putString(KEY_MODEL, modelId.trim())
            .apply()
    }

    fun setStreamPreview(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_STREAM, enabled)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "ai_backend"
        const val KEY_MODEL = "model_id"
        const val KEY_STREAM = "stream_preview"
    }
}
