package com.photobooth.kiosk.camera

/**
 * Clockwise rotation to apply to a captured frame.
 *
 * The preview shows `cameraX + mount` degrees: CameraX rotates the stream by
 * [cameraXRotationDegrees] and the mount correction is drawn on top of it. The
 * saved photo must use exactly the same sum, otherwise the guest sees a straight
 * preview and receives a rotated image.
 */
fun effectiveCaptureRotation(
    cameraXRotationDegrees: Int,
    mountRotationDegrees: Int,
): Int = ((cameraXRotationDegrees + mountRotationDegrees) % 360 + 360) % 360

/**
 * Mount correction for the camera that is actually in use.
 *
 * The manual correction exists because a webcam cannot report how it is
 * physically mounted. Built-in cameras are fully described by their metadata, so
 * a correction tuned for a USB camera must never leak into them when the kiosk
 * falls back to the front camera.
 */
fun mountRotationFor(external: Boolean, configuredDegrees: Int): Int =
    if (external) ((configuredDegrees % 360) + 360) % 360 else 0
