package com.photobooth.kiosk.data

import android.content.Context
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * App language selection. SYSTEM follows the device locale,
 * SPANISH and ENGLISH force the matching locale.
 */
enum class AppLanguage(val id: String, val tag: String) {
    SYSTEM("system", ""),
    SPANISH("es", "es"),
    ENGLISH("en", "en");

    companion object {
        fun fromId(id: String?): AppLanguage =
            entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * Persists the language choice and applies it to a [Context].
 */
object LanguageStore {
    private const val PREFS_NAME = "app_language"
    private const val KEY_LANGUAGE = "language"

    fun current(context: Context): AppLanguage {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppLanguage.fromId(prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.id))
    }

    fun set(context: Context, language: AppLanguage) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.id)
            .apply()
    }

    fun wrap(context: Context): Context {
        val language = current(context)
        if (language == AppLanguage.SYSTEM) return context
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            config.setLocales(LocaleList(locale))
        }
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
