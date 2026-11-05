package com.photobooth.kiosk.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.photobooth.kiosk.R
import com.photobooth.kiosk.photo.BitmapTransforms
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

data class BackgroundOption(
    val id: String,
    val name: String,
    val assetPath: String? = null,
    val filePath: String? = null,
    val isBuiltIn: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * Background catalog with the six bundled assets plus images imported by the
 * admin from the device storage. Imported images live in filesDir and survive
 * app restarts; enabled flags are persisted in SharedPreferences.
 */
class BackgroundRepository(private val context: Context) {

    companion object {
        const val ORIGINAL_ID = "original"
        const val NO_BACKGROUND_ID = "none"
        private const val PREFS_NAME = "background_catalog"
        private const val KEY_CATALOG = "catalog"
        private const val KEY_BUILTIN = "builtin"
        private const val KEY_CUSTOM = "custom"
        private const val OUTPUT_WIDTH = 1080
        private const val OUTPUT_HEIGHT = 1920

        /**
         * English scene descriptions for the six bundled backgrounds. The app
         * sends the matching prompt to the backend so it can generate the full
         * scene (person + background) without guessing the backdrop.
         */
        private val BUILTIN_BACKGROUND_PROMPTS: Map<String, String> = mapOf(
            "gala" to "Elegant gala ballroom at night: marble floor, warm chandeliers, " +
                "gold and deep-blue drapery, soft spotlight on the guest, blurred silhouettes " +
                "of other attendees far behind, no readable text.",
            "neon" to "Cyberpunk neon rooftop at night: glowing pink and cyan signs (abstract, " +
                "no readable text), wet reflective floor, city lights bokeh behind the guest, " +
                "cinematic haze.",
            "playa" to "Tropical beach at golden hour: turquoise sea, palm trees, warm sunset glow, " +
                "soft sand, distant sailboat, relaxed holiday mood, no readable text.",
            "estudio" to "Clean professional photo studio: seamless dark gradient backdrop, soft " +
                "key light and rim light on the guest, subtle floor reflection, no people, " +
                "no props, no text.",
            "retro80s" to "Retro 80s synthwave scene: sunset grid horizon, neon palm silhouettes, " +
                "chrome and pastel tones, arcade glow, nostalgic mood, no readable text.",
            "jardin" to "Sunlit Mediterranean garden: blooming flowers, stone fountain softly blurred, " +
                "green hedges, floating petals, daylight bokeh, no readable text.",
        )
    }

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val backgroundsDir = File(context.filesDir, "backgrounds").apply { mkdirs() }

    private val original = BackgroundOption(
        id = ORIGINAL_ID,
        name = context.getString(R.string.background_original),
    )

    @Volatile
    private var builtInOptions: List<BackgroundOption> = listOf(
        builtIn("gala", R.string.background_gala, "backgrounds/gala.webp"),
        builtIn("neon", R.string.background_neon, "backgrounds/neon.webp"),
        builtIn("playa", R.string.background_playa, "backgrounds/playa.webp"),
        builtIn("estudio", R.string.background_estudio, "backgrounds/estudio.webp"),
        builtIn("retro80s", R.string.background_retro80s, "backgrounds/retro80s.webp"),
        builtIn("jardin", R.string.background_jardin, "backgrounds/jardin.webp"),
    )

    private val customOptions = mutableListOf<BackgroundOption>()

    private val thumbnails = ConcurrentHashMap<String, Bitmap>()
    private val previewBackgrounds = ConcurrentHashMap<String, Bitmap>()

    /** Only one full-size background is kept in memory; the kiosk shows one at a time. */
    private var fullBackgroundId: String? = null
    private var fullBackground: Bitmap? = null

    init {
        loadCatalog()
    }

    /** All built-in and custom backgrounds, including hidden ones. */
    val allOptions: List<BackgroundOption>
        get() = builtInOptions + customOptions

    /** Backgrounds the guest can choose, always including "Sin fondo". */
    val visibleOptions: List<BackgroundOption>
        get() = allOptions.filter { it.enabled } + original

    /** True for admin-imported images (exact-image "custom" AI mode). */
    fun isCustom(id: String): Boolean = customOptions.any { it.id == id }

    /** English scene description for a bundled background, or null. */
    fun backgroundPrompt(id: String): String? = BUILTIN_BACKGROUND_PROMPTS[id]

    /**
     * File backing an admin-imported background, for the optional AI reference
     * upload. Bundled assets have no file; the backend uses the prompt instead.
     */
    fun customBackgroundFile(id: String): File? {
        val option = customOptions.firstOrNull { it.id == id } ?: return null
        val file = option.filePath?.let(::File) ?: return null
        return file.takeIf { it.isFile }
    }

    fun loadThumbnail(id: String): Bitmap? {
        if (id == ORIGINAL_ID) return null
        return thumbnails[id]?.takeIf { !it.isRecycled }
            ?: decode(id, sampleSize = 8)?.also { thumbnails[id] = it }
    }

    /** Loads a 540x960 preview background cached in memory for instant studio recomposition. */
    fun loadPreview(id: String): Bitmap? {
        if (id == ORIGINAL_ID) return null
        return previewBackgrounds[id]?.takeIf { !it.isRecycled }
            ?: decode(id, sampleSize = 2)?.also { previewBackgrounds[id] = it }
    }

    @Synchronized
    fun loadFull(id: String): Bitmap? {
        if (id == ORIGINAL_ID) return null
        if (fullBackgroundId == id && fullBackground?.isRecycled == false) return fullBackground
        fullBackground?.let { if (!it.isRecycled) it.recycle() }
        fullBackground = null
        fullBackgroundId = null
        val decoded = decode(id, sampleSize = 1)
        if (decoded != null) {
            fullBackground = decoded
            fullBackgroundId = id
        }
        return decoded
    }

    fun importFromUri(uri: Uri, displayName: String?): Result<BackgroundOption> = runCatching {
        val sampled = decodeSampled(uri) ?: error("Could not decode selected image")
        val cropped = BitmapTransforms.centerCrop(sampled, OUTPUT_WIDTH, OUTPUT_HEIGHT)
        if (cropped !== sampled && !sampled.isRecycled) sampled.recycle()

        val id = "custom_${System.currentTimeMillis()}"
        val fileName = "$id.webp"
        val file = File(backgroundsDir, fileName)
        FileOutputStream(file).use { output ->
            if (!cropped.compress(Bitmap.CompressFormat.WEBP, 88, output)) {
                error("Could not encode selected image")
            }
        }
        if (!cropped.isRecycled) cropped.recycle()

        val name = displayName
            ?.substringBeforeLast('.')
            ?.trim()
            ?.take(40)
            ?.ifBlank { context.getString(R.string.background_custom_default) }
            ?: context.getString(R.string.background_custom_default)
        val option = BackgroundOption(
            id = id,
            name = name,
            filePath = file.absolutePath,
            enabled = true,
        )
        customOptions += option
        saveCatalog()
        option
    }

    fun setEnabled(id: String, enabled: Boolean): Boolean {
        if (id == ORIGINAL_ID) return false
        val builtIn = builtInOptions.firstOrNull { it.id == id }
        if (builtIn != null) {
            builtInOptions = builtInOptions.map {
                if (it.id == id) it.copy(enabled = enabled) else it
            }
            saveCatalog()
            return true
        }
        val index = customOptions.indexOfFirst { it.id == id }
        if (index < 0) return false
        customOptions[index] = customOptions[index].copy(enabled = enabled)
        saveCatalog()
        return true
    }

    fun deleteCustom(id: String): Boolean {
        val index = customOptions.indexOfFirst { it.id == id }
        if (index < 0) return false
        val option = customOptions.removeAt(index)
        option.filePath?.let { runCatching { File(it).delete() } }
        invalidateCache(id)
        saveCatalog()
        return true
    }

    fun renameCustom(id: String, name: String): Boolean {
        val index = customOptions.indexOfFirst { it.id == id }
        if (index < 0) return false
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return false
        customOptions[index] = customOptions[index].copy(name = clean)
        saveCatalog()
        return true
    }

    private fun builtIn(id: String, nameRes: Int, assetPath: String) = BackgroundOption(
        id = id,
        name = context.getString(nameRes),
        assetPath = assetPath,
        isBuiltIn = true,
    )

    private fun decode(id: String, sampleSize: Int): Bitmap? {
        val option = allOptions.firstOrNull { it.id == id } ?: return null
        return runCatching {
            val bitmapOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig =
                    if (sampleSize > 2) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            }
            when {
                option.filePath != null -> BitmapFactory.decodeFile(option.filePath, bitmapOptions)
                option.assetPath != null -> context.assets.open(option.assetPath).use { stream ->
                    BitmapFactory.decodeStream(stream, null, bitmapOptions)
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun decodeSampled(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= OUTPUT_WIDTH || height / (sample * 2) >= OUTPUT_HEIGHT) {
            sample *= 2
        }
        return sample
    }

    private fun invalidateCache(id: String) {
        thumbnails.remove(id)?.let { if (!it.isRecycled) it.recycle() }
        previewBackgrounds.remove(id)?.let { if (!it.isRecycled) it.recycle() }
        if (fullBackgroundId == id) {
            fullBackground?.let { if (!it.isRecycled) it.recycle() }
            fullBackground = null
            fullBackgroundId = null
        }
    }

    private fun loadCatalog() {
        val raw = prefs.getString(KEY_CATALOG, null) ?: return
        runCatching {
            val root = JSONObject(raw)
            val builtin = root.optJSONObject(KEY_BUILTIN)
            if (builtin != null) {
                builtInOptions = builtInOptions.map { option ->
                    option.copy(enabled = builtin.optBoolean(option.id, true))
                }
            }
            val custom = root.optJSONArray(KEY_CUSTOM) ?: JSONArray()
            customOptions.clear()
            for (index in 0 until custom.length()) {
                val item = custom.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val fileName = item.optString("fileName")
                if (id.isBlank() || fileName.isBlank()) continue
                val file = File(backgroundsDir, fileName)
                if (!file.exists()) continue
                customOptions += BackgroundOption(
                    id = id,
                    name = item.optString("name").ifBlank {
                        context.getString(R.string.background_custom_default)
                    },
                    filePath = file.absolutePath,
                    enabled = item.optBoolean("enabled", true),
                )
            }
        }
    }

    private fun saveCatalog() {
        val root = JSONObject()
        root.put(KEY_BUILTIN, JSONObject().apply {
            builtInOptions.forEach { put(it.id, it.enabled) }
        })
        root.put(KEY_CUSTOM, JSONArray().apply {
            customOptions.forEach { option ->
                put(JSONObject().apply {
                    put("id", option.id)
                    put("name", option.name)
                    put("fileName", File(option.filePath.orEmpty()).name)
                    put("enabled", option.enabled)
                })
            }
        })
        prefs.edit().putString(KEY_CATALOG, root.toString()).apply()
    }

    fun clear() {
        thumbnails.values.forEach { if (!it.isRecycled) it.recycle() }
        thumbnails.clear()
        previewBackgrounds.values.forEach { if (!it.isRecycled) it.recycle() }
        previewBackgrounds.clear()
        fullBackground?.let { if (!it.isRecycled) it.recycle() }
        fullBackground = null
        fullBackgroundId = null
    }
}
