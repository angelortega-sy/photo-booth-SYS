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
import com.photobooth.kiosk.data.DeviceIdentity
import com.photobooth.kiosk.data.DeviceIdentityStore
import com.photobooth.kiosk.data.driveFolderBase
import com.photobooth.kiosk.data.CameraAdjustments
import com.photobooth.kiosk.data.CameraSettingsStore
import com.photobooth.kiosk.data.GestureSettingsStore
import com.photobooth.kiosk.data.StyleSettingsStore
import com.photobooth.kiosk.data.CollageCustomization
import com.photobooth.kiosk.data.CollageFrameStyle
import com.photobooth.kiosk.data.CollageLayout
import com.photobooth.kiosk.data.EventBadgePreset
import com.photobooth.kiosk.data.EventBranding
import com.photobooth.kiosk.data.EventProfile
import com.photobooth.kiosk.data.EventProfileStore
import com.photobooth.kiosk.data.EventSync
import com.photobooth.kiosk.data.FrameDecoration
import com.photobooth.kiosk.data.PhotoBorderStyle
import com.photobooth.kiosk.data.PhotoSettingsStore
import com.photobooth.kiosk.data.ProviderLogoStore
import com.photobooth.kiosk.delivery.DeliverySession
import com.photobooth.kiosk.delivery.LocalDeliveryServer
import com.photobooth.kiosk.network.AiBackendClient
import com.photobooth.kiosk.network.AiBackendException
import com.photobooth.kiosk.network.BoothModel
import com.photobooth.kiosk.network.BoothStyle
import com.photobooth.kiosk.network.ServerStatusProbe
import com.photobooth.kiosk.network.UsageMonth
import com.photobooth.kiosk.network.UsageLimits
import com.photobooth.kiosk.network.UsagePage
import com.photobooth.kiosk.network.UsageSummary
import com.photobooth.kiosk.security.DeviceKeyStore
import com.photobooth.kiosk.photo.BitmapTransforms
import com.photobooth.kiosk.photo.PhotoArchive
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
import java.util.Locale
import java.util.UUID

data class PhotoBoothUiState(
    val selectedBackgroundId: String = BackgroundRepository.ORIGINAL_ID,
    val applyBackgroundToAll: Boolean = true,
    val capturedCount: Int = 0,
    /** Slot being retaken by itself; when set the next capture replaces it. */
    val retakeIndex: Int? = null,
    val captureThumbnails: List<Bitmap> = emptyList(),
    /** Raw shots without any frame, safe to show before the design step. */
    val rawPhotoPreviews: List<Bitmap> = emptyList(),
    val isCapturing: Boolean = false,
    val isProcessing: Boolean = false,
    val isExporting: Boolean = false,
    val segmentedIndices: Set<Int> = emptySet(),
    val wizardStep: Int = 0,
    val customization: CollageCustomization = CollageCustomization(),
    val photoFiles: List<File> = emptyList(),
    val photoBackgroundIds: List<String> = List(PHOTO_COUNT) { BackgroundRepository.ORIGINAL_ID },
    val photoPreviews: List<Bitmap> = emptyList(),
    val stripFile: File? = null,
    val stripPreview: Bitmap? = null,
    val selectedPhotoIndex: Int? = null,
    val usedFallbackIndices: Set<Int> = emptySet(),
    val palmCaptureEnabled: Boolean = true,
    val cameraAdjustments: CameraAdjustments = CameraAdjustments(),
    val aiStyles: List<AiStyle> = AiStyles.ALL,
    val serverStyles: List<BoothStyle> = emptyList(),
    val serverModels: List<BoothModel> = emptyList(),
    val providerLogos: List<com.photobooth.kiosk.data.CachedProviderLogo> = emptyList(),
    val serverStatusProbe: ServerStatusProbe? = null,
    val serverStatusError: String? = null,
    val isServerStatusChecking: Boolean = false,
    /** Style ids the admin switched off; guests never see them. */
    val disabledStyleIds: Set<String> = emptySet(),
    val selectedModelId: String = "",
    val styleDecided: Boolean = false,
    val selectedAiStyleId: String = "ghibli",
    /** Per-photo AI backdrop: "none" (Sin fondo), a built-in id, or a custom id. */
    val aiBackgroundIds: List<String> = List(PHOTO_COUNT) { BackgroundRepository.NO_BACKGROUND_ID },
    val selectedAiAccessories: Set<String> = emptySet(),
    val aiStatus: AiStatus = AiStatus.IDLE,
    val aiUploadedCount: Int = 0,
    val aiProgress: Int = 0,
    val aiCost: Double? = null,
    val aiError: String? = null,
    val hasAiResult: Boolean = false,
    val backgrounds: List<BackgroundOption> = emptyList(),
    val adminBackgrounds: List<BackgroundOption> = emptyList(),
    val backgroundRevision: Int = 0,
    val eventProfiles: List<EventProfile> = emptyList(),
    val activeEventProfileId: String = EventProfileStore.SYSCOM_ID,
    val selectedEventProfileId: String = EventProfileStore.SYSCOM_ID,
    /** When true a copy of every finished photo is kept in the device gallery. */
    val savePhotosToDevice: Boolean = false,
    val errorMessage: String? = null,
    val shareError: String? = null,
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
    private val gestureSettings = GestureSettingsStore(application)
    private val cameraSettings = CameraSettingsStore(application)
    private val styleSettings = StyleSettingsStore(application)
    private val profileStore = EventProfileStore(application)
    private val photoSettings = PhotoSettingsStore(application)
    private val aiSettings = AiSettingsStore(application)
    private val identityStore = DeviceIdentityStore(application)
    private val deviceKeys = DeviceKeyStore()
    private val aiClient = AiBackendClient(identityStore, deviceKeys)
    private val aiFullBitmaps = arrayOfNulls<Bitmap>(PhotoBoothUiState.PHOTO_COUNT)
    private var aiJob: Job? = null

    private val sourceFiles = arrayOfNulls<File>(PhotoBoothUiState.PHOTO_COUNT)
    private val rawPreviews = arrayOfNulls<Bitmap>(PhotoBoothUiState.PHOTO_COUNT)
    /** Per-slot take id so a late write from a retaken shot cannot replace the new one. */
    private val captureGenerations = IntArray(PhotoBoothUiState.PHOTO_COUNT)
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
    private var remoteSessionId: String? = null
    private var remoteSessionStyled = false
    private var uploadedSignature: String? = null
    private var archivedSignature: String? = null
    private var remoteShareUrl: String? = null
    private var styledStrip: Bitmap? = null

    private val _uiState = MutableStateFlow(
        PhotoBoothUiState(
            palmCaptureEnabled = gestureSettings.palmCaptureEnabled,
            cameraAdjustments = cameraSettings.load(),
            disabledStyleIds = styleSettings.disabledIds,
            customization = CollageCustomization(
                eventBranding = profileStore.activeBranding(),
                badgePreset = defaultBadgePreset(),
            ),
            backgrounds = repository.visibleOptions,
            adminBackgrounds = repository.allOptions,
            selectedModelId = aiSettings.load().modelId,
            eventProfiles = profileStore.loadAll(),
            activeEventProfileId = profileStore.active().id,
            selectedEventProfileId = profileStore.active().id,
            savePhotosToDevice = photoSettings.saveToDevice(),
        ).let { state ->
            state.copy(
                customization = state.customization.copy(
                    sponsorLogoPaths = sponsorLogoPaths(state.customization.eventBranding, ProviderLogoStore.load(getApplication())),
                ),
            )
        },
    )
    val uiState: StateFlow<PhotoBoothUiState> = _uiState.asStateFlow()

    /** Absolute paths of the sponsor logos enabled on the active event, resolved from the cache. */
    private fun sponsorLogoPaths(branding: EventBranding, logos: List<com.photobooth.kiosk.data.CachedProviderLogo>): List<String> {
        val byId = logos.associateBy { it.id }
        return branding.sponsorLogoIds.mapNotNull { byId[it]?.path }
    }

    init {
        refreshBackgrounds()
        _uiState.update { it.copy(providerLogos = ProviderLogoStore.load(getApplication())) }
        refreshServerCatalog()
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
            ?: BackgroundRepository.ORIGINAL_ID
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

    private fun driveFolderName(): String? =
        _uiState.value.customization.eventBranding.driveFolderBase()
    suspend fun startDelivery(): DeliverySession? {
        _uiState.update { it.copy(shareError = null) }
        val files = ensureExportedFiles() ?: return null
        if (files.size != 4) return null
        if (identityStore.load().enrolled) {
            try {
                val shareUrl = withContext(Dispatchers.IO) { publishShare(files) }
                deliveryServer?.stop()
                deliveryServer = null
                return DeliverySession(pageUrl = shareUrl, token = "drive", port = 0)
            } catch (error: AiBackendException) {
                if (error.httpCode != null) {
                    val message = aiUserMessage(error)
                    _uiState.update { it.copy(errorMessage = message, shareError = message) }
                    return null
                }
            } catch (_: Throwable) {
                // No network: fall back to the local QR.
            }
        }
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

    /** Saves labels/colors/logo for one profile, without making it the active one. */
    fun updateEventProfileBranding(profileId: String, branding: EventBranding) {
        profileStore.updateBranding(profileId, branding)
        exportedStateSignature = null
        val isActive = profileId == _uiState.value.activeEventProfileId
        _uiState.update {
            it.copy(
                eventProfiles = profileStore.loadAll(),
                customization = if (isActive) {
                    it.customization.copy(
                        eventBranding = branding,
                        badgePreset = EventBadgePreset.CUSTOM,
                        sponsorLogoPaths = sponsorLogoPaths(branding, it.providerLogos),
                    )
                } else {
                    it.customization
                },
            )
        }
        if (isActive) requestPreviewUpdate(sessionToken)
        publishEvents()
    }

    /** Uploads the event list after an admin change so the other booths can download it. */
    private fun publishEvents() {
        if (!identityStore.load().enrolled) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { EventSync.publish(profileStore, aiClient) }
        }
    }

    private fun PhotoBoothUiState.withStoredEvents(): PhotoBoothUiState {
        val active = profileStore.active()
        val logos = providerLogos.ifEmpty { ProviderLogoStore.load(getApplication()) }
        return copy(
            eventProfiles = profileStore.loadAll(),
            activeEventProfileId = active.id,
            selectedEventProfileId = active.id,
            providerLogos = logos,
            customization = customization.copy(
                eventBranding = active.branding,
                sponsorLogoPaths = sponsorLogoPaths(active.branding, logos),
            ),
        )
    }

    fun setSavePhotosToDevice(enabled: Boolean) {
        photoSettings.setSaveToDevice(enabled)
        _uiState.update { it.copy(savePhotosToDevice = enabled) }
    }

    /** Switches every screen to the branding of [profileId]. */
    fun activateEventProfile(profileId: String) {
        val profile = profileStore.loadAll().firstOrNull { it.id == profileId } ?: return
        profileStore.setActive(profileId)
        exportedStateSignature = null
        _uiState.update {
            it.copy(
                activeEventProfileId = profile.id,
                eventProfiles = profileStore.loadAll(),
                customization = it.customization.copy(
                    eventBranding = profile.branding,
                    badgePreset = EventBadgePreset.CUSTOM,
                    sponsorLogoPaths = sponsorLogoPaths(profile.branding, it.providerLogos),
                ),
            )
        }
        requestPreviewUpdate(sessionToken)
        publishEvents()
    }

    fun addEventProfile(name: String): String {
        val profile = profileStore.add(name)
        activateEventProfile(profile.id)
        return profile.id
    }

    fun renameEventProfile(profileId: String, name: String) {
        profileStore.rename(profileId, name)
        exportedStateSignature = null
        _uiState.update {
            it.copy(eventProfiles = profileStore.loadAll())
        }
        publishEvents()
    }

    fun deleteEventProfile(profileId: String) {
        if (!profileStore.delete(profileId)) return
        val active = profileStore.active()
        exportedStateSignature = null
        _uiState.update {
            it.copy(
                eventProfiles = profileStore.loadAll(),
                activeEventProfileId = active.id,
                customization = it.customization.copy(
                    eventBranding = active.branding,
                    badgePreset = EventBadgePreset.CUSTOM,
                    sponsorLogoPaths = sponsorLogoPaths(active.branding, it.providerLogos),
                ),
            )
        }
        requestPreviewUpdate(sessionToken)
        publishEvents()
    }

    fun setEventProfileEnabled(profileId: String, enabled: Boolean) {
        profileStore.setEnabled(profileId, enabled)
        val active = profileStore.active()
        exportedStateSignature = null
        _uiState.update {
            it.copy(
                eventProfiles = profileStore.loadAll(),
                activeEventProfileId = active.id,
                customization = it.customization.copy(
                    eventBranding = active.branding,
                    badgePreset = EventBadgePreset.CUSTOM,
                    sponsorLogoPaths = sponsorLogoPaths(active.branding, it.providerLogos),
                ),
            )
        }
        requestPreviewUpdate(sessionToken)
        publishEvents()
    }

    private fun defaultBadgePreset(): EventBadgePreset =
        if (profileStore.activeBranding() == EventBranding()) {
            EventBadgePreset.EXPO_2027
        } else {
            EventBadgePreset.CUSTOM
        }

    fun beginCapture() {
        val state = _uiState.value
        if (state.isCapturing || state.isProcessing || state.hasResult) return
        if (state.capturedCount >= PhotoBoothUiState.PHOTO_COUNT && state.retakeIndex == null) return
        _uiState.update { it.copy(isCapturing = true, errorMessage = null) }
    }

    /** Marks one already captured slot so the next shot replaces only that photo. */
    fun beginRetake(index: Int) {
        val state = _uiState.value
        if (index !in 0 until PhotoBoothUiState.PHOTO_COUNT) return
        if (index >= state.capturedCount) return
        if (state.isCapturing || state.isProcessing) return
        captureGenerations[index] += 1
        _uiState.update { it.copy(retakeIndex = index, errorMessage = null) }
    }

    fun captureFailed() {
        _uiState.update { it.copy(isCapturing = false) }
    }

    /** Accepts an upright capture and runs MediaPipe person segmentation in the background. */
    fun processPhoto(photo: Bitmap, mirror: Boolean) {
        val state = _uiState.value
        val retake = state.retakeIndex
        if (!state.isCapturing ||
            (retake == null && state.capturedCount >= PhotoBoothUiState.PHOTO_COUNT)
        ) {
            if (!photo.isRecycled) photo.recycle()
            return
        }

        val token = sessionToken
        val index = retake ?: state.capturedCount
        val generation = ++captureGenerations[index]
        viewModelScope.launch {
            val oriented = withContext(Dispatchers.Default) {
                mirrorAndRelease(photo, mirror)
            }
            if (token != sessionToken || generation != captureGenerations[index]) {
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

            // Prepare 540x960 preview and 180x320 capture thumbnail immediately.
            // The capture is already cropped to the visible panel aspect in CameraPreview.
            val preview540 = withContext(Dispatchers.Default) {
                BitmapTransforms.centerCrop(
                    oriented,
                    PhotoProcessor.PREVIEW_WIDTH,
                    PhotoProcessor.PREVIEW_HEIGHT,
                )
            }
            val thumb = withContext(Dispatchers.Default) {
                Bitmap.createScaledBitmap(preview540, 180, 320, true)
            }

            if (token != sessionToken || generation != captureGenerations[index]) {
                if (!oriented.isRecycled) oriented.recycle()
                if (!preview540.isRecycled) preview540.recycle()
                if (!thumb.isRecycled) thumb.recycle()
                return@launch
            }

            rawPreviews[index]?.let(::recycleLater)
            rawPreviews[index] = preview540

            val currentBg = _uiState.value.selectedBackgroundId
            _uiState.update { current ->
                val updatedThumbs = current.captureThumbnails.toMutableList().apply {
                    while (size <= index) add(thumb)
                    this[index] = thumb
                }
                val newCount = maxOf(current.capturedCount, index + 1)
                val bgIds = if (newCount == 1) {
                    List(PhotoBoothUiState.PHOTO_COUNT) { currentBg }
                } else {
                    current.photoBackgroundIds
                }
                current.copy(
                    capturedCount = newCount,
                    captureThumbnails = updatedThumbs,
                    rawPhotoPreviews = rawPreviews.filterNotNull().toList(),
                    photoBackgroundIds = bgIds,
                    retakeIndex = null,
                    isCapturing = false,
                    errorMessage = null,
                )
            }

            // If all 3 photos are captured, build the initial studio preview immediately
            // using whatever cutouts are already finished (Shots 1 & 2) + raw Shot 3.
            if (maxOf(state.capturedCount, index + 1) == PhotoBoothUiState.PHOTO_COUNT) {
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

                if (token != sessionToken || generation != captureGenerations[index] || !saved) {
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
                    refreshServerCatalog()
                }
            }
        }
    }

    fun setApplyBackgroundToAll(applyAll: Boolean) {
        _uiState.update { state ->
            state.copy(
                applyBackgroundToAll = applyAll,
                selectedPhotoIndex = if (applyAll) null else (state.selectedPhotoIndex ?: 0),
            )
        }
    }

    /** Enables or disables the open-palm capture gesture for guests. */
    fun setPalmCaptureEnabled(enabled: Boolean) {
        gestureSettings.palmCaptureEnabled = enabled
        _uiState.update { it.copy(palmCaptureEnabled = enabled) }
    }

    /** Saves the manual preview and photo framing. The two sides do not follow each other. */
    fun setCameraAdjustments(adjustments: CameraAdjustments) {
        val value = adjustments.normalized()
        cameraSettings.save(value)
        _uiState.update { it.copy(cameraAdjustments = value) }
    }

    /** Shows or hides one AI style in the guest flow. */
    fun setStyleEnabled(id: String, enabled: Boolean) {
        styleSettings.setEnabled(id, enabled)
        _uiState.update { it.copy(disabledStyleIds = styleSettings.disabledIds) }
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

    fun selectEventProfile(profileId: String) {
        val profile = _uiState.value.eventProfiles.firstOrNull { it.id == profileId } ?: return
        if (_uiState.value.selectedEventProfileId == profileId) return
        exportedStateSignature = null
        _uiState.update { state ->
            state.copy(
                selectedEventProfileId = profile.id,
                customization = state.customization.copy(
                    eventBranding = profile.branding,
                    badgePreset = EventBadgePreset.CUSTOM,
                ),
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

    fun selectDecoration(decoration: FrameDecoration) {
        if (_uiState.value.customization.decoration == decoration) return
        exportedStateSignature = null
        _uiState.update { state ->
            state.copy(customization = state.customization.copy(decoration = decoration))
        }
        requestPreviewUpdate(sessionToken)
    }

    fun loadAiSettings(): AiSettings = aiSettings.load()

    fun loadIdentity(): DeviceIdentity = identityStore.load()

    fun saveServerBaseUrl(baseUrl: String) {
        identityStore.saveBaseUrl(baseUrl.trim())
        _uiState.update {
            it.copy(
                serverStatusProbe = null,
                serverStatusError = null,
                isServerStatusChecking = false,
            )
        }
    }

    fun saveSelectedModel(modelId: String) {
        aiSettings.saveModel(modelId)
        _uiState.update { it.copy(selectedModelId = modelId.trim()) }
    }

    /** Admin-only: stars or unstars a model. Returns the stored set so the screen can redraw. */
    fun setFavoriteModel(id: String, favorite: Boolean): Set<String> {
        val current = aiSettings.load().favoriteModelIds
        val updated = if (favorite) current + id.trim() else current - id.trim()
        aiSettings.saveFavorites(updated)
        return updated
    }

    /**
     * Admin-only: persists the AI model for every future session. Unlike guest
     * choices it is not blocked by an in-progress guest AI result.
     */
    fun saveAdminModel(id: String): Boolean {
        val model = _uiState.value.serverModels.firstOrNull { it.id == id } ?: return false
        if (!model.selectable) return false
        saveSelectedModel(id)
        return true
    }

    /**
     * Uploads a brand logo picked in the event editor so it appears as a shared
     * option on every booth. Returns the server logo id, or null when the device
     * is not enrolled or the server cannot be reached. The caller then keeps the
     * logo on this booth only.
     */
    suspend fun uploadBrandLogo(path: String, name: String): String? {
        if (!identityStore.load().enrolled) return null
        val id = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = java.io.File(path).readBytes()
                aiClient.uploadProviderLogo(name, "image/webp", bytes).id
            }.getOrNull()
        }
        // Refresh the shared catalog so the new logo shows up in the sponsor options.
        if (id != null) refreshServerCatalog()
        return id
    }

    fun refreshServerCatalog() {
        if (!identityStore.load().enrolled) return
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { aiClient.styles() to aiClient.models() }
            }
            val logos = withContext(Dispatchers.IO) {
                runCatching {
                    ProviderLogoStore.sync(getApplication(), aiClient.providerLogos(), aiClient::downloadProviderLogo)
                }.getOrNull()
            }
            val eventsChanged = withContext(Dispatchers.IO) {
                runCatching { EventSync.reconcile(getApplication(), profileStore, aiClient) }.getOrDefault(false)
            }
            loaded.onSuccess { (styles, models) ->
                val saved = aiSettings.load().modelId
                _uiState.update { state ->
                    val modelId = when {
                        models.any { it.id == state.selectedModelId && it.selectable } -> state.selectedModelId
                        models.any { it.id == saved && it.selectable } -> saved
                        else -> models.firstOrNull { it.selectable }?.id.orEmpty()
                    }
                    if (modelId.isNotBlank()) aiSettings.saveModel(modelId)
                    val styleId = when {
                        styles.any { it.id == state.selectedAiStyleId } -> state.selectedAiStyleId
                        else -> styles.firstOrNull()?.id ?: state.selectedAiStyleId
                    }
                    val newLogos = logos ?: state.providerLogos
                    val next = state.copy(
                        serverStyles = styles,
                        serverModels = models,
                        selectedModelId = modelId,
                        selectedAiStyleId = styleId,
                        providerLogos = newLogos,
                        customization = state.customization.copy(
                            sponsorLogoPaths = sponsorLogoPaths(state.customization.eventBranding, newLogos),
                        ),
                    )
                    if (eventsChanged) next.withStoredEvents() else next
                }
            }
            if (loaded.isFailure && (logos != null || eventsChanged)) {
                _uiState.update {
                    val next = it.copy(
                        providerLogos = logos ?: it.providerLogos,
                        customization = it.customization.copy(
                            sponsorLogoPaths = sponsorLogoPaths(it.customization.eventBranding, logos ?: it.providerLogos),
                        ),
                    )
                    if (eventsChanged) next.withStoredEvents() else next
                }
            }
        }
    }

    /** Refreshes the signed service probe and the server-provided style/model catalog. */
    fun refreshServerDiagnostics() {
        if (!identityStore.load().enrolled) {
            _uiState.update {
                it.copy(
                    serverStatusProbe = null,
                    serverStatusError = "unknown_device",
                    isServerStatusChecking = false,
                )
            }
            return
        }
        _uiState.update { it.copy(serverStatusError = null, isServerStatusChecking = true) }
        refreshServerCatalog()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { aiClient.status() } }
            result.onSuccess { probe ->
                _uiState.update {
                    it.copy(
                        serverStatusProbe = probe,
                        serverStatusError = null,
                        isServerStatusChecking = false,
                    )
                }
            }.onFailure { error ->
                val code = (error as? AiBackendException)?.code
                _uiState.update {
                    it.copy(
                        serverStatusProbe = null,
                        serverStatusError = code ?: error.message ?: "network",
                        isServerStatusChecking = false,
                    )
                }
            }
        }
    }

    suspend fun enrollDevice(code: String): String = withContext(Dispatchers.IO) {
        val deviceId = aiClient.enroll(code)
        deviceId
    }

    /**
     * Local-only unenroll: forgets this booth's device id and deletes its signing key.
     * The server keeps its record; AI stays off until the booth enrolls again.
     */
    fun unenrollThisBooth() {
        identityStore.clearDevice()
        deviceKeys.delete()
        _uiState.update {
            it.copy(
                serverModels = emptyList(),
                serverStatusProbe = null,
                serverStatusError = null,
                isServerStatusChecking = false,
            )
        }
    }

    suspend fun loadUsageMonths(): List<UsageMonth> = withContext(Dispatchers.IO) {
        aiClient.usageMonths()
    }

    suspend fun loadUsageSummary(month: String): UsageSummary = withContext(Dispatchers.IO) {
        aiClient.usageSummary(month)
    }

    /** Real OpenRouter limit, spend and remaining balance. */
    suspend fun loadUsageLimits(): UsageLimits = withContext(Dispatchers.IO) {
        aiClient.usageLimits()
    }

    /** One page of the usage log; pass the previous [cursor] to append. */
    suspend fun loadUsageRequests(
        month: String,
        cursor: String? = null,
    ): UsagePage = withContext(Dispatchers.IO) {
        val (from, to) = monthBounds(month)
        aiClient.usageRequests(from, to, cursor)
    }

    fun explainServerError(error: Throwable): String = when (error) {
        is AiBackendException -> aiUserMessage(error)
        else -> getApplication<Application>().getString(R.string.ai_error_network)
    }

    /**
     * AI config (style, per-photo backdrops, accessories) is locked while a run
     * is in flight and once a paid result exists: changing it would silently
     * drop the paid styling. Regeneration reuses the same locked config.
     */
    private fun aiConfigLocked(): Boolean {
        val state = _uiState.value
        return state.hasAiResult ||
            state.aiStatus == AiStatus.UPLOADING ||
            state.aiStatus == AiStatus.GENERATING
    }

    /**
     * Returns the Drive share URL. If the guest already paid for a styled strip,
     * that session already holds the originals and the styled strip, so we only
     * finalize it. Otherwise we upload the current exports once per export state.
     * Never calls stylize here: that would charge a second generation.
     */
    private fun publishShare(files: List<File>): String {
        val existingId = remoteSessionId
        val sessionId = if (existingId != null && remoteSessionStyled) {
            existingId
        } else {
            val signature = stateSignature(_uiState.value)
            if (existingId != null && uploadedSignature == signature) {
                existingId
            } else {
                val created = aiClient.createSession(driveFolderName())
                aiClient.uploadPhotos(created.id, files[0], files[1], files[2], files[3])
                remoteSessionId = created.id
                remoteSessionStyled = false
                uploadedSignature = signature
                remoteShareUrl = null
                created.id
            }
        }
        remoteShareUrl?.let { if (sessionId == remoteSessionId) return it }
        val shareUrl = aiClient.finalize(sessionId)
        remoteShareUrl = shareUrl
        return shareUrl
    }

    private fun monthBounds(month: String): Pair<String, String> {
        val parts = month.split("-")
        val year = parts[0].toInt()
        val mon = parts[1].toInt()
        val start = String.format(java.util.Locale.US, "%04d-%02d-01T00:00:00.000Z", year, mon)
        val nextYear = if (mon == 12) year + 1 else year
        val nextMonth = if (mon == 12) 1 else mon + 1
        val end = String.format(java.util.Locale.US, "%04d-%02d-01T00:00:00.000Z", nextYear, nextMonth)
        return start to end
    }

    private fun aiUserMessage(error: AiBackendException): String {
        val app = getApplication<Application>()
        return when (error.code) {
            "quota_exceeded" -> app.getString(R.string.ai_error_quota)
            "budget_exceeded" -> app.getString(R.string.ai_error_budget)
            "clock_skew" -> app.getString(R.string.ai_error_clock)
            "model_over_budget" -> app.getString(R.string.ai_error_model_budget)
            "invalid_password", "invalid_enroll_code" -> app.getString(R.string.ai_error_enroll_code)
            "enroll_not_configured" -> app.getString(R.string.ai_error_enroll_missing)
            "device_revoked" -> app.getString(R.string.ai_error_revoked)
            "unknown_device", "invalid_signature" ->
                app.getString(R.string.ai_error_not_enrolled)
            "style_not_found" -> app.getString(R.string.ai_error_style)
            "background_not_found" -> app.getString(R.string.ai_error_background)
            "upstream_error" -> app.getString(R.string.ai_error_upstream)
            else -> if (error.httpCode != null) {
                app.getString(R.string.ai_error_server_code, error.code ?: error.httpCode.toString())
            } else {
                app.getString(R.string.ai_error_network)
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
                            PhotoFrameComposer.framePhoto(bitmap!!, customization, previewScale = false)
                        }
                        val collage = PhotoFrameComposer.composeCollage(
                            styled.filterNotNull(),
                            customization,
                            previewScale = false,
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
                            // Fall back to the thumbnail so a missing preview asset
                            // never silently drops the background.
                            repository.loadPreview(targetBgId) ?: repository.loadThumbnail(targetBgId)
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

                // 2. Full-resolution frame so brand/sponsor logos stay sharp on the totem.
                val newPhotoPreviews = currentUnframed.map { unframed ->
                    PhotoFrameComposer.framePhoto(
                        photo = unframed,
                        customization = customization,
                        previewScale = false,
                    )
                }
                val newCollagePreview = PhotoFrameComposer.composeCollage(
                    photos = currentUnframed,
                    customization = customization,
                    previewScale = false,
                )

                if (token != sessionToken) {
                    newPhotoPreviews.forEach { it.recycle() }
                    newCollagePreview.recycle()
                    return@withLock
                }

                val heldStrip = styledStrip?.takeUnless { it.isRecycled }
                val stripToShow = heldStrip ?: newCollagePreview
                if (heldStrip != null && !newCollagePreview.isRecycled) newCollagePreview.recycle()

                val oldPreviews = _uiState.value.photoPreviews + listOfNotNull(_uiState.value.stripPreview)
                _uiState.update { current ->
                    current.copy(
                        isProcessing = false,
                        photoPreviews = newPhotoPreviews,
                        stripPreview = stripToShow,
                        errorMessage = null,
                    )
                }
                oldPreviews.forEach { old ->
                    if (old !in newPhotoPreviews && old !== stripToShow) {
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
        append("|")
        append(state.aiBackgroundIds.joinToString(","))
        append("|")
        append(state.selectedAiAccessories.sorted().joinToString(","))
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
                    if (_uiState.value.savePhotosToDevice && archivedSignature != signature) {
                        archivedSignature = signature
                        withContext(Dispatchers.IO) {
                            PhotoArchive.save(getApplication<Application>(), newPhotoFiles + newStripFile)
                        }
                    }
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
            captureGenerations[i] = 0
        }
        sessionDirectory = null
        outputDirectory = null
        exportedStateSignature = null
        remoteSessionId = null
        remoteSessionStyled = false
        uploadedSignature = null
        remoteShareUrl = null
        styledStrip = null

        val initialBg = if (resetBackground) {
            BackgroundRepository.ORIGINAL_ID
        } else {
            state.selectedBackgroundId
        }
        _uiState.value = PhotoBoothUiState(
            selectedBackgroundId = initialBg,
            photoBackgroundIds = List(PhotoBoothUiState.PHOTO_COUNT) { initialBg },
            palmCaptureEnabled = gestureSettings.palmCaptureEnabled,
            cameraAdjustments = cameraSettings.load(),
            serverStyles = state.serverStyles,
            serverModels = state.serverModels,
            providerLogos = state.providerLogos,
            disabledStyleIds = styleSettings.disabledIds,
            selectedModelId = state.selectedModelId,
            customization = CollageCustomization(
                eventBranding = profileStore.activeBranding(),
                badgePreset = defaultBadgePreset(),
                sponsorLogoPaths = sponsorLogoPaths(profileStore.activeBranding(), state.providerLogos),
            ),
            backgrounds = repository.visibleOptions,
            adminBackgrounds = repository.allOptions,
            eventProfiles = profileStore.loadAll(),
            activeEventProfileId = profileStore.active().id,
            selectedEventProfileId = profileStore.active().id,
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

    private fun mirrorAndRelease(photo: Bitmap, mirror: Boolean): Bitmap? {
        if (!mirror) return photo
        return try {
            BitmapTransforms.mirrorHorizontally(photo)
        } catch (_: Throwable) {
            null
        } finally {
            if (!photo.isRecycled) photo.recycle()
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
