package com.photobooth.kiosk.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import com.photobooth.kiosk.R
import java.util.concurrent.ConcurrentHashMap

data class BackgroundOption(
    val id: String,
    @param:StringRes val nameRes: Int,
    val assetPath: String,
)

class BackgroundRepository(private val context: Context) {

    val options: List<BackgroundOption> = listOf(
        BackgroundOption("gala", R.string.background_gala, "backgrounds/gala.webp"),
        BackgroundOption("neon", R.string.background_neon, "backgrounds/neon.webp"),
        BackgroundOption("playa", R.string.background_playa, "backgrounds/playa.webp"),
        BackgroundOption("estudio", R.string.background_estudio, "backgrounds/estudio.webp"),
        BackgroundOption("retro80s", R.string.background_retro80s, "backgrounds/retro80s.webp"),
        BackgroundOption("jardin", R.string.background_jardin, "backgrounds/jardin.webp"),
    )

    private val thumbnails = ConcurrentHashMap<String, Bitmap>()

    /** Only one full-size background is kept in memory; the kiosk shows one at a time. */
    private var fullBackgroundId: String? = null
    private var fullBackground: Bitmap? = null

    fun loadThumbnail(id: String): Bitmap? =
        thumbnails[id] ?: decode(id, sampleSize = 8)?.also { thumbnails[id] = it }

    fun loadFull(id: String): Bitmap? {
        if (fullBackgroundId == id) return fullBackground
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

    private fun decode(id: String, sampleSize: Int): Bitmap? {
        val option = options.firstOrNull { it.id == id } ?: return null
        return runCatching {
            context.assets.open(option.assetPath).use { stream ->
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig =
                        if (sampleSize > 1) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeStream(stream, null, options)
            }
        }.getOrNull()
    }

    fun clear() {
        thumbnails.values.forEach { if (!it.isRecycled) it.recycle() }
        thumbnails.clear()
        fullBackground?.let { if (!it.isRecycled) it.recycle() }
        fullBackground = null
        fullBackgroundId = null
    }
}
