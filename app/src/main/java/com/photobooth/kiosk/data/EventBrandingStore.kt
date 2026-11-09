package com.photobooth.kiosk.data

import android.content.Context
import org.json.JSONObject

/** Persists the event text used on the welcome screen and photo frames. */
class EventBrandingStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): EventBranding {
        val raw = prefs.getString(KEY_BRANDING, null) ?: return EventBranding()
        return runCatching {
            val json = JSONObject(raw)
            EventBranding(
                brand = json.optString("brand", "SYSCOM"),
                tagline = json.optString("tagline", "PHOTO BOOTH"),
                welcomeTitle = json.optString("welcomeTitle", "Tu momento.\nEn grande."),
                welcomeSubtitle = json.optString(
                    "welcomeSubtitle",
                    "Tres poses, tu fondo favorito y cuatro recuerdos para llevar.",
                ),
                badgeHeadline = json.optString("badgeHeadline", "EXPO SYSCOM 2027"),
                badgeSubheadline = json.optString(
                    "badgeSubheadline",
                    "CABINA DE FOTOS OFICIAL",
                ),
                badgeTag = json.optString("badgeTag", "SYSCOM · 2027"),
                hashtag = json.optString("hashtag", "#SYSCOM"),
                primaryColor = json.optLong("primaryColor", EventBranding().primaryColor),
                secondaryColor = json.optLong("secondaryColor", EventBranding().secondaryColor),
                logoPath = json.optString("logoPath", "").ifBlank { null },
            )
        }.getOrDefault(EventBranding())
    }

    fun hasSaved(): Boolean = prefs.contains(KEY_BRANDING)

    fun save(branding: EventBranding) {
        val json = JSONObject().apply {
            put("brand", branding.brand)
            put("tagline", branding.tagline)
            put("welcomeTitle", branding.welcomeTitle)
            put("welcomeSubtitle", branding.welcomeSubtitle)
            put("badgeHeadline", branding.badgeHeadline)
            put("badgeSubheadline", branding.badgeSubheadline)
            put("badgeTag", branding.badgeTag)
            put("hashtag", branding.hashtag)
            put("primaryColor", branding.primaryColor)
            put("secondaryColor", branding.secondaryColor)
            if (!branding.logoPath.isNullOrBlank()) put("logoPath", branding.logoPath)
        }
        prefs.edit().putString(KEY_BRANDING, json.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "event_branding"
        const val KEY_BRANDING = "branding"
    }
}
