package com.photobooth.kiosk.data

import android.content.Context

/** Selected image model. The OpenRouter key and the device private key never live here. */
data class AiSettings(
    val modelId: String = "",
    /** Models the admin starred in the picker; shown as one-tap chips in Control de IA. */
    val favoriteModelIds: Set<String> = emptySet(),
)

class AiSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AiSettings = AiSettings(
        modelId = prefs.getString(KEY_MODEL, "").orEmpty().trim(),
        favoriteModelIds = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet(),
    )

    fun saveModel(modelId: String) {
        prefs.edit()
            .putString(KEY_MODEL, modelId.trim())
            .apply()
    }

    fun saveFavorites(modelIds: Set<String>) {
        // Always store a fresh set: SharedPreferences hands back the live instance.
        prefs.edit()
            .putStringSet(KEY_FAVORITES, modelIds.toSet())
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "ai_backend"
        const val KEY_MODEL = "model_id"
        const val KEY_FAVORITES = "favorite_ids"
    }
}
