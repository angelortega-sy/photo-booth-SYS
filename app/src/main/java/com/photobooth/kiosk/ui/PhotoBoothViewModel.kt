package com.photobooth.kiosk.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.BackgroundOption
import com.photobooth.kiosk.data.BackgroundRepository
import com.photobooth.kiosk.data.CollageCustomization
import com.photobooth.kiosk.data.CollageFrameStyle
import com.photobooth.kiosk.data.CollageLayout
import com.photobooth.kiosk.data.EventBadgePreset
import com.photobooth.kiosk.data.PhotoBorderStyle
import com.photobooth.kiosk.photo.BitmapTransforms
import com.photobooth.kiosk.photo.PhotoFrameComposer
import com.photobooth.kiosk.photo.PhotoProcessor
import com.photobooth.kiosk.photo.PreparedCutout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class PhotoBoothUiState(
    val selectedBackgroundId: String = "gala",
    val applyBackgroundToAll: Boolean = true,
    val capturedCount: Int = 0,
    val captureThumbnails: List<Bitmap> = emptyList(),
    val isCapturing: Boolean = false,
    val isProcessing: Boolean = false,
    val isExporting: Boolean = false,
    val segmentedIndices: Set<Int> = emptySet(),
    val wizardStep: Int = 0,
    val customization: CollageCustomization = CollageCustomization(),
    val photoFiles: List<File> = emptyList(),
    val photoBackgroundIds: List<String> = listOf("gala", "gala", "gala"),
    val photoPreviews: List<Bitmap> = emptyList(),
    val stripFile: File? = null,
    val stripPreview: Bitmap? = null,
    val selectedPhotoIndex: Int? = null,
    val usedFallbackIndices: Set<Int> = emptySet(),
    val errorMessage: String? = null,
) {
    val hasResult: Boolean
        get() = capturedCount == PHOTO_COUNT && stripPreview != null && photoPreviews.size == PHOTO_COUNT

    val isSegmentingAny: Boolean
        get() = capturedCount == PHOTO_COUNT && segmentedIndices.size < PHOTO_COUNT

    val resultBitmap: Bitmap?
        get() = selectedPhotoIndex?.let(photoPreviews::getOrNull) ?: stripPreview

    val outputFiles: List<File>
        get() = photoFiles + listOfNotNull(stripFile)

    val usedFallback: Boolean
        get() = usedFallbackIndices.isNotEmpty()

    companion object {
        const val PHOTO_COUNT = 3
    }
}

class PhotoBoothViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BackgroundRepository(application)
    private val processor = PhotoProcessor(application)

    private val sourceFiles = arrayOfNulls<File>(PhotoBoothUiState.PHOTO_COUNT)
    private val rawPreviews = arrayOfNulls<Bitmap>(PhotoBoothUiState.PHOTO_COUNT)
    private val preparedCutouts = arrayOfNulls<PreparedCutout>(PhotoBoothUiState.PHOTO_COUNT)
    private val unframedComposites = arrayOfNulls<Bitmap>(PhotoBoothUiState.PHOTO_COUNT)
    private val unframedCompositeBgIds = arrayOfNulls<String>(PhotoBoothUiState.PHOTO_COUNT)
    private val unframedCompositeSegmented = BooleanArray(PhotoBoothUiState.PHOTO_COUNT)

    private val previewMutex = Mutex()
    private val exportMutex = Mutex()
    private var previewJob: Job? = null

    private var sessionDirectory: File? = null
    private var outputDirectory: File? = null
    private var exportedStateSignature: String? = null
    private var sessionToken = 0

    private val _uiState = MutableStateFlow(PhotoBoothUiState())
    val uiState: StateFlow<PhotoBoothUiState> = _uiState.asStateFlow()

    val backgrounds: List<BackgroundOption> = repository.options + BackgroundOption(
        id = BackgroundRepository.ORIGINAL_ID,
        nameRes = R.string.background_original,
        assetPath = "",
    )

    fun beginCapture() {
        val state = _uiState.value
        if (state.isCapturing || state.isProcessing || state.hasResult ||
            state.capturedCount >= PhotoBoothUiState.PHOTO_COUNT
        ) return
        _uiState.update { it.copy(isCapturing = true, errorMessage = null) }
    }

    fun captureFailed() {
        _uiState.update { it.copy(isCapturing = false) }
    }

    /**
     * Orients the captured photo immediately so the automatic 3-shot sequence can proceed
     * without delay, and runs MediaPipe person segmentation in the background pipeline.
     */
    fun processPhoto(photo: Bitmap, rotationDegrees: Int, mirror: Boolean) {
        val state = _uiState.value
        if (!state.isCapturing || state.capturedCount >= PhotoBoothUiState.PHOTO_COUNT) {
            if (!photo.isRecycled) photo.recycle()
            return
        }

        val token = sessionToken
        val index = state.capturedCount
        viewModelScope.launch {
            val oriented = withContext(Dispatchers.Default) {
                orientAndRelease(photo, rotationDegrees, mirror)
            }
            if (token != sessionToken) {
                oriented?.let { if (!it.isRecycled) it.recycle() }
                return@launch
            }

            if (oriented == null) {
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        errorMessage = getApplication<Application>().getString(R.string.photo_error),
                    )
                }
                return@launch
            }

            // Prepare 540x960 preview and 180x320 capture thumbnail immediately
            val preview540 = withContext(Dispatchers.Default) {
                val cropped = BitmapTransforms.centerCrop(
                    oriented,
                    PhotoProcessor.PREVIEW_WIDTH,
                    PhotoProcessor.PREVIEW_HEIGHT,
                )
                cropped
            }
            val thumb = withContext(Dispatchers.Default) {
                Bitmap.createScaledBitmap(preview540, 180, 320, true)
            }

            if (token != sessionToken) {
                if (!oriented.isRecycled) oriented.recycle()
                if (!preview540.isRecycled) preview540.recycle()
                if (!thumb.isRecycled) thumb.recycle()
                return@launch
            }

            rawPreviews[index]?.let(::recycleLater)
            rawPreviews[index] = preview540

            val newCount = index + 1
            val currentBg = _uiState.value.selectedBackgroundId
            _uiState.update { current ->
                val updatedThumbs = current.captureThumbnails + thumb
                val bgIds = if (newCount == 1) {
                    List(PhotoBoothUiState.PHOTO_COUNT) { currentBg }
                } else {
                    current.photoBackgroundIds
                }
                current.copy(
                    capturedCount = newCount,
                    captureThumbnails = updatedThumbs,
                    photoBackgroundIds = bgIds,
                    isCapturing = false,
                    errorMessage = null,
                )
            }

            // If all 3 photos are captured, build the initial studio preview immediately
            // using whatever cutouts are already finished (Shots 1 & 2) + raw Shot 3.
            if (newCount == PhotoBoothUiState.PHOTO_COUNT) {
                requestPreviewUpdate(token, forceIndex = index)
            }

            // Pipeline: write full-res JPEG to disk + run MediaPipe segmentation in background
            launch(Dispatchers.Default) {
                val sourceFile = File(ensureSessionDirectory(), "capture_${index + 1}.jpg")
                val saved = withContext(Dispatchers.IO) {
                    PhotoFrameComposer.writeJpeg(oriented, sourceFile)
                }
                val cutout = processor.extractCutout(oriented)
                if (!oriented.isRecycled) oriented.recycle()

                if (token != sessionToken || !saved) {
                    sourceFile.delete()
                    cutout.recycle()
                    return@launch
                }

                sourceFiles[index] = sourceFile
                preparedCutouts[index]?.recycle()
                preparedCutouts[index] = cutout
                unframedCompositeSegmented[index] = false

                _uiState.update { current ->
                    val fallbackSet = current.usedFallbackIndices.toMutableSet()
                    if (cutout.usedFallback && current.photoBackgroundIds.getOrNull(index) != BackgroundRepository.ORIGINAL_ID) {
                        fallbackSet += index
                    } else {
                        fallbackSet -= index
                    }
                    current.copy(
                        segmentedIndices = current.segmentedIndices + index,
                        usedFallbackIndices = fallbackSet,
                    )
                }

                if (_uiState.value.capturedCount == PhotoBoothUiState.PHOTO_COUNT) {
                    requestPreviewUpdate(token, forceIndex = index)
                }
            }
        }
    }

    fun setWizardStep(step: Int) {
        val coerced = step.coerceIn(0, 2)
        _uiState.update { it.copy(wizardStep = coerced) }
    }

    fun setApplyBackgroundToAll(applyAll: Boolean) {
        _uiState.update { state ->
            state.copy(
                applyBackgroundToAll = applyAll,
                selectedPhotoIndex = if (applyAll) null else (state.selectedPhotoIndex ?: 0),
            )
        }
    }

    /** Selects a background before capture or updates one/all photos instantly in the Studio Wizard. */
    fun selectBackground(id: String) {
        val state = _uiState.value
        if (state.capturedCount == PhotoBoothUiState.PHOTO_COUNT) {
            changeBackground(id)
            return
        }
        if (state.capturedCount > 0 || state.isCapturing) return
        _uiState.update {
            it.copy(
                selectedBackgroundId = id,
                photoBackgroundIds = List(PhotoBoothUiState.PHOTO_COUNT) { id },
            )
        }
    }

    /** Instantly updates the background of the selected photo (or all 3) in memory. */
    fun changeBackground(id: String) {
        val state = _uiState.value
        if (state.capturedCount < PhotoBoothUiState.PHOTO_COUNT) return

        val currentBgIds = state.photoBackgroundIds
            .ifEmpty { List(PhotoBoothUiState.PHOTO_COUNT) { state.selectedBackgroundId } }
            .toMutableList()

        val targetIndices = if (state.applyBackgroundToAll || state.selectedPhotoIndex == null) {
            (0 until PhotoBoothUiState.PHOTO_COUNT).toList()
        } else {
            listOf(state.selectedPhotoIndex)
        }

        var changed = false
        for (idx in targetIndices) {
            if (currentBgIds[idx] != id) {
                currentBgIds[idx] = id
                changed = true
            }
        }
        if (!changed && state.selectedBackgroundId == id) return

        val fallbackSet = mutableSetOf<Int>()
        for (idx in 0 until PhotoBoothUiState.PHOTO_COUNT) {
            val cutout = preparedCutouts[idx]
            if (cutout?.usedFallback == true && currentBgIds[idx] != BackgroundRepository.ORIGINAL_ID) {
                fallbackSet += idx
            }
        }

        exportedStateSignature = null
        _uiState.update {
            it.copy(
                selectedBackgroundId = id,
                photoBackgroundIds = currentBgIds,
                usedFallbackIndices = fallbackSet,
                errorMessage = null,
            )
        }
        requestPreviewUpdate(sessionToken)
    }

    fun selectCollageLayout(layout: CollageLayout) {
        if (_uiState.value.customization.layout == layout) return
        exportedStateSignature = null
        _uiState.update { state ->
            state.copy(
                customization = state.customization.copy(layout = layout),
                selectedPhotoIndex = null,
            )
        }
        requestPreviewUpdate(sessionToken)
    }

    fun selectFrameStyle(frameStyle: CollageFrameStyle) {
        if (_uiState.value.customization.frameStyle == frameStyle) return
        exportedStateSignature = null
        _uiState.update { state ->
            state.copy(customization = state.customization.copy(frameStyle = frameStyle))
        }
        requestPreviewUpdate(sessionToken)
    }

    fun selectPhotoBorderStyle(borderStyle: PhotoBorderStyle) {
        if (_uiState.value.customization.photoBorderStyle == borderStyle) return
        exportedStateSignature = null
        _uiState.update { state ->
            state.copy(customization = state.customization.copy(photoBorderStyle = borderStyle))
        }
        requestPreviewUpdate(sessionToken)
    }

    fun selectBadgePreset(badgePreset: EventBadgePreset) {
        if (_uiState.value.customization.badgePreset == badgePreset) return
        exportedStateSignature = null
        _uiState.update { state ->
            state.copy(customization = state.customization.copy(badgePreset = badgePreset))
        }
        requestPreviewUpdate(sessionToken)
    }

    fun toggleDateStamp(enabled: Boolean) {
        if (_uiState.value.customization.showDateStamp == enabled) return
        exportedStateSignature = null
        _uiState.update { state ->
            state.copy(customization = state.customization.copy(showDateStamp = enabled))
        }
        requestPreviewUpdate(sessionToken)
    }

    fun retryComposition() {
        val state = _uiState.value
        if (state.capturedCount != PhotoBoothUiState.PHOTO_COUNT) return
        requestPreviewUpdate(sessionToken, forceIndex = -1)
    }

    private fun requestPreviewUpdate(token: Int, forceIndex: Int? = null) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.Default) {
            previewMutex.withLock {
                if (token != sessionToken) return@withLock
                val state = _uiState.value
                if (state.capturedCount < PhotoBoothUiState.PHOTO_COUNT) return@withLock

                val bgIds = state.photoBackgroundIds
                val customization = state.customization

                // 1. Update cached 540x960 unframed composites only for changed slots
                val currentUnframed = mutableListOf<Bitmap>()
                for (i in 0 until PhotoBoothUiState.PHOTO_COUNT) {
                    val targetBgId = bgIds.getOrElse(i) { state.selectedBackgroundId }
                    val cutout = preparedCutouts[i]
                    val hasCutout = cutout != null
                    val cached = unframedComposites[i]

                    val needsRecomposite = cached == null ||
                        cached.isRecycled ||
                        unframedCompositeBgIds[i] != targetBgId ||
                        unframedCompositeSegmented[i] != hasCutout ||
                        forceIndex == i ||
                        forceIndex == -1

                    if (needsRecomposite) {
                        val previewBg = if (targetBgId == BackgroundRepository.ORIGINAL_ID) {
                            null
                        } else {
                            repository.loadPreview(targetBgId)
                        }
                        val nextBitmap = when {
                            cutout != null -> processor.compositePreview(cutout, previewBg).bitmap
                            rawPreviews[i] != null -> rawPreviews[i]!!.copy(Bitmap.Config.ARGB_8888, false)
                            else -> Bitmap.createBitmap(
                                PhotoProcessor.PREVIEW_WIDTH,
                                PhotoProcessor.PREVIEW_HEIGHT,
                                Bitmap.Config.ARGB_8888,
                            )
                        }
                        unframedComposites[i]?.let { old -> if (old !== nextBitmap) recycleLater(old) }
                        unframedComposites[i] = nextBitmap
                        unframedCompositeBgIds[i] = targetBgId
                        unframedCompositeSegmented[i] = hasCutout
                    }
                    currentUnframed += unframedComposites[i]!!
                }

                if (token != sessionToken) return@withLock

                // 2. Render 540x960 framed individual previews and the live collage preview
                val newPhotoPreviews = currentUnframed.map { unframed ->
                    PhotoFrameComposer.framePhoto(
                        photo = unframed,
                        customization = customization,
                        previewScale = true,
                    )
                }
                val newCollagePreview = PhotoFrameComposer.composeCollage(
                    photos = currentUnframed,
                    customization = customization,
                    previewScale = true,
                )

                if (token != sessionToken) {
                    newPhotoPreviews.forEach { it.recycle() }
                    newCollagePreview.recycle()
                    return@withLock
                }

                val oldPreviews = _uiState.value.photoPreviews + listOfNotNull(_uiState.value.stripPreview)
                _uiState.update { current ->
                    current.copy(
                        isProcessing = false,
                        photoPreviews = newPhotoPreviews,
                        stripPreview = newCollagePreview,
                        errorMessage = null,
                    )
                }
                oldPreviews.forEach { old ->
                    if (old !in newPhotoPreviews && old !== newCollagePreview) {
                        recycleLater(old)
                    }
                }
            }
        }
    }

    /**
     * Exports the 3 full-resolution individual framed photos (1080x1920) and 1 full-resolution
     * collage JPEG on demand when saving or sharing. Reuses the cached alpha mask from
     * [PreparedCutout] so MediaPipe never runs again.
     */
    suspend fun ensureExportedFiles(): List<File>? = withContext(Dispatchers.Default) {
        exportMutex.withLock {
            val token = sessionToken
            val state = _uiState.value
            if (state.capturedCount < PhotoBoothUiState.PHOTO_COUNT) return@withLock null

            val signature = buildString {
                append(state.photoBackgroundIds.joinToString(","))
                append("|")
                append(state.customization.layout.id)
                append("|")
                append(state.customization.frameStyle.id)
                append("|")
                append(state.customization.photoBorderStyle.id)
                append("|")
                append(state.customization.badgePreset.id)
                append("|")
                append(state.customization.showDateStamp)
            }

            val existingFiles = state.outputFiles
            if (signature == exportedStateSignature &&
                existingFiles.size == PhotoBoothUiState.PHOTO_COUNT + 1 &&
                existingFiles.all { it.isFile }
            ) {
                return@withLock existingFiles
            }

            _uiState.update { it.copy(isExporting = true) }
            try {
                // Wait briefly if the last shot's background file write is still finishing
                var waitAttempts = 0
                while (waitAttempts < 30 && sourceFiles.any { it == null }) {
                    delay(80L)
                    waitAttempts++
                }

                val currentSessionDir = ensureSessionDirectory()
                val newOutputDir = File(currentSessionDir, "render_${UUID.randomUUID()}")
                if (!newOutputDir.mkdirs()) return@withLock null

                val bgIds = state.photoBackgroundIds
                val customization = state.customization
                val fullComposites = mutableListOf<Bitmap>()
                val newPhotoFiles = List(PhotoBoothUiState.PHOTO_COUNT) { index ->
                    File(newOutputDir, "expo_syscom_2027_${index + 1}.jpg")
                }
                val newStripFile = File(newOutputDir, "expo_syscom_2027_collage.jpg")

                try {
                    for (index in 0 until PhotoBoothUiState.PHOTO_COUNT) {
                        if (token != sessionToken) throw SessionExpiredException()
                        val sourceBitmap = sourceFiles[index]?.let { file ->
                            BitmapFactory.decodeFile(file.absolutePath)
                        } ?: rawPreviews[index]?.let { preview ->
                            Bitmap.createScaledBitmap(
                                preview,
                                PhotoProcessor.OUTPUT_WIDTH,
                                PhotoProcessor.OUTPUT_HEIGHT,
                                true,
                            )
                        } ?: error("Missing source image for shot $index")

                        val bgId = bgIds.getOrElse(index) { state.selectedBackgroundId }
                        val fullBg = if (bgId == BackgroundRepository.ORIGINAL_ID) {
                            null
                        } else {
                            withContext(Dispatchers.IO) { repository.loadFull(bgId) }
                        }

                        val composite = try {
                            processor.compositeFull(sourceBitmap, preparedCutouts[index], fullBg).bitmap
                        } finally {
                            if (!sourceBitmap.isRecycled) sourceBitmap.recycle()
                        }
                        fullComposites += composite

                        val framed = PhotoFrameComposer.framePhoto(
                            photo = composite,
                            customization = customization,
                            previewScale = false,
                        )
                        val written = try {
                            withContext(Dispatchers.IO) {
                                PhotoFrameComposer.writeJpeg(framed, newPhotoFiles[index])
                            }
                        } finally {
                            if (!framed.isRecycled) framed.recycle()
                        }
                        if (!written) error("Could not write framed photo $index")
                    }

                    if (token != sessionToken) throw SessionExpiredException()
                    val fullCollage = PhotoFrameComposer.composeCollage(
                        photos = fullComposites,
                        customization = customization,
                        previewScale = false,
                    )
                    val collageWritten = try {
                        withContext(Dispatchers.IO) {
                            PhotoFrameComposer.writeJpeg(fullCollage, newStripFile)
                        }
                    } finally {
                        if (!fullCollage.isRecycled) fullCollage.recycle()
                    }
                    if (!collageWritten) error("Could not write collage file")

                    val oldOutputDir = outputDirectory
                    outputDirectory = newOutputDir
                    exportedStateSignature = signature
                    _uiState.update {
                        it.copy(
                            photoFiles = newPhotoFiles,
                            stripFile = newStripFile,
                            isExporting = false,
                        )
                    }
                    oldOutputDir?.takeIf { it != newOutputDir }?.let(::deleteLater)
                    return@withLock newPhotoFiles + newStripFile
                } finally {
                    fullComposites.forEach { if (!it.isRecycled) it.recycle() }
                }
            } catch (_: Throwable) {
                _uiState.update { it.copy(isExporting = false) }
                return@withLock null
            }
        }
    }

    fun selectResult(index: Int?) {
        if (index != null && index !in 0 until PhotoBoothUiState.PHOTO_COUNT) return
        _uiState.update { state ->
            state.copy(
                selectedPhotoIndex = index,
                applyBackgroundToAll = index == null,
                selectedBackgroundId = index?.let { state.photoBackgroundIds.getOrNull(it) }
                    ?: state.selectedBackgroundId,
            )
        }
    }

    fun clearResult() {
        clearSession(resetBackground = false)
    }

    /** Ends the guest session and returns a clean state for the welcome screen. */
    fun endSession() {
        clearSession(resetBackground = true)
    }

    private fun clearSession(resetBackground: Boolean) {
        sessionToken += 1
        previewJob?.cancel()
        val state = _uiState.value
        val oldBitmaps = state.photoPreviews +
            listOfNotNull(state.stripPreview) +
            state.captureThumbnails +
            rawPreviews.filterNotNull() +
            unframedComposites.filterNotNull()

        val oldCutouts = preparedCutouts.filterNotNull()
        val oldDirectory = sessionDirectory

        for (i in 0 until PhotoBoothUiState.PHOTO_COUNT) {
            sourceFiles[i] = null
            rawPreviews[i] = null
            preparedCutouts[i] = null
            unframedComposites[i] = null
            unframedCompositeBgIds[i] = null
            unframedCompositeSegmented[i] = false
        }
        sessionDirectory = null
        outputDirectory = null
        exportedStateSignature = null

        val initialBg = if (resetBackground) "gala" else state.selectedBackgroundId
        _uiState.value = PhotoBoothUiState(
            selectedBackgroundId = initialBg,
            photoBackgroundIds = List(PhotoBoothUiState.PHOTO_COUNT) { initialBg },
        )

        oldBitmaps.forEach(::recycleLater)
        viewModelScope.launch {
            delay(750L)
            oldCutouts.forEach { it.recycle() }
        }
        oldDirectory?.let(::deleteLater)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun loadThumbnail(id: String): Bitmap? = repository.loadThumbnail(id)

    private fun ensureSessionDirectory(): File {
        sessionDirectory?.let { return it }
        val directory = File(
            getApplication<Application>().cacheDir,
            "photo-booth-session-${UUID.randomUUID()}",
        )
        check(directory.mkdirs()) { "Could not create photo session storage" }
        sessionDirectory = directory
        return directory
    }

    private fun orientAndRelease(photo: Bitmap, rotationDegrees: Int, mirror: Boolean): Bitmap? {
        var rotated: Bitmap? = null
        try {
            rotated = BitmapTransforms.rotateDegrees(photo, rotationDegrees)
            if (rotated !== photo && !photo.isRecycled) photo.recycle()
            if (!mirror) return rotated.also { rotated = null }

            val mirrored = BitmapTransforms.mirrorHorizontally(rotated)
            if (mirrored !== rotated && !rotated.isRecycled) rotated.recycle()
            rotated = null
            return mirrored
        } catch (_: Throwable) {
            return null
        } finally {
            if (!photo.isRecycled) photo.recycle()
            rotated?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    /** Delay recycling until Compose has stopped drawing the old preview bitmap. */
    private fun recycleLater(bitmap: Bitmap) {
        viewModelScope.launch {
            delay(750L)
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun deleteLater(directory: File) {
        viewModelScope.launch(Dispatchers.IO) {
            delay(1_500L)
            directory.deleteRecursively()
        }
    }

    override fun onCleared() {
        super.onCleared()
        previewJob?.cancel()
        processor.close()
        repository.clear()
        val state = _uiState.value
        (state.photoPreviews + listOfNotNull(state.stripPreview) + state.captureThumbnails +
            rawPreviews.filterNotNull() + unframedComposites.filterNotNull())
            .forEach { if (!it.isRecycled) it.recycle() }
        preparedCutouts.filterNotNull().forEach { it.recycle() }
        sessionDirectory?.deleteRecursively()
    }

    private class SessionExpiredException : RuntimeException()
}
