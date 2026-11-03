package com.photobooth.kiosk.photo

import android.graphics.Bitmap
import android.graphics.Matrix
import kotlin.math.max
import kotlin.math.roundToInt

/** Bitmap operations that always return a new bitmap the caller owns. */
object BitmapTransforms {

    fun rotateDegrees(source: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
                ?: error("Could not copy bitmap")
        }
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun mirrorHorizontally(source: Bitmap): Bitmap {
        val matrix = Matrix().apply { postScale(-1f, 1f) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /** Scales and center-crops [source] to exactly [targetWidth] x [targetHeight]. */
    fun centerCrop(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val scale = max(
            targetWidth.toFloat() / source.width,
            targetHeight.toFloat() / source.height,
        )
        val scaledWidth = (source.width * scale).roundToInt().coerceAtLeast(targetWidth)
        val scaledHeight = (source.height * scale).roundToInt().coerceAtLeast(targetHeight)
        val scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        val x = ((scaledWidth - targetWidth) / 2).coerceAtLeast(0)
        val y = ((scaledHeight - targetHeight) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(scaled, x, y, targetWidth, targetHeight)

        val result = if (cropped === scaled) {
            scaled.copy(Bitmap.Config.ARGB_8888, false) ?: scaled
        } else {
            cropped
        }

        if (scaled !== source && scaled !== result) scaled.recycle()
        if (cropped !== scaled && cropped !== result) cropped.recycle()
        return result
    }
}
