package com.photobooth.kiosk.camera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbManager
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.photobooth.kiosk.data.CameraAdjustments
import com.photobooth.kiosk.photo.BitmapTransforms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
    adjustments: CameraAdjustments = CameraAdjustments(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val onPalmDetectedState = rememberUpdatedState(onPalmDetected)
    val handDetectionEnabledState = remember { AtomicBoolean(false) }
    val previewRotationDegrees = remember { AtomicInteger(0) }
    val outputRotationDegrees = remember { AtomicInteger(0) }
    val outputMirrored = remember { AtomicBoolean(false) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val gestureDetector = remember(context) { sharedHandGestureDetector(context) }
    val leftScreen = remember { AtomicBoolean(false) }

    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(handDetectionEnabled) {
        handDetectionEnabledState.set(handDetectionEnabled)
    }
    LaunchedEffect(adjustments) {
        val value = adjustments.normalized()
        previewRotationDegrees.set(value.previewRotation)
        outputRotationDegrees.set(value.outputRotation)
        outputMirrored.set(value.outputMirrored)
    }

    DisposableEffect(gestureDetector) {
        gestureDetector.listener = {
            mainExecutor.execute {
                if (gestureDetector.enabled && !leftScreen.get()) onPalmDetectedState.value()
            }
        }
        gestureDetector.enabled = true
        onDispose {
            gestureDetector.enabled = false
            gestureDetector.listener = {}
        }
    }

    // Declared before the camera effect so it runs after the camera is unbound.
    DisposableEffect(analysisExecutor) {
        onDispose { analysisExecutor.shutdown() }
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var bindGeneration by remember { mutableIntStateOf(0) }
    var analysisEnabled by remember { mutableStateOf(true) }
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnError by rememberUpdatedState(onError)

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (leftScreen.get()) return
                analysisEnabled = true
                bindGeneration += 1
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    DisposableEffect(lifecycleOwner, previewView, bindGeneration, analysisEnabled) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val disposed = AtomicBoolean(false)
        var stateObserver: Observer<androidx.camera.core.CameraState>? = null
        var boundCamera: androidx.camera.core.Camera? = null
        var analysisUseCase: ImageAnalysis? = null

        val listener = Runnable {
            if (disposed.get()) return@Runnable
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val choice = chooseCamera(context, cameraProvider)
                val rotation = displayRotation(previewView)
                Log.i(
                    TAG,
                    "bind external=${choice.isExternal} targetRotation=$rotation analysis=$analysisEnabled",
                )

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                    it.targetRotation = rotation
                }
                @Suppress("DEPRECATION")
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    // Portrait target: a landscape request makes CameraX crop the field of
                    // view to 16:9, so the saved photo ends up tighter than the preview.
                    .setTargetResolution(Size(1080, 1920))
                    .setTargetRotation(rotation)
                    .build()

                val useCases = mutableListOf<UseCase>(preview, capture)
                var analysis: ImageAnalysis? = null
                if (analysisEnabled) {
                    @Suppress("DEPRECATION")
                    analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(Size(640, 480))
                        .setTargetRotation(rotation)
                        .build()
                    var lastAnalysisAt = 0L
                    analysis.setAnalyzer(analysisExecutor) { image ->
                        try {
                            if (handDetectionEnabledState.get()) {
                                val now = SystemClock.uptimeMillis()
                                if (now - lastAnalysisAt >= HAND_ANALYSIS_INTERVAL_MS) {
                                    lastAnalysisAt = now
                                    val upright = uprightBitmap(
                                        image,
                                        previewRotationDegrees.get(),
                                    )
                                    gestureDetector.analyze(upright, now)
                                }
                            }
                        } catch (error: Throwable) {
                            Log.w(TAG, "Hand analysis failed", error)
                        } finally {
                            image.close()
                        }
                    }
                    useCases += analysis
                    analysisUseCase = analysis
                }

                cameraProvider.unbindAll()
                val camera = try {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        choice.selector,
                        *useCases.toTypedArray(),
                    )
                } catch (error: Throwable) {
                    if (analysis == null) throw error
                    Log.w(TAG, "Hand analysis unavailable, using camera without it", error)
                    analysis.clearAnalyzer()
                    runCatching { cameraProvider.unbindAll() }
                    // Rebind once without the analysis stream. Some USB cameras drop
                    // off the bus when a third stream is opened.
                    analysisEnabled = false
                    return@Runnable
                }
                boundCamera = camera
                val observer = Observer<androidx.camera.core.CameraState> { state ->
                    val cameraError = state.error ?: return@Observer
                    if (disposed.get() || analysis == null || !analysisEnabled) return@Observer
                    Log.w(TAG, "Camera dropped during hand analysis: ${cameraError.code}")
                    analysisEnabled = false
                }
                stateObserver = observer
                camera.cameraInfo.cameraState.observe(lifecycleOwner, observer)
                imageCapture = capture
                currentOnReady()
            } catch (error: Throwable) {
                imageCapture = null
                currentOnError(error.message ?: "Camera initialization failed")
            }
        }

        future.addListener(listener, mainExecutor)

        onDispose {
            leftScreen.set(true)
            disposed.set(true)
            gestureDetector.enabled = false
            gestureDetector.listener = {}
            handDetectionEnabledState.set(false)
            runCatching { analysisUseCase?.clearAnalyzer() }
            imageCapture = null
            stateObserver?.let { observer ->
                boundCamera?.cameraInfo?.cameraState?.removeObserver(observer)
            }
            if (future.isDone) {
                runCatching { provider?.unbindAll() }
            } else {
                future.cancel(true)
            }
        }
    }

    val framed = adjustments.normalized()
    BoxWithConstraints(modifier) {
        val rotation = framed.previewRotation
        val fillScale = if (
            (rotation == 90 || rotation == 270) && maxWidth.value > 0f && maxHeight.value > 0f
        ) {
            maxOf(maxHeight / maxWidth, maxWidth / maxHeight)
        } else {
            1f
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = if (framed.previewMirrored) -1f else 1f },
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = rotation.toFloat()
                        scaleX = fillScale
                        scaleY = fillScale
                    },
            )
        }
    }

    LaunchedEffect(captureRequest) {
        if (captureRequest <= 0) return@LaunchedEffect
        val capture = imageCapture ?: run {
            currentOnError("Camera is not ready")
            return@LaunchedEffect
        }
        val mirror = outputMirrored.get()

        capture.takePicture(
            mainExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotationDegrees = manualRotation(
                        image.imageInfo.rotationDegrees,
                        outputRotationDegrees.get(),
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
                            rotateBitmap(bitmap, rotationDegrees)
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

private fun sharedHandGestureDetector(context: Context): HandGestureDetector =
    SharedHandGesture.detector ?: HandGestureDetector(context.applicationContext).also {
        SharedHandGesture.detector = it
    }

private object SharedHandGesture {
    var detector: HandGestureDetector? = null
}

private fun uprightBitmap(image: ImageProxy, extraDegrees: Int): Bitmap {
    val bitmap = image.toBitmap()
    val rotation = manualRotation(image.imageInfo.rotationDegrees, extraDegrees)
    return rotateBitmap(bitmap, rotation)
}

private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    return BitmapTransforms.rotateDegrees(bitmap, degrees).also {
        if (it !== bitmap && !bitmap.isRecycled) bitmap.recycle()
    }
}

private data class CameraChoice(
    val selector: CameraSelector,
    val isExternal: Boolean,
)

private fun chooseCamera(context: Context, provider: ProcessCameraProvider): CameraChoice {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    var externalId: String? = null
    var frontId: String? = null
    for (id in manager.cameraIdList) {
        val facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
        when (facing) {
            CameraCharacteristics.LENS_FACING_EXTERNAL -> if (externalId == null) externalId = id
            CameraCharacteristics.LENS_FACING_FRONT -> if (frontId == null) frontId = id
        }
    }
    val useExternal = externalId != null && runCatching {
        provider.hasCamera(EXTERNAL_CAMERA_SELECTOR)
    }.getOrDefault(false)
    val useFront = !useExternal && frontId != null &&
        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
    val selector = when {
        useExternal -> EXTERNAL_CAMERA_SELECTOR
        useFront -> CameraSelector.DEFAULT_FRONT_CAMERA
        else -> CameraSelector.DEFAULT_BACK_CAMERA
    }
    return CameraChoice(selector = selector, isExternal = useExternal)
}

@Suppress("DEPRECATION")
private fun displayRotation(view: View): Int {
    view.display?.rotation?.let { return it }
    val window = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return window.defaultDisplay?.rotation ?: Surface.ROTATION_0
}
