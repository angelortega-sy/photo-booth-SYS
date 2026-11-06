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
import com.photobooth.kiosk.data.EventBrandingStore
import com.photobooth.kiosk.data.GestureSettingsStore
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
    val selectedModelId: String = "",
    val backgroundId: String = BackgroundRepository.NO_BACKGROUND_ID,
    val accessories: Set<String> = emptySet(),
    val status: AiStatus = AiStatus.IDLE,
    val uploadProgress: Int = 0,
    val progress: Int = 0,
    val cost: Double? = null,
    val error: String? = null,
    val resultPreview: Bitmap? = null,
    val resultFile: File? = null,
    val frameStyle: CollageFrameStyle = CollageFrameStyle.SYSCOM_DARK,
    val customization: CollageCustomization = CollageCustomization(),
    val palmCaptureEnabled: Boolean = true,
) {
    val canGenerate: Boolean =
        hasPhoto && status != AiStatus.UPLOADING && status != AiStatus.GENERATING
    val hasResult: Boolean = resultPreview != null && resultFile != null
}

class AiSingleViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BackgroundRepository(application)
    private val brandingStore = EventBrandingStore(application)
    private val aiSettings = AiSettingsStore(application)
    private val identityStore = DeviceIdentityStore(application)
    private val gestureSettings = GestureSettingsStore(application)
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
            customization = CollageCustomization(eventBranding = brandingStore.load()),
            palmCaptureEnabled = gestureSettings.palmCaptureEnabled,
        ),
    )
    val uiState: StateFlow<AiSingleUiState> = _uiState.asStateFlow()

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
            val full = BitmapTransforms.centerCrop(
                current,
                PhotoProcessor.OUTPUT_WIDTH,
                PhotoProcessor.OUTPUT_HEIGHT,
            )
            if (!current.isRecycled) current.recycle()
            val preview = Bitmap.createScaledBitmap(full, 540, 960, true)
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
        _uiState.update { it.copy(selectedStyleId = id, error = null) }
    }

    fun selectModel(id: String) {
        if (isLocked()) return
        val model = _uiState.value.serverModels.firstOrNull { it.id == id } ?: return
        if (!model.selectable) return
        aiSettings.saveModel(id)
        _uiState.update { it.copy(selectedModelId = id, error = null) }
    }

    /** Drive link for the styled photo. Null when the booth is offline or not enrolled. */
    fun driveShareUrl(): String? {
        val sessionId = remoteSessionId ?: return null
        if (!identityStore.load().enrolled) return null
        return runCatching { aiClient.finalize(sessionId) }.getOrNull()
    }

    fun selectBackground(id: String) {
        if (isLocked()) return
        _uiState.update { it.copy(backgroundId = id, error = null) }
    }

    fun selectAccessoryExclusive(id: String) {
        if (isLocked()) return
        _uiState.update {
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

    fun selectFrame(style: CollageFrameStyle) {
        _uiState.update { it.copy(frameStyle = style) }
        rebuildFramed()
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
            _uiState.update { it.copy(status = AiStatus.ERROR, error = app.getString(R.string.ai_error_not_enrolled)) }
            return
        }
        val modelId = snapshot.selectedModelId.ifBlank { aiSettings.load().modelId }
        if (modelId.isBlank() || snapshot.selectedStyleId.isBlank()) {
            _uiState.update { it.copy(status = AiStatus.ERROR, error = app.getString(R.string.ai_error_no_model)) }
            return
        }
        if (repository.isCustom(snapshot.backgroundId)) {
            _uiState.update { it.copy(status = AiStatus.ERROR, error = app.getString(R.string.ai_error_custom_background)) }
            return
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
                        backgroundId = snapshot.backgroundId,
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
                val branding = brandingStore.load()
                val customization = _uiState.value.customization.copy(eventBranding = branding)
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
                _uiState.update { it.copy(status = AiStatus.ERROR, error = userMessage(e)) }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        status = AiStatus.ERROR,
                        error = getApplication<Application>().getString(R.string.ai_error_network),
                    )
                }
            }
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
                    val modelId = if (models.any { it.id == saved && it.selectable }) saved else ""
                    val styleId = when {
                        styles.any { it.id == state.selectedStyleId } -> state.selectedStyleId
                        else -> styles.firstOrNull()?.id ?: state.selectedStyleId
                    }
                    state.copy(
                        serverStyles = styles,
                        serverModels = models,
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
                eventBranding = brandingStore.load(),
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
}
