package com.photobooth.kiosk.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureOrientationPolicyTest {
    @Test
    fun `capture rotation adds the mount correction to the CameraX rotation`() {
        listOf(
            CaptureRotationCase(cameraX = 0, mount = 0, expected = 0),
            CaptureRotationCase(cameraX = 0, mount = 90, expected = 90),
            CaptureRotationCase(cameraX = 0, mount = 270, expected = 270),
            CaptureRotationCase(cameraX = 270, mount = 0, expected = 270),
            CaptureRotationCase(cameraX = 270, mount = 270, expected = 180),
            CaptureRotationCase(cameraX = 90, mount = 270, expected = 0),
            CaptureRotationCase(cameraX = 0, mount = -90, expected = 270),
            CaptureRotationCase(cameraX = 0, mount = 450, expected = 90),
        ).forEach { case ->
            assertEquals(
                "CameraX=${case.cameraX}, mount=${case.mount}",
                case.expected,
                effectiveCaptureRotation(case.cameraX, case.mount),
            )
        }
    }

    @Test
    fun `sideways usb camera keeps the photo matching the preview`() {
        // Regression: subtracting the correction left the photo 180 degrees flipped
        // while the preview looked straight.
        val cameraX = 0
        val mount = 90
        val previewShows = (cameraX + mount) % 360
        assertEquals(previewShows, effectiveCaptureRotation(cameraX, mount))
    }

    @Test
    fun `mount correction applies only to external cameras`() {
        assertEquals(270, mountRotationFor(external = true, configuredDegrees = 270))
        assertEquals(0, mountRotationFor(external = false, configuredDegrees = 270))
        assertEquals(0, mountRotationFor(external = true, configuredDegrees = 360))
        assertEquals(90, mountRotationFor(external = true, configuredDegrees = -270))
    }

    private data class CaptureRotationCase(
        val cameraX: Int,
        val mount: Int,
        val expected: Int,
    )
}
