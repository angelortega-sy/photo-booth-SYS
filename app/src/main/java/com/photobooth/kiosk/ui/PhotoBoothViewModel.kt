package com.photobooth.kiosk.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.BackgroundOption
import com.photobooth.kiosk.data.BackgroundRepository
import com.photobooth.kiosk.photo.BitmapTransforms
import com.photobooth.kiosk.photo.PhotoFrameComposer
import com.photobooth.kiosk.photo.PhotoProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class PhotoBoothUiState(
    val selectedBackgroundId: String = "gala",
    val capturedCount: Int = 0,
    val isCapturing: Boolean = false,
    val isProcessing: Boolean = false,
    val photoFiles: List<File> = emptyList(),
    val photoBackgroundIds: List<String> = emptyList(),
    val photoPreviews: List<Bitmap> = emptyList(),
    val stripFile: File? = null,
    val stripPreview: Bitmap? = null,
    val selectedPhotoIndex: Int? = null,
    val usedFallbackIndices: Set<Int> = emptySet(),
    val errorMessage: String? = null,
) {
    val hasResult: Boolean
        get() = stripFile != null && photoFiles.size == PHOTO_COUNT

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

    /** Full-size captures remain on disk so background changes do not keep several bitmaps alive. */
    private val sourceFiles = mutableListOf<File>()
    private var sessionDirectory: File? = null
    private var outputDirectory: File? = null
    private var sessionToken = 0

    private val _uiState = MutableStateFlow(PhotoBoothUiState())
    val uiState: StateFlow<PhotoBoothUiState> = _uiState.asStateFlow()

    val backgrounds: List<BackgroundOption> = repository.options

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

    /** Stores one oriented capture and starts composition only after all three are captured. */
    fun processPhoto(photo: Bitmap, rotationDegrees: Int, mirror: Boolean) {
        val state = _uiState.value
        if (!state.isCapturing || state.isProcessing || state.capturedCount >= PhotoBoothUiState.PHOTO_COUNT) {
            if (!photo.isRecycled) photo.recycle()
            return
        }

        val token = sessionToken
        val index = sourceFiles.size
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

            val sourceFile = File(ensureSessionDirectory(), "capture_${index + 1}.jpg")
            val saved = withContext(Dispatchers.IO) {
                PhotoFrameComposer.writeJpeg(oriented, sourceFile)
            }
            if (!oriented.isRecycled) oriented.recycle()

            if (token != sessionToken) {
                sourceFile.delete()
                return@launch
            }
            if (!saved) {
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        errorMessage = getApplication<Application>().getString(R.string.photo_error),
                    )
                }
                return@launch
            }

            sourceFiles += sourceFile
            val count = sourceFiles.size
            val backgroundId = _uiState.value.selectedBackgroundId
            _uiState.update {
                it.copy(
                    capturedCount = count,
                    isCapturing = false,
                    isProcessing = count == PhotoBoothUiState.PHOTO_COUNT,
                    errorMessage = null,
                )
            }

            if (count == PhotoBoothUiState.PHOTO_COUNT) {
                composeSession(
                    token = token,
                    backgroundIds = List(PhotoBoothUiState.PHOTO_COUNT) { backgroundId },
                )
            }
        }
    }

    /** Backgrounds are fixed during a capture sequence so all three photos match. */
    fun selectBackground(id: String) {
        val state = _uiState.value
        if (state.hasResult) {
            changeBackground(id)
            return
        }
        if (state.capturedCount > 0 || state.isCapturing || state.isProcessing) return
        _uiState.update { it.copy(selectedBackgroundId = id) }
    }

    /** Re-composites only the selected capture, then rebuilds the strip from all three photos. */
    fun changeBackground(id: String) {
        val state = _uiState.value
        val selectedIndex = state.selectedPhotoIndex ?: return
        if (!state.hasResult || state.isProcessing) return

        val backgroundIds = state.photoBackgroundIds
            .ifEmpty { List(PhotoBoothUiState.PHOTO_COUNT) { state.selectedBackgroundId } }
            .toMutableList()
        if (id == backgroundIds[selectedIndex]) return
        val previousBackgroundId = backgroundIds[selectedIndex]
        backgroundIds[selectedIndex] = id

        val token = sessionToken
        _uiState.update { it.copy(selectedBackgroundId = id, isProcessing = true, errorMessage = null) }
        viewModelScope.launch {
            val success = composeSession(token, backgroundIds, setOf(selectedIndex))
            if (!success && token == sessionToken) {
                _uiState.update { it.copy(selectedBackgroundId = previousBackgroundId) }
            }
        }
    }

    fun retryComposition() {
        val state = _uiState.value
        if (state.capturedCount != PhotoBoothUiState.PHOTO_COUNT || state.hasResult || state.isProcessing) return
        val token = sessionToken
        val backgroundId = state.selectedBackgroundId
        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
        viewModelScope.launch {
            composeSession(
                token = token,
                backgroundIds = List(PhotoBoothUiState.PHOTO_COUNT) { backgroundId },
            )
        }
    }

    private suspend fun composeSession(
        token: Int,
        backgroundIds: List<String>,
        photoIndicesToRender: Set<Int> = (0 until PhotoBoothUiState.PHOTO_COUNT).toSet(),
    ): Boolean {
        val sources = sourceFiles.toList()
        if (sources.size != PhotoBoothUiState.PHOTO_COUNT ||
            backgroundIds.size != PhotoBoothUiState.PHOTO_COUNT
        ) return false
        val currentSessionDirectory = sessionDirectory ?: return false
        val previousState = _uiState.value

        val newOutputDirectory = File(currentSessionDirectory, "render_${UUID.randomUUID()}")
        if (!newOutputDirectory.mkdirs()) {
            failComposition(token)
            return false
        }

        val newPhotoFiles = List(PhotoBoothUiState.PHOTO_COUNT) { index ->
            File(newOutputDirectory, "expo_syscom_2027_${index + 1}.jpg")
        }
        val fallbackIndices = previousState.usedFallbackIndices.toMutableSet()
        val newStripFile = File(newOutputDirectory, "expo_syscom_2027_strip.jpg")
        try {
            for (index in sources.indices) {
                if (token != sessionToken) throw SessionExpiredException()
                val targetFile = newPhotoFiles[index]
                if (index !in photoIndicesToRender) {
                    val previousFile = previousState.photoFiles.getOrNull(index)
                        ?: error("Could not reuse existing framed photo")
                    withContext(Dispatchers.IO) {
                        previousFile.copyTo(targetFile, overwrite = true)
                    }
                    continue
                }

                val background = withContext(Dispatchers.IO) {
                    repository.loadFull(backgroundIds[index])
                } ?: error("Could not load selected background")
                withContext(Dispatchers.Default) {
                    val source = BitmapFactory.decodeFile(sources[index].absolutePath)
                        ?: error("Could not read captured image")
                    val composite = try {
                        processor.composite(source, background)
                    } finally {
                        if (!source.isRecycled) source.recycle()
                    }
                    if (composite.usedFallback) {
                        fallbackIndices += index
                    } else {
                        fallbackIndices -= index
                    }

                    val framed = try {
                        PhotoFrameComposer.framePhoto(composite.bitmap)
                    } finally {
                        if (!composite.bitmap.isRecycled) composite.bitmap.recycle()
                    }
                    val written = try {
                        PhotoFrameComposer.writeJpeg(framed, targetFile)
                    } finally {
                        if (!framed.isRecycled) framed.recycle()
                    }
                    if (!written) error("Could not save framed photo")
                }
            }

            if (token != sessionToken) throw SessionExpiredException()
            withContext(Dispatchers.Default) {
                if (!PhotoFrameComposer.writeStrip(newPhotoFiles, newStripFile)) {
                    error("Could not create photo strip")
                }
            }

            if (token != sessionToken) throw SessionExpiredException()
            val previews = withContext(Dispatchers.IO) {
                newPhotoFiles.map { PhotoFrameComposer.loadPreview(it, sampleSize = 4) }
            }
            val stripPreview = withContext(Dispatchers.IO) {
                PhotoFrameComposer.loadPreview(newStripFile, sampleSize = 8)
            }
            if (token != sessionToken) {
                previews.filterNotNull().forEach { it.recycle() }
                stripPreview?.recycle()
                throw SessionExpiredException()
            }
            if (previews.any { it == null } || stripPreview == null) {
                previews.filterNotNull().forEach { it.recycle() }
                stripPreview?.recycle()
                error("Could not prepare photo previews")
            }

            val oldPreviews = _uiState.value.photoPreviews + listOfNotNull(_uiState.value.stripPreview)
            val oldOutputDirectory = outputDirectory
            outputDirectory = newOutputDirectory
            _uiState.update {
                it.copy(
                    capturedCount = PhotoBoothUiState.PHOTO_COUNT,
                    isCapturing = false,
                    isProcessing = false,
                    photoFiles = newPhotoFiles,
                    photoBackgroundIds = backgroundIds.toList(),
                    photoPreviews = previews.filterNotNull(),
                    stripFile = newStripFile,
                    stripPreview = stripPreview,
                    usedFallbackIndices = fallbackIndices,
                    errorMessage = null,
                )
            }
            oldPreviews.forEach(::recycleLater)
            oldOutputDirectory?.takeIf { it != newOutputDirectory }?.let(::deleteLater)
            return true
        } catch (_: SessionExpiredException) {
            newOutputDirectory.deleteRecursively()
            return false
        } catch (_: Throwable) {
            newOutputDirectory.deleteRecursively()
            failComposition(token)
            return false
        }
    }

    private fun failComposition(token: Int) {
        if (token != sessionToken) return
        _uiState.update {
            it.copy(
                isCapturing = false,
                isProcessing = false,
                errorMessage = getApplication<Application>().getString(R.string.photo_error),
            )
        }
    }

    fun selectResult(index: Int?) {
        if (index != null && index !in 0 until PhotoBoothUiState.PHOTO_COUNT) return
        _uiState.update { state ->
            state.copy(
                selectedPhotoIndex = index,
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
        val state = _uiState.value
        val previews = state.photoPreviews + listOfNotNull(state.stripPreview)
        val oldDirectory = sessionDirectory
        sourceFiles.clear()
        sessionDirectory = null
        outputDirectory = null
        _uiState.value = PhotoBoothUiState(
            selectedBackgroundId = if (resetBackground) "gala" else state.selectedBackgroundId,
        )
        previews.forEach(::recycleLater)
        oldDirectory?.let(::deleteLater)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun loadThumbnail(id: String): Bitmap? = repository.loadThumbnail(id)

    private fun ensureSessionDirectory(): File {
        sessionDirectory?.let { return it }
        val directory = File(getApplication<Application>().cacheDir, "photo-booth-session-${UUID.randomUUID()}")
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
        processor.close()
        repository.clear()
        (_uiState.value.photoPreviews + listOfNotNull(_uiState.value.stripPreview))
            .forEach { if (!it.isRecycled) it.recycle() }
        sessionDirectory?.deleteRecursively()
    }

    private class SessionExpiredException : RuntimeException()
}
