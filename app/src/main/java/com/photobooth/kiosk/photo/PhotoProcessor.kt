package com.photobooth.kiosk.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class CompositeResult(
    val bitmap: Bitmap,
    val usedFallback: Boolean,
)

/**
 * Holds the pre-segmented person cutout and refined 540x960 alpha mask for a single capture.
 * Keeping 540x960 layers in memory allows <5ms live studio previews and fast 1080x1920 final
 * exports without re-running MediaPipe or the guided filter when changing backgrounds or frames.
 */
class PreparedCutout(
    val previewOriginal: Bitmap,
    val previewPersonLayer: Bitmap?,
    val workAlphaMask: Bitmap?,
    val usedFallback: Boolean,
) {
    fun recycle() {
        if (!previewOriginal.isRecycled) previewOriginal.recycle()
        previewPersonLayer?.let { if (!it.isRecycled) it.recycle() }
        workAlphaMask?.let { if (!it.isRecycled) it.recycle() }
    }
}

class PhotoProcessor(context: Context) {

    companion object {
        const val OUTPUT_WIDTH = 1080
        const val OUTPUT_HEIGHT = 1920
        const val PREVIEW_WIDTH = 540
        const val PREVIEW_HEIGHT = 960

        private const val TAG = "PhotoProcessor"
        private const val MODEL_PATH = "models/selfie_multiclass_256x256.tflite"

        // The model mask is only 256x256. Refining at half output resolution is
        // enough to remove the stair-stepped edges without a big memory cost.
        private const val ALPHA_WORK_WIDTH = PREVIEW_WIDTH
        private const val ALPHA_WORK_HEIGHT = PREVIEW_HEIGHT
        private const val GUIDE_RADIUS = 8
        private const val GUIDE_EPSILON = 0.006f
    }

    private val segmenter: ImageSegmenter? = runCatching {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_PATH)
            .setDelegate(Delegate.CPU)
            .build()
        val options = ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setOutputCategoryMask(true)
            .setOutputConfidenceMasks(true)
            .build()
        ImageSegmenter.createFromOptions(context, options)
    }.onFailure {
        Log.w(TAG, "Could not create segmenter; photos will use the original image", it)
    }.getOrNull()

    /**
     * Runs MediaPipe segmentation and the guided filter once for a captured photo,
     * producing a reusable [PreparedCutout] for instant background switching.
     */
    @Synchronized
    fun extractCutout(photo: Bitmap): PreparedCutout {
        val framed = runCatching {
            BitmapTransforms.centerCrop(photo, OUTPUT_WIDTH, OUTPUT_HEIGHT)
        }.getOrElse {
            val fallbackPreview = Bitmap.createBitmap(PREVIEW_WIDTH, PREVIEW_HEIGHT, Bitmap.Config.ARGB_8888)
            return PreparedCutout(
                previewOriginal = fallbackPreview,
                previewPersonLayer = null,
                workAlphaMask = null,
                usedFallback = true,
            )
        }

        val previewOriginal = Bitmap.createScaledBitmap(framed, PREVIEW_WIDTH, PREVIEW_HEIGHT, true)
        val activeSegmenter = segmenter
        if (activeSegmenter == null) {
            if (framed !== photo && !framed.isRecycled) framed.recycle()
            return PreparedCutout(
                previewOriginal = previewOriginal,
                previewPersonLayer = null,
                workAlphaMask = null,
                usedFallback = true,
            )
        }

        return try {
            val image = BitmapImageBuilder(framed).build()
            val result = activeSegmenter.segment(image)
            val workAlpha = buildWorkAlpha(previewOriginal, result)
            if (workAlpha == null) {
                PreparedCutout(
                    previewOriginal = previewOriginal,
                    previewPersonLayer = null,
                    workAlphaMask = null,
                    usedFallback = true,
                )
            } else {
                val previewPerson = Bitmap.createBitmap(
                    PREVIEW_WIDTH,
                    PREVIEW_HEIGHT,
                    Bitmap.Config.ARGB_8888,
                )
                val layerCanvas = Canvas(previewPerson)
                layerCanvas.drawBitmap(previewOriginal, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                layerCanvas.drawBitmap(
                    workAlpha,
                    0f,
                    0f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    },
                )
                PreparedCutout(
                    previewOriginal = previewOriginal,
                    previewPersonLayer = previewPerson,
                    workAlphaMask = workAlpha,
                    usedFallback = false,
                )
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Cutout extraction failed; falling back to original photo", error)
            PreparedCutout(
                previewOriginal = previewOriginal,
                previewPersonLayer = null,
                workAlphaMask = null,
                usedFallback = true,
            )
        } finally {
            if (framed !== photo && !framed.isRecycled) framed.recycle()
        }
    }

    /**
     * Fast preview composition (540x960) using a cached [PreparedCutout].
     * When [previewBackground] is null, returns a copy of the original preview photo.
     */
    fun compositePreview(cutout: PreparedCutout, previewBackground: Bitmap?): CompositeResult {
        val personLayer = cutout.previewPersonLayer
        if (previewBackground == null || personLayer == null || cutout.usedFallback) {
            val copy = cutout.previewOriginal.copy(Bitmap.Config.ARGB_8888, false)
                ?: Bitmap.createBitmap(PREVIEW_WIDTH, PREVIEW_HEIGHT, Bitmap.Config.ARGB_8888)
            return CompositeResult(
                bitmap = copy,
                usedFallback = previewBackground != null && cutout.usedFallback,
            )
        }

        val output = Bitmap.createBitmap(PREVIEW_WIDTH, PREVIEW_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val bgFrame = if (
            previewBackground.width == PREVIEW_WIDTH && previewBackground.height == PREVIEW_HEIGHT
        ) {
            previewBackground
        } else {
            BitmapTransforms.centerCrop(previewBackground, PREVIEW_WIDTH, PREVIEW_HEIGHT)
        }
        canvas.drawBitmap(bgFrame, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        if (bgFrame !== previewBackground) bgFrame.recycle()

        canvas.drawBitmap(personLayer, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return CompositeResult(output, usedFallback = false)
    }

    /**
     * Full-resolution composition (1080x1920) reusing the cached alpha mask from [cutout]
     * when available, avoiding a second MediaPipe inference pass.
     */
    fun compositeFull(
        sourcePhoto: Bitmap,
        cutout: PreparedCutout?,
        background: Bitmap?,
    ): CompositeResult {
        val framed = runCatching {
            BitmapTransforms.centerCrop(sourcePhoto, OUTPUT_WIDTH, OUTPUT_HEIGHT)
        }.getOrElse {
            return fallback(sourcePhoto)
        }

        if (background == null) {
            return CompositeResult(framed, usedFallback = false)
        }

        val cachedWorkAlpha = cutout?.workAlphaMask?.takeIf { !it.isRecycled && !cutout.usedFallback }
        if (cachedWorkAlpha != null) {
            return try {
                val fullAlpha = Bitmap.createScaledBitmap(
                    cachedWorkAlpha,
                    OUTPUT_WIDTH,
                    OUTPUT_HEIGHT,
                    true,
                )
                try {
                    CompositeResult(
                        bitmap = renderLayers(framed, background, fullAlpha),
                        usedFallback = false,
                    )
                } finally {
                    if (fullAlpha !== cachedWorkAlpha && !fullAlpha.isRecycled) fullAlpha.recycle()
                }
            } catch (error: Throwable) {
                Log.w(TAG, "Cached full composite failed; using fallback", error)
                CompositeResult(framed, usedFallback = true)
            } finally {
                if (framed !== sourcePhoto && !framed.isRecycled) framed.recycle()
            }
        }

        if (cutout != null && cutout.usedFallback) {
            return CompositeResult(framed, usedFallback = true)
        }

        return try {
            composite(framed, background)
        } finally {
            if (framed !== sourcePhoto && !framed.isRecycled) framed.recycle()
        }
    }

    @Synchronized
    fun composite(photo: Bitmap, background: Bitmap): CompositeResult {
        val activeSegmenter = segmenter ?: return fallback(photo)
        val framed = runCatching {
            BitmapTransforms.centerCrop(photo, OUTPUT_WIDTH, OUTPUT_HEIGHT)
        }.getOrElse {
            return fallback(photo)
        }

        return try {
            createComposite(framed, background, activeSegmenter) ?: fallback(framed)
        } catch (error: Throwable) {
            Log.w(TAG, "Segmentation failed; using the original image", error)
            fallback(framed)
        } finally {
            if (framed !== photo && !framed.isRecycled) framed.recycle()
        }
    }

    fun close() {
        runCatching { segmenter?.close() }
    }

    private fun createComposite(
        framed: Bitmap,
        background: Bitmap,
        activeSegmenter: ImageSegmenter,
    ): CompositeResult? {
        val image = BitmapImageBuilder(framed).build()
        val result = activeSegmenter.segment(image)
        val alpha = buildRefinedAlpha(framed, result) ?: return null

        try {
            return CompositeResult(
                bitmap = renderLayers(framed, background, alpha),
                usedFallback = false,
            )
        } finally {
            if (!alpha.isRecycled) alpha.recycle()
        }
    }

    private fun renderLayers(
        framed: Bitmap,
        background: Bitmap,
        alpha: Bitmap,
    ): Bitmap {
        val output = Bitmap.createBitmap(OUTPUT_WIDTH, OUTPUT_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val backgroundFrame = if (
            background.width == OUTPUT_WIDTH && background.height == OUTPUT_HEIGHT
        ) {
            background
        } else {
            BitmapTransforms.centerCrop(background, OUTPUT_WIDTH, OUTPUT_HEIGHT)
        }
        canvas.drawBitmap(backgroundFrame, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        if (backgroundFrame !== background) backgroundFrame.recycle()

        val personLayer = Bitmap.createBitmap(
            OUTPUT_WIDTH,
            OUTPUT_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
        val layerCanvas = Canvas(personLayer)
        layerCanvas.drawBitmap(framed, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        layerCanvas.drawBitmap(
            alpha,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            },
        )
        canvas.drawBitmap(personLayer, 0f, 0f, null)
        personLayer.recycle()
        return output
    }

    /**
     * Builds the refined alpha mask at [ALPHA_WORK_WIDTH]x[ALPHA_WORK_HEIGHT] (540x960)
     * using confidence probabilities and a guided filter.
     */
    private fun buildWorkAlpha(
        guideBitmap540: Bitmap,
        result: ImageSegmenterResult,
    ): Bitmap? {
        val confidenceMasks = result.confidenceMasks().orElse(null)
        val categoryMask = result.categoryMask().orElse(null)

        val source = try {
            val fromConfidence = confidenceMasks
                ?.let { masks -> runCatching { readConfidenceAlpha(masks) }.getOrNull() }
                ?.takeIf { candidate -> candidate.values.any { it > 0.05f } }
            fromConfidence ?: categoryMask?.let { readCategoryAlpha(it) }
        } finally {
            confidenceMasks?.forEach { mask -> runCatching { mask.close() } }
            categoryMask?.let { mask -> runCatching { mask.close() } }
        }

        if (source == null) {
            Log.w(TAG, "Segmentation result has no usable mask")
            return null
        }

        val personRatio = source.values.count { it >= 0.5f }.toFloat() / source.values.size
        Log.d(
            TAG,
            "Mask ${source.width}x${source.height}, confidence=${confidenceMasks?.size}, " +
                "personRatio=$personRatio",
        )
        if (personRatio < 0.02f || personRatio > 0.98f) {
            Log.w(TAG, "Mask rejected; person ratio=$personRatio")
            return null
        }

        val workWidth = min(ALPHA_WORK_WIDTH, OUTPUT_WIDTH)
        val workHeight = min(ALPHA_WORK_HEIGHT, OUTPUT_HEIGHT)
        val resized = resampleAlpha(
            source.values,
            source.width,
            source.height,
            workWidth,
            workHeight,
        )

        val scaledGuide = if (
            guideBitmap540.width == workWidth && guideBitmap540.height == workHeight
        ) {
            guideBitmap540
        } else {
            Bitmap.createScaledBitmap(guideBitmap540, workWidth, workHeight, true)
        }
        val guidePixels = IntArray(workWidth * workHeight)
        scaledGuide.getPixels(guidePixels, 0, workWidth, 0, 0, workWidth, workHeight)
        if (scaledGuide !== guideBitmap540) scaledGuide.recycle()

        val guide = FloatArray(guidePixels.size) { index ->
            val color = guidePixels[index]
            val red = (color shr 16) and 0xFF
            val green = (color shr 8) and 0xFF
            val blue = color and 0xFF
            (red * 0.299f + green * 0.587f + blue * 0.114f) / 255f
        }

        val refined = guidedFilter(
            alpha = resized,
            guide = guide,
            width = workWidth,
            height = workHeight,
            radius = GUIDE_RADIUS,
            epsilon = GUIDE_EPSILON,
        )

        val workPixels = IntArray(refined.size) { index ->
            val value = smoothstep(0.18f, 0.82f, refined[index])
            val alphaByte = (value * 255f).roundToInt().coerceIn(0, 255)
            (alphaByte shl 24) or 0x00FFFFFF
        }
        return Bitmap.createBitmap(
            workPixels,
            workWidth,
            workHeight,
            Bitmap.Config.ARGB_8888,
        )
    }

    /**
     * The category mask is hard 0/1 at 256x256, which is what created the square,
     * stair-stepped silhouette. The confidence masks keep soft probabilities, and
     * a guided filter snaps those probabilities to the real image edges.
     */
    private fun buildRefinedAlpha(
        framed: Bitmap,
        result: ImageSegmenterResult,
    ): Bitmap? {
        val workWidth = min(ALPHA_WORK_WIDTH, OUTPUT_WIDTH)
        val workHeight = min(ALPHA_WORK_HEIGHT, OUTPUT_HEIGHT)
        val guideBitmap = Bitmap.createScaledBitmap(framed, workWidth, workHeight, true)
        val workAlpha = try {
            buildWorkAlpha(guideBitmap, result)
        } finally {
            guideBitmap.recycle()
        } ?: return null

        val fullAlpha = if (workWidth == OUTPUT_WIDTH && workHeight == OUTPUT_HEIGHT) {
            workAlpha
        } else {
            Bitmap.createScaledBitmap(workAlpha, OUTPUT_WIDTH, OUTPUT_HEIGHT, true)
        }
        if (fullAlpha !== workAlpha) workAlpha.recycle()
        return fullAlpha
    }

    private data class AlphaSource(
        val values: FloatArray,
        val width: Int,
        val height: Int,
    )

    private fun readConfidenceAlpha(masks: List<MPImage>): AlphaSource? {
        val first = masks.firstOrNull() ?: return null
        val width = first.width
        val height = first.height
        if (width <= 0 || height <= 0) return null
        val pixelCount = width * height

        val values = FloatArray(pixelCount)
        val startIndex = if (masks.size > 1) 1 else 0

        for (maskIndex in startIndex until masks.size) {
            val buffer = ByteBufferExtractor.extract(masks[maskIndex])
                .order(ByteOrder.nativeOrder())
            buffer.rewind()
            val floats = buffer.asFloatBuffer()
            val count = min(pixelCount, floats.remaining())
            for (index in 0 until count) {
                values[index] = (values[index] + floats.get(index)).coerceIn(0f, 1f)
            }
        }

        // Some binary models expose a single person mask; keep it as-is.
        if (masks.size == 1) {
            val buffer = ByteBufferExtractor.extract(first)
                .order(ByteOrder.nativeOrder())
            buffer.rewind()
            val floats = buffer.asFloatBuffer()
            val count = min(pixelCount, floats.remaining())
            for (index in 0 until count) values[index] = floats.get(index).coerceIn(0f, 1f)
        }

        return AlphaSource(values, width, height)
    }

    private fun readCategoryAlpha(mask: MPImage): AlphaSource? {
        val width = mask.width
        val height = mask.height
        if (width <= 0 || height <= 0) return null
        val pixelCount = width * height
        val buffer = ByteBufferExtractor.extract(mask)
            .order(ByteOrder.nativeOrder())
        buffer.rewind()
        if (buffer.remaining() < pixelCount) return null

        val values = FloatArray(pixelCount) { index ->
            if ((buffer.get(index).toInt() and 0xFF) != 0) 1f else 0f
        }
        return AlphaSource(values, width, height)
    }

    private fun resampleAlpha(
        source: FloatArray,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): FloatArray {
        if (sourceWidth == targetWidth && sourceHeight == targetHeight) {
            return source.copyOf()
        }

        val output = FloatArray(targetWidth * targetHeight)
        val scaleX = sourceWidth.toFloat() / targetWidth
        val scaleY = sourceHeight.toFloat() / targetHeight

        for (y in 0 until targetHeight) {
            val sourceY = (y + 0.5f) * scaleY - 0.5f
            val y0 = floor(sourceY).toInt().coerceIn(0, sourceHeight - 1)
            val y1 = (y0 + 1).coerceAtMost(sourceHeight - 1)
            val fy = (sourceY - y0).coerceIn(0f, 1f)

            for (x in 0 until targetWidth) {
                val sourceX = (x + 0.5f) * scaleX - 0.5f
                val x0 = floor(sourceX).toInt().coerceIn(0, sourceWidth - 1)
                val x1 = (x0 + 1).coerceAtMost(sourceWidth - 1)
                val fx = (sourceX - x0).coerceIn(0f, 1f)

                val topLeft = source[y0 * sourceWidth + x0]
                val topRight = source[y0 * sourceWidth + x1]
                val bottomLeft = source[y1 * sourceWidth + x0]
                val bottomRight = source[y1 * sourceWidth + x1]
                val top = topLeft + (topRight - topLeft) * fx
                val bottom = bottomLeft + (bottomRight - bottomLeft) * fx
                output[y * targetWidth + x] = top + (bottom - top) * fy
            }
        }
        return output
    }

    /**
     * Guided filter: uses the photo as the guide so the soft mask follows hair,
     * ears and clothing edges instead of the 256px model grid.
     */
    private fun guidedFilter(
        alpha: FloatArray,
        guide: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
        epsilon: Float,
    ): FloatArray {
        val squaredGuide = FloatArray(guide.size) { guide[it] * guide[it] }
        val guideAlpha = FloatArray(guide.size) { guide[it] * alpha[it] }

        val meanGuide = boxFilter(guide, width, height, radius)
        val meanAlpha = boxFilter(alpha, width, height, radius)
        val meanGuideSquared = boxFilter(squaredGuide, width, height, radius)
        val meanGuideAlpha = boxFilter(guideAlpha, width, height, radius)

        val a = FloatArray(guide.size)
        val b = FloatArray(guide.size)
        for (index in guide.indices) {
            val variance = meanGuideSquared[index] - meanGuide[index] * meanGuide[index]
            val covariance = meanGuideAlpha[index] - meanGuide[index] * meanAlpha[index]
            val coefficient = covariance / (variance + epsilon)
            a[index] = coefficient
            b[index] = meanAlpha[index] - coefficient * meanGuide[index]
        }

        val meanA = boxFilter(a, width, height, radius)
        val meanB = boxFilter(b, width, height, radius)
        return FloatArray(guide.size) { index ->
            (meanA[index] * guide[index] + meanB[index]).coerceIn(0f, 1f)
        }
    }

    private fun boxFilter(
        source: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
    ): FloatArray {
        val stride = width + 1
        val integral = FloatArray(stride * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0f
            for (x in 0 until width) {
                rowSum += source[y * width + x]
                integral[(y + 1) * stride + (x + 1)] =
                    integral[y * stride + (x + 1)] + rowSum
            }
        }

        val output = FloatArray(width * height)
        for (y in 0 until height) {
            val y0 = max(0, y - radius)
            val y1 = min(height - 1, y + radius)
            for (x in 0 until width) {
                val x0 = max(0, x - radius)
                val x1 = min(width - 1, x + radius)
                val sum = integral[(y1 + 1) * stride + (x1 + 1)] -
                    integral[y0 * stride + (x1 + 1)] -
                    integral[(y1 + 1) * stride + x0] +
                    integral[y0 * stride + x0]
                val area = (x1 - x0 + 1) * (y1 - y0 + 1)
                output[y * width + x] = sum / area
            }
        }
        return output
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        if (edge0 == edge1) return if (value < edge0) 0f else 1f
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun fallback(photo: Bitmap): CompositeResult {
        val output = runCatching {
            BitmapTransforms.centerCrop(photo, OUTPUT_WIDTH, OUTPUT_HEIGHT)
        }.getOrElse {
            Bitmap.createBitmap(OUTPUT_WIDTH, OUTPUT_HEIGHT, Bitmap.Config.ARGB_8888)
        }
        return CompositeResult(output, usedFallback = true)
    }
}
