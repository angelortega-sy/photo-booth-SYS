package com.photobooth.kiosk.ui

import androidx.compose.runtime.Composable
import com.photobooth.kiosk.ui.admin.AdminShell

/**
 * Admin entry point used by [PhotoBoothApp]. The PIN gate, navigation and all
 * admin screens live in `ui.admin`; this wrapper only preserves the public
 * entry-point signature so no caller needs to change.
 */
@Composable
fun AdminGate(
    viewModel: PhotoBoothViewModel,
    onClose: () -> Unit,
) {
    AdminShell(
        viewModel = viewModel,
        onClose = onClose,
    )
}
