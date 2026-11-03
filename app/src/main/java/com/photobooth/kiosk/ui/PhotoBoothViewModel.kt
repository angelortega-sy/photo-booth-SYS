package com.photobooth.kiosk.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.BackgroundOption
import com.photobooth.kiosk.data.BackgroundRepository
import com.photobooth.kiosk.photo.BitmapTransforms
import com.photobooth.kiosk.photo.PhotoProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PhotoBoothUiState(
    val selectedBackgroundId: String = "gala",
    val isProcessing: Boolean = false,
    val resultBitmap: Bitmap? = null,
    val usedFallback: Boolean = false,
    val errorMessage: String? = null,
)

class PhotoBoothViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BackgroundRepository(application)
    private val processor = PhotoProcessor(application)

    /** Kept so the guest can switch backgrounds without taking the photo again. */
    private var capturedPhoto: Bitmap? = null

    /** Invalidates late camera callbacks after the guest leaves the session. */
    private var sessionToken = 0

    private val _uiState = MutableStateFlow(PhotoBoothUiState())
    val uiState: StateFlow<PhotoBoothUiState> = _uiState.asStateFlow()

    val backgrounds: List<BackgroundOption> = repository.options

    fun selectBackground(id: String) {
        if (id == _uiState.value.selectedBackgroundId) return
        _uiState.update { it.copy(selectedBackgroundId = id) }
    }

    fun processPhoto(photo: Bitmap, rotationDegrees: Int, mirror: Boolean) {
        if (_uiState.value.isProcessing) {
            photo.recycle()
            return
        }

        _uiState.update {
            it.copy(
                isProcessing = true,
                errorMessage = null,
                resultBitmap = null,
                usedFallback = false,
            )
        }

        val token = sessionToken
        viewModelScope.launch {
            val oriented = withContext(Dispatchers.Default) {
                runCatching {
                    val rotated = BitmapTransforms.rotateDegrees(photo, rotationDegrees)
                    if (rotated !== photo) photo.recycle()

                    val mirrored = if (mirror) {
                        BitmapTransforms.mirrorHorizontally(rotated)
                    } else {
                        rotated
                    }
                    if (mirrored !== rotated) rotated.recycle()
                    mirrored
                }.getOrElse {
                    if (!photo.isRecycled) photo.recycle()
                    null
                }
            }

            if (token != sessionToken) {
                oriented?.let { recycleLater(it) }
                return@launch
            }

            if (oriented == null) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = getApplication<Application>().getString(R.string.photo_error),
                    )
                }
                return@launch
            }

            capturedPhoto?.let { recycleLater(it) }
            capturedPhoto = oriented
            renderComposite(oriented)
        }
    }

    /** Re-composites the current capture over a different background. */
    fun changeBackground(id: String) {
        if (id == _uiState.value.selectedBackgroundId) return
        _uiState.update { it.copy(selectedBackgroundId = id) }

        val source = capturedPhoto ?: return
        if (_uiState.value.resultBitmap == null || _uiState.value.isProcessing) return

        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
        viewModelScope.launch { renderComposite(source) }
    }

    private suspend fun renderComposite(source: Bitmap) {
        val background = repository.loadFull(_uiState.value.selectedBackgroundId)
        val result = if (background == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                runCatching { processor.composite(source, background) }.getOrNull()
            }
        }

        if (result == null) {
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    errorMessage = getApplication<Application>().getString(R.string.photo_error),
                )
            }
            return
        }

        val previous = _uiState.value.resultBitmap
        _uiState.update {
            it.copy(
                isProcessing = false,
                resultBitmap = result.bitmap,
                usedFallback = result.usedFallback,
            )
        }
        previous?.let { recycleLater(it) }
    }

    fun clearResult() {
        sessionToken += 1
        val result = _uiState.value.resultBitmap
        val source = capturedPhoto
        capturedPhoto = null
        _uiState.update {
            it.copy(
                resultBitmap = null,
                isProcessing = false,
                usedFallback = false,
                errorMessage = null,
            )
        }
        result?.let { recycleLater(it) }
        source?.let { recycleLater(it) }
    }

    /** Ends the guest session and returns a clean state for the welcome screen. */
    fun endSession() {
        sessionToken += 1
        val result = _uiState.value.resultBitmap
        val source = capturedPhoto
        capturedPhoto = null
        _uiState.update {
            it.copy(
                selectedBackgroundId = "gala",
                resultBitmap = null,
                isProcessing = false,
                usedFallback = false,
                errorMessage = null,
            )
        }
        result?.let { recycleLater(it) }
        source?.let { recycleLater(it) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun loadThumbnail(id: String): Bitmap? = repository.loadThumbnail(id)

    /** Delays recycling so Compose has time to stop drawing the old bitmap. */
    private fun recycleLater(bitmap: Bitmap) {
        viewModelScope.launch {
            delay(750L)
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    override fun onCleared() {
        super.onCleared()
        processor.close()
        repository.clear()
        capturedPhoto?.let { if (!it.isRecycled) it.recycle() }
        _uiState.value.resultBitmap?.let { if (!it.isRecycled) it.recycle() }
    }
}
