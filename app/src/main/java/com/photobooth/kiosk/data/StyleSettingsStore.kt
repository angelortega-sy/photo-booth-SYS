package com.photobooth.kiosk.data

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers which AI styles the admin hid from guests.
 *
 * Only the hidden ids are stored, so a style that the server introduces later
 * shows up enabled by default without any migration.
 */
class StyleSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Ids the admin switched off. Empty means every style is available. */
    val disabledIds: Set<String>
        get() = prefs.getStringSet(KEY_DISABLED, emptySet()).orEmpty().toSet()

    fun isEnabled(id: String): Boolean = id !in disabledIds

    fun setEnabled(id: String, enabled: Boolean) {
        val next = disabledIds.toMutableSet()
        if (enabled) next.remove(id) else next.add(id)
        prefs.edit { putStringSet(KEY_DISABLED, next) }
    }

    private companion object {
        const val PREFS_NAME = "style_settings"
        const val KEY_DISABLED = "disabled_style_ids"
    }
}
