package com.photobooth.kiosk.ui.ai

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.AiAccessories
import com.photobooth.kiosk.data.AiSettingsStore
import com.photobooth.kiosk.data.AiStatus
import com.photobooth.kiosk.data.AiStyles
import com.photobooth.kiosk.data.BackgroundOption
import com.photobooth.kiosk.data.BackgroundRepository
import com.photobooth.kiosk.data.CollageCustomization
import com.photobooth.kiosk.data.CollageFrameStyle
import com.photobooth.kiosk.data.DeviceIdentityStore
import com.photobooth.kiosk.data.EventBadgePreset
import com.photobooth.kiosk.data.EventProfile
import com.photobooth.kiosk.data.EventProfileStore
import com.photobooth.kiosk.data.FrameDecoration
import com.photobooth.kiosk.data.GestureSettingsStore
import com.photobooth.kiosk.data.StyleSettingsStore
import com.photobooth.kiosk.network.AiBackendClient
import com.photobooth.kiosk.network.AiBackendException
import com.photobooth.kiosk.network.BoothModel
import com.photobooth.kiosk.network.BoothStyle
import com.photobooth.kiosk.network.decodeImage
import com.photobooth.kiosk.security.DeviceKeyStore
import com.photobooth.kiosk.photo.BitmapTransforms
import com.photobooth.kiosk.photo.PhotoFrameComposer
import com.photobooth.kiosk.photo.PhotoProcessor
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AiSingleUiState(
    val hasPhoto: Boolean = false,
    val photoPreview: Bitmap? = null,
    val selectedStyleId: String = "ghibli",
    val serverStyles: List<BoothStyle> = emptyList(),
    val serverModels: List<BoothModel> = emptyList(),
    /** Style ids the admin switched off; the guest never sees them. */
    val disabledStyleIds: Set<String> = emptySet(),
    val selectedModelId: String = "",
    val backgroundId: String = BackgroundRepository.NO_BACKGROUND_ID,
    val accessories: Set<String> = emptySet(),
    val status: AiStatus = AiStatus.IDLE,
    val uploadProgress: Int = 0,
    val progress: Int = 0,
    val cost: Double? = null,
    val error: String? = null,
    /** Increments for every error, including repeated messages, so the UI can show each event. */
    val errorEventId: Long = 0,
    val resultPreview: Bitmap? = null,
    val resultFile: File? = null,
    val frameStyle: CollageFrameStyle = CollageFrameStyle.SYSCOM_DARK,
    val frameDecoration: FrameDecoration = FrameDecoration.NONE,
    val customization: CollageCustomization = CollageCustomization(),
    val eventProfiles: List<EventProfile> = emptyList(),
    val selectedEventProfileId: String = EventProfileStore.SYSCOM_ID,
    val palmCaptureEnabled: Boolean = true,
) {
    val canGenerate: Boolean =
        hasPhoto && status != AiStatus.UPLOADING && status != AiStatus.GENERATING
    val hasResult: Boolean = resultPreview != null && resultFile != null
}

class AiSingleViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BackgroundRepository(application)
    private val profileStore = EventProfileStore(application)
    private val aiSettings = AiSettingsStore(application)
    private val identityStore = DeviceIdentityStore(application)
    private val gestureSettings = GestureSettingsStore(application)
    private val styleSettings = StyleSettingsStore(application)
    private val aiClient = AiBackendClient(identityStore, DeviceKeyStore())
    private var remoteSessionId: String? = null

    init {
        refreshCatalog()
    }

    private var originalFile: File? = null
    private var styledBitmap: Bitmap? = null
    private var aiJob: Job? = null
    private var sessionDir: File? = null

    private val _uiState = MutableStateFlow(
        AiSingleUiState(
            customization = CollageCustomization(eventBranding = profileStore.activeBranding()),
            eventProfiles = profileStore.enabledProfiles(),
            selectedEventProfileId = profileStore.active().id,
            palmCaptureEnabled = gestureSettings.palmCaptureEnabled,
            disabledStyleIds = styleSettings.disabledIds,
        ),
    )
    val uiState: StateFlow<AiSingleUiState> = _uiState.asStateFlow()

    /**
     * Re-reads the admin style switches. The screen is activity scoped, so
     * without this a style that was switched back on would stay hidden for the
     * rest of the process.
     */
    fun refreshStyleSettings() {
        val disabled = styleSettings.disabledIds
        _uiState.update { state ->
            val visible = state.serverStyles.filterNot { it.id in disabled }
            val styleId = if (visible.any { it.id == state.selectedStyleId }) {
                state.selectedStyleId
            } else {
                visible.firstOrNull()?.id ?: state.selectedStyleId
            }
            state.copy(disabledStyleIds = disabled, selectedStyleId = styleId)
        }
    }

    val backgrounds: List<BackgroundOption> get() = repository.visibleOptions

    fun loadThumbnail(id: String): Bitmap? = repository.loadThumbnail(id)

    fun backgroundPrompt(id: String): String? = repository.backgroundPrompt(id)

    fun customBackgroundFile(id: String): File? = repository.customBackgroundFile(id)

    fun onPhotoCaptured(photo: Bitmap, rotationDegrees: Int, mirror: Boolean) {
        viewModelScope.launch(Dispatchers.Default) {
            val oriented = BitmapTransforms.rotateDegrees(photo, rotationDegrees)
            if (!photo.isRecycled) photo.recycle()
            var current = oriented
            if (mirror) {
                val mirrored = BitmapTransforms.mirrorHorizontally(current)
                if (!current.isRecycled) current.recycle()
                current = mirrored
            }
            // Keep the whole frame. Forcing 9:16 here cut the sides of a 3:4 camera
            // buffer, so the guest ended up with a close-up of one cheek.
            val targetWidth = minOf(PhotoProcessor.OUTPUT_WIDTH, current.width)
            val targetHeight = current.height * targetWidth / current.width
            val full = if (targetWidth == current.width && targetHeight == current.height) {
                current.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                Bitmap.createScaledBitmap(current, targetWidth, targetHeight, true)
            }
            if (!current.isRecycled) current.recycle()
            val previewWidth = 540
            val preview = Bitmap.createScaledBitmap(
                full,
                previewWidth,
                full.height * previewWidth / full.width,
                true,
            )
            val dir = ensureSessionDir()
            val file = File(dir, "ai_single_original.jpg")
            withContext(Dispatchers.IO) { PhotoFrameComposer.writeJpeg(full, file) }
            if (!full.isRecycled) full.recycle()
            originalFile = file
            _uiState.value.photoPreview?.let { if (!it.isRecycled) it.recycle() }
            _uiState.update {
                it.copy(
                    hasPhoto = true,
                    photoPreview = preview,
                    // A fresh shot starts with no accessories so a stale selection
                    // is never applied to a different photo by accident.
                    accessories = emptySet(),
                    frameStyle = CollageFrameStyle.SYSCOM_DARK,
                    frameDecoration = FrameDecoration.NONE,
                    eventProfiles = profileStore.enabledProfiles(),
                    selectedEventProfileId = profileStore.active().id,
                    error = null,
                )
            }
        }
    }

    fun selectStyle(id: String) {
        val state = _uiState.value
        val known = state.serverStyles.any { it.id == id } ||
            (state.serverStyles.isEmpty() && AiStyles.ALL.any { it.id == id })
        if (!known || isLocked()) return
        _uiState.update {
            val blocked = AiAccessories.blockedForStyle(id)
            it.copy(
                selectedStyleId = id,
                accessories = it.accessories - blocked,
                error = null,
            )
        }
    }

    fun selectModel(id: String) {
        if (isLocked()) return
        val model = _uiState.value.serverModels.firstOrNull { it.id == id } ?: return
        if (!model.selectable) return
        aiSettings.saveModel(id)
        _uiState.update { it.copy(selectedModelId = id, error = null) }
    }

    /**
     * Drive link for the styled photo. A failed finalization remains recoverable through the
     * local delivery server, but must still be visible to the guest.
     */
    fun driveShareUrl(): String? {
        val sessionId = remoteSessionId ?: return null
        if (!identityStore.load().enrolled) return null
        val result = _uiState.value.resultFile
        if (result?.isFile != true) {
            showError(getApplication<Application>().getString(R.string.ai_error_network))
            return null
        }
        return try {
            aiClient.uploadFinalResult(sessionId, result)
            aiClient.finalize(sessionId)
        } catch (error: AiBackendException) {
            showError(userMessage(error))
            null
        } catch (_: Exception) {
            showError(getApplication<Application>().getString(R.string.ai_error_network))
            null
        }
    }

    fun selectBackground(id: String) {
        if (isLocked()) return
        _uiState.update { it.copy(backgroundId = id, error = null) }
    }

    fun selectAccessoryExclusive(id: String) {
        if (isLocked()) return
        _uiState.update {
            val blocked = AiAccessories.blockedIds(it.selectedStyleId, it.accessories)
            if (id in blocked) return@update it
            it.copy(accessories = AiAccessories.selectExclusive(it.accessories, id), error = null)
        }
    }

    fun clearAccessoryGroup(groupId: String) {
        if (isLocked()) return
        val group = AiAccessories.GROUPS.firstOrNull { it.id == groupId } ?: return
        val ids = group.options.map { it.id }.toSet()
        _uiState.update {
            it.copy(accessories = it.accessories - ids, error = null)
        }
    }

    /** Restores personalization defaults without discarding the captured source photo. */
    fun resetPersonalization() {
        if (isLocked()) return
        styledBitmap?.let { if (!it.isRecycled) it.recycle() }
        styledBitmap = null
        _uiState.value.resultPreview?.let { if (!it.isRecycled) it.recycle() }
        _uiState.update {
            it.copy(
                selectedStyleId = DEFAULT_STYLE_ID,
                backgroundId = BackgroundRepository.NO_BACKGROUND_ID,
                accessories = emptySet(),
                frameStyle = CollageFrameStyle.SYSCOM_DARK,
                frameDecoration = FrameDecoration.NONE,
                status = AiStatus.IDLE,
                uploadProgress = 0,
                progress = 0,
                cost = null,
                error = null,
                resultPreview = null,
                resultFile = null,
            )
        }
    }

    /** Clears the current error message after it has been presented to the guest. */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun selectFrame(style: CollageFrameStyle) {
        _uiState.update { it.copy(frameStyle = style) }
        rebuildFramed()
    }

    fun selectDecoration(decoration: FrameDecoration) {
        if (_uiState.value.frameDecoration == decoration) return
        _uiState.update { it.copy(frameDecoration = decoration) }
        rebuildFramed()
    }

    /** Re-brands the photo with another enabled event profile (SYSCOM is always one). */
    fun selectEventProfile(profileId: String) {
        val state = _uiState.value
        val profile = state.eventProfiles.firstOrNull { it.id == profileId } ?: return
        if (state.selectedEventProfileId == profileId) return
        _uiState.update {
            it.copy(
                selectedEventProfileId = profileId,
                customization = it.customization.copy(
                    eventBranding = profile.branding,
                    badgePreset = EventBadgePreset.CUSTOM,
                ),
            )
        }
        rebuildFramed()
    }

    /** Re-reads the admin profile list so a brand added or hidden mid-session is honored. */
    fun refreshEventProfiles() {
        val profiles = profileStore.enabledProfiles()
        val active = profileStore.active()
        _uiState.update { state ->
            val selection = profiles.firstOrNull { it.id == state.selectedEventProfileId }
                ?: profiles.firstOrNull { it.id == active.id }
                ?: profiles.first()
            state.copy(
                eventProfiles = profiles,
                selectedEventProfileId = selection.id,
                customization = if (state.resultFile == null) {
                    state.customization.copy(eventBranding = selection.branding)
                } else {
                    state.customization
                },
            )
        }
    }

    fun cancel() {
        aiJob?.cancel()
    }

    fun reset() {
        aiJob?.cancel()
        _uiState.value.photoPreview?.let { if (!it.isRecycled) it.recycle() }
        _uiState.value.resultPreview?.let { if (!it.isRecycled) it.recycle() }
        styledBitmap?.let { if (!it.isRecycled) it.recycle() }
        styledBitmap = null
        originalFile = null
        _uiState.update {
            it.copy(
                hasPhoto = false,
                photoPreview = null,
                status = AiStatus.IDLE,
                progress = 0,
                uploadProgress = 0,
                cost = null,
                error = null,
                resultPreview = null,
                resultFile = null,
                accessories = emptySet(),
                frameStyle = CollageFrameStyle.SYSCOM_DARK,
                frameDecoration = FrameDecoration.NONE,
                eventProfiles = profileStore.enabledProfiles(),
                selectedEventProfileId = profileStore.active().id,
            )
        }
    }

    private fun isLocked(): Boolean {
        val s = _uiState.value.status
        return s == AiStatus.UPLOADING || s == AiStatus.GENERATING
    }

    fun generate() {
        val snapshot = _uiState.value
        val source = originalFile
        if (!snapshot.hasPhoto || source == null || !source.isFile) return
        if (isLocked()) return
        val app = getApplication<Application>()
        if (!identityStore.load().enrolled) {
            showError(app.getString(R.string.ai_error_not_enrolled))
            return
        }
        val modelId = snapshot.selectedModelId.ifBlank { aiSettings.load().modelId }
        if (modelId.isBlank() || snapshot.selectedStyleId.isBlank()) {
            showError(app.getString(R.string.ai_error_no_model))
            return
        }
        // A background is a text prompt. The model receives only the guest photo.
        val backgroundForPrompt = if (repository.isCustom(snapshot.backgroundId)) {
            null
        } else {
            snapshot.backgroundId
        }
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(status = AiStatus.UPLOADING, uploadProgress = 0, progress = 0, error = null, cost = null)
                }
                val session = withContext(Dispatchers.IO) { aiClient.createSession() }
                remoteSessionId = session.id
                withContext(Dispatchers.IO) { aiClient.uploadPhoto(session.id, source) }
                ensureActive()
                _uiState.update { it.copy(status = AiStatus.GENERATING, uploadProgress = 1, progress = 45) }
                val styled = withContext(Dispatchers.IO) {
                    aiClient.stylize(
                        sessionId = session.id,
                        modelId = modelId,
                        styleId = snapshot.selectedStyleId,
                        source = "0",
                        backgroundId = backgroundForPrompt,
                        accessoryIds = snapshot.accessories.toList(),
                    )
                }
                ensureActive()
                val bytes = decodeImage(styled.imageBase64)
                if (bytes.isEmpty()) throw AiBackendException("Styled photo is empty")
                var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw AiBackendException("Styled photo is not a valid image")
                val cropped = BitmapTransforms.centerCrop(
                    bitmap,
                    PhotoProcessor.OUTPUT_WIDTH,
                    PhotoProcessor.OUTPUT_HEIGHT,
                )
                if (cropped !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                bitmap = cropped
                styledBitmap?.let { if (!it.isRecycled) it.recycle() }
                styledBitmap = bitmap
                val activeProfile = profileStore.active()
                val customization = _uiState.value.customization.copy(
                    eventBranding = activeProfile.branding,
                    badgePreset = EventBadgePreset.CUSTOM,
                    decoration = _uiState.value.frameDecoration,
                )
                val framed = PhotoFrameComposer.framePhoto(bitmap, customization, previewScale = true)
                val full = PhotoFrameComposer.framePhoto(bitmap, customization, previewScale = false)
                val framedFile = File(ensureSessionDir(), "ai_single_framed_${UUID.randomUUID()}.jpg")
                withContext(Dispatchers.IO) { PhotoFrameComposer.writeJpeg(full, framedFile) }
                if (!full.isRecycled) full.recycle()
                _uiState.value.resultPreview?.let { if (!it.isRecycled) it.recycle() }
                _uiState.update {
                    it.copy(
                        status = AiStatus.READY,
                        progress = 100,
                        cost = styled.costUsd ?: styled.estimatedCostUsd,
                        resultPreview = framed,
                        resultFile = framedFile,
                        customization = customization,
                    )
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(status = AiStatus.IDLE) }
                throw e
            } catch (e: AiBackendException) {
                showError(userMessage(e))
            } catch (_: Exception) {
                showError(getApplication<Application>().getString(R.string.ai_error_network))
            }
        }
    }

    private fun showError(message: String) {
        _uiState.update {
            it.copy(
                status = AiStatus.ERROR,
                error = message,
                errorEventId = it.errorEventId + 1,
            )
        }
    }

    private fun refreshCatalog() {
        if (!identityStore.load().enrolled) return
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { aiClient.styles() to aiClient.models() }
            }
            loaded.onSuccess { (styles, models) ->
                val saved = aiSettings.load().modelId
                _uiState.update { state ->
                    val disabled = styleSettings.disabledIds
                    val visible = styles.filterNot { it.id in disabled }
                    val modelId = if (models.any { it.id == saved && it.selectable }) saved else ""
                    val styleId = when {
                        visible.any { it.id == state.selectedStyleId } -> state.selectedStyleId
                        else -> visible.firstOrNull()?.id ?: state.selectedStyleId
                    }
                    state.copy(
                        serverStyles = styles,
                        serverModels = models,
                        disabledStyleIds = disabled,
                        selectedModelId = modelId,
                        selectedStyleId = styleId,
                    )
                }
            }
        }
    }

    private fun userMessage(error: AiBackendException): String {
        val app = getApplication<Application>()
        return when (error.code) {
            "quota_exceeded" -> app.getString(R.string.ai_error_quota)
            "budget_exceeded" -> app.getString(R.string.ai_error_budget)
            "clock_skew" -> app.getString(R.string.ai_error_clock)
            "model_over_budget" -> app.getString(R.string.ai_error_model_budget)
            "unknown_device", "invalid_signature" -> app.getString(R.string.ai_error_not_enrolled)
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

    private fun rebuildFramed() {
        val bitmap = styledBitmap ?: return
        if (bitmap.isRecycled) return
        viewModelScope.launch(Dispatchers.Default) {
            val customization = _uiState.value.customization.copy(
                frameStyle = _uiState.value.frameStyle,
                decoration = _uiState.value.frameDecoration,
            )
            val framed = PhotoFrameComposer.framePhoto(bitmap, customization, previewScale = true)
            val full = PhotoFrameComposer.framePhoto(bitmap, customization, previewScale = false)
            val dir = ensureSessionDir()
            val framedFile = File(dir, "ai_single_framed_${UUID.randomUUID()}.jpg")
            withContext(Dispatchers.IO) { PhotoFrameComposer.writeJpeg(full, framedFile) }
            if (!full.isRecycled) full.recycle()
            _uiState.value.resultPreview?.let { if (!it.isRecycled) it.recycle() }
            _uiState.update { it.copy(resultPreview = framed, resultFile = framedFile) }
        }
    }

    private fun ensureSessionDir(): File {
        val existing = sessionDir?.takeIf { it.isDirectory || it.mkdirs() }
        if (existing != null) return existing
        val dir = File(
            getApplication<Application>().cacheDir,
            "ai_single_${UUID.randomUUID()}",
        )
        dir.mkdirs()
        sessionDir = dir
        return dir
    }

    override fun onCleared() {
        aiJob?.cancel()
        _uiState.value.photoPreview?.let { if (!it.isRecycled) it.recycle() }
        _uiState.value.resultPreview?.let { if (!it.isRecycled) it.recycle() }
        styledBitmap?.let { if (!it.isRecycled) it.recycle() }
    }

    private companion object {
        const val DEFAULT_STYLE_ID = "ghibli"
    }
}
