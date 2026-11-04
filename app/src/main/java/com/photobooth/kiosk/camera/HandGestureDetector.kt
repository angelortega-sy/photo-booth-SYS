package com.photobooth.kiosk.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult

/**
 * Detects an open palm and fires once per presentation.
 *
 * The gesture recognizer is created lazily so the model is only loaded when
 * hand detection is actually enabled.
 */
class HandGestureDetector(
    context: Context,
    private val onPalmDetected: () -> Unit,
) : AutoCloseable {

    private val appContext = context.applicationContext
    private val lock = Any()
    private var recognizer: GestureRecognizer? = null
    private var initialized = false
    private var lastTriggerAt = 0L
    private var palmMissingSince = 0L
    private var consecutivePalmFrames = 0
    private var armed = true

    fun analyze(bitmap: Bitmap, timestampMs: Long) {
        val current = ensureInitialized() ?: return
        runCatching {
            current.recognizeAsync(BitmapImageBuilder(bitmap).build(), timestampMs)
        }.onFailure {
            Log.w(TAG, "Could not analyze hand frame", it)
        }
    }

    private fun ensureInitialized(): GestureRecognizer? {
        synchronized(lock) {
            if (initialized) return recognizer
            initialized = true
            recognizer = runCatching {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .setDelegate(Delegate.CPU)
                    .build()
                val options = GestureRecognizer.GestureRecognizerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumHands(1)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setResultListener { result, _ -> handleResult(result) }
                    .setErrorListener { error ->
                        Log.w(TAG, "Gesture recognizer error", error)
                    }
                    .build()
                GestureRecognizer.createFromOptions(appContext, options)
            }.onSuccess {
                Log.i(TAG, "Hand gesture recognizer ready")
            }.onFailure {
                Log.e(TAG, "Could not initialize hand gesture recognizer", it)
            }.getOrNull()
            return recognizer
        }
    }

    private fun handleResult(result: GestureRecognizerResult) {
        val gesture = result.gestures()
            .asSequence()
            .mapNotNull { it.firstOrNull() }
            .maxByOrNull { it.score() }
        val isPalm = gesture?.categoryName().orEmpty()
            .equals(OPEN_PALM, ignoreCase = true) &&
            (gesture?.score() ?: 0f) >= MIN_SCORE

        val now = SystemClock.uptimeMillis()
        var trigger = false
        synchronized(lock) {
            if (isPalm) {
                palmMissingSince = 0L
                consecutivePalmFrames += 1
                if (armed &&
                    consecutivePalmFrames >= REQUIRED_FRAMES &&
                    now - lastTriggerAt >= TRIGGER_COOLDOWN_MS
                ) {
                    armed = false
                    consecutivePalmFrames = 0
                    lastTriggerAt = now
                    trigger = true
                }
            } else {
                consecutivePalmFrames = 0
                if (!armed) {
                    if (palmMissingSince == 0L) palmMissingSince = now
                    if (now - palmMissingSince >= REARM_MS) {
                        armed = true
                        palmMissingSince = 0L
                    }
                }
            }
        }
        if (trigger) {
            Log.i(TAG, "Open palm detected; starting countdown")
            onPalmDetected()
        }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { recognizer?.close() }
            recognizer = null
            initialized = true
        }
    }

    private companion object {
        const val TAG = "HandGesture"
        const val MODEL_ASSET = "models/gesture_recognizer.task"
        const val OPEN_PALM = "Open_Palm"
        const val MIN_SCORE = 0.55f
        const val REQUIRED_FRAMES = 2
        const val REARM_MS = 900L
        const val TRIGGER_COOLDOWN_MS = 1_500L
    }
}
