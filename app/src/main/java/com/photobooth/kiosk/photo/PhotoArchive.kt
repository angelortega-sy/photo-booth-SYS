package com.photobooth.kiosk.photo

import android.content.Context
import java.io.File

/**
 * Copies finished session photos into the shared gallery when the admin
 * enabled "save photos on this device". Failures never interrupt a session.
 */
object PhotoArchive {

    fun save(context: Context, files: List<File>) {
        files.filter { it.isFile }.forEach { file ->
            runCatching { PhotoSaver.saveFileToGallery(context, file) }
        }
    }
}
