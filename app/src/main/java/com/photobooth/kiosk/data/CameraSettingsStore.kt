package com.photobooth.kiosk.data

import android.content.Context

/**
 * Manual camera framing. Preview and the saved photo are independent:
 * a kiosk can show the live view one way and store the file another way.
 */
data class CameraAdjustments(
    val previewRotation: Int = 0,
    val outputRotation: Int = 0,
    val previewMirrored: Boolean = false,
    val outputMirrored: Boolean = false,
) {
    fun normalized(): CameraAdjustments = copy(
        previewRotation = previewRotation.quarterTurn(),
        outputRotation = outputRotation.quarterTurn(),
    )
}

fun Int.quarterTurn(): Int {
    val normalized = ((this % 360) + 360) % 360
    return if (normalized % 90 == 0) normalized else 0
}

class CameraSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): CameraAdjustments = CameraAdjustments(
        previewRotation = prefs.getInt(KEY_PREVIEW_ROTATION, 0).quarterTurn(),
        outputRotation = prefs.getInt(KEY_OUTPUT_ROTATION, 0).quarterTurn(),
        previewMirrored = prefs.getBoolean(KEY_PREVIEW_MIRROR, false),
        outputMirrored = prefs.getBoolean(KEY_OUTPUT_MIRROR, false),
    )

    fun save(adjustments: CameraAdjustments) {
        val value = adjustments.normalized()
        prefs.edit()
            .putInt(KEY_PREVIEW_ROTATION, value.previewRotation)
            .putInt(KEY_OUTPUT_ROTATION, value.outputRotation)
            .putBoolean(KEY_PREVIEW_MIRROR, value.previewMirrored)
            .putBoolean(KEY_OUTPUT_MIRROR, value.outputMirrored)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "camera_settings"
        const val KEY_PREVIEW_ROTATION = "preview_rotation"
        const val KEY_OUTPUT_ROTATION = "output_rotation"
        const val KEY_PREVIEW_MIRROR = "preview_mirrored"
        const val KEY_OUTPUT_MIRROR = "output_mirrored"
    }
}
