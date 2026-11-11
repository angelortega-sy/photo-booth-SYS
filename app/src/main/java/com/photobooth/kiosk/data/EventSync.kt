package com.photobooth.kiosk.data

import android.content.Context
import android.util.Base64
import com.photobooth.kiosk.network.AiBackendClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Keeps the event list identical on every enrolled booth. The newest save on
 * any device replaces the copy stored on the server; the others download it.
 */
object EventSync {
    private const val MAX_LOGO_BYTES = 1_200_000

    /** Downloads a newer server copy, or uploads this device when it is ahead. */
    fun reconcile(context: Context, store: EventProfileStore, client: AiBackendClient): Boolean {
        val remote = JSONObject(client.eventsDocument())
        val remoteAt = remote.optLong("updatedAt")
        val localAt = store.revision()
        val remoteCount = remote.optJSONArray("profiles")?.length() ?: 0
        if (remoteAt > localAt && remoteCount > 0) {
            store.applyRemote(remote)
            materialize(context, store, client)
            return true
        }
        if (localAt > remoteAt || (remoteAt == 0L && store.hasCustomProfiles())) {
            if (store.revision() == 0L) store.markEdited()
            publish(store, client)
        }
        return false
    }

    fun publish(store: EventProfileStore, client: AiBackendClient) {
        val body = document(store).toString().toByteArray(Charsets.UTF_8)
        client.putEvents(body)
    }

    private fun materialize(context: Context, store: EventProfileStore, client: AiBackendClient) {
        val catalog = ProviderLogoStore.load(context).associateBy { it.id }
        val profiles = store.loadAll().map { profile ->
            val branding = profile.branding
            val fromCatalog = branding.brandLogoId?.let { catalog[it]?.path }?.takeIf { File(it).isFile }
            val current = branding.logoPath?.takeIf { File(it).isFile }
            val path = when {
                fromCatalog != null -> fromCatalog
                current != null -> current
                branding.hasRemoteLogo && branding.brandLogoId == null -> runCatching {
                    BrandLogoStore.saveBytes(context, "remote_${profile.id}", client.eventLogo(profile.id))
                }.getOrNull()
                else -> null
            }
            if (path == branding.logoPath) profile else profile.copy(branding = branding.copy(logoPath = path))
        }
        store.writeLogoPaths(profiles)
    }

    private fun document(store: EventProfileStore): JSONObject {
        val profiles = JSONArray()
        store.loadAll().forEach { profile ->
            profiles.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("enabled", profile.enabled)
                    .put("builtIn", profile.builtIn)
                    .put("branding", brandingJson(profile.branding)),
            )
        }
        return JSONObject()
            .put("updatedAt", store.revision())
            .put("activeId", store.active().id)
            .put("profiles", profiles)
    }

    private fun brandingJson(branding: EventBranding): JSONObject {
        val json = JSONObject()
            .put("brand", branding.brand)
            .put("tagline", branding.tagline)
            .put("welcomeTitle", branding.welcomeTitle)
            .put("welcomeSubtitle", branding.welcomeSubtitle)
            .put("badgeHeadline", branding.badgeHeadline)
            .put("badgeSubheadline", branding.badgeSubheadline)
            .put("badgeTag", branding.badgeTag)
            .put("hashtag", branding.hashtag)
            .put("primaryColor", branding.primaryColor)
            .put("secondaryColor", branding.secondaryColor)
            .put("driveFolderName", branding.driveFolderName)
            .put("sponsorLogoIds", JSONArray(branding.sponsorLogoIds))
        if (!branding.brandLogoId.isNullOrBlank()) {
            json.put("brandLogoId", branding.brandLogoId)
        } else {
            val file = branding.logoPath?.let { File(it) }
            if (file != null && file.isFile && file.length() in 1..MAX_LOGO_BYTES) {
                json.put("brandLogoBase64", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            }
        }
        return json
    }
}
