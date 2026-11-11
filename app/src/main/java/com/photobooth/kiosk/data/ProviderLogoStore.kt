package com.photobooth.kiosk.data

import android.content.Context
import com.photobooth.kiosk.network.ProviderLogo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CachedProviderLogo(
    val id: String,
    val name: String,
    val path: String,
    val modifiedTime: String,
)

/** Logos the server sends. Files stay on the device even if a later list omits them. */
object ProviderLogoStore {
    private const val DIR = "provider-logos"
    private const val INDEX = "index.json"

    fun load(context: Context): List<CachedProviderLogo> {
        val root = File(context.filesDir, DIR)
        val index = File(root, INDEX)
        if (!index.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(index.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val file = File(root, item.optString("file"))
                    if (!file.isFile) continue
                    add(
                        CachedProviderLogo(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            path = file.absolutePath,
                            modifiedTime = item.optString("modifiedTime"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun sync(context: Context, remote: List<ProviderLogo>, download: (String) -> ByteArray): List<CachedProviderLogo> {
        val root = File(context.filesDir, DIR).apply { mkdirs() }
        val current = load(context)
        val next = JSONArray()
        remote.forEach { logo ->
            if (!logo.id.matches(Regex("^[a-z0-9-]{1,48}$"))) return@forEach
            val ext = when (logo.mimeType) {
                "image/webp" -> ".webp"
                "image/jpeg" -> ".jpg"
                else -> ".png"
            }
            val fileName = logo.id + ext
            val file = File(root, fileName)
            val kept = current.firstOrNull { it.id == logo.id }
            if (kept == null || kept.modifiedTime != logo.modifiedTime || !file.isFile) {
                file.writeBytes(download(logo.id))
            }
            next.put(
                JSONObject()
                    .put("id", logo.id)
                    .put("name", logo.name)
                    .put("file", fileName)
                    .put("modifiedTime", logo.modifiedTime),
            )
        }
        current.filter { local -> remote.none { it.id == local.id } }.forEach { local ->
            next.put(
                JSONObject()
                    .put("id", local.id)
                    .put("name", local.name)
                    .put("file", File(local.path).name)
                    .put("modifiedTime", local.modifiedTime),
            )
        }
        File(root, INDEX).writeText(next.toString())
        return load(context)
    }
}
