package com.photobooth.kiosk.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * A named brand preset. SYSCOM is the built-in default and is always present;
 * every other brand is an extra profile the admin adds for a specific stand or event.
 */
data class EventProfile(
    val id: String,
    val name: String,
    val branding: EventBranding,
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
)

/**
 * Persists the list of event profiles and which one is active.
 *
 * Data lives in SharedPreferences("event_profiles") as a single JSON object so the
 * profile list and the active id stay in sync. On first run the legacy single
 * branding stored by [EventBrandingStore] is migrated into the SYSCOM profile.
 */
class EventProfileStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val legacy = EventBrandingStore(context)

    fun loadAll(): List<EventProfile> {
        val stored = readStored()
        val syscom = stored.firstOrNull { it.id == SYSCOM_ID }
            ?: EventProfile(
                id = SYSCOM_ID,
                name = SYSCOM_NAME,
                branding = migratedBranding(),
                builtIn = true,
            )
        val others = stored.filterNot { it.id == SYSCOM_ID }
        return listOf(syscom.copy(builtIn = true, enabled = true)) + others
    }

    /** Profiles the guest may pick from. SYSCOM is always one of them. */
    fun enabledProfiles(): List<EventProfile> = loadAll().filter { it.enabled || it.builtIn }

    fun active(): EventProfile {
        val profiles = loadAll()
        val activeId = prefs.getString(KEY_ACTIVE, SYSCOM_ID)
        return profiles.firstOrNull { it.id == activeId && it.enabled }
            ?: profiles.first { it.id == SYSCOM_ID }
    }

    fun activeBranding(): EventBranding = active().branding

    fun setActive(id: String) {
        if (loadAll().none { it.id == id }) return
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    fun updateBranding(id: String, branding: EventBranding) {
        val profiles = loadAll().map { if (it.id == id) it.copy(branding = branding) else it }
        writeAll(profiles)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        // The built-in SYSCOM profile can be edited but never switched off.
        if (id == SYSCOM_ID) return
        val profiles = loadAll().map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        writeAll(profiles)
        if (!enabled && prefs.getString(KEY_ACTIVE, SYSCOM_ID) == id) {
            prefs.edit().putString(KEY_ACTIVE, SYSCOM_ID).apply()
        }
    }

    fun add(name: String, branding: EventBranding? = null): EventProfile {
        val label = name.trim().ifBlank { "Nueva marca" }
        // A double tap on the add action must not create two brands with the same
        // name: reuse the existing profile instead.
        loadAll().firstOrNull { it.name.equals(label, ignoreCase = true) }?.let { return it }
        val profile = EventProfile(
            id = "brand_" + UUID.randomUUID().toString().replace("-", "").take(12),
            name = label,
            branding = branding ?: defaultBrandingFor(label),
            enabled = true,
        )
        writeAll(loadAll() + profile)
        return profile
    }

    fun delete(id: String): Boolean {
        if (id == SYSCOM_ID) return false
        val profiles = loadAll()
        if (profiles.none { it.id == id }) return false
        writeAll(profiles.filterNot { it.id == id })
        if (prefs.getString(KEY_ACTIVE, SYSCOM_ID) == id) {
            prefs.edit().putString(KEY_ACTIVE, SYSCOM_ID).apply()
        }
        return true
    }

    fun rename(id: String, name: String) {
        val label = name.trim()
        if (label.isEmpty()) return
        writeAll(loadAll().map { if (it.id == id) it.copy(name = label) else it })
    }

    /** True once an admin has created or edited anything beyond the built-in SYSCOM profile. */
    fun hasCustomProfiles(): Boolean {
        val profiles = loadAll()
        if (profiles.size > 1) return true
        return profiles.first().branding != EventBranding()
    }

    fun defaultBrandingFor(name: String): EventBranding {
        val label = name.trim().ifBlank { "Evento" }.uppercase(Locale.ROOT)
        return EventBranding(
            brand = label,
            tagline = "PHOTO BOOTH",
            welcomeTitle = "Tu momento.\nEn grande.",
            welcomeSubtitle = "Tres poses, tu fondo favorito y cuatro recuerdos para llevar.",
            badgeHeadline = label,
            badgeSubheadline = "CABINA DE FOTOS OFICIAL",
            badgeTag = label,
            hashtag = "#" + label.filter { !it.isWhitespace() },
        )
    }

    private fun migratedBranding(): EventBranding =
        if (legacy.hasSaved()) legacy.load() else EventBranding()

    private fun readStored(): List<EventProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val id = json.optString("id").ifBlank { continue }
                    add(
                        EventProfile(
                            id = id,
                            name = json.optString("name").ifBlank { id },
                            branding = readBranding(json.optJSONObject("branding")),
                            enabled = json.optBoolean("enabled", true),
                            builtIn = json.optBoolean("builtIn", id == SYSCOM_ID),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAll(profiles: List<EventProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject().apply {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("enabled", profile.enabled)
                    put("builtIn", profile.builtIn)
                    put("branding", writeBranding(profile.branding))
                },
            )
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    private fun readBranding(json: JSONObject?): EventBranding {
        val defaults = EventBranding()
        if (json == null) return defaults
        return EventBranding(
            brand = json.optString("brand", defaults.brand),
            tagline = json.optString("tagline", defaults.tagline),
            welcomeTitle = json.optString("welcomeTitle", defaults.welcomeTitle),
            welcomeSubtitle = json.optString("welcomeSubtitle", defaults.welcomeSubtitle),
            badgeHeadline = json.optString("badgeHeadline", defaults.badgeHeadline),
            badgeSubheadline = json.optString("badgeSubheadline", defaults.badgeSubheadline),
            badgeTag = json.optString("badgeTag", defaults.badgeTag),
            hashtag = json.optString("hashtag", defaults.hashtag),
            primaryColor = json.optLong("primaryColor", defaults.primaryColor),
            secondaryColor = json.optLong("secondaryColor", defaults.secondaryColor),
            logoPath = json.optString("logoPath", "").ifBlank { null },
            sponsorLogoIds = readSponsorIds(json.optJSONArray("sponsorLogoIds")),
            driveFolderName = json.optString("driveFolderName", defaults.driveFolderName),
        )
    }

    private fun writeBranding(branding: EventBranding): JSONObject = JSONObject().apply {
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
        if (branding.sponsorLogoIds.isNotEmpty()) {
            put("sponsorLogoIds", JSONArray().apply { branding.sponsorLogoIds.forEach { put(it) } })
        }
        if (branding.driveFolderName.isNotBlank()) put("driveFolderName", branding.driveFolderName)
    }

    private fun readSponsorIds(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val id = array.optString(index)
                if (id.isNotBlank()) add(id)
            }
        }
    }

    companion object {
        const val SYSCOM_ID = "syscom"
        const val SYSCOM_NAME = "SYSCOM"
        private const val PREFS_NAME = "event_profiles"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE = "active_id"
    }
}
