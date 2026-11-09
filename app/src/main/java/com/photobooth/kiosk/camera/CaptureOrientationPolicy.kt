package com.photobooth.kiosk.camera

fun effectiveCaptureRotation(
    cameraXRotationDegrees: Int,
    previewRotationDegrees: Int,
): Int = ((cameraXRotationDegrees - previewRotationDegrees) % 360 + 360) % 360
