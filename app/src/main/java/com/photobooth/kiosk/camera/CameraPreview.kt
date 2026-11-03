package com.photobooth.kiosk.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    captureRequest: Int,
    onReady: () -> Unit,
    onCaptured: (Bitmap, Int, Boolean) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isFrontCamera by remember { mutableStateOf(false) }
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnError by rememberUpdatedState(onError)

    DisposableEffect(lifecycleOwner, previewView) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        val listener = Runnable {
            try {
                val cameraProvider = future.get()
                provider = cameraProvider

                val useFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                val selector = if (useFront) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                isFrontCamera = useFront

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                @Suppress("DEPRECATION")
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetResolution(Size(1920, 1080))
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                imageCapture = capture
                currentOnReady()
            } catch (error: Throwable) {
                imageCapture = null
                currentOnError(error.message ?: "Camera initialization failed")
            }
        }

        future.addListener(listener, mainExecutor)

        onDispose {
            imageCapture = null
            if (future.isDone) {
                runCatching { provider?.unbindAll() }
            } else {
                future.cancel(true)
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )

    LaunchedEffect(captureRequest) {
        if (captureRequest <= 0) return@LaunchedEffect
        val capture = imageCapture ?: return@LaunchedEffect
        val mirror = isFrontCamera

        capture.takePicture(
            mainExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()

                    scope.launch(Dispatchers.Default) {
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        withContext(Dispatchers.Main) {
                            if (bitmap == null) {
                                currentOnError("Could not decode captured image")
                            } else {
                                currentOnCaptured(bitmap, rotationDegrees, mirror)
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    currentOnError(exception.message ?: "Capture failed")
                }
            },
        )
    }
}
