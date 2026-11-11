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
    // Built-in options keep the resource id so the UI can resolve the label in
    // the app language instead of the (possibly different) device locale.
    val nameRes: Int? = null,
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
        private const val MAX_CACHED_PREVIEWS = 6

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
            "pixel" to "16-bit pixel art video game level: chunky brick platforms, gold coins and " +
                "floating blocks close to the guest, a row of heart icons as a health bar, blue sky " +
                "with pixel clouds, no readable text.",
            "bricks" to "Life-size LEGO brick room: glossy plastic brick walls, a red brick arch, " +
                "oversized loose bricks and a yellow minifigure head on a shelf, studs on the floor, " +
                "no readable text.",
            "toyshelf" to "Collector toy shelf: shelves packed with boxed and loose action figures " +
                "and model kits turned so no label is readable, warm shop spotlights, shallow depth " +
                "of field, no readable text.",
            "anime_room" to "Cozy anime fan bedroom drawn in cel-shaded anime style: shelves of " +
                "figure boxes with no readable labels, rolled posters with abstract art, a low table " +
                "with snacks, floor cushions and a paper lantern, no readable text.",
            "dojo" to "Martial arts tournament arena in bold 90s anime style: raised stone platform, " +
                "cracked tiles, lanterns and banners with abstract symbols, faint crackling energy " +
                "aura, blurred spectators, no readable text.",
            "sakura" to "Narrow Japanese street in cherry blossom season: thick sakura branches and " +
                "falling petals in the foreground, wooden shop front, paper lanterns, stone pavement, " +
                "pink daylight bokeh, no readable text.",
            "neon_alley" to "Narrow cyberpunk alley at night: wet walls and pipes, steam vents, " +
                "holographic signs with abstract glyphs, stacked crates and cables, magenta and cyan " +
                "neon reflected on the floor, light rain, no readable text.",
            "noir_office" to "1940s film noir detective office in black and white: wooden desk with " +
                "a rotary phone and a green-shaded lamp, venetian blinds casting hard light slats, " +
                "trench coat on a hook, smoke haze, no readable text.",
            "videoclub" to "1990s video rental store: tall shelves of VHS tapes with blank spines, a " +
                "CRT television with static glow, a popcorn bucket, fluorescent light mixed with warm " +
                "lamp, slight VHS softness, no readable text.",
            "altar" to "Elegant Day of the Dead altar, never gory: tiers covered in marigolds, sugar " +
                "skulls, candles and pan de muerto, papel picado with abstract cut-outs overhead, " +
                "warm candlelight and incense smoke, no readable text.",
            "plaza" to "Mexican plaza at night: papel picado and warm lanterns hanging low, stone arch " +
                "and bandstand behind, cacti and flower pots, a cart with paper flowers and sombreros, " +
                "string lights over a deep blue sky, no readable text.",
            "xmas_cabin" to "Cozy Christmas cabin interior: decorated evergreen tree with fairy lights, " +
                "wrapped gifts and a wooden reindeer figure, frosted window with falling snow, stone " +
                "fireplace glow, warm golden bokeh, no readable text.",
            "kpop_stage" to "K-pop concert stage: glossy black floor with reflective haze, low " +
                "barricade with glowing light sticks, abstract LED panels and neon bars, pink cyan and " +
                "violet rim light, confetti and smoke, no readable text.",
            "meadow" to "Dreamy hand-painted meadow in classic animated film style: tall grass and " +
                "wildflowers in the foreground, a small stone cottage with a red roof, fluffy painted " +
                "clouds, a bicycle on the fence, warm afternoon light, no readable text.",
        )
    }

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val backgroundsDir = File(context.filesDir, "backgrounds").apply { mkdirs() }

    private val original = BackgroundOption(
        id = ORIGINAL_ID,
        name = context.getString(R.string.background_original),
        nameRes = R.string.background_original,
    )

    @Volatile
    private var builtInOptions: List<BackgroundOption> = listOf(
        builtIn("gala", R.string.background_gala, "backgrounds/gala.webp"),
        builtIn("neon", R.string.background_neon, "backgrounds/neon.webp"),
        builtIn("playa", R.string.background_playa, "backgrounds/playa.webp"),
        builtIn("estudio", R.string.background_estudio, "backgrounds/estudio.webp"),
        builtIn("retro80s", R.string.background_retro80s, "backgrounds/retro80s.webp"),
        builtIn("jardin", R.string.background_jardin, "backgrounds/jardin.webp"),
        builtIn("pixel", R.string.background_pixel, "backgrounds/pixel.webp"),
        builtIn("bricks", R.string.background_bricks, "backgrounds/bricks.webp"),
        builtIn("toyshelf", R.string.background_toyshelf, "backgrounds/toyshelf.webp"),
        builtIn("anime_room", R.string.background_anime_room, "backgrounds/anime_room.webp"),
        builtIn("dojo", R.string.background_dojo, "backgrounds/dojo.webp"),
        builtIn("sakura", R.string.background_sakura, "backgrounds/sakura.webp"),
        builtIn("neon_alley", R.string.background_neon_alley, "backgrounds/neon_alley.webp"),
        builtIn("noir_office", R.string.background_noir_office, "backgrounds/noir_office.webp"),
        builtIn("videoclub", R.string.background_videoclub, "backgrounds/videoclub.webp"),
        builtIn("altar", R.string.background_altar, "backgrounds/altar.webp"),
        builtIn("plaza", R.string.background_plaza, "backgrounds/plaza.webp"),
        builtIn("xmas_cabin", R.string.background_xmas_cabin, "backgrounds/xmas_cabin.webp"),
        builtIn("kpop_stage", R.string.background_kpop_stage, "backgrounds/kpop_stage.webp"),
        builtIn("meadow", R.string.background_meadow, "backgrounds/meadow.webp"),
    )

    private val customOptions = mutableListOf<BackgroundOption>()

    private val thumbnails = ConcurrentHashMap<String, Bitmap>()

    /**
     * A 540x960 preview is about 2 MB. With 20+ bundled backgrounds an unbounded
     * map would keep every one the guest ever tapped, so only the most recently
     * used few stay cached.
     */
    private val previewLock = Any()
    private val previewBackgrounds = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean =
            size > MAX_CACHED_PREVIEWS
    }

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
        return synchronized(previewLock) {
            previewBackgrounds[id]?.takeIf { !it.isRecycled }
        } ?: decode(id, sampleSize = 2)?.also {
            synchronized(previewLock) { previewBackgrounds[id] = it }
        }
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
        nameRes = nameRes,
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
        synchronized(previewLock) {
            previewBackgrounds.remove(id)?.let { if (!it.isRecycled) it.recycle() }
        }
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
        synchronized(previewLock) {
            previewBackgrounds.values.forEach { if (!it.isRecycled) it.recycle() }
            previewBackgrounds.clear()
        }
        fullBackground?.let { if (!it.isRecycled) it.recycle() }
        fullBackground = null
        fullBackgroundId = null
    }
}
