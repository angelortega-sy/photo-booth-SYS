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
import com.photobooth.kiosk.data.EventBrandingStore
import com.photobooth.kiosk.network.AiBackendClient
import com.photobooth.kiosk.network.AiBackendException
import com.photobooth.kiosk.network.AiGeneratePhoto
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
) {
    val canGenerate: Boolean =
        hasPhoto && status != AiStatus.UPLOADING && status != AiStatus.GENERATING
    val hasResult: Boolean = resultPreview != null && resultFile != null
}

class AiSingleViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BackgroundRepository(application)
    private val brandingStore = EventBrandingStore(application)
    private val aiSettings = AiSettingsStore(application)
    private val aiClient = AiBackendClient()

    private var originalFile: File? = null
    private var styledBitmap: Bitmap? = null
    private var aiJob: Job? = null
    private var sessionDir: File? = null

    private val _uiState = MutableStateFlow(
        AiSingleUiState(
            customization = CollageCustomization(eventBranding = brandingStore.load()),
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
                it.copy(hasPhoto = true, photoPreview = preview, error = null)
            }
        }
    }

    fun selectStyle(id: String) {
        if (AiStyles.ALL.none { it.id == id }) return
        if (isLocked()) return
        _uiState.update { it.copy(selectedStyleId = id, error = null) }
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
        val settings = aiSettings.load()
        if (settings.baseUrl.isBlank()) {
            _uiState.update {
                it.copy(
                    status = AiStatus.ERROR,
                    error = getApplication<Application>().getString(R.string.ai_error_no_server),
                )
            }
            return
        }
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        status = AiStatus.UPLOADING,
                        uploadProgress = 0,
                        progress = 0,
                        error = null,
                        cost = null,
                    )
                }
                val baseUrl = settings.baseUrl
                val token = settings.kioskToken
                val modelId = settings.modelId.ifBlank { AiSettingsStore::class.java.name }
                val styleId = snapshot.selectedStyleId
                val stylePrompt = AiStyles.byId(styleId).prompt
                val bgId = snapshot.backgroundId
                val isCustom = repository.isCustom(bgId)
                val mode = when {
                    bgId == BackgroundRepository.NO_BACKGROUND_ID -> "none"
                    // "original" is hidden in IA mode; stale selections behave like "none".
                    bgId == BackgroundRepository.ORIGINAL_ID -> "none"
                    isCustom -> "custom"
                    else -> "prompt"
                }
                val bgPrompt = if (mode == "prompt") {
                    repository.backgroundPrompt(bgId)
                        ?: "Festive event backdrop matching '$bgId', no readable text."
                } else {
                    null
                }
                val sessionId = withContext(Dispatchers.IO) {
                    aiClient.createSession(baseUrl, token)
                }
                ensureActive()
                withContext(Dispatchers.IO) {
                    aiClient.uploadPhoto(baseUrl, token, sessionId, 0, source)
                }
                _uiState.update { it.copy(uploadProgress = 1) }
                var refId: String? = null
                var inline: String? = null
                if (mode == "custom") {
                    val bgFile = withContext(Dispatchers.IO) { repository.customBackgroundFile(bgId) }
                    if (bgFile != null) {
                        refId = try {
                            withContext(Dispatchers.IO) {
                                aiClient.uploadBackground(baseUrl, token, sessionId, 0, bgFile)
                            }
                        } catch (e: AiBackendException) {
                            if (e.httpCode == 404 || e.httpCode == 405) null else throw e
                        }
                        if (refId == null) {
                            inline = withContext(Dispatchers.IO) {
                                val bytes = bgFile.readBytes()
                                "data:image/jpeg;base64," + android.util.Base64.encodeToString(
                                    bytes,
                                    android.util.Base64.NO_WRAP,
                                )
                            }
                        }
                    }
                }
                _uiState.update { it.copy(status = AiStatus.GENERATING) }
                val accIds = snapshot.accessories.toList()
                val accPrompt = AiAccessories.promptFor(snapshot.accessories)
                val branding = brandingStore.load()
                val jobId = withContext(Dispatchers.IO) {
                    aiClient.requestGenerate(
                        baseUrl,
                        token,
                        sessionId,
                        styleId,
                        stylePrompt,
                        settings.modelId,
                        branding.brand.ifBlank { "SYSCOM" },
                        listOf(
                            AiGeneratePhoto(
                                index = 0,
                                backgroundId = bgId,
                                backgroundMode = mode,
                                backgroundPrompt = bgPrompt,
                                backgroundRefId = refId,
                                backgroundDataBase64 = inline,
                            ),
                        ),
                        accIds,
                        accPrompt,
                    )
                }
                val job = withContext(Dispatchers.IO) {
                    aiClient.awaitJob(baseUrl, token, jobId, expectedPhotos = 1) { p ->
                        _uiState.update { it.copy(progress = p) }
                    }
                }
                ensureActive()
                val first = job.results.sortedBy { it.index }.firstOrNull()
                    ?: throw AiBackendException("Backend job finished without image")
                val dir = ensureSessionDir()
                val target = File(dir, "ai_single_${UUID.randomUUID()}.jpg")
                withContext(Dispatchers.IO) {
                    aiClient.downloadToFile(baseUrl, first.url, token, target)
                }
                var bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(target.absolutePath)
                } ?: throw AiBackendException("Styled photo is not a valid image")
                val cropped = BitmapTransforms.centerCrop(
                    bitmap,
                    PhotoProcessor.OUTPUT_WIDTH,
                    PhotoProcessor.OUTPUT_HEIGHT,
                )
                if (cropped !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                bitmap = cropped
                styledBitmap?.let { if (!it.isRecycled) it.recycle() }
                styledBitmap = bitmap
                val customization = _uiState.value.customization.copy(
                    eventBranding = branding,
                )
                val framed = PhotoFrameComposer.framePhoto(bitmap, customization, previewScale = true)
                val full = PhotoFrameComposer.framePhoto(bitmap, customization, previewScale = false)
                val framedFile = File(dir, "ai_single_framed_${UUID.randomUUID()}.jpg")
                withContext(Dispatchers.IO) { PhotoFrameComposer.writeJpeg(full, framedFile) }
                if (!full.isRecycled) full.recycle()
                _uiState.value.resultPreview?.let { if (!it.isRecycled) it.recycle() }
                _uiState.update {
                    it.copy(
                        status = AiStatus.READY,
                        progress = 100,
                        cost = job.cost,
                        resultPreview = framed,
                        resultFile = framedFile,
                        customization = customization,
                    )
                }
                voidUnused(modelId)
            } catch (e: CancellationException) {
                _uiState.update { it.copy(status = AiStatus.IDLE) }
                throw e
            } catch (e: AiBackendException) {
                val msg = if (e.httpCode != null) {
                    getApplication<Application>().getString(R.string.ai_error_server)
                } else {
                    getApplication<Application>().getString(R.string.ai_error_network)
                }
                _uiState.update { it.copy(status = AiStatus.ERROR, error = msg) }
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

    private fun voidUnused(@Suppress("UNUSED_PARAMETER") value: Any?) = Unit

    override fun onCleared() {
        aiJob?.cancel()
        _uiState.value.photoPreview?.let { if (!it.isRecycled) it.recycle() }
        _uiState.value.resultPreview?.let { if (!it.isRecycled) it.recycle() }
        styledBitmap?.let { if (!it.isRecycled) it.recycle() }
    }
}
