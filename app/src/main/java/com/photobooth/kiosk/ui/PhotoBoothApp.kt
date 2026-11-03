package com.photobooth.kiosk.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photobooth.kiosk.R
import com.photobooth.kiosk.camera.CameraPreview
import com.photobooth.kiosk.data.BackgroundOption
import com.photobooth.kiosk.photo.PhotoSaver
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.InkSoft
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomBlueDeep
import com.photobooth.kiosk.ui.theme.SyscomBlueLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val IDLE_TIMEOUT_MS = 60_000L
private const val LOG_TAG = "PhotoBooth"

@Composable
fun PhotoBoothApp(viewModel: PhotoBoothViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var hasCameraPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var showWelcome by rememberSaveable { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        lastInteraction = SystemClock.uptimeMillis()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasCameraPermission = context.hasCameraPermission()
        lastInteraction = SystemClock.uptimeMillis()
    }

    LaunchedEffect(showWelcome, state.isProcessing) {
        if (showWelcome) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            if (viewModel.uiState.value.isProcessing) continue
            if (SystemClock.uptimeMillis() - lastInteraction >= IDLE_TIMEOUT_MS) {
                viewModel.endSession()
                showWelcome = true
                break
            }
        }
    }

    fun goHome() {
        viewModel.endSession()
        showWelcome = true
        lastInteraction = SystemClock.uptimeMillis()
    }

    fun startSession() {
        showWelcome = false
        lastInteraction = SystemClock.uptimeMillis()
    }

    BackHandler(enabled = !showWelcome && !state.isProcessing) { goHome() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastInteraction = SystemClock.uptimeMillis()
                    }
                }
            },
    ) {
        when {
            !hasCameraPermission -> PermissionScreen(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = { context.openAppSettings() },
            )

            showWelcome -> WelcomeScreen(onStart = { startSession() })

            else -> PhotoBoothScreen(
                viewModel = viewModel,
                onHome = { goHome() },
                onShared = { goHome() },
                onInteraction = { lastInteraction = SystemClock.uptimeMillis() },
            )
        }
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(InkSoft, Ink))),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            SyscomBlue.copy(alpha = 0.32f),
                            SyscomBlueDeep.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.welcome_brand),
                    color = SyscomBlueLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.welcome_tagline),
                    color = Cream.copy(alpha = 0.42f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.sp,
                )
            }

            Spacer(Modifier.weight(1f))

            Box(modifier = Modifier.size(238.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(238.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    SyscomBlue.copy(alpha = 0.55f),
                                    SyscomBlueDeep.copy(alpha = 0.16f),
                                    Color.Transparent,
                                ),
                            ),
                            CircleShape,
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(178.dp)
                        .border(1.dp, SyscomBlueLight.copy(alpha = 0.38f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(138.dp)
                            .background(InkSoft.copy(alpha = 0.92f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = Cream,
                            modifier = Modifier.size(68.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

            Text(
                text = stringResource(R.string.welcome_title),
                color = Cream,
                fontSize = 46.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.welcome_subtitle),
                color = Cream.copy(alpha = 0.66f),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onStart,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(29.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SyscomBlue,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = stringResource(R.string.welcome_cta),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.welcome_hint),
                color = Cream.copy(alpha = 0.38f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PhotoBoothScreen(
    viewModel: PhotoBoothViewModel,
    onHome: () -> Unit,
    onShared: () -> Unit,
    onInteraction: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var captureRequest by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraKey by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var showBackgrounds by remember { mutableStateOf(false) }

    val selectedOption = viewModel.backgrounds.firstOrNull { it.id == state.selectedBackgroundId }
    val selectedName = if (selectedOption != null) {
        stringResource(selectedOption.nameRes)
    } else {
        ""
    }

    fun startCapture() {
        if (!cameraReady || busy || state.isProcessing || countdown > 0) return
        scope.launch {
            countdown = 3
            delay(1_000L)
            countdown = 2
            delay(1_000L)
            countdown = 1
            delay(1_000L)
            countdown = 0
            flash = true
            delay(100L)
            flash = false
            captureRequest += 1
        }
    }

    fun savePhoto() {
        val bitmap = state.resultBitmap ?: return
        if (busy || state.isProcessing) return
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                PhotoSaver.saveToGallery(context, bitmap)
            }
            busy = false
            snackbarHostState.showSnackbar(
                context.getString(
                    if (result.isSuccess) R.string.photo_saved else R.string.save_error,
                ),
            )
        }
    }

    fun sharePhoto() {
        val bitmap = state.resultBitmap ?: return
        if (busy || state.isProcessing) return
        scope.launch {
            busy = true
            val uri = withContext(Dispatchers.IO) {
                PhotoSaver.cacheForShare(context, bitmap)
            }
            if (uri == null) {
                busy = false
                snackbarHostState.showSnackbar(context.getString(R.string.share_error))
                return@launch
            }

            val chooser = Intent.createChooser(
                PhotoSaver.shareIntent(context, uri),
                context.getString(R.string.share_chooser),
            )
            val launched = runCatching { context.startActivity(chooser) }.isSuccess
            busy = false
            if (launched) {
                onShared()
            } else {
                snackbarHostState.showSnackbar(context.getString(R.string.share_error))
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Ink)) {
        if (state.resultBitmap == null) {
            key(cameraKey) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    captureRequest = captureRequest,
                    onReady = {
                        cameraReady = true
                        cameraError = null
                    },
                    onCaptured = { bitmap, rotation, mirror ->
                        cameraReady = true
                        viewModel.processPhoto(bitmap, rotation, mirror)
                    },
                    onError = { message ->
                        Log.e(LOG_TAG, "Camera error: $message")
                        cameraReady = false
                        cameraError = message
                    },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Ink.copy(alpha = 0.82f), Color.Transparent),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Ink.copy(alpha = 0.92f)),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = Cream,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = selectedName,
                    color = Cream.copy(alpha = 0.56f),
                    fontSize = 12.sp,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(114.dp)) {
                    CaptureButton(
                        modifier = Modifier.align(Alignment.Center),
                        enabled = cameraReady && !state.isProcessing && countdown == 0,
                        onClick = { startCapture() },
                    )
                    OutlinedButton(
                        onClick = { showBackgrounds = true },
                        enabled = !state.isProcessing && countdown == 0,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 20.dp)
                            .height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, Cream.copy(alpha = 0.24f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.backgrounds),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    IconButton(
                        onClick = onHome,
                        enabled = !state.isProcessing && countdown == 0,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = stringResource(R.string.home),
                            tint = Cream,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.capture_hint),
                    color = Cream.copy(alpha = 0.48f),
                    fontSize = 12.sp,
                )
            }
        }

        state.resultBitmap?.let { bitmap ->
            ResultOverlay(
                bitmap = bitmap,
                usedFallback = state.usedFallback,
                enabled = !busy && !state.isProcessing,
                onSave = { savePhoto() },
                onShare = { sharePhoto() },
                onRetake = {
                    cameraReady = false
                    viewModel.clearResult()
                },
                onHome = onHome,
                onOpenBackgrounds = { showBackgrounds = true },
            )
        }

        if (countdown > 0) {
            CountdownOverlay(count = countdown)
        }

        if (flash) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.86f)))
        }

        if (state.isProcessing) {
            ProcessingOverlay()
        }

        if (cameraError != null && state.resultBitmap == null) {
            CameraErrorOverlay(
                onRetry = {
                    cameraError = null
                    cameraReady = false
                    cameraKey += 1
                },
            )
        }

        if (showBackgrounds) {
            BackgroundSheet(
                viewModel = viewModel,
                selectedId = state.selectedBackgroundId,
                onSelect = { id ->
                    if (state.resultBitmap == null) {
                        viewModel.selectBackground(id)
                    } else {
                        viewModel.changeBackground(id)
                    }
                    showBackgrounds = false
                },
                onDismiss = { showBackgrounds = false },
                onInteraction = onInteraction,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun CaptureButton(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(112.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(3.dp, Color.White.copy(alpha = 0.82f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(82.dp)
                .background(
                    if (enabled) SyscomBlue else Cream.copy(alpha = 0.20f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.CameraAlt,
                contentDescription = stringResource(R.string.capture),
                tint = Color.White,
                modifier = Modifier.size(38.dp),
            )
        }
    }
}

@Composable
private fun ResultOverlay(
    bitmap: Bitmap,
    usedFallback: Boolean,
    enabled: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onRetake: () -> Unit,
    onHome: () -> Unit,
    onOpenBackgrounds: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Ink)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.result_title),
                    color = Cream,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.welcome_brand),
                    color = SyscomBlueLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.sp,
                )
            }

            if (usedFallback) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.result_fallback),
                    color = Cream.copy(alpha = 0.58f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(InkSoft)
                    .border(1.dp, Cream.copy(alpha = 0.08f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.photo_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(29.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SyscomBlue,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = stringResource(R.string.save_photo),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onShare,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    border = BorderStroke(1.dp, Cream.copy(alpha = 0.22f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.share), fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = onOpenBackgrounds,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    border = BorderStroke(1.dp, Cream.copy(alpha = 0.22f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.backgrounds), fontSize = 13.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onRetake, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.retake), fontSize = 12.sp)
                }
                TextButton(onClick = onHome, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Rounded.Home,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.home), fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackgroundSheet(
    viewModel: PhotoBoothViewModel,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onInteraction: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = InkSoft,
        contentColor = Cream,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 44.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Cream.copy(alpha = 0.25f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            onInteraction()
                        }
                    }
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.choose_background),
                        color = Cream,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.backgrounds_hint),
                        color = Cream.copy(alpha = 0.52f),
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close), color = SyscomBlueLight)
                }
            }

            Spacer(Modifier.height(14.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(236.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(viewModel.backgrounds, key = { it.id }) { option ->
                    BackgroundCard(
                        option = option,
                        selected = option.id == selectedId,
                        loadThumbnail = viewModel::loadThumbnail,
                        onClick = { onSelect(option.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundCard(
    option: BackgroundOption,
    selected: Boolean,
    loadThumbnail: (String) -> Bitmap?,
    onClick: () -> Unit,
) {
    val thumbnail by produceState<Bitmap?>(initialValue = null, option.id) {
        value = withContext(Dispatchers.IO) { loadThumbnail(option.id) }
    }
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier.width(108.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(shape)
                .background(InkSoft)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) SyscomBlueLight else Cream.copy(alpha = 0.12f),
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            thumbnail?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(20.dp)
                        .background(SyscomBlue, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(option.nameRes),
            color = if (selected) Cream else Cream.copy(alpha = 0.62f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun CountdownOverlay(count: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(154.dp)
                .background(Ink.copy(alpha = 0.55f), CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.72f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                color = Color.White,
                fontSize = 88.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ProcessingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = SyscomBlueLight,
                strokeWidth = 4.dp,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.processing),
                color = Cream,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun CameraErrorOverlay(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.86f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 380.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.camera_error),
                color = Cream,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SyscomBlue,
                    contentColor = Color.White,
                ),
            ) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun PermissionScreen(
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Ink),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.CameraAlt,
                contentDescription = null,
                tint = SyscomBlueLight,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.permission_title),
                color = Cream,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.permission_message),
                color = Cream.copy(alpha = 0.64f),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SyscomBlue,
                    contentColor = Color.White,
                ),
            ) {
                Text(stringResource(R.string.grant_permission))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.open_settings), color = Cream.copy(alpha = 0.72f))
            }
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
    }
    startActivity(intent)
}
