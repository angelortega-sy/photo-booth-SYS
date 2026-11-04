package com.photobooth.kiosk.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream

/** Adds the event identity to finished photos and combines them into a classic photo strip. */
object PhotoFrameComposer {

    private const val WIDTH = PhotoProcessor.OUTPUT_WIDTH
    private const val HEIGHT = PhotoProcessor.OUTPUT_HEIGHT
    private const val PHOTO_MARGIN = 40f
    private const val FOOTER_HEIGHT = 210f
    private const val JPEG_QUALITY = 94
    private const val STRIP_GAP = 28
    private const val STRIP_MARGIN = 32
    private const val STRIP_FOOTER = 160

    fun framePhoto(photo: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)

        val photoArea = RectF(
            PHOTO_MARGIN,
            PHOTO_MARGIN,
            WIDTH - PHOTO_MARGIN,
            HEIGHT - FOOTER_HEIGHT,
        )
        val scale = minOf(photoArea.width() / photo.width, photoArea.height() / photo.height)
        val drawWidth = photo.width * scale
        val drawHeight = photo.height * scale
        val destination = RectF(
            photoArea.centerX() - drawWidth / 2f,
            photoArea.centerY() - drawHeight / 2f,
            photoArea.centerX() + drawWidth / 2f,
            photoArea.centerY() + drawHeight / 2f,
        )
        canvas.drawBitmap(photo, null, destination, Paint(Paint.FILTER_BITMAP_FLAG))

        val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(11, 99, 206) }
        val footerTop = HEIGHT - FOOTER_HEIGHT
        canvas.drawRect(PHOTO_MARGIN, footerTop + 16f, WIDTH - PHOTO_MARGIN, footerTop + 23f, blue)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(5, 11, 20)
            textAlign = Paint.Align.CENTER
            textSize = 42f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.09f
        }
        canvas.drawText("EXPO SYSCOM 2027", WIDTH / 2f, HEIGHT - 92f, titlePaint)

        val subtitlePaint = Paint(titlePaint).apply {
            color = Color.rgb(80, 99, 122)
            textSize = 25f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            letterSpacing = 0.13f
        }
        canvas.drawText("CABINA DE FOTOS", WIDTH / 2f, HEIGHT - 48f, subtitlePaint)
        return output
    }

    /** Writes a tall strip while decoding one framed photo at a time to bound temporary memory. */
    fun writeStrip(photoFiles: List<File>, target: File): Boolean {
        if (photoFiles.isEmpty()) return false
        val panelWidth = WIDTH - STRIP_MARGIN * 2
        val panelHeight = panelWidth * HEIGHT / WIDTH
        val outputHeight = STRIP_MARGIN * 2 + photoFiles.size * panelHeight +
            (photoFiles.size - 1) * STRIP_GAP + STRIP_FOOTER
        val strip = Bitmap.createBitmap(WIDTH, outputHeight, Bitmap.Config.ARGB_8888)

        return try {
            val canvas = Canvas(strip)
            canvas.drawColor(Color.rgb(243, 248, 255))
            val destination = RectF(
                STRIP_MARGIN.toFloat(),
                0f,
                (WIDTH - STRIP_MARGIN).toFloat(),
                panelHeight.toFloat(),
            )
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)

            photoFiles.forEachIndexed { index, file ->
                val photo = BitmapFactory.decodeFile(file.absolutePath)
                    ?: error("Could not decode framed photo for strip")
                destination.offsetTo(
                    STRIP_MARGIN.toFloat(),
                    (STRIP_MARGIN + index * (panelHeight + STRIP_GAP)).toFloat(),
                )
                canvas.drawBitmap(photo, null, destination, paint)
                photo.recycle()
            }

            val footerY = STRIP_MARGIN + photoFiles.size * panelHeight +
                (photoFiles.size - 1) * STRIP_GAP
            val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(11, 99, 206) }
            canvas.drawRect(STRIP_MARGIN.toFloat(), footerY + 32f, (WIDTH - STRIP_MARGIN).toFloat(), footerY + 40f, accent)

            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(5, 11, 20)
                textAlign = Paint.Align.CENTER
                textSize = 40f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                letterSpacing = 0.09f
            }
            canvas.drawText("EXPO SYSCOM 2027", WIDTH / 2f, outputHeight - 56f, label)

            val written = FileOutputStream(target).use { output ->
                strip.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
            if (!written) target.delete()
            written
        } catch (_: Throwable) {
            target.delete()
            false
        } finally {
            strip.recycle()
        }
    }

    /** Returns the photo panel touched in a strip preview, excluding its margins and footer. */
    fun photoIndexAtStripPosition(normalizedY: Float, photoCount: Int = 3): Int? {
        if (normalizedY !in 0f..1f || photoCount <= 0) return null
        val panelWidth = WIDTH - STRIP_MARGIN * 2
        val panelHeight = panelWidth * HEIGHT / WIDTH
        val y = normalizedY * (
            STRIP_MARGIN * 2 + photoCount * panelHeight +
                (photoCount - 1) * STRIP_GAP + STRIP_FOOTER
            )
        return (0 until photoCount).firstOrNull { index ->
            val panelTop = STRIP_MARGIN + index * (panelHeight + STRIP_GAP)
            y >= panelTop && y < panelTop + panelHeight
        }
    }

    fun writeJpeg(bitmap: Bitmap, target: File): Boolean = runCatching {
        FileOutputStream(target).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }
    }.getOrDefault(false)

    fun loadPreview(file: File, sampleSize: Int): Bitmap? = runCatching {
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            },
        )
    }.getOrNull()
}
