package com.photobooth.kiosk.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.AiSettings
import com.photobooth.kiosk.data.AiSettingsStore
import com.photobooth.kiosk.data.AiStatus
import com.photobooth.kiosk.data.AiStyle
import com.photobooth.kiosk.data.AiStyles
import com.photobooth.kiosk.data.BackgroundOption
import com.photobooth.kiosk.data.BackgroundRepository
import com.photobooth.kiosk.data.CameraSettingsStore
import com.photobooth.kiosk.data.CollageCustomization
import com.photobooth.kiosk.data.CollageFrameStyle
import com.photobooth.kiosk.data.CollageLayout
import com.photobooth.kiosk.data.EventBadgePreset
import com.photobooth.kiosk.data.EventBranding
import com.photobooth.kiosk.data.EventBrandingStore
import com.photobooth.kiosk.data.PhotoBorderStyle
import com.photobooth.kiosk.delivery.DeliverySession
import com.photobooth.kiosk.delivery.LocalDeliveryServer
import com.photobooth.kiosk.network.AiBackendClient
import com.photobooth.kiosk.network.AiBackendException
import com.photobooth.kiosk.network.AiGeneratePhoto
import com.photobooth.kiosk.photo.BitmapTransforms
import com.photobooth.kiosk.photo.PhotoFrameComposer
import com.photobooth.kiosk.photo.PhotoProcessor
import com.photobooth.kiosk.photo.PreparedCutout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
    val cameraRotationDegrees: Int = 0,
    val aiStyles: List<AiStyle> = AiStyles.ALL,
    val selectedAiStyleId: String = "ghibli",
    val aiStatus: AiStatus = AiStatus.IDLE,
    val aiUploadedCount: Int = 0,
    val aiProgress: Int = 0,
    val aiCost: Double? = null,
    val aiError: String? = null,
    val hasAiResult: Boolean = false,
    val backgrounds: List<BackgroundOption> = emptyList(),
    val adminBackgrounds: List<BackgroundOption> = emptyList(),
    val backgroundRevision: Int = 0,
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
    private val cameraSettings = CameraSettingsStore(application)
    private val brandingStore = EventBrandingStore(application)
    private val aiSettings = AiSettingsStore(application)
    private val aiClient = AiBackendClient()
    private val aiFullBitmaps = arrayOfNulls<Bitmap>(PhotoBoothUiState.PHOTO_COUNT)
    private var aiJob: Job? = null

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
    private var deliveryServer: LocalDeliveryServer? = null

    private val _uiState = MutableStateFlow(
        PhotoBoothUiState(
            cameraRotationDegrees = cameraSettings.rotationDegrees,
            customization = CollageCustomization(
                eventBranding = brandingStore.load(),
                badgePreset = if (brandingStore.hasSaved()) {
                    EventBadgePreset.CUSTOM
                } else {
                    EventBadgePreset.EXPO_2027
                },
            ),
            backgrounds = repository.visibleOptions,
            adminBackgrounds = repository.allOptions,
        ),
    )
    val uiState: StateFlow<PhotoBoothUiState> = _uiState.asStateFlow()

    init {
        refreshBackgrounds()
    }

    val allBackgrounds: List<BackgroundOption>
        get() = repository.allOptions

    fun refreshBackgrounds() {
        val visible = repository.visibleOptions
        val visibleIds = visible.mapTo(mutableSetOf()) { it.id }
        val state = _uiState.value
        val selected = state.selectedBackgroundId
            .takeIf { it in visibleIds }
            ?: visible.firstOrNull()?.id
            ?: "gala"
        val photoIds = state.photoBackgroundIds.map {
            if (it in visibleIds) it else selected
        }
        _uiState.update {
            it.copy(
                backgrounds = visible,
                adminBackgrounds = repository.allOptions,
                selectedBackgroundId = selected,
                photoBackgroundIds = photoIds,
                backgroundRevision = it.backgroundRevision + 1,
            )
        }
    }

    private val backgroundMutex = Mutex()

    fun importBackground(uri: Uri, displayName: String?, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                backgroundMutex.withLock {
                    repository.importFromUri(uri, displayName)
                }
            }
            result.onSuccess {
                refreshBackgrounds()
                onResult(null)
            }.onFailure {
                onResult(it.message)
            }
        }
    }

    fun setBackgroundEnabled(id: String, enabled: Boolean) {
        if (!repository.setEnabled(id, enabled)) return
        refreshBackgrounds()
        if (_uiState.value.capturedCount == PhotoBoothUiState.PHOTO_COUNT) {
            requestPreviewUpdate(sessionToken)
        }
    }

    fun deleteBackground(id: String) {
        if (!repository.deleteCustom(id)) return
        refreshBackgrounds()
        if (_uiState.value.capturedCount == PhotoBoothUiState.PHOTO_COUNT) {
            requestPreviewUpdate(sessionToken)
        }
    }

    fun renameBackground(id: String, name: String) {
        if (!repository.renameCustom(id, name)) return
        refreshBackgrounds()
    }

    suspend fun startDelivery(): DeliverySession? {
        val files = ensureExportedFiles() ?: return null
        if (files.size != 4) return null
        val title = _uiState.value.customization.eventBranding.badgeHeadline
        deliveryServer?.stop()
        val server = LocalDeliveryServer()
        val session = server.start(files, title)
        if (session == null) {
            server.stop()
            return null
        }
        deliveryServer = server
        return session
    }

    fun stopDelivery() {
        deliveryServer?.stop()
        deliveryServer = null
    }

    fun updateEventBranding(branding: EventBranding) {
        brandingStore.save(branding)
        exportedStateSignature = null
        _uiState.update {
            it.copy(
                customization = it.customization.copy(
                    eventBranding = branding,
                    badgePreset = EventBadgePreset.CUSTOM,
                ),
            )
        }
        requestPreviewUpdate(sessionToken)
    }

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
    fun setCameraRotation(degrees: Int) {
        cameraSettings.rotationDegrees = degrees
        _uiState.update { it.copy(cameraRotationDegrees = cameraSettings.rotationDegrees) }
    }

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
        // Backgrounds are locked once an AI result exists: recomposing would
        // silently drop the paid styling until the next paid generation.
        if (state.hasAiResult ||
            state.aiStatus == AiStatus.UPLOADING ||
            state.aiStatus == AiStatus.GENERATING
        ) return

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
        val customization = _uiState.value.customization
        if (customization.badgePreset == badgePreset) return
        exportedStateSignature = null
        _uiState.update { state ->
            val branding = if (badgePreset == EventBadgePreset.CUSTOM) {
                brandingStore.load()
            } else {
                state.customization.eventBranding.withBadge(badgePreset)
            }
            state.copy(
                customization = state.customization.copy(
                    badgePreset = badgePreset,
                    eventBranding = branding,
                ),
            )
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

    fun loadAiSettings(): AiSettings = aiSettings.load()

    fun saveAiSettings(settings: AiSettings) {
        aiSettings.save(settings)
    }

    fun selectAiStyle(id: String) {
        if (AiStyles.ALL.none { it.id == id }) return
        val status = _uiState.value.aiStatus
        if (status == AiStatus.UPLOADING || status == AiStatus.GENERATING) return
        _uiState.update { it.copy(selectedAiStyleId = id) }
    }

    fun cancelAi() {
        aiJob?.cancel()
    }

    /**
     * Uploads the 3 already-composed photos to the configured backend, polls
     * the styling job, downloads the 3 styled 1080x1920 images and rebuilds
     * the strip with the existing frame customization.
     */
    fun generateWithAi() {
        val snapshot = _uiState.value
        if (snapshot.capturedCount != PhotoBoothUiState.PHOTO_COUNT) return
        if (snapshot.aiStatus == AiStatus.UPLOADING || snapshot.aiStatus == AiStatus.GENERATING) return
        if (snapshot.isExporting || snapshot.isProcessing) return
        val settings = aiSettings.load()
        if (settings.baseUrl.isBlank()) {
            _uiState.update {
                it.copy(
                    aiStatus = AiStatus.ERROR,
                    aiError = getApplication<Application>().getString(R.string.ai_error_no_server),
                )
            }
            return
        }
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            val token = sessionToken
            try {
                _uiState.update {
                    it.copy(
                        aiStatus = AiStatus.UPLOADING,
                        aiUploadedCount = 0,
                        aiProgress = 0,
                        aiError = null,
                        aiCost = null,
                    )
                }
                val baseUrl = settings.baseUrl
                val kioskToken = settings.kioskToken
                val modelId = settings.modelId.ifBlank { AiSettings.DEFAULT_MODEL_ID }

                val bgIds = _uiState.value.photoBackgroundIds
                val styleId = _uiState.value.selectedAiStyleId
                val stylePrompt = AiStyles.byId(styleId).prompt
                val eventId = _uiState.value.customization.eventBranding.brand.ifBlank { "SYSCOM" }

                val inputs = withContext(Dispatchers.IO) { buildAiInputs(bgIds) }
                if (token != sessionToken) throw SessionExpiredException()

                val sessionId = withContext(Dispatchers.IO) {
                    aiClient.createSession(baseUrl, kioskToken)
                }
                for (index in inputs.indices) {
                    coroutineContext.ensureActive()
                    withContext(Dispatchers.IO) {
                        aiClient.uploadPhoto(baseUrl, kioskToken, sessionId, index, inputs[index])
                    }
                    if (token != sessionToken) throw SessionExpiredException()
                    _uiState.update { it.copy(aiUploadedCount = index + 1) }
                }

                _uiState.update { it.copy(aiStatus = AiStatus.GENERATING) }
                val generatePhotos = bgIds.mapIndexed { index, backgroundId ->
                    AiGeneratePhoto(
                        index = index,
                        backgroundId = backgroundId,
                        backgroundMode = if (backgroundId == BackgroundRepository.ORIGINAL_ID) {
                            "original"
                        } else {
                            "asset"
                        },
                    )
                }
                val jobId = withContext(Dispatchers.IO) {
                    aiClient.requestGenerate(
                        baseUrl,
                        kioskToken,
                        sessionId,
                        styleId,
                        stylePrompt,
                        modelId,
                        eventId,
                        generatePhotos,
                    )
                }
                val job = withContext(Dispatchers.IO) {
                    aiClient.awaitJob(baseUrl, kioskToken, jobId) { progress ->
                        _uiState.update { it.copy(aiProgress = progress) }
                    }
                }
                coroutineContext.ensureActive()
                if (token != sessionToken) throw SessionExpiredException()

                val customization = _uiState.value.customization
                val styledDir = File(ensureSessionDirectory(), "ai_${UUID.randomUUID()}")
                check(styledDir.mkdirs()) { "Could not create AI session storage" }
                val decoded = withContext(Dispatchers.IO) {
                    job.results.sortedBy { it.index }
                        .take(PhotoBoothUiState.PHOTO_COUNT)
                        .map { image ->
                            val target = File(styledDir, "ai_styled_${image.index + 1}.jpg")
                            aiClient.downloadToFile(baseUrl, image.url, kioskToken, target)
                            BitmapFactory.decodeFile(target.absolutePath)
                                ?: throw AiBackendException("Styled photo ${image.index + 1} is not a valid image")
                        }
                }
                if (token != sessionToken) {
                    decoded.forEach { if (!it.isRecycled) it.recycle() }
                    throw SessionExpiredException()
                }
                val normalized = decoded.map { bitmap ->
                    val cropped = BitmapTransforms.centerCrop(
                        bitmap,
                        PhotoProcessor.OUTPUT_WIDTH,
                        PhotoProcessor.OUTPUT_HEIGHT,
                    )
                    if (cropped !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                    cropped
                }
                for (index in normalized.indices) {
                    aiFullBitmaps[index]?.let { old -> if (!old.isRecycled) old.recycle() }
                    aiFullBitmaps[index] = normalized[index]
                }

                val framedPreviews = normalized.map { bitmap ->
                    PhotoFrameComposer.framePhoto(bitmap, customization, previewScale = true)
                }
                val collagePreview = PhotoFrameComposer.composeCollage(
                    normalized,
                    customization,
                    previewScale = true,
                )
                val framedFull = normalized.map { bitmap ->
                    PhotoFrameComposer.framePhoto(bitmap, customization, previewScale = false)
                }
                try {
                    val filesWritten = withContext(Dispatchers.IO) {
                        val renderDir = File(ensureSessionDirectory(), "render_${UUID.randomUUID()}")
                        check(renderDir.mkdirs()) { "Could not create AI render storage" }
                        val photoFiles = List(PhotoBoothUiState.PHOTO_COUNT) { index ->
                            File(renderDir, "cabina_ia_${index + 1}.jpg")
                        }
                        val stripFile = File(renderDir, "cabina_ia_tira.jpg")
                        for (index in normalized.indices) {
                            if (!PhotoFrameComposer.writeJpeg(framedFull[index], photoFiles[index])) {
                                error("Could not write styled photo $index")
                            }
                        }
                        val collageFull = PhotoFrameComposer.composeCollage(
                            normalized,
                            customization,
                            previewScale = false,
                        )
                        try {
                            if (!PhotoFrameComposer.writeJpeg(collageFull, stripFile)) {
                                error("Could not write styled collage")
                            }
                        } finally {
                            if (!collageFull.isRecycled) collageFull.recycle()
                        }
                        photoFiles + stripFile
                    }
                    if (token != sessionToken) throw SessionExpiredException()

                    val oldPreviews = _uiState.value.photoPreviews +
                        listOfNotNull(_uiState.value.stripPreview)
                    val oldOutputDir = outputDirectory
                    outputDirectory = filesWritten.last().parentFile
                    _uiState.update { current ->
                        current.copy(
                            photoPreviews = framedPreviews,
                            stripPreview = collagePreview,
                            photoFiles = filesWritten.take(PhotoBoothUiState.PHOTO_COUNT),
                            stripFile = filesWritten.last(),
                            aiStatus = AiStatus.READY,
                            hasAiResult = true,
                            aiCost = job.cost,
                            aiProgress = 100,
                            errorMessage = null,
                        )
                    }
                    exportedStateSignature = stateSignature(_uiState.value)
                    oldPreviews.forEach { old ->
                        if (old !in framedPreviews && old !== collagePreview) recycleLater(old)
                    }
                    oldOutputDir?.let(::deleteLater)
                } finally {
                    framedFull.forEach { if (!it.isRecycled) it.recycle() }
                }
            } catch (error: CancellationException) {
                _uiState.update { current ->
                    current.copy(
                        aiStatus = if (current.hasAiResult) AiStatus.READY else AiStatus.IDLE,
                    )
                }
                throw error
            } catch (_: SessionExpiredException) {
                // Session was already reset; nothing to report.
            } catch (error: AiBackendException) {
                _uiState.update {
                    it.copy(aiStatus = AiStatus.ERROR, aiError = aiUserMessage(error))
                }
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        aiStatus = AiStatus.ERROR,
                        aiError = getApplication<Application>().getString(R.string.ai_error_network),
                    )
                }
            }
        }
    }

    private fun aiUserMessage(error: AiBackendException): String {
        val app = getApplication<Application>()
        return if (error.httpCode != null) {
            app.getString(R.string.ai_error_server)
        } else {
            app.getString(R.string.ai_error_network)
        }
    }

    /** Full-resolution unframed composites (person + chosen background) used as AI input. */
    private fun buildAiInputs(bgIds: List<String>): List<File> {
        val directory = ensureSessionDirectory()
        return List(PhotoBoothUiState.PHOTO_COUNT) { index ->
            val source = sourceFiles[index]?.let { file ->
                BitmapFactory.decodeFile(file.absolutePath)
            } ?: rawPreviews[index]?.let { preview ->
                Bitmap.createScaledBitmap(
                    preview,
                    PhotoProcessor.OUTPUT_WIDTH,
                    PhotoProcessor.OUTPUT_HEIGHT,
                    true,
                )
            } ?: throw AiBackendException("Missing source image for shot $index")
            try {
                val bgId = bgIds.getOrElse(index) { _uiState.value.selectedBackgroundId }
                val fullBg = if (bgId == BackgroundRepository.ORIGINAL_ID) {
                    null
                } else {
                    repository.loadFull(bgId)
                }
                val composite = processor.compositeFull(source, preparedCutouts[index], fullBg).bitmap
                try {
                    val target = File(directory, "ai_input_${index + 1}.jpg")
                    if (!PhotoFrameComposer.writeJpeg(composite, target)) {
                        throw AiBackendException("Could not prepare photo ${index + 1} for upload")
                    }
                    target
                } finally {
                    if (!composite.isRecycled) composite.recycle()
                }
            } finally {
                if (!source.isRecycled) source.recycle()
            }
        }
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

                // AI-styled photos replace the local composites: only reframe
                // them here so layout/frame/badge changes stay free.
                if (state.hasAiResult) {
                    val styled = (0 until PhotoBoothUiState.PHOTO_COUNT).map { index ->
                        aiFullBitmaps[index]?.takeUnless { it.isRecycled }
                    }
                    if (styled.all { it != null }) {
                        val framed = styled.map { bitmap ->
                            PhotoFrameComposer.framePhoto(bitmap!!, customization, previewScale = true)
                        }
                        val collage = PhotoFrameComposer.composeCollage(
                            styled.filterNotNull(),
                            customization,
                            previewScale = true,
                        )
                        if (token != sessionToken) {
                            framed.forEach { it.recycle() }
                            collage.recycle()
                            return@withLock
                        }
                        val oldPreviews = _uiState.value.photoPreviews +
                            listOfNotNull(_uiState.value.stripPreview)
                        _uiState.update { current ->
                            current.copy(
                                isProcessing = false,
                                photoPreviews = framed,
                                stripPreview = collage,
                                errorMessage = null,
                            )
                        }
                        oldPreviews.forEach { old ->
                            if (old !in framed && old !== collage) recycleLater(old)
                        }
                        return@withLock
                    }
                }

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
     * Cache key for the exported JPEGs. Includes the AI state so cached files
     * are only reused when they match the current (possibly AI-styled) result.
     */
    private fun stateSignature(state: PhotoBoothUiState): String = buildString {
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
        append("|ai=")
        append(state.hasAiResult)
        append("|")
        append(state.selectedAiStyleId)
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

            val signature = stateSignature(state)

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
                        // AI-styled photos are already composed: reuse them directly
                        // so customization-only changes never trigger a new generation.
                        val aiBitmap = if (state.hasAiResult) {
                            aiFullBitmaps[index]?.takeUnless { it.isRecycled }
                        } else {
                            null
                        }
                        val composite = if (aiBitmap != null) {
                            aiBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                ?: error("Missing styled photo for shot $index")
                        } else {
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

                            try {
                                processor.compositeFull(sourceBitmap, preparedCutouts[index], fullBg).bitmap
                            } finally {
                                if (!sourceBitmap.isRecycled) sourceBitmap.recycle()
                            }
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
        stopDelivery()
        previewJob?.cancel()
        aiJob?.cancel()
        val state = _uiState.value
        val oldBitmaps = state.photoPreviews +
            listOfNotNull(state.stripPreview) +
            state.captureThumbnails +
            rawPreviews.filterNotNull() +
            unframedComposites.filterNotNull() +
            aiFullBitmaps.filterNotNull()

        val oldCutouts = preparedCutouts.filterNotNull()
        val oldDirectory = sessionDirectory

        for (i in 0 until PhotoBoothUiState.PHOTO_COUNT) {
            sourceFiles[i] = null
            rawPreviews[i] = null
            preparedCutouts[i] = null
            unframedComposites[i] = null
            unframedCompositeBgIds[i] = null
            unframedCompositeSegmented[i] = false
            aiFullBitmaps[i] = null
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
        stopDelivery()
        previewJob?.cancel()
        aiJob?.cancel()
        processor.close()
        repository.clear()
        val state = _uiState.value
        (state.photoPreviews + listOfNotNull(state.stripPreview) + state.captureThumbnails +
            rawPreviews.filterNotNull() + unframedComposites.filterNotNull() +
            aiFullBitmaps.filterNotNull())
            .forEach { if (!it.isRecycled) it.recycle() }
        preparedCutouts.filterNotNull().forEach { it.recycle() }
        sessionDirectory?.deleteRecursively()
    }

    private class SessionExpiredException : RuntimeException()
}
