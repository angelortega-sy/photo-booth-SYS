package com.photobooth.kiosk.camera

/** Adds the operator's rotation on top of the rotation CameraX already reports. */
fun manualRotation(cameraXRotationDegrees: Int, manualDegrees: Int): Int =
    (cameraXRotationDegrees.mod(360) + manualDegrees.mod(360)).mod(360)
