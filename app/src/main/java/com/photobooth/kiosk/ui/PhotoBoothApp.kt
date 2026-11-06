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
            )

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
                WelcomeStepPill("1. 3 Poses seguidas")
                WelcomeStepPill("2. Diseña tu collage")
                WelcomeStepPill("3. Comparte")
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
private fun DeliveryOptionsDialog(
    onDismiss: () -> Unit,
    onQuickShare: () -> Unit,
    onShowQr: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f).widthIn(max = 420.dp),
            shape = RoundedCornerShape(28.dp),
            color = InkSoft,
            border = BorderStroke(1.dp, SyscomBlueLight.copy(alpha = 0.28f)),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.download_options_title),
                    color = Cream,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.download_options_subtitle),
                    color = Cream.copy(alpha = 0.68f),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onQuickShare,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SyscomBlue,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.quick_share_option), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onShowQr,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.5.dp, SyscomBlueLight.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream),
                ) {
                    Icon(Icons.Rounded.QrCode2, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.qr_option), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.admin_cancel), color = Cream.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun QrDeliveryDialog(
    session: DeliverySession,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val qr = remember(session.pageUrl) {
        QrCodeGenerator.generate(session.pageUrl, 720)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 420.dp),
            shape = RoundedCornerShape(28.dp),
            color = InkSoft,
            border = BorderStroke(1.dp, SyscomBlueLight.copy(alpha = 0.28f)),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.qr_title),
                    color = Cream,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        if (session.token == "drive") R.string.qr_hint_drive else R.string.qr_hint,
                    ),
                    color = Cream.copy(alpha = 0.68f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White)
                        .padding(12.dp),
                ) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(260.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = session.pageUrl,
                    color = Cream.copy(alpha = 0.58f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(session.pageUrl))
                            copied = true
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.2.dp, SyscomBlueLight.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream),
                    ) {
                        Text(
                            text = stringResource(
                                if (copied) R.string.link_copied else R.string.copy_link,
                            ),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SyscomBlue,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(stringResource(R.string.close), fontWeight = FontWeight.Bold)
                    }
                }
            }
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
    var countdownFraction by remember { mutableFloatStateOf(1f) }
    var autoSequenceActive by remember { mutableStateOf(false) }
    var betweenShotBanner by remember { mutableStateOf<String?>(null) }
    var flash by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraKey by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var showBackgrounds by remember { mutableStateOf(false) }
    var showDeliveryOptions by remember { mutableStateOf(false) }
    var deliverySession by remember { mutableStateOf<DeliverySession?>(null) }

    val selectedOption = state.backgrounds.firstOrNull { it.id == state.selectedBackgroundId }
    val selectedName = selectedOption?.name ?: ""

    LaunchedEffect(state.capturedCount) {
        if (state.capturedCount == 0) {
            autoSequenceActive = false
            betweenShotBanner = null
            countdown = 0
        } else if (state.capturedCount >= PhotoBoothUiState.PHOTO_COUNT) {
            autoSequenceActive = false
            betweenShotBanner = null
            countdown = 0
            cameraReady = false
        }
    }

    // Automatic 3-shot sequence controller: triggers Shot 1 -> pause -> Shot 2 -> pause -> Shot 3
    LaunchedEffect(autoSequenceActive, state.capturedCount, cameraReady) {
        if (!autoSequenceActive || !cameraReady) return@LaunchedEffect
        val shotIndex = state.capturedCount
        if (shotIndex >= PhotoBoothUiState.PHOTO_COUNT) {
            autoSequenceActive = false
            return@LaunchedEffect
        }

        onInteraction()
        if (shotIndex > 0) {
            betweenShotBanner = context.getString(
                R.string.countdown_next_pose,
                shotIndex,
                shotIndex + 1,
            )
            delay(1_250L)
            betweenShotBanner = null
        }

        viewModel.beginCapture()
        for (sec in 3 downTo 1) {
            countdown = sec
            val steps = 20
            for (step in 0 until steps) {
                val remainingSecs = (sec - 1) + (1f - step / steps.toFloat())
                countdownFraction = (remainingSecs / 3f).coerceIn(0f, 1f)
                delay(50L)
            }
        }
        countdown = 0
        countdownFraction = 0f
        flash = true
        delay(110L)
        flash = false
        onInteraction()
        captureRequest += 1
    }

    fun startCapture() {
        if (!cameraReady || busy || state.isProcessing || state.isCapturing ||
            state.capturedCount >= PhotoBoothUiState.PHOTO_COUNT || countdown > 0 || autoSequenceActive
        ) return
        autoSequenceActive = true
    }

    fun sharePhoto() {
        if (busy || state.isExporting) return
        scope.launch {
            busy = true
            val files = viewModel.ensureExportedFiles()
            if (files == null || files.size != 4) {
                busy = false
                snackbarHostState.showSnackbar(context.getString(R.string.share_error))
                return@launch
            }
            val uris = withContext(Dispatchers.IO) {
                PhotoSaver.cacheFilesForShare(context, files)
            }
            if (uris == null || uris.size != files.size) {
                busy = false
                snackbarHostState.showSnackbar(context.getString(R.string.share_error))
                return@launch
            }

            val launched = PhotoSaver.launchDownload(
                context = context,
                uris = uris,
                chooserTitle = context.getString(R.string.share_chooser),
            )
            busy = false
            if (launched) {
                onShared()
            } else {
                snackbarHostState.showSnackbar(context.getString(R.string.share_error))
            }
        }
    }

    fun showQrDelivery() {
        if (busy || state.isExporting) return
        scope.launch {
            busy = true
            val session = viewModel.startDelivery()
            busy = false
            if (session == null && viewModel.uiState.value.errorMessage == null) {
                snackbarHostState.showSnackbar(context.getString(R.string.qr_error))
            } else {
                session?.let { deliverySession = it }
            }
        }
    }

    fun closeQrDelivery() {
        deliverySession = null
        viewModel.stopDelivery()
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    val handDetectionActive = cameraReady &&
        !state.isProcessing &&
        !state.isCapturing &&
        !state.hasResult &&
        state.capturedCount < PhotoBoothUiState.PHOTO_COUNT &&
        countdown == 0 &&
        !showBackgrounds

    Box(modifier = Modifier.fillMaxSize().background(Ink)) {
        if (state.capturedCount < PhotoBoothUiState.PHOTO_COUNT) {
            key(cameraKey) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    captureRequest = captureRequest,
                    handDetectionEnabled = handDetectionActive,
                    previewRotationDegrees = state.cameraRotationDegrees,
                    onPalmDetected = {
                        onInteraction()
                        startCapture()
                    },
                    onReady = {
                        cameraReady = true
                        cameraError = null
                    },
                    onCaptured = { bitmap, rotation, mirror ->
                        cameraReady = true
                        cameraError = null
                        viewModel.processPhoto(bitmap, rotation, mirror)
                    },
                    onError = { message ->
                        Log.e(LOG_TAG, "Camera error: $message")
                        autoSequenceActive = false
                        betweenShotBanner = null
                        countdown = 0
                        viewModel.captureFailed()
                        cameraReady = false
                        cameraError = message
                    },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Ink.copy(alpha = 0.88f), Color.Transparent),
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

            // Top progress HUD with live captured thumbnails
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp, start = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = state.customization.eventBranding.brand,
                    color = SyscomBlueLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp,
                )
                Text(
                    text = stringResource(
                        R.string.photo_progress,
                        (state.capturedCount + 1).coerceAtMost(PhotoBoothUiState.PHOTO_COUNT),
                    ),
                    color = Cream,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = selectedName,
                    color = Cream.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(PhotoBoothUiState.PHOTO_COUNT) { index ->
                        val capturedThumb = state.captureThumbnails.getOrNull(index)
                        val isCurrent = index == state.capturedCount
                        Box(
                            modifier = Modifier
                                .size(width = 58.dp, height = 78.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCurrent) SyscomBlue.copy(alpha = 0.35f)
                                    else InkSoft.copy(alpha = 0.75f),
                                )
                                .border(
                                    width = if (isCurrent) 2.5.dp else 1.dp,
                                    color = when {
                                        capturedThumb != null -> SyscomBlueLight
                                        isCurrent -> Color.White
                                        else -> Cream.copy(alpha = 0.25f)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (capturedThumb != null && !capturedThumb.isRecycled) {
                                Image(
                                    bitmap = capturedThumb.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .size(16.dp)
                                        .background(SyscomBlue, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp),
                                    )
                                }
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    color = if (isCurrent) Color.White else Cream.copy(alpha = 0.45f),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            // Bottom camera controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(118.dp)) {
                    CaptureButton(
                        modifier = Modifier.align(Alignment.Center),
                        enabled = cameraReady && !state.isProcessing && !state.isCapturing &&
                            state.capturedCount == 0 && countdown == 0 && !autoSequenceActive,
                        isRunningSequence = autoSequenceActive || countdown > 0 || state.isCapturing,
                        onClick = { startCapture() },
                    )
                    OutlinedButton(
                        onClick = { showBackgrounds = true },
                        enabled = state.capturedCount == 0 && !state.isProcessing &&
                            countdown == 0 && !autoSequenceActive,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 20.dp)
                            .height(46.dp),
                        shape = RoundedCornerShape(23.dp),
                        border = BorderStroke(1.dp, Cream.copy(alpha = 0.28f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.backgrounds),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    IconButton(
                        onClick = {
                            autoSequenceActive = false
                            onHome()
                        },
                        enabled = !state.isProcessing,
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
                    text = stringResource(R.string.capture_auto_sequence_hint),
                    color = Cream.copy(alpha = 0.66f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }

        if (state.capturedCount >= PhotoBoothUiState.PHOTO_COUNT) {
            StudioWizardScreen(
                state = state,
                backgrounds = state.backgrounds,
                loadThumbnail = viewModel::loadThumbnail,
                enabled = !busy && !state.isExporting,
                onStepSelected = viewModel::setWizardStep,
                onSelectPhotoTarget = viewModel::selectResult,
                onSelectBackground = viewModel::selectBackground,
                onApplyBackgroundToAll = viewModel::setApplyBackgroundToAll,
                onSelectAiBackground = viewModel::selectAiBackground,
                onSelectLayout = viewModel::selectCollageLayout,
                onSelectFrameStyle = viewModel::selectFrameStyle,
                onSelectBorderStyle = viewModel::selectPhotoBorderStyle,
                onSelectBadgePreset = viewModel::selectBadgePreset,
                onToggleDateStamp = viewModel::toggleDateStamp,
                onShare = { showDeliveryOptions = true },
                onRetake = {
                    autoSequenceActive = false
                    cameraReady = false
                    viewModel.clearResult()
                },
                onHome = onHome,
            )
        }

        if (showDeliveryOptions) {
            DeliveryOptionsDialog(
                onDismiss = { showDeliveryOptions = false },
                onQuickShare = {
                    showDeliveryOptions = false
                    sharePhoto()
                },
                onShowQr = {
                    showDeliveryOptions = false
                    showQrDelivery()
                },
            )
        }

        deliverySession?.let { session ->
            QrDeliveryDialog(
                session = session,
                onDismiss = { closeQrDelivery() },
            )
        }

        betweenShotBanner?.let { bannerText ->
            BetweenShotPoseBanner(
                text = bannerText,
                nextShotNumber = (state.capturedCount + 1).coerceAtMost(PhotoBoothUiState.PHOTO_COUNT),
            )
        }

        if (countdown > 0) {
            CountdownOverlay(
                count = countdown,
                fraction = countdownFraction,
                shotNumber = (state.capturedCount + 1).coerceAtMost(PhotoBoothUiState.PHOTO_COUNT),
            )
        }

        if (flash) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.90f)))
        }

        if (cameraError != null && !state.hasResult) {
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
                backgrounds = state.backgrounds,
                selectedId = state.selectedBackgroundId,
                onSelect = { id ->
                    if (!state.hasResult) {
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
    isRunningSequence: Boolean = false,
    onClick: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "capture_btn")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (enabled) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ring_scale",
    )

    Box(
        modifier = modifier
            .size(116.dp)
            .scale(ringScale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(
                width = 3.5.dp,
                color = if (isRunningSequence) SyscomBlueLight else Color.White.copy(alpha = 0.90f),
                shape = CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .background(
                    when {
                        isRunningSequence -> SyscomBlueDeep
                        enabled -> SyscomBlue
                        else -> Cream.copy(alpha = 0.20f)
                    },
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isRunningSequence) {
                CircularProgressIndicator(
                    color = SyscomBlueLight,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = stringResource(R.string.capture),
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackgroundSheet(
    viewModel: PhotoBoothViewModel,
    backgrounds: List<BackgroundOption>,
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
                items(backgrounds, key = { it.id }) { option ->
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
            if (option.id == "original") {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    tint = SyscomBlueLight,
                    modifier = Modifier.size(34.dp),
                )
            } else {
                thumbnail?.let { bitmap ->
                    if (!bitmap.isRecycled) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
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
            text = option.name,
            color = if (selected) Cream else Cream.copy(alpha = 0.62f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun BetweenShotPoseBanner(
    text: String,
    nextShotNumber: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            InkSoft.copy(alpha = 0.96f),
                            Ink.copy(alpha = 0.96f),
                        ),
                    ),
                )
                .border(2.5.dp, SyscomBlueLight, RoundedCornerShape(32.dp))
                .padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(SyscomBlue)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.photo_progress, nextShotNumber),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = text,
                color = Cream,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CountdownOverlay(
    count: Int,
    fraction: Float,
    shotNumber: Int,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "countdown_glow")
    val outerPulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "outer_pulse",
    )
    val animatedSweep by animateFloatAsState(
        targetValue = fraction * 360f,
        animationSpec = tween(durationMillis = 70, easing = LinearEasing),
        label = "sweep_arc",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(
                width = 8.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        SyscomBlueLight.copy(alpha = 0.85f),
                        SyscomBlue.copy(alpha = 0.55f),
                        SyscomBlueLight.copy(alpha = 0.85f),
                    ),
                ),
                shape = RoundedCornerShape(0.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Ink.copy(alpha = 0.78f))
                    .border(1.5.dp, SyscomBlueLight.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            ) {
                Text(
                    text = stringResource(
                        if (count == 1) R.string.countdown_smile else R.string.countdown_get_ready,
                    ),
                    color = Cream,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp,
                )
            }

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(270.dp)
                    .scale(outerPulse),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Ambient outer halo
                    drawCircle(
                        color = SyscomBlue.copy(alpha = 0.28f),
                        radius = size.minDimension / 2f,
                    )
                    // Dark glass backdrop
                    drawCircle(
                        color = Ink.copy(alpha = 0.76f),
                        radius = size.minDimension / 2f - 16.dp.toPx(),
                    )
                    // Track circle
                    drawCircle(
                        color = Color.White.copy(alpha = 0.16f),
                        radius = size.minDimension / 2f - 16.dp.toPx(),
                        style = Stroke(width = 14.dp.toPx()),
                    )
                    // Progress sweep arc
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                SyscomBlueLight,
                                Color(0xFF00E5FF),
                                Cream,
                                SyscomBlueLight,
                            ),
                        ),
                        startAngle = -90f,
                        sweepAngle = animatedSweep,
                        useCenter = false,
                        style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round),
                    )
                }

                AnimatedContent(
                    targetState = count,
                    transitionSpec = {
                        (scaleIn(
                            initialScale = 0.45f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ) + fadeIn(tween(150))) togetherWith fadeOut(tween(120))
                    },
                    label = "countdown_digit",
                ) { targetCount ->
                    Text(
                        text = targetCount.toString(),
                        color = Color.White,
                        fontSize = 136.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.photo_progress, shotNumber),
                color = SyscomBlueLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.5.sp,
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
