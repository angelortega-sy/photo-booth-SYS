package com.photobooth.kiosk.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream

/**
 * Copies a brand logo picked from storage into app-private storage so the
 * renderer can read it later without holding a SAF permission.
 *
 * Logos are normalized to a max width of [MAX_WIDTH] px and stored as WebP.
 */
object BrandLogoStore {
    private const val DIR = "logos"
    private const val MAX_WIDTH = 1024
    private const val QUALITY = 92

    fun logoDir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    /** Returns the absolute path of the stored logo, or null when the image cannot be read. */
    fun save(context: Context, uri: Uri): String? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, MAX_WIDTH)
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val scaled = if (decoded.width > MAX_WIDTH) {
            val height = (decoded.height * (MAX_WIDTH.toFloat() / decoded.width)).toInt()
                .coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, MAX_WIDTH, height, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        val target = File(logoDir(context), "logo_${System.currentTimeMillis()}.webp")
        return try {
            FileOutputStream(target).use { out ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                scaled.compress(format, QUALITY, out)
            }
            target.absolutePath
        } catch (_: Exception) {
            target.delete()
            null
        } finally {
            scaled.recycle()
        }
    }

    /** Copies a shared provider logo into this brand's own file. */
    fun copyFile(context: Context, source: File): String? {
        if (!source.isFile) return null
        val ext = source.extension.ifBlank { "png" }
        val target = File(logoDir(context), "logo_${System.currentTimeMillis()}.$ext")
        return try {
            source.copyTo(target, overwrite = true)
            target.absolutePath
        } catch (_: Exception) {
            target.delete()
            null
        }
    }

    /** Deletes a previously stored logo, if any. Safe to call with null. */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        val file = File(path)
        if (file.parentFile?.name == "provider-logos") return
        runCatching { file.delete() }
    }

    private fun sampleSize(width: Int, maxWidth: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= maxWidth) sample *= 2
        return sample
    }
}
