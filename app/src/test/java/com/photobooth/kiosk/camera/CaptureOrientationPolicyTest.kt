package com.photobooth.kiosk.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureOrientationPolicyTest {
    @Test
    fun `capture rotation subtracts preview visual correction from CameraX rotation`() {
        listOf(
            CaptureRotationCase(cameraX = 270, preview = 270, expected = 0),
            CaptureRotationCase(cameraX = 270, preview = 0, expected = 270),
            CaptureRotationCase(cameraX = 90, preview = 270, expected = 180),
        ).forEach { case ->
            assertEquals(
                "CameraX=${case.cameraX}, preview=${case.preview}",
                case.expected,
                effectiveCaptureRotation(case.cameraX, case.preview),
            )
        }
    }

    private data class CaptureRotationCase(
        val cameraX: Int,
        val preview: Int,
        val expected: Int,
    )
}
