package com.photobooth.kiosk.photo

import android.content.ContentValues
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object PhotoSaver {

    private const val ALBUM = "Cabina de Fotos"
    private const val JPEG_QUALITY = 95

    fun saveToGallery(context: Context, bitmap: Bitmap): Result<Uri> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveModern(context, bitmap)
        } else {
            saveLegacy(context, bitmap)
        }
    }

    fun saveFileToGallery(context: Context, source: File): Result<Uri> = runCatching {
        if (!source.isFile) throw IOException("Photo output is missing")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveModern(context, source)
        } else {
            saveLegacy(context, source)
        }
    }

    fun cacheForShare(context: Context, bitmap: Bitmap): Uri? = runCatching {
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, "photo_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }.getOrNull()

    fun cacheFilesForShare(context: Context, sources: List<File>): List<Uri>? = runCatching {
        if (sources.isEmpty() || sources.any { !it.isFile }) return null
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        sources.mapIndexed { index, source ->
            val file = File(directory, "photo_${index + 1}_${System.currentTimeMillis()}.jpg")
            source.inputStream().use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }
    }.getOrNull()

    fun shareIntent(context: Context, uri: Uri): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(null)
        }

    fun shareIntent(context: Context, uris: List<Uri>): Intent =
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            uris.firstOrNull()?.let { first ->
                clipData = ClipData.newUri(context.contentResolver, "Cabina de Fotos", first).apply {
                    uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(null)
        }

    private fun saveModern(context: Context, bitmap: Bitmap): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "photobooth_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create MediaStore entry")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            } ?: throw IOException("Could not open MediaStore output")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
        return uri
    }

    private fun saveModern(context: Context, source: File): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "photobooth_${System.currentTimeMillis()}_${source.name}",
            )
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create MediaStore entry")
        try {
            source.inputStream().use { input ->
                resolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                    ?: throw IOException("Could not open MediaStore output")
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, bitmap: Bitmap): Uri {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM,
        )
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create photo directory")
        }
        val file = File(directory, "photobooth_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }
        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf("image/jpeg"),
            null,
        )
        return Uri.fromFile(file)
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, source: File): Uri {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM,
        )
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create photo directory")
        }
        val file = File(directory, "photobooth_${System.currentTimeMillis()}_${source.name}")
        source.inputStream().use { input -> FileOutputStream(file).use { input.copyTo(it) } }
        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf("image/jpeg"),
            null,
        )
        return Uri.fromFile(file)
    }
}
