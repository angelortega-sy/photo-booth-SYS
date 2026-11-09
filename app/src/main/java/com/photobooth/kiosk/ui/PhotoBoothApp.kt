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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCode2
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photobooth.kiosk.R
import com.photobooth.kiosk.camera.CameraPreview
import com.photobooth.kiosk.data.BackgroundOption
import com.photobooth.kiosk.data.EventBranding
import com.photobooth.kiosk.delivery.DeliverySession
import com.photobooth.kiosk.photo.PhotoFrameComposer
import com.photobooth.kiosk.photo.PhotoSaver
import com.photobooth.kiosk.photo.QrCodeGenerator
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
    var showAdmin by rememberSaveable { mutableStateOf(false) }
    var boothMode by rememberSaveable { mutableStateOf<String?>(null) } // "ai" | "collage"
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

    LaunchedEffect(showWelcome, state.isProcessing, showAdmin) {
        if (showWelcome || showAdmin) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            if (viewModel.uiState.value.isProcessing) continue
            if (SystemClock.uptimeMillis() - lastInteraction >= IDLE_TIMEOUT_MS) {
                viewModel.endSession()
                boothMode = null
                showWelcome = true
                break
            }
        }
    }

    fun goHome() {
        viewModel.endSession()
        showWelcome = true
        boothMode = null
        lastInteraction = SystemClock.uptimeMillis()
    }

    fun startSession() {
        showWelcome = false
        lastInteraction = SystemClock.uptimeMillis()
    }

    BackHandler(enabled = showAdmin) {
        showAdmin = false
        lastInteraction = SystemClock.uptimeMillis()
    }

    BackHandler(enabled = !showWelcome && !state.isProcessing && !showAdmin) { goHome() }

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

            showAdmin -> AdminGate(
                viewModel = viewModel,
                onClose = {
                    showAdmin = false
                    lastInteraction = SystemClock.uptimeMillis()
                },
            )

            showWelcome -> WelcomeScreen(
                loadThumbnail = viewModel::loadThumbnail,
                branding = state.customization.eventBranding,
                onStart = { startSession() },
                onAdminRequested = {
                    showAdmin = true
                    lastInteraction = SystemClock.uptimeMillis()
                },
            )

            boothMode == null -> ModeSelectScreen(
                onPickAi = {
                    boothMode = "ai"
                    lastInteraction = SystemClock.uptimeMillis()
                },
                onPickCollage = {
                    boothMode = "collage"
                    lastInteraction = SystemClock.uptimeMillis()
                },
                onHome = { goHome() },
            )

            boothMode == "ai" -> com.photobooth.kiosk.ui.ai.AiSingleFlow(
                onHome = { goHome() },
                onInteraction = { lastInteraction = SystemClock.uptimeMillis() },
                previewRotationDegrees = state.cameraRotationDegrees,
            )

            else -> com.photobooth.kiosk.ui.collage.CollageFlow(
                viewModel = viewModel,
                onHome = { goHome() },
                onInteraction = { lastInteraction = SystemClock.uptimeMillis() },
                previewRotationDegrees = state.cameraRotationDegrees,
            )
        }
    }
}

@Composable
private fun WelcomeScreen(
    loadThumbnail: (String) -> Bitmap?,
    branding: EventBranding,
    onStart: () -> Unit,
    onAdminRequested: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "welcome_pulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_scale",
    )
    val ctaPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cta_pulse",
    )

    val thumbGala by produceState<Bitmap?>(initialValue = null, "gala") {
        value = withContext(Dispatchers.IO) { loadThumbnail("gala") }
    }
    val thumbNeon by produceState<Bitmap?>(initialValue = null, "neon") {
        value = withContext(Dispatchers.IO) { loadThumbnail("neon") }
    }
    val thumbPlaya by produceState<Bitmap?>(initialValue = null, "playa") {
        value = withContext(Dispatchers.IO) { loadThumbnail("playa") }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(InkSoft, Ink))),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .scale(glowScale)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            SyscomBlue.copy(alpha = 0.42f),
                            SyscomBlueDeep.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SyscomBlue.copy(alpha = 0.22f))
                        .border(1.dp, SyscomBlueLight.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { onAdminRequested() })
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = branding.brand,
                        color = SyscomBlueLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.5.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Cream.copy(alpha = 0.06f))
                        .border(1.dp, Cream.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = SyscomBlueLight,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = branding.tagline.uppercase(),
                        color = Cream.copy(alpha = 0.78f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                    )
                }
            }

            Spacer(Modifier.weight(0.8f))

            // Fanned 3-card collage showcase with actual booth background thumbnails
            Box(
                modifier = Modifier.size(width = 330.dp, height = 300.dp),
                contentAlignment = Alignment.Center,
            ) {
                WelcomeShowcaseCard(
                    bitmap = thumbPlaya,
                    label = "PLAYA",
                    modifier = Modifier
                        .offset(x = (-66).dp, y = 14.dp)
                        .rotate(-12f)
                        .size(width = 166.dp, height = 232.dp),
                    accentColor = SyscomBlue.copy(alpha = 0.65f),
                )
                WelcomeShowcaseCard(
                    bitmap = thumbNeon,
                    label = "CYBER NEÓN",
                    modifier = Modifier
                        .offset(x = 66.dp, y = 14.dp)
                        .rotate(12f)
                        .size(width = 166.dp, height = 232.dp),
                    accentColor = Color(0xFF00E5FF).copy(alpha = 0.65f),
                )
                // Center hero card simulating the 1+2 poster collage
                Column(
                    modifier = Modifier
                        .size(width = 204.dp, height = 272.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    InkSoft,
                                    Ink,
                                ),
                            ),
                        )
                        .border(2.dp, SyscomBlueLight.copy(alpha = 0.78f), RoundedCornerShape(26.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SyscomBlue)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = branding.brand,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.4.sp,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "COLLAGE STUDIO",
                            color = SyscomBlueLight,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.25f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SyscomBlue.copy(alpha = 0.25f))
                            .border(1.dp, SyscomBlueLight.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        thumbGala?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .background(Ink.copy(alpha = 0.65f), CircleShape)
                                .border(1.5.dp, SyscomBlueLight, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = null,
                                tint = Cream,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(0.85f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(InkSoft)
                                .border(1.dp, SyscomBlueLight.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                        ) {
                            thumbNeon?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(InkSoft)
                                .border(1.dp, SyscomBlueLight.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                        ) {
                            thumbPlaya?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = branding.badgeHeadline,
                        color = Cream,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.6.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = branding.welcomeTitle,
                color = Cream,
                fontSize = 46.sp,
                lineHeight = 50.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = branding.welcomeSubtitle,
                color = Cream.copy(alpha = 0.72f),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 460.dp),
            )
            if (branding.hashtag.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = branding.hashtag,
                    color = SyscomBlueLight,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WelcomeStepPill(stringResource(R.string.welcome_step_1))
                WelcomeStepPill(stringResource(R.string.welcome_step_2))
                WelcomeStepPill(stringResource(R.string.welcome_step_3))
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onStart,
                modifier = Modifier
                    .scale(ctaPulse)
                    .widthIn(max = 430.dp)
                    .fillMaxWidth()
                    .height(70.dp)
                    .border(1.5.dp, SyscomBlueLight.copy(alpha = 0.65f), RoundedCornerShape(35.dp)),
                shape = RoundedCornerShape(35.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SyscomBlue,
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.welcome_cta),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.welcome_hint),
                color = Cream.copy(alpha = 0.45f),
                fontSize = 13.sp,
            )
        }

        // Discreet guest language switch: absolute corner so the kiosk
        // layout (including the 55" vertical panel) never shifts.
        LanguageQuickToggle(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun WelcomeShowcaseCard(
    bitmap: Bitmap?,
    label: String,
    modifier: Modifier = Modifier,
    accentColor: Color,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(InkSoft)
            .border(1.5.dp, accentColor, RoundedCornerShape(22.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Ink),
        ) {
            bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = Cream.copy(alpha = 0.78f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
        )
    }
}

@Composable
private fun WelcomeStepPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(InkSoft.copy(alpha = 0.9f))
            .border(1.dp, SyscomBlueLight.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = Cream.copy(alpha = 0.86f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
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
