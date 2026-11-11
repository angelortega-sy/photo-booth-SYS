package com.photobooth.kiosk.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.photobooth.kiosk.data.CollageCustomization
import com.photobooth.kiosk.data.CollageLayout
import com.photobooth.kiosk.data.FrameDecoration
import com.photobooth.kiosk.data.FrameShape
import com.photobooth.kiosk.data.PhotoBorderStyle
import com.photobooth.kiosk.data.palette
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Renders individual framed photos and multi-layout collages with customizable
 * frame themes, per-photo borders, and SYSCOM Expo event badges.
 */
object PhotoFrameComposer {

    const val WIDTH = PhotoProcessor.OUTPUT_WIDTH
    const val HEIGHT = PhotoProcessor.OUTPUT_HEIGHT
    private const val STRIP_HEIGHT = 2640
    private const val JPEG_QUALITY = 94

    fun framePhoto(
        photo: Bitmap,
        customization: CollageCustomization = CollageCustomization(),
        previewScale: Boolean = false,
    ): Bitmap {
        val scale = if (previewScale) 0.5f else 1f
        val outWidth = (WIDTH * scale).toInt()
        val outHeight = (HEIGHT * scale).toInt()
        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        drawFrameBackground(canvas, outWidth.toFloat(), outHeight.toFloat(), scale, customization)
        drawTopHeader(canvas, outWidth.toFloat(), 44f * scale, scale, customization)
        drawSponsorLogos(canvas, customization, outWidth.toFloat(), 44f * scale, scale)

        val shape = customization.frameStyle.shape
        val marginX = when (shape) {
            FrameShape.TECH -> 48f
            FrameShape.POLAROID -> 64f
            FrameShape.CYBER -> 52f
            FrameShape.GALA -> 84f
        } * scale
        val photoTop = when (shape) {
            FrameShape.TECH -> 136f
            FrameShape.POLAROID -> 158f
            FrameShape.CYBER -> 150f
            FrameShape.GALA -> 196f
        } * scale
        val photoRect = RectF(
            marginX,
            photoTop,
            outWidth - marginX,
            if (hasFooterContent(customization)) outHeight - 236f * scale else outHeight - 64f * scale,
        )
        drawPhotoPanel(
            canvas = canvas,
            photo = photo,
            dest = photoRect,
            scale = scale,
            customization = customization,
            panelIndex = 0,
            verticalBias = 0.45f,
        )

        drawFooter(
            canvas = canvas,
            width = outWidth.toFloat(),
            footerTop = outHeight - 220f * scale,
            footerBottom = outHeight - 36f * scale,
            scale = scale,
            customization = customization,
        )
        return output
    }

    /**
     * Composes the 3 captured/segmented photos into the selected [CollageLayout]
     * with the active frame theme, border style, and SYSCOM badge preset.
     */
    fun composeCollage(
        photos: List<Bitmap>,
        customization: CollageCustomization = CollageCustomization(),
        previewScale: Boolean = false,
    ): Bitmap {
        val scale = if (previewScale) 0.5f else 1f
        val baseHeight = when (customization.layout) {
            CollageLayout.STRIP_CLASSIC -> STRIP_HEIGHT
            CollageLayout.POSTER_HERO, CollageLayout.EDITORIAL_GRID -> HEIGHT
        }
        val outWidth = (WIDTH * scale).toInt()
        val outHeight = (baseHeight * scale).toInt()
        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        drawFrameBackground(canvas, outWidth.toFloat(), outHeight.toFloat(), scale, customization)
        drawTopHeader(canvas, outWidth.toFloat(), 42f * scale, scale, customization)
        drawSponsorLogos(canvas, customization, outWidth.toFloat(), 42f * scale, scale)

        val slots = slotRects(customization.layout, scale)
        slots.forEachIndexed { index, slotRect ->
            val photo = photos.getOrNull(index) ?: photos.lastOrNull()
            if (photo != null) {
                val verticalBias = when (customization.layout) {
                    CollageLayout.STRIP_CLASSIC -> 0.35f
                    CollageLayout.POSTER_HERO -> if (index == 0) 0.34f else 0.42f
                    CollageLayout.EDITORIAL_GRID -> if (index == 2) 0.45f else 0.36f
                }
                drawPhotoPanel(
                    canvas = canvas,
                    photo = photo,
                    dest = slotRect,
                    scale = scale,
                    customization = customization,
                    panelIndex = index,
                    verticalBias = verticalBias,
                )
            }
        }

        if (customization.layout == CollageLayout.EDITORIAL_GRID) {
            drawEditorialSideCard(
                canvas = canvas,
                rect = RectF(686f * scale, 1364f * scale, 1032f * scale, 1660f * scale),
                scale = scale,
                customization = customization,
            )
        }

        val footerTop = outHeight - 230f * scale
        val footerBottom = outHeight - 36f * scale
        drawFooter(
            canvas = canvas,
            width = outWidth.toFloat(),
            footerTop = footerTop,
            footerBottom = footerBottom,
            scale = scale,
            customization = customization,
        )

        return output
    }

    /** Writes the collage to [target] from already-rendered or raw photo files. */
    fun writeStrip(
        photoFiles: List<File>,
        target: File,
        customization: CollageCustomization = CollageCustomization(),
    ): Boolean {
        if (photoFiles.isEmpty()) return false
        val decoded = mutableListOf<Bitmap>()
        return try {
            for (file in photoFiles) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    ?: error("Could not decode photo for collage")
                decoded += bmp
            }
            val collage = composeCollage(decoded, customization, previewScale = false)
            try {
                writeJpeg(collage, target)
            } finally {
                if (!collage.isRecycled) collage.recycle()
            }
        } catch (_: Throwable) {
            target.delete()
            false
        } finally {
            decoded.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    /**
     * Maps a normalized tap coordinate (0..1, 0..1) inside the rendered collage image
     * to the touched photo index (0, 1, or 2), or null if outside any photo panel.
     */
    fun photoIndexAtPosition(
        normalizedX: Float,
        normalizedY: Float,
        layout: CollageLayout = CollageLayout.POSTER_HERO,
        photoCount: Int = 3,
    ): Int? {
        if (normalizedX !in 0f..1f || normalizedY !in 0f..1f || photoCount <= 0) return null
        val totalHeight = when (layout) {
            CollageLayout.STRIP_CLASSIC -> STRIP_HEIGHT.toFloat()
            CollageLayout.POSTER_HERO, CollageLayout.EDITORIAL_GRID -> HEIGHT.toFloat()
        }
        val x = normalizedX * WIDTH
        val y = normalizedY * totalHeight
        val slots = slotRects(layout, scale = 1f)
        return slots.take(photoCount).indexOfFirst { it.contains(x, y) }.takeIf { it >= 0 }
    }

    /** Legacy helper for vertical strip tap detection. */
    fun photoIndexAtStripPosition(normalizedY: Float, photoCount: Int = 3): Int? =
        photoIndexAtPosition(0.5f, normalizedY, CollageLayout.STRIP_CLASSIC, photoCount)

    private fun slotRects(layout: CollageLayout, scale: Float): List<RectF> =
        when (layout) {
            CollageLayout.POSTER_HERO -> listOf(
                RectF(48f * scale, 134f * scale, 1032f * scale, 996f * scale),
                RectF(48f * scale, 1024f * scale, 526f * scale, 1660f * scale),
                RectF(554f * scale, 1024f * scale, 1032f * scale, 1660f * scale),
            )

            CollageLayout.STRIP_CLASSIC -> {
                val topStart = 136f * scale
                val panelW = (WIDTH - 112) * scale
                val panelH = 710f * scale
                val gap = 34f * scale
                val left = 56f * scale
                List(3) { index ->
                    val top = topStart + index * (panelH + gap)
                    RectF(left, top, left + panelW, top + panelH)
                }
            }

            CollageLayout.EDITORIAL_GRID -> listOf(
                RectF(48f * scale, 134f * scale, 656f * scale, 882f * scale),
                RectF(48f * scale, 912f * scale, 656f * scale, 1660f * scale),
                RectF(686f * scale, 134f * scale, 1032f * scale, 1336f * scale),
            )
        }

    /** Octagon with 45° cut corners: the tech/cyber silhouette. */
    private fun chamferPath(rect: RectF, cut: Float): Path = Path().apply {
        val c = cut.coerceAtMost(rect.width() * 0.4f).coerceAtMost(rect.height() * 0.4f)
        moveTo(rect.left + c, rect.top)
        lineTo(rect.right - c, rect.top)
        lineTo(rect.right, rect.top + c)
        lineTo(rect.right, rect.bottom - c)
        lineTo(rect.right - c, rect.bottom)
        lineTo(rect.left + c, rect.bottom)
        lineTo(rect.left, rect.bottom - c)
        lineTo(rect.left, rect.top + c)
        close()
    }

    /** Rectangle whose top corners are a single wide arch, like a mirror or a theatre window. */
    private fun archedPath(rect: RectF, topRadius: Float): Path {
        val radius = topRadius.coerceIn(0f, rect.width() * 0.5f).coerceAtMost(rect.height() * 0.9f)
        val radii = floatArrayOf(
            radius, radius,
            radius, radius,
            8f, 8f,
            8f, 8f,
        )
        return Path().apply {
            addRoundRect(rect, radii, Path.Direction.CW)
        }
    }

    /** Five-point star used as the gala ornament. */
    private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float): Path = Path().apply {
        val points = 5
        var angle = -90.0
        val step = 360.0 / points
        for (index in 0 until points * 2) {
            val radius = if (index % 2 == 0) outer else inner
            val radians = Math.toRadians(angle)
            val x = cx + (radius * Math.cos(radians)).toFloat()
            val y = cy + (radius * Math.sin(radians)).toFloat()
            if (index == 0) moveTo(x, y) else lineTo(x, y)
            angle += step
        }
        close()
    }

    private fun drawFrameBackground(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        customization: CollageCustomization,
    ) {
        val style = customization.frameStyle
        val decoration = customization.decoration
        val palette = customization.palette()
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                width * 0.35f,
                height,
                palette.background.toInt(),
                palette.secondaryBackground.toInt(),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width, height, bgPaint)

        // Subtle ambient corner glow; fainter on light brand backgrounds.
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent.toInt()
            alpha = if (palette.textPrimary == 0xFF050B14L) 22 else 46
        }
        canvas.drawCircle(width * 0.88f, height * 0.08f, 360f * scale, glowPaint)
        canvas.drawCircle(width * 0.14f, height * 0.90f, 320f * scale, glowPaint)

        // Outer silhouette: each style has its own outline, not just its own colours.
        val outerInset = 20f * scale
        val outerRect = RectF(outerInset, outerInset, width - outerInset, height - outerInset)
        when (style.shape) {
            FrameShape.TECH -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 2.2f * scale
                    color = palette.accent.toInt()
                    alpha = 130
                }
                canvas.drawPath(chamferPath(outerRect, 56f * scale), paint)

                val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.secondaryAccent.toInt()
                    alpha = 170
                }
                val inset = 34f * scale
                val size = 10f * scale
                listOf(
                    inset to inset,
                    width - inset to inset,
                    inset to height - inset,
                    width - inset to height - inset,
                ).forEach { (x, y) ->
                    canvas.drawCircle(x, y, size * 0.55f, tick)
                }
            }

            FrameShape.POLAROID -> {
                val card = RectF(26f * scale, 26f * scale, width - 26f * scale, height - 26f * scale)
                val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(252, 253, 255)
                }
                canvas.drawRoundRect(card, 10f * scale, 10f * scale, cardPaint)

                // Slight bottom shade so the print reads as a physical card.
                val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.secondaryBackground.toInt()
                    alpha = 150
                }
                canvas.drawRoundRect(
                    RectF(card.left, card.bottom - 12f * scale, card.right, card.bottom),
                    10f * scale,
                    10f * scale,
                    shade,
                )

                val hairline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 1.6f * scale
                    color = palette.accent.toInt()
                    alpha = 90
                }
                canvas.drawRoundRect(card, 10f * scale, 10f * scale, hairline)
            }

            FrameShape.CYBER -> {
                val chamfer = chamferPath(outerRect, 34f * scale)
                val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 3.6f * scale
                    color = palette.accent.toInt()
                    alpha = 200
                }
                canvas.drawPath(chamfer, outer)

                val innerRect = RectF(
                    outerInset + 14f * scale,
                    outerInset + 14f * scale,
                    width - outerInset - 14f * scale,
                    height - outerInset - 14f * scale,
                )
                val inner = Paint(outer).apply {
                    strokeWidth = 1.2f * scale
                    color = palette.secondaryAccent.toInt()
                    alpha = 110
                }
                canvas.drawPath(chamferPath(innerRect, 24f * scale), inner)

                val bracket = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 5f * scale
                    strokeCap = Paint.Cap.SQUARE
                    color = palette.accent.toInt()
                    alpha = 220
                }
                val arm = 62f * scale
                val bInset = 40f * scale
                val l = bInset
                val t = bInset
                val r = width - bInset
                val b = height - bInset
                canvas.drawLine(l, t, l + arm, t, bracket)
                canvas.drawLine(l, t, l, t + arm, bracket)
                canvas.drawLine(r - arm, t, r, t, bracket)
                canvas.drawLine(r, t, r, t + arm, bracket)
                canvas.drawLine(l, b - arm, l, b, bracket)
                canvas.drawLine(l, b, l + arm, b, bracket)
                canvas.drawLine(r - arm, b, r, b, bracket)
                canvas.drawLine(r, b - arm, r, b, bracket)
            }

            FrameShape.GALA -> {
                val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 2.4f * scale
                    color = palette.accent.toInt()
                    alpha = 165
                }
                canvas.drawPath(archedPath(outerRect, width * 0.24f), outer)

                val innerRect = RectF(
                    outerInset + 12f * scale,
                    outerInset + 12f * scale,
                    width - outerInset - 12f * scale,
                    height - outerInset - 12f * scale,
                )
                val inner = Paint(outer).apply {
                    strokeWidth = 1.3f * scale
                    color = palette.secondaryAccent.toInt()
                    alpha = 105
                }
                canvas.drawPath(archedPath(innerRect, width * 0.22f), inner)
            }
        }

        drawDecoration(canvas, width, height, scale, customization)
    }

    /**
     * Draws the decorative shape that gives each frame its own silhouette.
     * All geometry scales with the canvas so the 1080×1920 export and the preview match.
     */
    private fun drawDecoration(
        canvas: Canvas,
        width: Float,
        height: Float,
        scale: Float,
        customization: CollageCustomization,
    ) {
        val palette = customization.palette()
        val decoration = customization.decoration
        when (decoration) {
            FrameDecoration.NONE -> Unit

            FrameDecoration.CORNERS -> {
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 4f * scale
                    color = palette.secondaryAccent.toInt()
                    alpha = 200
                    strokeCap = Paint.Cap.ROUND
                }
                val inset = 40f * scale
                val arm = 78f * scale
                val right = width - inset
                val bottom = height - inset
                canvas.drawLine(inset, inset, inset + arm, inset, stroke)
                canvas.drawLine(inset, inset, inset, inset + arm, stroke)
                canvas.drawLine(right - arm, inset, right, inset, stroke)
                canvas.drawLine(right, inset, right, inset + arm, stroke)
                canvas.drawLine(inset, bottom - arm, inset, bottom, stroke)
                canvas.drawLine(inset, bottom, inset + arm, bottom, stroke)
                canvas.drawLine(right - arm, bottom, right, bottom, stroke)
                canvas.drawLine(right, bottom - arm, right, bottom, stroke)

                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.accent.toInt()
                }
                listOf(
                    inset to inset,
                    right to inset,
                    inset to bottom,
                    right to bottom,
                ).forEach { (x, y) ->
                    canvas.drawCircle(x, y, 6f * scale, dotPaint)
                }
            }

            FrameDecoration.CONFETTI -> {
                // Seeded so the same photo always renders the same pattern.
                val random = kotlin.random.Random(20270101)
                val paletteConfetti = intArrayOf(
                    palette.accent.toInt(),
                    palette.secondaryAccent.toInt(),
                    palette.textSecondary.toInt(),
                )
                repeat(70) {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = paletteConfetti[random.nextInt(paletteConfetti.size)]
                        alpha = 170
                    }
                    val x = random.nextFloat() * width
                    val y = random.nextFloat() * height
                    val size = (4f + random.nextFloat() * 7f) * scale
                    if (random.nextBoolean()) {
                        canvas.drawCircle(x, y, size, paint)
                    } else {
                        canvas.save()
                        canvas.rotate(random.nextFloat() * 90f, x, y)
                        canvas.drawRect(
                            x - size,
                            y - size * 1.7f,
                            x + size,
                            y + size * 1.7f,
                            paint,
                        )
                        canvas.restore()
                    }
                }
            }

            FrameDecoration.TECH -> {
                val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 1f * scale
                    color = palette.secondaryAccent.toInt()
                    alpha = 24
                }
                val step = 72f * scale
                var x = step
                while (x < width) {
                    canvas.drawLine(x, 0f, x, height, gridPaint)
                    x += step
                }
                var y = step
                while (y < height) {
                    canvas.drawLine(0f, y, width, y, gridPaint)
                    y += step
                }

                val bracket = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 3.5f * scale
                    strokeCap = Paint.Cap.SQUARE
                    color = palette.accent.toInt()
                    alpha = 195
                }
                val inset = 34f * scale
                val arm = 58f * scale
                val right = width - inset
                val bottom = height - inset
                canvas.drawLine(inset, inset, inset + arm, inset, bracket)
                canvas.drawLine(inset, inset, inset, inset + arm, bracket)
                canvas.drawLine(right - arm, inset, right, inset, bracket)
                canvas.drawLine(right, inset, right, inset + arm, bracket)
                canvas.drawLine(inset, bottom - arm, inset, bottom, bracket)
                canvas.drawLine(inset, bottom, inset + arm, bottom, bracket)
                canvas.drawLine(right - arm, bottom, right, bottom, bracket)
                canvas.drawLine(right, bottom - arm, right, bottom, bracket)
            }

            FrameDecoration.FESTIVE -> {
                val paletteFestive = intArrayOf(
                    palette.accent.toInt(),
                    palette.secondaryAccent.toInt(),
                    palette.textSecondary.toInt(),
                )
                var y = 150f * scale
                var index = 0
                while (y < height - 280f * scale) {
                    drawPennant(canvas, 34f * scale, y, 15f * scale, paletteFestive[index % paletteFestive.size], pointRight = true)
                    drawPennant(
                        canvas,
                        width - 34f * scale,
                        y,
                        15f * scale,
                        paletteFestive[index % paletteFestive.size],
                        pointRight = false,
                    )
                    y += 96f * scale
                    index++
                }

                val sparkle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.textSecondary.toInt()
                    alpha = 190
                }
                val random = kotlin.random.Random(771)
                repeat(26) {
                    val cx = random.nextFloat() * width
                    val cy = if (random.nextBoolean()) {
                        random.nextFloat() * 120f * scale
                    } else {
                        height - 120f * scale + random.nextFloat() * 100f * scale
                    }
                    canvas.drawCircle(cx, cy, (2f + random.nextFloat() * 2.5f) * scale, sparkle)
                }
            }
        }
    }

    private fun drawPennant(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        pointRight: Boolean,
    ) {
        val path = Path().apply {
            if (pointRight) {
                moveTo(x - size, y - size)
                lineTo(x + size, y)
                lineTo(x - size, y + size)
            } else {
                moveTo(x + size, y - size)
                lineTo(x - size, y)
                lineTo(x + size, y + size)
            }
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            alpha = 205
        }
        canvas.drawPath(path, paint)
    }

    /**
     * Decodes the brand logo file, or null when it is missing, empty or undecodable.
     * Never throws.
     */
    private fun decodeBrandLogo(logoPath: String?): Bitmap? {
        if (logoPath.isNullOrBlank()) return null
        return runCatching {
            val file = File(logoPath)
            if (!file.isFile || file.length() <= 0L) return null
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            if (bitmap.width <= 0 || bitmap.height <= 0 || bitmap.isRecycled) {
                if (!bitmap.isRecycled) bitmap.recycle()
                return null
            }
            bitmap
        }.getOrNull()
    }

    /**
     * Draws the brand logo fitted inside the header band (top..top+64*scale):
     * at most 58*scale tall and 45% of the frame wide, keeping the aspect ratio.
     * Returns the drawn width, or 0f when there is no usable logo so the caller
     * falls back to the brand text. The decoded bitmap is always recycled.
     * Never throws.
     */
    private fun drawBrandLogo(
        canvas: Canvas,
        logoPath: String?,
        left: Float,
        top: Float,
        frameWidth: Float,
        scale: Float,
        centerX: Float? = null,
    ): Float {
        val bitmap = decodeBrandLogo(logoPath) ?: return 0f
        try {
            val maxWidth = frameWidth * 0.45f
            val maxHeight = 58f * scale
            if (maxWidth <= 0f || maxHeight <= 0f) return 0f
            val fit = minOf(
                maxWidth / bitmap.width.toFloat(),
                maxHeight / bitmap.height.toFloat(),
            ).coerceAtMost(1f)
            if (fit <= 0f) return 0f
            val drawnWidth = bitmap.width * fit
            val drawnHeight = bitmap.height * fit
            val drawLeft = if (centerX != null) centerX - drawnWidth * 0.5f else left
            val drawTop = top + ((64f * scale - drawnHeight) * 0.5f).coerceAtLeast(0f)
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(drawLeft, drawTop, drawLeft + drawnWidth, drawTop + drawnHeight),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            return drawnWidth
        } catch (_: Throwable) {
            return 0f
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /**
     * Draws the enabled sponsor logos in a row under the header band, centered.
     * Each is fitted to the header height, keeping the aspect ratio. Never throws.
     */
    private fun drawSponsorLogos(
        canvas: Canvas,
        customization: CollageCustomization,
        width: Float,
        headerTop: Float,
        scale: Float,
    ) {
        val paths = customization.sponsorLogoPaths
        if (paths.isEmpty()) return
        val bitmaps = paths.mapNotNull { path ->
            decodeBrandLogo(path)
        }
        if (bitmaps.isEmpty()) return
        try {
            val maxHeight = 40f * scale
            val gap = 26f * scale
            val entries = bitmaps.map { bitmap ->
                val fit = (maxHeight / bitmap.height.toFloat()).coerceAtMost(1f)
                val w = bitmap.width.toFloat() * fit
                bitmap to w
            }
            val totalWidth = entries.sumOf { it.second.toDouble() }.toFloat() + gap * (entries.size - 1).coerceAtLeast(0)
            var x = (width - totalWidth) * 0.5f
            val top = headerTop + 66f * scale
            entries.forEach { (bitmap, w) ->
                canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(x, top, x + w, top + maxHeight),
                    Paint(Paint.FILTER_BITMAP_FLAG),
                )
                x += w + gap
            }
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun drawTopHeader(
        canvas: Canvas,
        width: Float,
        top: Float,
        scale: Float,
        customization: CollageCustomization,
    ) {
        val style = customization.frameStyle
        val palette = customization.palette()
        val leftX = 48f * scale
        val rightX = width - 48f * scale
        val branding = customization.eventBranding
        val brand = branding.brand.trim().uppercase(Locale.ROOT)

        // Right-aligned tag chip + optional date. Never leave a dangling " · " separator.
        val dateText = if (customization.showDateStamp) {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(Date())
                .uppercase(Locale.ROOT)
        } else {
            ""
        }
        val tagText = branding.badgeTag.trim()
        val headerRight = when {
            tagText.isNotEmpty() && dateText.isNotEmpty() -> "$tagText · $dateText"
            tagText.isNotEmpty() -> tagText
            else -> dateText
        }

        when (style.shape) {
            FrameShape.TECH -> {
                var textStartX = leftX
                val logoWidth = drawBrandLogo(canvas, branding.logoPath, leftX, top, width, scale)
                if (logoWidth > 0f) {
                    textStartX = leftX + logoWidth + 20f * scale
                } else if (brand.isNotEmpty()) {
                    val brandTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textAlign = Paint.Align.CENTER
                        textSize = 26f * scale
                        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                        letterSpacing = 0.18f
                    }
                    // Grow the pill with the brand so HIKVISION or UNITREE ROBOTICS stay readable,
                    // but never past 45% of the frame: the right side belongs to the tag + date.
                    val maxPillWidth = ((rightX - leftX) * 0.45f).coerceAtLeast(218f * scale)
                    val pillWidth = (brandTextPaint.measureText(brand) + 56f * scale)
                        .coerceIn(218f * scale, maxPillWidth)
                    val pillRect = RectF(leftX, top, leftX + pillWidth, top + 58f * scale)
                    val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = palette.accent.toInt()
                        alpha = 220
                    }
                    canvas.drawRoundRect(pillRect, 29f * scale, 29f * scale, pillPaint)
                    canvas.drawText(
                        ellipsized(brand, brandTextPaint, pillRect.width() - 28f * scale),
                        pillRect.centerX(),
                        pillRect.centerY() + 9f * scale,
                        brandTextPaint,
                    )
                    textStartX = pillRect.right + 20f * scale
                }
                if (headerRight.isEmpty()) return
                val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.textSecondary.toInt()
                    textAlign = Paint.Align.RIGHT
                    textSize = 22f * scale
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    letterSpacing = 0.10f
                }
                canvas.drawText(
                    ellipsized(headerRight, tagPaint, (rightX - textStartX).coerceAtLeast(48f * scale)),
                    rightX,
                    top + 37f * scale,
                    tagPaint,
                )
            }

            FrameShape.CYBER -> {
                var textStartX = leftX
                val logoWidth = drawBrandLogo(canvas, branding.logoPath, leftX, top, width, scale)
                if (logoWidth > 0f) {
                    textStartX = leftX + logoWidth + 34f * scale

                    // Neon rail from the logo to the right edge.
                    val railPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = palette.secondaryAccent.toInt()
                        alpha = 150
                        strokeWidth = 1.6f * scale
                    }
                    canvas.drawLine(textStartX, top + 27f * scale, rightX, top + 27f * scale, railPaint)
                } else if (brand.isNotEmpty()) {
                    val brandTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(4, 10, 16)
                        textAlign = Paint.Align.CENTER
                        textSize = 25f * scale
                        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                        letterSpacing = 0.16f
                    }
                    val maxWidth = ((rightX - leftX) * 0.45f).coerceAtLeast(200f * scale)
                    val tagWidth = (brandTextPaint.measureText(brand) + 62f * scale)
                        .coerceIn(200f * scale, maxWidth)
                    val slant = 18f * scale
                    val tagPath = Path().apply {
                        moveTo(leftX + slant, top)
                        lineTo(leftX + tagWidth + slant, top)
                        lineTo(leftX + tagWidth, top + 54f * scale)
                        lineTo(leftX, top + 54f * scale)
                        close()
                    }
                    canvas.drawPath(
                        tagPath,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = palette.accent.toInt()
                            alpha = 235
                        },
                    )
                    canvas.drawText(
                        ellipsized(brand, brandTextPaint, tagWidth - 34f * scale),
                        leftX + tagWidth * 0.5f + slant * 0.5f,
                        top + 35f * scale,
                        brandTextPaint,
                    )
                    textStartX = leftX + tagWidth + 34f * scale

                    // Neon rail from the tag to the right edge.
                    val railPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = palette.secondaryAccent.toInt()
                        alpha = 150
                        strokeWidth = 1.6f * scale
                    }
                    canvas.drawLine(textStartX, top + 27f * scale, rightX, top + 27f * scale, railPaint)
                }
                if (headerRight.isEmpty()) return
                val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.secondaryAccent.toInt()
                    textAlign = Paint.Align.RIGHT
                    textSize = 20f * scale
                    typeface = Typeface.create("monospace", Typeface.BOLD)
                    letterSpacing = 0.12f
                }
                canvas.drawText(
                    ellipsized("‹ $headerRight ›", tagPaint, (rightX - textStartX - 22f * scale).coerceAtLeast(48f * scale)),
                    rightX,
                    top + 34f * scale,
                    tagPaint,
                )
            }

            FrameShape.POLAROID -> {
                val centerX = width * 0.5f
                val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.textPrimary.toInt()
                    textAlign = Paint.Align.CENTER
                    textSize = 30f * scale
                    typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                    letterSpacing = 0.20f
                }
                val logoWidth = drawBrandLogo(canvas, branding.logoPath, leftX, top, width, scale, centerX = centerX)
                if (logoWidth <= 0f && brand.isNotEmpty()) {
                    canvas.drawText(
                        ellipsized(brand, brandPaint, width - 160f * scale),
                        centerX,
                        top + 40f * scale,
                        brandPaint,
                    )
                }
                val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.textSecondary.toInt()
                    textAlign = Paint.Align.CENTER
                    textSize = 19f * scale
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    letterSpacing = 0.16f
                }
                if (headerRight.isNotEmpty()) {
                    canvas.drawText(
                        ellipsized(headerRight, subPaint, width - 160f * scale),
                        centerX,
                        top + 74f * scale,
                        subPaint,
                    )
                }
                val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.accent.toInt()
                    alpha = 110
                    strokeWidth = 2f * scale
                }
                canvas.drawLine(
                    centerX - 60f * scale,
                    top + 90f * scale,
                    centerX + 60f * scale,
                    top + 90f * scale,
                    rulePaint,
                )
            }

            FrameShape.GALA -> {
                val centerX = width * 0.5f
                val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.textPrimary.toInt()
                    textAlign = Paint.Align.CENTER
                    textSize = 31f * scale
                    typeface = Typeface.create("serif", Typeface.BOLD)
                    letterSpacing = 0.22f
                }
                val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.accent.toInt()
                    alpha = 230
                }
                val logoWidth = drawBrandLogo(canvas, branding.logoPath, leftX, top, width, scale, centerX = centerX)
                if (logoWidth <= 0f && brand.isNotEmpty()) {
                    val shown = ellipsized(brand, brandPaint, width - 240f * scale)
                    val brandWidth = brandPaint.measureText(shown)
                    canvas.drawText(shown, centerX, top + 44f * scale, brandPaint)
                    val starY = top + 34f * scale
                    canvas.drawPath(starPath(centerX - brandWidth * 0.5f - 28f * scale, starY, 11f * scale, 4.6f * scale), starPaint)
                    canvas.drawPath(starPath(centerX + brandWidth * 0.5f + 28f * scale, starY, 11f * scale, 4.6f * scale), starPaint)
                }
                val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.textSecondary.toInt()
                    textAlign = Paint.Align.CENTER
                    textSize = 18f * scale
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    letterSpacing = 0.30f
                }
                if (headerRight.isNotEmpty()) {
                    val baseline = if (brand.isEmpty()) top + 40f * scale else top + 78f * scale
                    canvas.drawText(
                        ellipsized(headerRight, subPaint, width - 240f * scale),
                        centerX,
                        baseline,
                        subPaint,
                    )
                }
            }
        }
    }

    private fun drawPhotoPanel(
        canvas: Canvas,
        photo: Bitmap,
        dest: RectF,
        scale: Float,
        customization: CollageCustomization,
        panelIndex: Int,
        verticalBias: Float,
    ) {
        val style = customization.frameStyle
        val borderStyle = customization.photoBorderStyle
        val palette = customization.palette()

        val cornerRadius = when (borderStyle) {
            PhotoBorderStyle.CLEAN_ROUNDED -> 26f * scale
            PhotoBorderStyle.POLAROID_WHITE -> 18f * scale
            PhotoBorderStyle.TECH_HUD -> 14f * scale
            PhotoBorderStyle.MINIMAL_SHARP -> 4f * scale
        }

        val imageRect = if (borderStyle == PhotoBorderStyle.POLAROID_WHITE) {
            val pad = 14f * scale
            val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(248, 251, 255)
            }
            canvas.drawRoundRect(dest, cornerRadius, cornerRadius, outerPaint)
            RectF(dest.left + pad, dest.top + pad, dest.right - pad, dest.bottom - pad)
        } else {
            dest
        }

        val innerCorner = if (borderStyle == PhotoBorderStyle.POLAROID_WHITE) {
            (cornerRadius - 6f * scale).coerceAtLeast(4f * scale)
        } else {
            cornerRadius
        }

        // Draw center-cropped photo with BitmapShader for crisp rounded corners
        val shaderMatrix = Matrix()
        val scaleFactor = max(
            imageRect.width() / photo.width.toFloat(),
            imageRect.height() / photo.height.toFloat(),
        )
        val scaledW = photo.width * scaleFactor
        val scaledH = photo.height * scaleFactor
        val dx = imageRect.left + (imageRect.width() - scaledW) * 0.5f
        val dy = imageRect.top + (imageRect.height() - scaledH) * verticalBias
        shaderMatrix.setScale(scaleFactor, scaleFactor)
        shaderMatrix.postTranslate(dx, dy)

        val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
                it.setLocalMatrix(shaderMatrix)
            }
        }
        // The frame shape also decides how the photo itself is cut.
        val imagePath = when (style.shape) {
            FrameShape.TECH -> Path().apply {
                addRoundRect(imageRect, innerCorner, innerCorner, Path.Direction.CW)
            }
            FrameShape.POLAROID -> Path().apply { addRect(imageRect, Path.Direction.CW) }
            FrameShape.CYBER -> chamferPath(imageRect, 26f * scale)
            FrameShape.GALA -> archedPath(imageRect, imageRect.width() * 0.34f)
        }
        canvas.save()
        canvas.clipPath(imagePath)
        canvas.drawRect(imageRect, photoPaint)
        canvas.restore()

        // Frame border overlay per PhotoBorderStyle
        when (borderStyle) {
            PhotoBorderStyle.CLEAN_ROUNDED -> {
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 3.5f * scale
                    color = palette.secondaryAccent.toInt()
                    alpha = 185
                }
                canvas.drawPath(imagePath, strokePaint)
            }

            PhotoBorderStyle.POLAROID_WHITE -> {
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 2f * scale
                    color = palette.accent.toInt()
                    alpha = 120
                }
                canvas.drawRoundRect(dest, cornerRadius, cornerRadius, strokePaint)
            }

            PhotoBorderStyle.TECH_HUD -> {
                val thinStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 1.8f * scale
                    color = palette.secondaryAccent.toInt()
                    alpha = 110
                }
                canvas.drawRoundRect(dest, cornerRadius, cornerRadius, thinStroke)

                val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 5f * scale
                    strokeCap = Paint.Cap.SQUARE
                    color = palette.accent.toInt()
                }
                val arm = 34f * scale
                val inset = 6f * scale
                val l = dest.left + inset
                val t = dest.top + inset
                val r = dest.right - inset
                val b = dest.bottom - inset
                // Top-left bracket
                canvas.drawLine(l, t, l + arm, t, hudPaint)
                canvas.drawLine(l, t, l, t + arm, hudPaint)
                // Top-right bracket
                canvas.drawLine(r - arm, t, r, t, hudPaint)
                canvas.drawLine(r, t, r, t + arm, hudPaint)
                // Bottom-left bracket
                canvas.drawLine(l, b - arm, l, b, hudPaint)
                canvas.drawLine(l, b, l + arm, b, hudPaint)
                // Bottom-right bracket
                canvas.drawLine(r - arm, b, r, b, hudPaint)
                canvas.drawLine(r, b - arm, r, b, hudPaint)

                // Micro HUD tag
                val hudBadgeRect = RectF(
                    dest.left + 18f * scale,
                    dest.bottom - 44f * scale,
                    dest.left + 150f * scale,
                    dest.bottom - 14f * scale,
                )
                val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(175, 5, 11, 20)
                }
                canvas.drawRoundRect(hudBadgeRect, 8f * scale, 8f * scale, hudBgPaint)
                val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.secondaryAccent.toInt()
                    textSize = 16f * scale
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create("monospace", Typeface.BOLD)
                    letterSpacing = 0.08f
                }
                canvas.drawText(
                    "CAM 0${panelIndex + 1} · HD",
                    hudBadgeRect.centerX(),
                    hudBadgeRect.centerY() + 6f * scale,
                    hudTextPaint,
                )
            }

            PhotoBorderStyle.MINIMAL_SHARP -> {
                val sharpStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 2.5f * scale
                    color = palette.textPrimary.toInt()
                    alpha = 90
                }
                canvas.drawRect(dest, sharpStroke)
            }
        }
    }

    private fun drawEditorialSideCard(
        canvas: Canvas,
        rect: RectF,
        scale: Float,
        customization: CollageCustomization,
    ) {
        val palette = customization.palette()
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                palette.accent.toInt(),
                palette.secondaryBackground.toInt(),
                Shader.TileMode.CLAMP,
            )
            alpha = 215
        }
        canvas.drawRoundRect(rect, 22f * scale, 22f * scale, cardPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 2f * scale
            color = palette.secondaryAccent.toInt()
            alpha = 180
        }
        canvas.drawRoundRect(rect, 22f * scale, 22f * scale, borderPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
            textSize = 30f * scale
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = 0.12f
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 243, 248, 255)
            textAlign = Paint.Align.LEFT
            textSize = 20f * scale
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.08f
        }

        val padX = rect.left + 26f * scale
        val availableWidth = rect.width() - 52f * scale
        val branding = customization.eventBranding
        canvas.drawText(
            ellipsized(branding.brand.uppercase().ifBlank { "SYSCOM" }, titlePaint, availableWidth),
            padX,
            rect.top + 68f * scale,
            titlePaint,
        )
        canvas.drawText(
            ellipsized(branding.badgeTag.ifBlank { branding.hashtag }, titlePaint, availableWidth),
            padX,
            rect.top + 106f * scale,
            titlePaint,
        )

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 4f * scale
        }
        canvas.drawLine(padX, rect.top + 132f * scale, rect.right - 26f * scale, rect.top + 132f * scale, linePaint)

        canvas.drawText(
            ellipsized(branding.badgeHeadline, subPaint, availableWidth),
            padX,
            rect.top + 178f * scale,
            subPaint,
        )
        canvas.drawText(
            ellipsized(branding.badgeSubheadline, subPaint, availableWidth),
            padX,
            rect.top + 208f * scale,
            subPaint,
        )
        canvas.drawText(
            ellipsized(branding.hashtag.ifBlank { "#SYSCOM" }, subPaint, availableWidth),
            padX,
            rect.bottom - 30f * scale,
            subPaint,
        )
    }

    private fun ellipsized(text: String, paint: Paint, maxWidth: Float): String {
        val probe = TextPaint(paint)
        return TextUtils.ellipsize(text, probe, maxWidth, TextUtils.TruncateAt.END).toString()
    }

    private fun drawFooter(
        canvas: Canvas,
        width: Float,
        footerTop: Float,
        footerBottom: Float,
        scale: Float,
        customization: CollageCustomization,
    ) {
        val style = customization.frameStyle
        val palette = customization.palette()
        val badge = customization.eventBranding
        val headline = badge.badgeHeadline.trim()
        val subLine = listOf(badge.badgeSubheadline.trim(), badge.hashtag.trim())
            .filter { it.isNotEmpty() }
            .joinToString("  •  ")
        // An empty profile (no badge text at all) should leave a clean photo, not an empty panel.
        if (headline.isEmpty() && subLine.isEmpty()) return

        val left = 48f * scale
        val right = width - 48f * scale
        val centerX = width * 0.5f
        val centerY = (footerTop + footerBottom) * 0.5f
        val footerRect = RectF(left, footerTop, right, footerBottom)

        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.secondaryBackground.toInt()
            alpha = 210
        }
        val panelStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 2f * scale
            color = palette.accent.toInt()
            alpha = 150
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary.toInt()
            textAlign = Paint.Align.LEFT
            textSize = 38f * scale
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textSecondary.toInt()
            textAlign = Paint.Align.LEFT
            textSize = 23f * scale
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.12f
        }

        when (style.shape) {
            FrameShape.TECH -> {
                canvas.drawRoundRect(footerRect, 24f * scale, 24f * scale, panelPaint)
                canvas.drawRoundRect(footerRect, 24f * scale, 24f * scale, panelStroke)

                // Left decorative accent bar inside footer
                val barRect = RectF(
                    left + 24f * scale,
                    footerTop + 28f * scale,
                    left + 36f * scale,
                    footerBottom - 28f * scale,
                )
                val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.accent.toInt()
                }
                canvas.drawRoundRect(barRect, 6f * scale, 6f * scale, barPaint)

                val textLeft = left + 56f * scale
                // Reserve room for the seal only when there is a headline to sit next to.
                val sealRadius = 46f * scale
                val sealCx = right - 68f * scale
                val sealCy = centerY
                val showSeal = headline.isNotEmpty()
                val textRight = if (showSeal) sealCx - sealRadius - 24f * scale else right - 40f * scale
                val textWidth = (textRight - textLeft).coerceAtLeast(80f * scale)

                if (headline.isNotEmpty()) {
                    canvas.drawText(ellipsized(headline, titlePaint, textWidth), textLeft, centerY - 6f * scale, titlePaint)
                }
                if (subLine.isNotEmpty()) {
                    val baseline = if (headline.isEmpty()) centerY + 8f * scale else centerY + 34f * scale
                    canvas.drawText(ellipsized(subLine, subtitlePaint, textWidth), textLeft, baseline, subtitlePaint)
                }

                if (showSeal) {
                    val sealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = palette.accent.toInt()
                        alpha = 38
                    }
                    canvas.drawCircle(sealCx, sealCy, sealRadius, sealPaint)
                    val sealStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.style = Paint.Style.STROKE
                        strokeWidth = 2.5f * scale
                        color = palette.secondaryAccent.toInt()
                        alpha = 180
                    }
                    canvas.drawCircle(sealCx, sealCy, sealRadius, sealStroke)

                    val diamond = Path().apply {
                        moveTo(sealCx, sealCy - 20f * scale)
                        lineTo(sealCx + 20f * scale, sealCy)
                        lineTo(sealCx, sealCy + 20f * scale)
                        lineTo(sealCx - 20f * scale, sealCy)
                        close()
                    }
                    val diamondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = palette.accent.toInt()
                    }
                    canvas.drawPath(diamond, diamondPaint)
                }
            }

            FrameShape.CYBER -> {
                val chamfer = chamferPath(footerRect, 22f * scale)
                canvas.drawPath(chamfer, panelPaint)
                canvas.drawPath(chamfer, panelStroke)

                val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.accent.toInt()
                    strokeWidth = 4f * scale
                    strokeCap = Paint.Cap.SQUARE
                }
                canvas.drawLine(
                    footerRect.left + 26f * scale,
                    footerRect.top + 18f * scale,
                    footerRect.left + 190f * scale,
                    footerRect.top + 18f * scale,
                    rulePaint,
                )

                val textLeft = footerRect.left + 26f * scale
                val textWidth = (footerRect.right - textLeft - 120f * scale).coerceAtLeast(120f * scale)
                if (headline.isNotEmpty()) {
                    canvas.drawText(ellipsized(headline, titlePaint, textWidth), textLeft, centerY + 2f * scale, titlePaint)
                }
                if (subLine.isNotEmpty()) {
                    val baseline = if (headline.isEmpty()) centerY + 8f * scale else centerY + 34f * scale
                    canvas.drawText(ellipsized(subLine, subtitlePaint, textWidth), textLeft, baseline, subtitlePaint)
                }

                // Clip-style marker instead of the round seal.
                val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.accent.toInt()
                    alpha = 200
                }
                val marker = Path().apply {
                    val cx = footerRect.right - 54f * scale
                    val cy = centerY
                    val size = 18f * scale
                    moveTo(cx - size, cy - size)
                    lineTo(cx + size, cy)
                    lineTo(cx - size, cy + size)
                    close()
                }
                canvas.drawPath(marker, markerPaint)
            }

            FrameShape.POLAROID -> {
                // A print caption: rule, then centered serif-less text on the white card.
                val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.accent.toInt()
                    alpha = 120
                    strokeWidth = 2f * scale
                }
                canvas.drawLine(left + 40f * scale, footerTop + 6f * scale, right - 40f * scale, footerTop + 6f * scale, rulePaint)

                titlePaint.textAlign = Paint.Align.CENTER
                subtitlePaint.textAlign = Paint.Align.CENTER
                val textWidth = width - 160f * scale
                if (headline.isNotEmpty()) {
                    canvas.drawText(ellipsized(headline, titlePaint, textWidth), centerX, footerTop + 58f * scale, titlePaint)
                }
                if (subLine.isNotEmpty()) {
                    val baseline = if (headline.isEmpty()) footerTop + 30f * scale else footerTop + 96f * scale
                    canvas.drawText(ellipsized(subLine, subtitlePaint, textWidth), centerX, baseline, subtitlePaint)
                }
            }

            FrameShape.GALA -> {
                titlePaint.textAlign = Paint.Align.CENTER
                titlePaint.typeface = Typeface.create("serif", Typeface.BOLD)
                titlePaint.letterSpacing = 0.12f
                subtitlePaint.textAlign = Paint.Align.CENTER
                subtitlePaint.letterSpacing = 0.24f

                val textWidth = width - 220f * scale
                if (headline.isNotEmpty()) {
                    val shown = ellipsized(headline, titlePaint, textWidth)
                    val baseline = footerTop + 66f * scale
                    canvas.drawText(shown, centerX, baseline, titlePaint)

                    val shownWidth = titlePaint.measureText(shown)
                    val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = palette.accent.toInt()
                        alpha = 225
                    }
                    val starY = baseline - 12f * scale
                    canvas.drawPath(starPath(centerX - shownWidth * 0.5f - 30f * scale, starY, 10f * scale, 4.2f * scale), starPaint)
                    canvas.drawPath(starPath(centerX + shownWidth * 0.5f + 30f * scale, starY, 10f * scale, 4.2f * scale), starPaint)

                    val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = palette.secondaryAccent.toInt()
                        alpha = 120
                        strokeWidth = 1.4f * scale
                    }
                    canvas.drawLine(left + 70f * scale, baseline + 22f * scale, centerX - 40f * scale, baseline + 22f * scale, rulePaint)
                    canvas.drawLine(centerX + 40f * scale, baseline + 22f * scale, right - 70f * scale, baseline + 22f * scale, rulePaint)
                }
                if (subLine.isNotEmpty()) {
                    val baseline = if (headline.isEmpty()) footerTop + 40f * scale else footerTop + 118f * scale
                    canvas.drawText(ellipsized(subLine, subtitlePaint, textWidth), centerX, baseline, subtitlePaint)
                }
            }
        }
    }

    private fun hasFooterContent(customization: CollageCustomization): Boolean {
        val branding = customization.eventBranding
        return branding.badgeHeadline.isNotBlank() ||
            branding.badgeSubheadline.isNotBlank() ||
            branding.hashtag.isNotBlank()
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
