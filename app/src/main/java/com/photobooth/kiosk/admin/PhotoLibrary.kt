package com.photobooth.kiosk.admin

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size

data class SavedPhoto(
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
)

object PhotoLibrary {

    private const val ALBUM = "Cabina de Fotos"

    fun list(context: Context): List<SavedPhoto> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
        )

        val selection: String
        val selectionArgs: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            selectionArgs = arrayOf("%Pictures/$ALBUM%")
        } else {
            @Suppress("DEPRECATION")
            selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            selectionArgs = arrayOf("%/Pictures/$ALBUM/%")
        }

        val photos = mutableListOf<SavedPhoto>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    photos += SavedPhoto(
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = cursor.getString(nameColumn) ?: "photo_$id.jpg",
                        dateAdded = cursor.getLong(dateColumn),
                    )
                }
            }
        }
        return photos
    }

    fun thumbnail(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(360, 640), null)
        } else {
            @Suppress("DEPRECATION")
            val options = BitmapFactory.Options().apply { inSampleSize = 8 }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        }
    }.getOrNull()

    fun delete(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.delete(uri, null, null) > 0
    }.getOrDefault(false)

    fun deleteAll(context: Context): Int {
        val photos = list(context)
        return photos.count { delete(context, it.uri) }
    }
}
