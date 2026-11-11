package com.photobooth.kiosk.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureOrientationPolicyTest {
    @Test
    fun `manual rotation is added to the CameraX rotation`() {
        assertEquals(0, manualRotation(0, 0))
        assertEquals(90, manualRotation(0, 90))
        assertEquals(270, manualRotation(270, 0))
        assertEquals(0, manualRotation(270, 90))
        assertEquals(180, manualRotation(270, 270))
        assertEquals(270, manualRotation(-90, 0))
    }

    @Test
    fun `preview and output rotations stay independent`() {
        val cameraX = 270
        assertEquals(0, manualRotation(cameraX, 90))
        assertEquals(270, manualRotation(cameraX, 0))
    }
}
