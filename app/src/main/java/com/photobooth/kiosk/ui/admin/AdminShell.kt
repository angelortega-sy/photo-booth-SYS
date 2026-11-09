package com.photobooth.kiosk.ui.admin

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.admin.AdminAuth
import com.photobooth.kiosk.admin.CameraOption
import com.photobooth.kiosk.admin.DeviceDiagnostics
import com.photobooth.kiosk.admin.DeviceSummary
import com.photobooth.kiosk.admin.NetworkAddress
import com.photobooth.kiosk.admin.PhotoLibrary
import com.photobooth.kiosk.admin.SavedPhoto
import com.photobooth.kiosk.admin.UsbDeviceOption
import com.photobooth.kiosk.data.AiStyles
import com.photobooth.kiosk.ui.PhotoBoothViewModel
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.SyscomBlueLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Admin entry point: PIN gate, route back stack, shared device data and one
 * compact header. Each route renders its own screen from this package.
 */
@Composable
fun AdminShell(
    viewModel: PhotoBoothViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val auth = remember(context) { AdminAuth(context) }
    var unlocked by remember { mutableStateOf(false) }
    var changingPin by remember { mutableStateOf(false) }

    val mode = if (!auth.hasPin || changingPin) PinMode.CREATE else PinMode.VERIFY

    if (unlocked && !changingPin) {
        AdminShellContent(
            viewModel = viewModel,
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
private fun AdminShellContent(
    viewModel: PhotoBoothViewModel,
    onClose: () -> Unit,
    onChangePin: () -> Unit,
) {
    val context = LocalContext.current
    val appState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var backStack by rememberSaveable { mutableStateOf(listOf(AdminRoute.Home.key)) }
    fun navigate(route: AdminRoute) {
        backStack = backStack + route.key
    }
    fun pop() {
        if (backStack.size > 1) backStack = backStack.dropLast(1)
    }
    val route = AdminRoute.fromKey(backStack.last())

    BackHandler {
        if (backStack.size > 1) pop() else onClose()
    }

    var refreshKey by remember { mutableIntStateOf(0) }
    var photos by remember { mutableStateOf<List<SavedPhoto>>(emptyList()) }
    var cameras by remember { mutableStateOf<List<CameraOption>>(emptyList()) }
    var usbDevices by remember { mutableStateOf<List<UsbDeviceOption>>(emptyList()) }
    var addresses by remember { mutableStateOf<List<NetworkAddress>>(emptyList()) }
    var networkType by remember { mutableStateOf("—") }
    var summary by remember { mutableStateOf<DeviceSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var photoPermissionGranted by remember { mutableStateOf(hasPhotoPermission(context)) }

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

    fun onMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val compact = LocalConfiguration.current.screenWidthDp < 600
    val editorProfileName = (route as? AdminRoute.BrandEditor)
        ?.let { editor -> appState.eventProfiles.firstOrNull { it.id == editor.profileId }?.name }
    val (headerTitle, headerSubtitle) = AdminRouteTitle(route, editorProfileName)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 1100.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .padding(horizontal = if (compact) 18.dp else 26.dp),
        ) {
            AdminHeader(
                title = headerTitle,
                subtitle = headerSubtitle,
                showBack = route != AdminRoute.Home,
                onBack = {
                    if (route == AdminRoute.Home) onClose() else pop()
                },
                onClose = onClose,
            )

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (route) {
                    AdminRoute.Home -> {
                        val backgrounds = appState.adminBackgrounds
                        val modelLabel = appState.serverModels
                            .firstOrNull { it.id == appState.selectedModelId }?.name
                            ?: context.getString(R.string.ai_model_none)
                        val activeBrand = appState.eventProfiles
                            .firstOrNull { it.id == appState.activeEventProfileId }
                            ?: appState.eventProfiles.firstOrNull()
                        AdminHomeScreen(
                            compact = compact,
                            brandCount = appState.eventProfiles.size,
                            activeBrandName = activeBrand?.name ?: "SYSCOM",
                            backgroundTotal = backgrounds.size,
                            backgroundVisible = backgrounds.count { it.enabled },
                            cameraCount = cameras.size,
                            usbCount = usbDevices.size,
                            styleVisible = AiStyles.ALL.size - appState.disabledStyleIds.size,
                            styleTotal = AiStyles.ALL.size,
                            photoCount = photos.size,
                            networkType = networkType,
                            ipAddress = addresses.firstOrNull()?.address ?: "—",
                            modelLabel = modelLabel,
                            appVersion = summary?.appVersion ?: "",
                            onNavigate = ::navigate,
                        )
                    }

                    AdminRoute.Brands -> {
                        AdminBrandsScreen(
                            profiles = appState.eventProfiles,
                            activeProfileId = appState.activeEventProfileId,
                            onToggleEnabled = viewModel::setEventProfileEnabled,
                            onDelete = viewModel::deleteEventProfile,
                            onAdd = { name ->
                                val id = viewModel.addEventProfile(name)
                                navigate(AdminRoute.BrandEditor(id))
                            },
                            onActivate = viewModel::activateEventProfile,
                            onOpenEditor = { profileId ->
                                navigate(AdminRoute.BrandEditor(profileId))
                            },
                        )
                    }

                    is AdminRoute.BrandEditor -> {
                        AdminBrandEditorScreen(
                            profileId = route.profileId,
                            profiles = appState.eventProfiles,
                            activeProfileId = appState.activeEventProfileId,
                            onEnsureActive = viewModel::activateEventProfile,
                            onRename = viewModel::renameEventProfile,
                            onSaveBranding = viewModel::updateEventProfileBranding,
                            onSaved = {
                                onMessage(context.getString(R.string.admin_event_saved))
                            },
                        )
                    }

                    AdminRoute.Event -> {
                        AdminEventScreen(
                            backgrounds = appState.adminBackgrounds.ifEmpty { viewModel.allBackgrounds },
                            loadThumbnail = viewModel::loadThumbnail,
                            onImport = { uri, name ->
                                viewModel.importBackground(uri, name) { error ->
                                    onMessage(
                                        if (error == null) {
                                            context.getString(R.string.admin_background_imported)
                                        } else {
                                            context.getString(
                                                R.string.admin_background_import_error,
                                            )
                                        },
                                    )
                                }
                            },
                            onToggleBackground = viewModel::setBackgroundEnabled,
                            onDeleteBackground = viewModel::deleteBackground,
                            onRenameBackground = viewModel::renameBackground,
                        )
                    }

                    AdminRoute.Camera -> {
                        AdminCameraScreen(
                            summary = summary,
                            cameras = cameras,
                            usbDevices = usbDevices,
                            rotationDegrees = appState.cameraRotationDegrees,
                            onRotationChange = viewModel::setCameraRotation,
                            palmEnabled = appState.palmCaptureEnabled,
                            onPalmToggle = viewModel::setPalmCaptureEnabled,
                        )
                    }

                    AdminRoute.Styles -> {
                        AdminStylesScreen(
                            serverStyles = appState.serverStyles,
                            disabledIds = appState.disabledStyleIds,
                            onToggle = viewModel::setStyleEnabled,
                        )
                    }

                    AdminRoute.Photos -> {
                        AdminPhotosScreen(
                            photos = photos,
                            photoPermissionGranted = photoPermissionGranted,
                            compact = compact,
                            saveToDevice = appState.savePhotosToDevice,
                            onSaveToDeviceChange = viewModel::setSavePhotosToDevice,
                            onRefresh = { refreshKey++ },
                            onRequestPermission = { permissionLauncher.launch(photoPermission()) },
                            onMessage = ::onMessage,
                        )
                    }

                    AdminRoute.Usage -> {
                        AdminUsageScreen(
                            viewModel = viewModel,
                            models = appState.serverModels,
                            selectedModelId = appState.selectedModelId,
                            statusProbe = appState.serverStatusProbe,
                            statusError = appState.serverStatusError,
                            isStatusChecking = appState.isServerStatusChecking,
                            onMessage = ::onMessage,
                        )
                    }

                    AdminRoute.Network -> {
                        AdminNetworkScreen(
                            networkType = networkType,
                            addresses = addresses,
                            onOpenSystem = { navigate(AdminRoute.System) },
                        )
                    }

                    AdminRoute.System -> {
                        AdminSystemScreen(onChangePin = onChangePin)
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
}
