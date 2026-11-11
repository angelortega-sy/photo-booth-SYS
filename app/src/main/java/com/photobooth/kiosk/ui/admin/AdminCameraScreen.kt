package com.photobooth.kiosk.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.admin.CameraOption
import com.photobooth.kiosk.admin.DeviceSummary
import com.photobooth.kiosk.admin.UsbDeviceOption
import com.photobooth.kiosk.camera.CameraPreview
import com.photobooth.kiosk.data.CameraAdjustments
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.SyscomBlueLight

@Composable
internal fun AdminCameraScreen(
    summary: DeviceSummary?,
    cameras: List<CameraOption>,
    usbDevices: List<UsbDeviceOption>,
    adjustments: CameraAdjustments,
    onAdjustmentsChange: (CameraAdjustments) -> Unit,
    palmEnabled: Boolean,
    onPalmToggle: (Boolean) -> Unit,
    onSaved: () -> Unit,
) {
    var showCameraTest by remember { mutableStateOf(false) }
    var cameraTestError by remember { mutableStateOf<String?>(null) }
    var draftPalm by remember(palmEnabled) { mutableStateOf(palmEnabled) }
    var draftAdjustments by remember(adjustments) { mutableStateOf(adjustments) }
    val dirty = draftPalm != palmEnabled || draftAdjustments != adjustments

    AdminSettingsScreen(
        dirty = dirty,
        onSave = {
            if (draftPalm != palmEnabled) onPalmToggle(draftPalm)
            if (draftAdjustments != adjustments) onAdjustmentsChange(draftAdjustments)
            onSaved()
        },
        onDiscard = {
            draftPalm = palmEnabled
            draftAdjustments = adjustments
        },
    ) {
        item { DeviceCard(summary) }
        item {
            CameraCard(
                cameras = cameras,
                usbDevices = usbDevices,
                showCameraTest = showCameraTest,
                cameraTestError = cameraTestError,
                onToggleTest = {
                    showCameraTest = !showCameraTest
                    cameraTestError = null
                },
                onCameraError = { cameraTestError = it },
                adjustments = draftAdjustments,
                onAdjustmentsChange = { draftAdjustments = it },
            )
        }
        item {
            GesturesCard(
                palmEnabled = draftPalm,
                onPalmToggle = { draftPalm = it },
            )
        }
    }
}

@Composable
private fun DeviceCard(summary: DeviceSummary?) {
    AdminCard(
        title = stringResource(R.string.admin_section_device),
        subtitle = "Datos del equipo donde corre la cabina",
    ) {
        if (summary == null) {
            Text("Leyendo información…", color = Cream.copy(alpha = 0.55f))
        } else {
            InfoRow("Modelo", summary.model)
            InfoRow("Fabricante", summary.manufacturer)
            InfoRow("Android", "${summary.androidVersion} (API ${summary.sdk})")
            InfoRow("Pantalla", summary.screen)
            InfoRow("Densidad", summary.density)
            InfoRow("Versión", summary.appVersion)
        }
    }
}

@Composable
private fun GesturesCard(
    palmEnabled: Boolean,
    onPalmToggle: (Boolean) -> Unit,
) {
    AdminCard(
        title = stringResource(R.string.admin_gestures_title),
        subtitle = stringResource(R.string.admin_gestures_subtitle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(role = Role.Switch) { onPalmToggle(!palmEnabled) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.admin_gestures_palm),
                    color = Cream,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        if (palmEnabled) {
                            R.string.admin_gestures_palm_on
                        } else {
                            R.string.admin_gestures_palm_off
                        },
                    ),
                    color = Color(0xFF9AC2F4),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = palmEnabled, onCheckedChange = onPalmToggle)
        }
        Text(
            text = stringResource(R.string.admin_gestures_palm_hint),
            color = Cream.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CameraCard(
    cameras: List<CameraOption>,
    usbDevices: List<UsbDeviceOption>,
    showCameraTest: Boolean,
    cameraTestError: String?,
    onToggleTest: () -> Unit,
    onCameraError: (String) -> Unit,
    adjustments: CameraAdjustments,
    onAdjustmentsChange: (CameraAdjustments) -> Unit,
) {
    AdminCard(
        title = stringResource(R.string.admin_camera_controls_title),
        subtitle = stringResource(R.string.admin_section_camera_hint),
    ) {
        if (cameras.isEmpty()) {
            Text(
                text = stringResource(R.string.admin_no_cameras),
                color = Color(0xFFFFB86B),
                fontSize = 14.sp,
            )
        } else {
            cameras.forEach { camera ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ink.copy(alpha = 0.38f))
                        .padding(12.dp),
                ) {
                    Text(
                        text = "Cámara ${camera.id} · ${camera.facing}",
                        color = Cream,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${camera.megapixels} · ${camera.maxResolution} · ${camera.hardwareLevel}",
                        color = Cream.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        val active = when {
            cameras.any { it.facing == "Frontal" } -> "Frontal"
            cameras.any { it.facing == "Trasera" } -> "Trasera"
            cameras.any { it.facing == "Externa / USB" } -> "Externa / USB"
            else -> "Sin cámara"
        }
        InfoRow("La app usaría", active)

        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.admin_usb_devices),
            color = Cream.copy(alpha = 0.70f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (usbDevices.isEmpty()) {
            Text(
                text = stringResource(R.string.admin_no_usb),
                color = Cream.copy(alpha = 0.55f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            usbDevices.forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Usb,
                        contentDescription = null,
                        tint = SyscomBlueLight,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(device.name, color = Cream, fontSize = 13.sp)
                        Text(
                            text = "${device.vendorId}:${device.productId}",
                            color = Cream.copy(alpha = 0.45f),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.admin_camera_adjust_hint),
            color = Color(0xFF9AC2F4),
            fontSize = 12.sp,
        )
        RotationChoice(
            label = stringResource(R.string.admin_camera_preview_rotation),
            selected = adjustments.previewRotation,
            onSelect = { onAdjustmentsChange(adjustments.copy(previewRotation = it)) },
        )
        MirrorChoice(
            label = stringResource(R.string.admin_camera_preview_mirror),
            checked = adjustments.previewMirrored,
            onCheckedChange = { onAdjustmentsChange(adjustments.copy(previewMirrored = it)) },
        )
        RotationChoice(
            label = stringResource(R.string.admin_camera_output_rotation),
            selected = adjustments.outputRotation,
            onSelect = { onAdjustmentsChange(adjustments.copy(outputRotation = it)) },
        )
        MirrorChoice(
            label = stringResource(R.string.admin_camera_output_mirror),
            checked = adjustments.outputMirrored,
            onCheckedChange = { onAdjustmentsChange(adjustments.copy(outputMirrored = it)) },
        )

        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onToggleTest) {
            Icon(
                imageVector = Icons.Rounded.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (showCameraTest) R.string.admin_stop_camera else R.string.admin_test_camera,
                ),
            )
        }

        if (showCameraTest) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Ink),
            ) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    captureRequest = 0,
                    onReady = {},
                    onCaptured = { _, _ -> },
                    onError = onCameraError,
                    adjustments = adjustments,
                )
            }
            if (cameraTestError != null) {
                Text(
                    text = stringResource(R.string.admin_camera_test_error, cameraTestError),
                    color = Color(0xFFFF7A7A),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun RotationChoice(
    label: String,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Text(
        text = label,
        color = Cream,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp),
    )
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(0, 90, 180, 270).forEach { degrees ->
            FilterChip(
                selected = selected == degrees,
                onClick = { onSelect(degrees) },
                label = { Text("$degrees°") },
            )
        }
    }
}

@Composable
private fun MirrorChoice(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Cream,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
