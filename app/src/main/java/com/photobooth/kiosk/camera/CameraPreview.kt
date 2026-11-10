package com.photobooth.kiosk.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.photobooth.kiosk.photo.BitmapTransforms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "CameraPreview"
private const val HAND_ANALYSIS_INTERVAL_MS = 250L

private val EXTERNAL_CAMERA_SELECTOR = CameraSelector.Builder()
    .addCameraFilter { cameras ->
        cameras.filter { it.lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL }
    }
    .build()

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    captureRequest: Int,
    onReady: () -> Unit,
    onCaptured: (Bitmap, Boolean) -> Unit,
    onError: (String) -> Unit,
    handDetectionEnabled: Boolean = false,
    onPalmDetected: () -> Unit = {},
    previewRotationDegrees: Int = 0,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val onPalmDetectedState = rememberUpdatedState(onPalmDetected)
    val handDetectionEnabledState = remember { AtomicBoolean(false) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val gestureDetector = remember(context) {
        HandGestureDetector(context) {
            mainExecutor.execute { onPalmDetectedState.value() }
        }
    }

    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(handDetectionEnabled) {
        handDetectionEnabledState.set(handDetectionEnabled)
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var isExternalCamera by remember { mutableStateOf(false) }
    // One correction for both preview and capture. A built-in camera keeps 0 so a
    // value tuned for the USB webcam never leaks into the front-camera fallback.
    val mountRotation = mountRotationFor(isExternalCamera, previewRotationDegrees)
    val currentMountRotation by rememberUpdatedState(mountRotation)
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnError by rememberUpdatedState(onError)

    DisposableEffect(lifecycleOwner, previewView) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        val listener = Runnable {
            if (disposed) return@Runnable
            try {
                val cameraProvider = future.get()
                provider = cameraProvider

                val useExternal = runCatching {
                    cameraProvider.hasCamera(EXTERNAL_CAMERA_SELECTOR)
                }.getOrDefault(false)
                val useFront = !useExternal &&
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                val selector = when {
                    useExternal -> EXTERNAL_CAMERA_SELECTOR
                    useFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> CameraSelector.DEFAULT_BACK_CAMERA
                }
                isFrontCamera = useFront
                isExternalCamera = useExternal

                // Pin the rotation so imageInfo.rotationDegrees only depends on the
                // camera hardware, not on the display rotation of the host device.
                // Without this the same build behaves differently on a portrait
                // tablet and on a landscape 55" kiosk panel.
                val preview = Preview.Builder()
                    .setTargetRotation(Surface.ROTATION_0)
                    .build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                @Suppress("DEPRECATION")
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(Surface.ROTATION_0)
                    // Portrait target: a landscape request makes CameraX crop the field of
                    // view to 16:9, so the saved photo ends up tighter than the preview.
                    .setTargetResolution(Size(1080, 1920))
                    .build()

                @Suppress("DEPRECATION")
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(Surface.ROTATION_0)
                    .setTargetResolution(Size(640, 480))
                    .build()
                var lastAnalysisAt = 0L
                analysis.setAnalyzer(analysisExecutor) { image ->
                    try {
                        if (handDetectionEnabledState.get()) {
                            val now = SystemClock.uptimeMillis()
                            if (now - lastAnalysisAt >= HAND_ANALYSIS_INTERVAL_MS) {
                                lastAnalysisAt = now
                                val bitmap = image.toBitmap()
                                // Same policy as capture: the gesture model must see the
                                // frame exactly as the guest and the photo do.
                                val rotation = effectiveCaptureRotation(
                                    image.imageInfo.rotationDegrees,
                                    currentMountRotation,
                                )
                                val upright = if (rotation == 0) {
                                    bitmap
                                } else {
                                    BitmapTransforms.rotateDegrees(bitmap, rotation)
                                }
                                if (upright !== bitmap && !bitmap.isRecycled) {
                                    bitmap.recycle()
                                }
                                gestureDetector.analyze(upright, now)
                            }
                        }
                    } catch (error: Throwable) {
                        Log.w(TAG, "Hand analysis failed", error)
                    } finally {
                        image.close()
                    }
                }

                cameraProvider.unbindAll()
                Log.i(
                    TAG,
                    "Binding camera external=$useExternal front=$useFront " +
                        "displayRotation=${previewView.display?.rotation ?: Surface.ROTATION_0} " +
                        "configuredMount=$previewRotationDegrees targetRotation=${Surface.ROTATION_0}",
                )
                try {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        capture,
                        analysis,
                    )
                } catch (error: Throwable) {
                    Log.w(TAG, "Hand analysis unavailable, using camera without it", error)
                    analysis.clearAnalyzer()
                    runCatching { cameraProvider.unbindAll() }
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                }
                imageCapture = capture
                currentOnReady()
            } catch (error: Throwable) {
                imageCapture = null
                currentOnError(error.message ?: "Camera initialization failed")
            }
        }

        future.addListener(listener, mainExecutor)

        onDispose {
            disposed = true
            imageCapture = null
            analysisExecutor.shutdownNow()
            gestureDetector.close()
            if (future.isDone) {
                runCatching { provider?.unbindAll() }
            } else {
                future.cancel(true)
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val extraRotation = ((mountRotation % 360) + 360) % 360
        val fillScale = if (extraRotation == 90 || extraRotation == 270) {
            maxOf(maxHeight / maxWidth, maxWidth / maxHeight)
        } else {
            1f
        }
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = extraRotation.toFloat()
                    scaleX = fillScale
                    scaleY = fillScale
                },
        )
    }

    LaunchedEffect(captureRequest) {
        if (captureRequest <= 0) return@LaunchedEffect
        val capture = imageCapture ?: run {
            currentOnError("Camera is not ready")
            return@LaunchedEffect
        }
        val mirror = isFrontCamera

        capture.takePicture(
            mainExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val cameraXRotationDegrees = image.imageInfo.rotationDegrees
                    val rotationDegrees = effectiveCaptureRotation(
                        cameraXRotationDegrees,
                        mountRotation,
                    )
                    Log.i(
                        TAG,
                        "Captured ${image.width}x${image.height} " +
                            "cameraX=$cameraXRotationDegrees mount=$mountRotation " +
                            "applied=$rotationDegrees",
                    )
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()

                    scope.launch(Dispatchers.Default) {
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap == null) {
                            withContext(Dispatchers.Main) {
                                currentOnError("Could not decode captured image")
                            }
                            return@launch
                        }
                        val oriented = try {
                            if (rotationDegrees == 0) {
                                bitmap
                            } else {
                                BitmapTransforms.rotateDegrees(bitmap, rotationDegrees).also {
                                    if (!bitmap.isRecycled) bitmap.recycle()
                                }
                            }
                        } catch (error: Throwable) {
                            if (!bitmap.isRecycled) bitmap.recycle()
                            withContext(Dispatchers.Main) {
                                currentOnError(error.message ?: "Could not orient captured image")
                            }
                            return@launch
                        }
                        var ownershipTransferred = false
                        try {
                            withContext(Dispatchers.Main) {
                                ownershipTransferred = true
                                currentOnCaptured(oriented, mirror)
                            }
                        } finally {
                            if (!ownershipTransferred && !oriented.isRecycled) {
                                oriented.recycle()
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
