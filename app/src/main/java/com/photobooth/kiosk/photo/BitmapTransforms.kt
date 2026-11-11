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

    /**
     * Center-crops [source] to [aspectWidth] / [aspectHeight] without scaling up.
     * If the bitmap already matches that proportion, returns a copy.
     * The caller owns the result; [source] is never recycled here.
     */
    fun centerCropToAspect(source: Bitmap, aspectWidth: Float, aspectHeight: Float): Bitmap {
        if (aspectWidth <= 0f || aspectHeight <= 0f) {
            return source.copy(Bitmap.Config.ARGB_8888, false) ?: source
        }
        val targetAspect = aspectWidth / aspectHeight
        val sourceAspect = source.width.toFloat() / source.height.toFloat()
        if (kotlin.math.abs(sourceAspect - targetAspect) < 0.01f) {
            return source.copy(Bitmap.Config.ARGB_8888, false) ?: source
        }
        val cropWidth: Int
        val cropHeight: Int
        if (sourceAspect > targetAspect) {
            cropHeight = source.height
            cropWidth = (source.height * targetAspect).roundToInt().coerceAtMost(source.width)
        } else {
            cropWidth = source.width
            cropHeight = (source.width / targetAspect).roundToInt().coerceAtMost(source.height)
        }
        val x = ((source.width - cropWidth) / 2).coerceAtLeast(0)
        val y = ((source.height - cropHeight) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(source, x, y, cropWidth, cropHeight)
    }

    /**
     * Halves [source] with filtering until both sides are near [maxWidth] x [maxHeight].
     * Avoids a single huge downscale that turns logos into blocks. Returns [source]
     * when no shrink is needed; otherwise a new bitmap the caller must recycle.
     */
    fun downscaleForDraw(source: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (maxWidth <= 0 || maxHeight <= 0) return source
        var current = source
        var ownsCurrent = false
        while (current.width > maxWidth * 2 && current.height > maxHeight * 2) {
            val next = Bitmap.createScaledBitmap(
                current,
                (current.width / 2).coerceAtLeast(1),
                (current.height / 2).coerceAtLeast(1),
                true,
            )
            if (ownsCurrent && current !== source && !current.isRecycled) current.recycle()
            current = next
            ownsCurrent = true
        }
        return current
    }
}
