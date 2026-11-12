package com.photobooth.kiosk.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.StartOffset
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.photobooth.kiosk.data.EventBranding
import com.photobooth.kiosk.ui.attract.ATTRACT_TAP_GRACE_MS
import com.photobooth.kiosk.ui.attract.AttractOverlay
import com.photobooth.kiosk.ui.attract.shouldShowAttract
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.InkSoft
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomBlueLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var attractVisible by remember { mutableStateOf(false) }
    var attractTapGuard by remember { mutableStateOf(false) }
    var attractFlashKey by remember { mutableIntStateOf(0) }
    val attractFlash = remember { Animatable(0f) }

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

    var idleRemainingSec by remember { mutableIntStateOf((IDLE_TIMEOUT_MS / 1000L).toInt()) }
    val showIdleTimer = !showWelcome && !showAdmin && hasCameraPermission

    LaunchedEffect(showWelcome, state.isProcessing, showAdmin, lastInteraction) {
        if (showWelcome || showAdmin) {
            idleRemainingSec = (IDLE_TIMEOUT_MS / 1000L).toInt()
            return@LaunchedEffect
        }
        while (true) {
            val elapsed = SystemClock.uptimeMillis() - lastInteraction
            val remainingMs = (IDLE_TIMEOUT_MS - elapsed).coerceAtLeast(0L)
            idleRemainingSec = ((remainingMs + 999L) / 1000L).toInt()
            if (viewModel.uiState.value.isProcessing) {
                delay(1_000L)
                continue
            }
            if (remainingMs <= 0L) {
                viewModel.endSession()
                boothMode = null
                showWelcome = true
                break
            }
            delay(250L)
        }
    }

    fun goHome() {
        viewModel.endSession()
        showWelcome = true
        boothMode = null
        lastInteraction = SystemClock.uptimeMillis()
    }

    LaunchedEffect(showWelcome, showAdmin, lastInteraction, attractTapGuard, state.attractIdleMs) {
        if (attractVisible || attractTapGuard) return@LaunchedEffect
        if (!showWelcome || showAdmin) return@LaunchedEffect
        val remaining = state.attractIdleMs - (SystemClock.uptimeMillis() - lastInteraction)
        if (remaining > 0L) delay(remaining)
        val idleMs = SystemClock.uptimeMillis() - lastInteraction
        if (
            shouldShowAttract(
                showWelcome = showWelcome,
                showAdmin = showAdmin,
                hasSession = !showWelcome,
                idleMs = idleMs,
                delayMs = state.attractIdleMs,
            )
        ) {
            attractVisible = true
        }
    }

    LaunchedEffect(attractTapGuard) {
        if (!attractTapGuard) return@LaunchedEffect
        delay(ATTRACT_TAP_GRACE_MS)
        attractTapGuard = false
    }

    LaunchedEffect(attractFlashKey) {
        if (attractFlashKey == 0) return@LaunchedEffect
        attractFlash.snapTo(1f)
        attractFlash.animateTo(0f, animationSpec = tween(500))
    }

    fun dismissAttract() {
        if (!attractVisible) return
        attractVisible = false
        attractTapGuard = true
        attractFlashKey += 1
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

            else -> com.photobooth.kiosk.ui.collage.CollageFlow(
                viewModel = viewModel,
                onHome = { goHome() },
                onInteraction = { lastInteraction = SystemClock.uptimeMillis() },
            )
        }

        AttractOverlay(
            visible = attractVisible && !showAdmin && showWelcome,
            videoId = state.attractVideoId,
            onDismiss = { dismissAttract() },
        )

        if (attractFlash.value > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = attractFlash.value)),
            )
        }

        if (attractTapGuard) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {}, onLongPress = {})
                    },
            )
        }

        if (showIdleTimer) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable {
                        lastInteraction = SystemClock.uptimeMillis()
                        idleRemainingSec = (IDLE_TIMEOUT_MS / 1000L).toInt()
                    },
                color = InkSoft.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, Cream.copy(alpha = 0.28f)),
                shape = RoundedCornerShape(999.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.idle_timer_seconds, idleRemainingSec),
                        color = Cream,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.idle_timer_hint),
                        color = Cream.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(
    branding: EventBranding,
    onStart: () -> Unit,
    onAdminRequested: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "welcome_pulse")
    val ctaPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cta_pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(InkSoft, Ink))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 28.dp),
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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = branding.brand,
                        color = SyscomBlueLight,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                LanguageQuickToggle()
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.welcome_far_title),
                color = Cream,
                fontSize = 92.sp,
                lineHeight = 96.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.welcome_far_line),
                color = SyscomBlueLight,
                fontSize = 40.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                FloatingFlowStep(
                    imageRes = R.drawable.flow_pose,
                    step = "1",
                    label = stringResource(R.string.welcome_flow_1),
                    phaseMs = 0,
                    alignEnd = false,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                FloatingFlowStep(
                    imageRes = R.drawable.flow_style,
                    step = "2",
                    label = stringResource(R.string.welcome_flow_2),
                    phaseMs = 500,
                    alignEnd = true,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                FloatingFlowStep(
                    imageRes = R.drawable.flow_keep,
                    step = "3",
                    label = stringResource(R.string.welcome_flow_3),
                    phaseMs = 1000,
                    alignEnd = false,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onStart,
                modifier = Modifier
                    .scale(ctaPulse)
                    .fillMaxWidth()
                    .height(104.dp)
                    .border(2.dp, SyscomBlueLight.copy(alpha = 0.7f), RoundedCornerShape(52.dp)),
                shape = RoundedCornerShape(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SyscomBlue,
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.welcome_cta),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun FloatingFlowStep(
    imageRes: Int,
    step: String,
    label: String,
    phaseMs: Int,
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
) {
    val floatY by rememberInfiniteTransition(label = "flow_$step").animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(phaseMs),
        ),
        label = "flow_y_$step",
    )
    Row(
        modifier = modifier.graphicsLayer { translationY = floatY },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        if (alignEnd) {
            FlowCaption(step, label, TextAlign.End)
            Spacer(Modifier.width(22.dp))
        }
        Image(
            painter = painterResource(imageRes),
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(3f / 4f, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, Cream.copy(alpha = 0.16f), RoundedCornerShape(28.dp)),
        )
        if (!alignEnd) {
            Spacer(Modifier.width(22.dp))
            FlowCaption(step, label, TextAlign.Start)
        }
    }
}

@Composable
private fun FlowCaption(step: String, label: String, align: TextAlign) {
    Column(horizontalAlignment = if (align == TextAlign.End) Alignment.End else Alignment.Start) {
        Text(
            text = step,
            color = SyscomBlueLight,
            fontSize = 54.sp,
            fontWeight = FontWeight.Black,
            textAlign = align,
        )
        Text(
            text = label,
            color = Cream,
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = align,
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
