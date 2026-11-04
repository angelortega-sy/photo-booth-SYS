package com.photobooth.kiosk.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.photobooth.kiosk.R
import com.photobooth.kiosk.admin.AdminAuth
import com.photobooth.kiosk.admin.CameraOption
import com.photobooth.kiosk.admin.DeviceDiagnostics
import com.photobooth.kiosk.admin.DeviceSummary
import com.photobooth.kiosk.admin.NetworkAddress
import com.photobooth.kiosk.admin.PhotoLibrary
import com.photobooth.kiosk.admin.SavedPhoto
import com.photobooth.kiosk.admin.UsbDeviceOption
import com.photobooth.kiosk.camera.CameraPreview
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.InkSoft
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomBlueLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PinMode { CREATE, VERIFY }

private enum class AdminSection(val titleRes: Int) {
    SUMMARY(R.string.admin_tab_summary),
    CAMERA(R.string.admin_section_camera),
    PHOTOS(R.string.admin_section_photos),
    SYSTEM(R.string.admin_tab_system),
}

private fun photoPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun hasPhotoPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, photoPermission()) == PackageManager.PERMISSION_GRANTED

@Composable
fun AdminGate(onClose: () -> Unit) {
    val context = LocalContext.current
    val auth = remember(context) { AdminAuth(context) }
    var unlocked by remember { mutableStateOf(false) }
    var changingPin by remember { mutableStateOf(false) }

    val mode = if (!auth.hasPin || changingPin) PinMode.CREATE else PinMode.VERIFY

    if (unlocked && !changingPin) {
        AdminScreen(
            onClose = onClose,
            onChangePin = { changingPin = true },
        )
    } else {
        PinScreen(
            mode = mode,
            verify = auth::verify,
            onCancel = { if (changingPin) changingPin = false else onClose() },
            onPinReady = { pin ->
                if (changingPin) {
                    auth.setPin(pin)
                    changingPin = false
                } else {
                    if (!auth.hasPin) auth.setPin(pin)
                    unlocked = true
                }
            },
        )
    }
}

@Composable
private fun PinScreen(
    mode: PinMode,
    verify: (String) -> Boolean,
    onCancel: () -> Unit,
    onPinReady: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val creating = mode == PinMode.CREATE
    val current = if (confirming) confirmPin else pin

    fun addDigit(digit: String) {
        error = null
        if (confirming) {
            if (confirmPin.length < 8) confirmPin += digit
        } else if (pin.length < 8) {
            pin += digit
        }
    }

    fun backspace() {
        error = null
        if (confirming) {
            confirmPin = confirmPin.dropLast(1)
        } else {
            pin = pin.dropLast(1)
        }
    }

    fun submit() {
        when {
            creating && !confirming -> {
                if (pin.length < 4) {
                    error = "Usa al menos 4 dígitos"
                } else {
                    confirming = true
                    error = null
                }
            }

            creating && confirming -> {
                if (confirmPin.length < 4) {
                    error = "Usa al menos 4 dígitos"
                } else if (confirmPin != pin) {
                    error = "Los PIN no coinciden"
                    confirmPin = ""
                } else {
                    onPinReady(pin)
                }
            }

            else -> {
                if (pin.length < 4) {
                    error = "Usa al menos 4 dígitos"
                } else if (verify(pin)) {
                    onPinReady(pin)
                } else {
                    error = "PIN incorrecto"
                    pin = ""
                }
            }
        }
    }

    BackHandler {
        if (confirming) {
            confirming = false
            confirmPin = ""
            error = null
        } else {
            onCancel()
        }
    }

    val title = when {
        creating && confirming -> stringResource(R.string.admin_pin_confirm)
        creating -> stringResource(R.string.admin_pin_create)
        else -> stringResource(R.string.admin_pin_enter)
    }
    val hint = when {
        creating && confirming -> stringResource(R.string.admin_pin_hint_confirm)
        creating -> stringResource(R.string.admin_pin_hint_create)
        else -> stringResource(R.string.admin_pin_hint_enter)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (confirming) {
                        confirming = false
                        confirmPin = ""
                        error = null
                    } else {
                        onCancel()
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.admin_cancel),
                        tint = Cream,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "SYSCOM",
                    color = SyscomBlueLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                )
            }

            Spacer(Modifier.weight(0.6f))

            Box(
                modifier = Modifier
                    .size(86.dp)
                    .background(SyscomBlue.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = SyscomBlueLight,
                    modifier = Modifier.size(38.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = title,
                color = Cream,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = hint,
                color = Cream.copy(alpha = 0.58f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(8) { index ->
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                if (index < current.length) SyscomBlue else InkSoft,
                                CircleShape,
                            )
                            .border(
                                1.dp,
                                if (index < current.length) SyscomBlue else Cream.copy(alpha = 0.18f),
                                CircleShape,
                            ),
                    )
                }
            }

            if (error != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = error.orEmpty(),
                    color = Color(0xFFFF7A7A),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.weight(0.5f))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PinKeyRow(listOf("1", "2", "3"), onDigit = ::addDigit)
                PinKeyRow(listOf("4", "5", "6"), onDigit = ::addDigit)
                PinKeyRow(listOf("7", "8", "9"), onDigit = ::addDigit)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PinKey(
                        label = "",
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                contentDescription = "Borrar",
                                tint = Cream,
                                modifier = Modifier.size(26.dp),
                            )
                        },
                        onClick = ::backspace,
                    )
                    PinKey(label = "0", onClick = { addDigit("0") })
                    PinKey(
                        label = "",
                        highlighted = true,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Continuar",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        },
                        onClick = ::submit,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            TextButton(onClick = onCancel) {
                Text(
                    text = stringResource(R.string.admin_cancel),
                    color = Cream.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun PinKeyRow(
    digits: List<String>,
    onDigit: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        digits.forEach { digit ->
            PinKey(label = digit, onClick = { onDigit(digit) })
        }
    }
}

@Composable
private fun PinKey(
    label: String,
    highlighted: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(if (highlighted) SyscomBlue else InkSoft)
            .border(
                1.dp,
                if (highlighted) SyscomBlue else Cream.copy(alpha = 0.10f),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            icon()
        } else {
            Text(
                text = label,
                color = Cream,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AdminScreen(
    onClose: () -> Unit,
    onChangePin: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var refreshKey by remember { mutableIntStateOf(0) }
    var photos by remember { mutableStateOf<List<SavedPhoto>>(emptyList()) }
    var cameras by remember { mutableStateOf<List<CameraOption>>(emptyList()) }
    var usbDevices by remember { mutableStateOf<List<UsbDeviceOption>>(emptyList()) }
    var addresses by remember { mutableStateOf<List<NetworkAddress>>(emptyList()) }
    var networkType by remember { mutableStateOf("—") }
    var summary by remember { mutableStateOf<DeviceSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var photoPermissionGranted by remember { mutableStateOf(hasPhotoPermission(context)) }
    var photoToDelete by remember { mutableStateOf<SavedPhoto?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }
    var showCameraTest by remember { mutableStateOf(false) }
    var cameraTestError by remember { mutableStateOf<String?>(null) }
    var selectedSection by remember { mutableStateOf(AdminSection.SUMMARY) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        photoPermissionGranted = granted
        refreshKey++
    }

    LaunchedEffect(Unit) {
        if (!photoPermissionGranted) {
            permissionLauncher.launch(photoPermission())
        }
    }

    LaunchedEffect(refreshKey, photoPermissionGranted) {
        loading = true
        val data = withContext(Dispatchers.IO) {
            AdminData(
                photos = if (photoPermissionGranted) PhotoLibrary.list(context) else emptyList(),
                cameras = DeviceDiagnostics.cameras(context),
                usbDevices = DeviceDiagnostics.usbDevices(context),
                addresses = DeviceDiagnostics.ipv4Addresses(),
                networkType = DeviceDiagnostics.networkType(context),
                summary = DeviceDiagnostics.summary(context),
            )
        }
        photos = data.photos
        cameras = data.cameras
        usbDevices = data.usbDevices
        addresses = data.addresses
        networkType = data.networkType
        summary = data.summary
        loading = false
    }

    BackHandler { onClose() }

    fun deletePhoto(photo: SavedPhoto) {
        scope.launch {
            val deleted = withContext(Dispatchers.IO) {
                PhotoLibrary.delete(context, photo.uri)
            }
            photoToDelete = null
            refreshKey++
            snackbarHostState.showSnackbar(
                if (deleted) {
                    context.getString(R.string.admin_photo_deleted)
                } else {
                    context.getString(R.string.admin_delete_error)
                },
            )
        }
    }

    fun deleteAllPhotos() {
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                PhotoLibrary.deleteAll(context)
            }
            showDeleteAll = false
            refreshKey++
            snackbarHostState.showSnackbar(
                context.getString(R.string.admin_photos_deleted, count),
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        val compact = LocalConfiguration.current.screenWidthDp < 600
        val photoColumns = if (compact) 2 else 4
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 18.dp else 26.dp),
        ) {
            AdminHeader(onClose = onClose)
            AdminSectionTabs(
                selected = selectedSection,
                compact = compact,
                onSelect = { selectedSection = it },
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (selectedSection) {
                    AdminSection.SUMMARY -> {
                        item { DeviceCard(summary) }
                        item {
                            AdminCard(
                                title = stringResource(R.string.admin_status_title),
                                subtitle = stringResource(R.string.admin_status_subtitle),
                            ) {
                                InfoRow("Cámaras", cameras.size.toString())
                                InfoRow("USB", usbDevices.size.toString())
                                InfoRow("Conexión", networkType)
                                InfoRow("IP local", addresses.firstOrNull()?.address ?: "—")
                                InfoRow("Galería", photos.size.toString())
                            }
                        }
                        item {
                            Text(
                                text = "Modo administrador · ${summary?.appVersion ?: ""}",
                                color = Cream.copy(alpha = 0.32f),
                                fontSize = 12.sp,
                            )
                        }
                    }

                    AdminSection.CAMERA -> {
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
                            )
                        }
                    }

                    AdminSection.PHOTOS -> {
                        item {
                            PhotosHeader(
                                photoCount = photos.size,
                                permissionGranted = photoPermissionGranted,
                                compact = compact,
                                onRefresh = { refreshKey++ },
                                onRequestPermission = { permissionLauncher.launch(photoPermission()) },
                                onDeleteAll = { showDeleteAll = true },
                            )
                        }
                        if (!photoPermissionGranted) {
                            item {
                                AdminCard(
                                    title = stringResource(R.string.admin_section_photos),
                                    subtitle = stringResource(R.string.admin_permission_photos),
                                ) {
                                    Button(
                                        onClick = { permissionLauncher.launch(photoPermission()) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SyscomBlue,
                                            contentColor = Color.White,
                                        ),
                                    ) {
                                        Text(stringResource(R.string.admin_permission_photos))
                                    }
                                }
                            }
                        } else if (photos.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.admin_no_photos),
                                    color = Cream.copy(alpha = 0.55f),
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        } else {
                            items(photos.chunked(photoColumns)) { rowPhotos ->
                                PhotoRow(
                                    photos = rowPhotos,
                                    columns = photoColumns,
                                    onDelete = { photoToDelete = it },
                                )
                            }
                        }
                    }

                    AdminSection.SYSTEM -> {
                        item {
                            NetworkCard(
                                networkType = networkType,
                                addresses = addresses,
                                onOpenNetworkSettings = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                                    }
                                },
                                onOpenHotspotSettings = {
                                    val intent = Intent("android.settings.WIFI_HOTSPOT_SETTINGS")
                                    val opened = runCatching { context.startActivity(intent) }.isSuccess
                                    if (!opened) {
                                        runCatching {
                                            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                                        }
                                    }
                                },
                            )
                        }
                        item {
                            AdminCard(
                                title = stringResource(R.string.admin_security_title),
                                subtitle = stringResource(R.string.admin_security_subtitle),
                            ) {
                                InfoRow("PIN", "Protegido")
                                Text(
                                    text = stringResource(R.string.admin_security_hint),
                                    color = Cream.copy(alpha = 0.55f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                OutlinedButton(
                                    onClick = onChangePin,
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                ) {
                                    Text(stringResource(R.string.admin_change_pin))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (loading) {
            CircularProgressIndicator(
                color = SyscomBlueLight,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp),
        )
    }

    photoToDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title = { Text(stringResource(R.string.admin_delete_photo_title)) },
            text = { Text(stringResource(R.string.admin_delete_photo_message)) },
            confirmButton = {
                TextButton(onClick = { deletePhoto(photo) }) {
                    Text(stringResource(R.string.admin_delete), color = Color(0xFFFF7A7A))
                }
            },
            dismissButton = {
                TextButton(onClick = { photoToDelete = null }) {
                    Text(stringResource(R.string.admin_cancel))
                }
            },
        )
    }

    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text(stringResource(R.string.admin_delete_all_title)) },
            text = { Text(stringResource(R.string.admin_delete_all_message)) },
            confirmButton = {
                TextButton(onClick = ::deleteAllPhotos) {
                    Text(stringResource(R.string.admin_delete_all), color = Color(0xFFFF7A7A))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }) {
                    Text(stringResource(R.string.admin_cancel))
                }
            },
        )
    }

}

private data class AdminData(
    val photos: List<SavedPhoto>,
    val cameras: List<CameraOption>,
    val usbDevices: List<UsbDeviceOption>,
    val addresses: List<NetworkAddress>,
    val networkType: String,
    val summary: DeviceSummary?,
)

@Composable
private fun AdminSectionTabs(
    selected: AdminSection,
    compact: Boolean,
    onSelect: (AdminSection) -> Unit,
) {
    val sections = AdminSection.entries
    if (compact) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(sections) { section ->
                AdminSectionTab(
                    section = section,
                    selected = selected == section,
                    onSelect = { onSelect(section) },
                    modifier = Modifier.widthIn(min = 108.dp),
                )
            }
        }
    } else {
        Row(Modifier.fillMaxWidth()) {
            sections.forEach { section ->
                AdminSectionTab(
                    section = section,
                    selected = selected == section,
                    onSelect = { onSelect(section) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AdminSectionTab(
    section: AdminSection,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onSelect, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(section.titleRes),
                color = if (selected) Cream else Cream.copy(alpha = 0.52f),
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (selected) SyscomBlueLight else Color.Transparent),
            )
        }
    }
}

@Composable
private fun AdminHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.admin_close),
                tint = Cream,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.admin_title),
                color = Cream,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.admin_subtitle),
                color = Cream.copy(alpha = 0.50f),
                fontSize = 12.sp,
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.admin_close),
                tint = Cream.copy(alpha = 0.75f),
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
private fun CameraCard(
    cameras: List<CameraOption>,
    usbDevices: List<UsbDeviceOption>,
    showCameraTest: Boolean,
    cameraTestError: String?,
    onToggleTest: () -> Unit,
    onCameraError: (String) -> Unit,
) {
    AdminCard(
        title = stringResource(R.string.admin_section_camera),
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
                color = Cream.copy(alpha = 0.45f),
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
        Button(
            onClick = onToggleTest,
            colors = ButtonDefaults.buttonColors(
                containerColor = SyscomBlue,
                contentColor = Color.White,
            ),
        ) {
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
                    onCaptured = { _, _, _ -> },
                    onError = onCameraError,
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
private fun PhotosHeader(
    photoCount: Int,
    permissionGranted: Boolean,
    compact: Boolean,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.admin_section_photos),
            color = Cream,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = if (permissionGranted) {
                "$photoCount foto(s) en la galería"
            } else {
                stringResource(R.string.admin_permission_photos)
            },
            color = Cream.copy(alpha = 0.50f),
            fontSize = 13.sp,
        )
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!permissionGranted) {
                    TextButton(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.admin_permission_photos), color = SyscomBlueLight)
                    }
                } else {
                    TextButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = Cream.copy(alpha = 0.70f),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.admin_refresh), color = Cream.copy(alpha = 0.70f))
                    }
                    TextButton(
                        onClick = onDeleteAll,
                        enabled = photoCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.admin_delete_all), color = Color(0xFFFF7A7A))
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!permissionGranted) {
                    TextButton(onClick = onRequestPermission) {
                        Text(stringResource(R.string.admin_permission_photos), color = SyscomBlueLight)
                    }
                } else {
                    TextButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = Cream.copy(alpha = 0.70f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.admin_refresh), color = Cream.copy(alpha = 0.70f))
                    }
                    TextButton(onClick = onDeleteAll, enabled = photoCount > 0) {
                        Text(stringResource(R.string.admin_delete_all), color = Color(0xFFFF7A7A))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoRow(
    photos: List<SavedPhoto>,
    columns: Int,
    onDelete: (SavedPhoto) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        photos.forEach { photo ->
            PhotoCard(
                photo = photo,
                onDelete = onDelete,
                modifier = Modifier.weight(1f),
            )
        }
        repeat(columns - photos.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PhotoCard(
    photo: SavedPhoto,
    onDelete: (SavedPhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, photo.uri) {
        value = withContext(Dispatchers.IO) {
            PhotoLibrary.thumbnail(context, photo.uri)
        }
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(14.dp))
                .background(InkSoft),
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = photo.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PhotoLibrary,
                    contentDescription = null,
                    tint = Cream.copy(alpha = 0.35f),
                    modifier = Modifier
                        .size(34.dp)
                        .align(Alignment.Center),
                )
            }
            IconButton(
                onClick = { onDelete(photo) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(Ink.copy(alpha = 0.72f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.admin_delete),
                    tint = Color(0xFFFF8A8A),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = formatPhotoDate(photo.dateAdded),
            color = Cream.copy(alpha = 0.48f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun NetworkCard(
    networkType: String,
    addresses: List<NetworkAddress>,
    onOpenNetworkSettings: () -> Unit,
    onOpenHotspotSettings: () -> Unit,
) {
    AdminCard(
        title = stringResource(R.string.admin_section_network),
        subtitle = stringResource(R.string.admin_section_network_hint),
    ) {
        InfoRow("Conexión", networkType)
        if (addresses.isEmpty()) {
            Text(
                text = "Sin IP local. Conecta el kiosko a Wi-Fi o Ethernet.",
                color = Color(0xFFFFB86B),
                fontSize = 13.sp,
            )
        } else {
            addresses.forEach { address ->
                InfoRow(address.interfaceName, address.address)
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onOpenNetworkSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.admin_open_network),
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            OutlinedButton(
                onClick = onOpenHotspotSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.admin_open_hotspot),
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "El QR de descarga usará una de estas direcciones. La descarga por hotspot se integra en la siguiente fase.",
            color = Cream.copy(alpha = 0.45f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun AdminCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = title,
            color = Cream,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = Cream.copy(alpha = 0.45f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = Cream.copy(alpha = 0.52f),
            fontSize = 14.sp,
            modifier = Modifier.widthIn(min = 92.dp, max = 160.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            color = Cream,
            fontSize = 14.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatPhotoDate(epochSeconds: Long): String =
    SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000))
