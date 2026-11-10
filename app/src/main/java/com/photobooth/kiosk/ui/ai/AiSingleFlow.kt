package com.photobooth.kiosk.ui.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photobooth.kiosk.R
import com.photobooth.kiosk.camera.CameraPreview
import com.photobooth.kiosk.data.AiAccessories
import com.photobooth.kiosk.data.AiStatus
import com.photobooth.kiosk.data.AiStyles
import com.photobooth.kiosk.data.BackgroundOption
import com.photobooth.kiosk.data.BackgroundRepository
import com.photobooth.kiosk.ui.backgroundLabel
import com.photobooth.kiosk.data.CollageFrameStyle
import com.photobooth.kiosk.data.EventProfile
import com.photobooth.kiosk.data.FrameDecoration
import com.photobooth.kiosk.data.FramePalette
import com.photobooth.kiosk.data.palette
import com.photobooth.kiosk.delivery.LocalDeliveryServer
import com.photobooth.kiosk.network.BoothStyle
import com.photobooth.kiosk.photo.PhotoSaver
import com.photobooth.kiosk.photo.QrCodeGenerator
import com.photobooth.kiosk.ui.components.BoothBottomBar
import com.photobooth.kiosk.ui.components.BoothFlowHeader
import com.photobooth.kiosk.ui.components.BoothOutlinedButton
import com.photobooth.kiosk.ui.components.BoothSecondaryButton
import com.photobooth.kiosk.ui.components.BoothSectionTitle
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.InkSoft
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomBlueLight
import com.photobooth.kiosk.ui.theme.SyscomTeal
import com.photobooth.kiosk.ui.QrZoomDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FINISH_GUARD_MS = 3_000L
private const val REVEAL_MS = 1_800L

private enum class AiStep(val index: Int, val titleRes: Int) {
    CAPTURE(0, R.string.ai_step_capture),
    STYLE(1, R.string.ai_step_style),
    BACKGROUND(2, R.string.ai_step_background),
    ACCESSORIES(3, R.string.ai_step_accessories),
    FRAMED(4, R.string.ai_step_frame),
    SHARE(5, R.string.ai_step_share),
}

private const val TOTAL_STEPS = 6

private fun accessoryIcon(id: String?): Int? = when (id) {
    null -> null
    "cap" -> R.drawable.ic_accessory_cap
    "headband" -> R.drawable.ic_accessory_headband
    "crown" -> R.drawable.ic_accessory_crown
    "party_hat" -> R.drawable.ic_accessory_party_hat
    "bunny_ears" -> R.drawable.ic_accessory_bunny_ears
    "sunglasses" -> R.drawable.ic_accessory_sunglasses
            "sharingan" -> R.drawable.ic_accessory_sunglasses
    "mask" -> R.drawable.ic_accessory_mask
    "mustache" -> R.drawable.ic_accessory_mustache
    "flowers" -> R.drawable.ic_accessory_flowers
    "scarf" -> R.drawable.ic_accessory_scarf
    "lei" -> R.drawable.ic_accessory_lei
    "ssj_yellow", "ssj_red", "ssj_blue", "ssj_rose", "naruto" -> R.drawable.ic_accessory_ssj_hair
    "gi_syscom" -> R.drawable.ic_accessory_gi_syscom
            "naruto_outfit" -> R.drawable.ic_accessory_gi_syscom
            "akatsuki" -> R.drawable.ic_accessory_tux
    "bulma_style" -> R.drawable.ic_accessory_bulma_style
    "gown" -> R.drawable.ic_accessory_gown
    "tux" -> R.drawable.ic_accessory_tux
    "santa_hat" -> R.drawable.ic_accessory_santa_hat
    "cowboy" -> R.drawable.ic_accessory_cowboy
    "flower_crown" -> R.drawable.ic_accessory_flower_crown
    "guitar" -> R.drawable.ic_accessory_guitar
    "balloons" -> R.drawable.ic_accessory_balloons
    else -> null
}

private fun accessoryTint(id: String?): Color = when (id) {
    "ssj_yellow" -> Color(0xFFFFD54F)
    "ssj_red" -> Color(0xFFEF5350)
    "ssj_blue" -> Color(0xFF42A5F5)
    "ssj_rose" -> Color(0xFFF48FB1)
    null -> Cream.copy(alpha = 0.55f)
    else -> Cream
}

/**
 * Single-photo IA flow, kiosk-first layout:
 * fixed top bar + progress, scrollable content, fixed bottom bar.
 * Camera gesture (open palm) triggers capture like the button.
 */
@Composable
fun AiSingleFlow(
    onHome: () -> Unit,
    onInteraction: () -> Unit,
    previewRotationDegrees: Int = 0,
    viewModel: AiSingleViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The admin may have switched styles on or off since the last guest run.
    LaunchedEffect(Unit) {
        viewModel.refreshModel()
        viewModel.refreshStyleSettings()
        viewModel.refreshEventProfiles()
    }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val compact = LocalConfiguration.current.screenWidthDp < 600

    var step by remember { mutableStateOf(AiStep.CAPTURE) }
    var captureRequest by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var cameraReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var flash by remember { mutableStateOf(false) }
    var deliveryUrl by remember { mutableStateOf<String?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var shareStarting by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf<LocalDeliveryServer?>(null) }
    var stylePreviews by remember { mutableStateOf(mapOf<String, Bitmap>()) }
    var bgPreviews by remember { mutableStateOf(mapOf<String, Bitmap>()) }
    var previewTitle by remember { mutableStateOf<String?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val hPad = if (compact) 18.dp else 32.dp
    val maxWidth = if (compact) 560.dp else 680.dp

    fun closeDelivery() {
        deliveryUrl = null
        qrBitmap?.recycle()
        qrBitmap = null
        server?.stop()
        server = null
        shareStarting = false
    }

    fun exit() {
        closeDelivery()
        viewModel.reset()
        onHome()
    }

    // Style preview thumbnails (bundled assets). Keys come from the local catalog
    // plus whatever the server advertises, so new server styles only need an asset.
    LaunchedEffect(state.serverStyles) {
        val ids = (AiStyles.ALL.map { it.id } + state.serverStyles.map { it.id }).distinct()
        stylePreviews = withContext(Dispatchers.IO) {
            ids.mapNotNull { id ->
                runCatching {
                    context.assets.open("style_previews/$id.webp").use {
                        BitmapFactory.decodeStream(it)?.let { bmp -> id to bmp }
                    }
                }.getOrNull()
            }.toMap()
        }
    }

    // Background preview thumbnails (bundled + custom imports).
    LaunchedEffect(Unit) {
        bgPreviews = withContext(Dispatchers.IO) {
            viewModel.backgrounds.mapNotNull { option ->
                runCatching { viewModel.loadThumbnail(option.id)?.let { option.id to it } }.getOrNull()
            }.toMap()
        }
    }

    // Keep the 60s kiosk idle timer alive while the AI job runs.
    LaunchedEffect(state.status) {
        if (state.status == AiStatus.UPLOADING || state.status == AiStatus.GENERATING) {
            while (true) {
                delay(15_000)
                onInteraction()
            }
        }
    }

    // A finished generation keeps the overlay up a moment longer to reveal the result.
    var revealing by remember { mutableStateOf(false) }
    var wasGenerating by remember { mutableStateOf(false) }
    LaunchedEffect(state.status) {
        if (state.status == AiStatus.UPLOADING || state.status == AiStatus.GENERATING) {
            wasGenerating = true
            return@LaunchedEffect
        }
        val finished = wasGenerating && state.hasResult
        wasGenerating = false
        if (!finished) return@LaunchedEffect
        revealing = true
        try {
            delay(REVEAL_MS)
        } finally {
            revealing = false
        }
    }

    // After generation finishes, jump straight to frame selection.
    LaunchedEffect(state.hasResult) {
        if (state.hasResult && step == AiStep.ACCESSORIES) step = AiStep.FRAMED
    }
    LaunchedEffect(state.errorEventId) {
        state.error?.let { snackbar.showSnackbar(it) }
    }

    // Leaving the flow (idle timeout, back, close): stop QR server, free bitmaps.
    DisposableEffect(Unit) {
        onDispose {
            deliveryUrl = null
            qrBitmap?.recycle()
            qrBitmap = null
            server?.stop()
            server = null
            stylePreviews.values.forEach { if (!it.isRecycled) it.recycle() }
            bgPreviews.values.forEach { if (!it.isRecycled) it.recycle() }
            viewModel.reset()
        }
    }

    fun startCapture() {
        if (countdown > 0 || step != AiStep.CAPTURE) return
        scope.launch {
            countdown = 3
            while (countdown > 0) {
                delay(700)
                countdown--
            }
            flash = true
            captureRequest++
            delay(180)
            flash = false
            onInteraction()
        }
    }

    fun startShare() {
        if (shareStarting || deliveryUrl != null) return
        scope.launch {
            val file = state.resultFile ?: return@launch
            shareStarting = true
            deliveryUrl = null
            qrBitmap?.recycle()
            qrBitmap = null
            server?.stop()
            server = null
            val remote = withContext(Dispatchers.IO) { viewModel.driveShareUrl() }
            if (!remote.isNullOrBlank()) {
                deliveryUrl = remote
                qrBitmap = withContext(Dispatchers.IO) { QrCodeGenerator.generate(remote, 880) }
                shareStarting = false
                onInteraction()
                return@launch
            }
            val srv = LocalDeliveryServer()
            val session = withContext(Dispatchers.IO) {
                srv.start(listOf(file), context.getString(R.string.ai_delivery_title))
            }
            if (session == null) {
                shareStarting = false
                snackbar.showSnackbar(context.getString(R.string.qr_error))
                return@launch
            }
            server = srv
            deliveryUrl = session.pageUrl
            qrBitmap = withContext(Dispatchers.IO) {
                QrCodeGenerator.generate(session.pageUrl, 880)
            }
            shareStarting = false
            onInteraction()
        }
    }

    val gestureActive = state.palmCaptureEnabled &&
        step == AiStep.CAPTURE &&
        !state.hasPhoto &&
        countdown == 0

    // Auto-start the QR server when the share screen opens.
    LaunchedEffect(step) {
        if (step == AiStep.SHARE && state.resultFile != null) startShare()
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(Modifier.fillMaxSize()) {
            BoothFlowHeader(
                title = stringResource(R.string.ai_flow_title),
                stepIndex = step.index,
                totalSteps = TOTAL_STEPS,
                stepTitle = stringResource(step.titleRes),
                horizontalPadding = hPad,
                onClose = { exit() },
            )

            // Content
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                val contentScroll = rememberScrollState()
                // Each step starts at the top: the previous step may have been
                // scrolled down, which used to leave the next step half visible.
                LaunchedEffect(step) { contentScroll.scrollTo(0) }
                Column(
                    Modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = hPad)
                        .verticalScroll(contentScroll),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (step) {
                        AiStep.CAPTURE -> CaptureContent(
                            hasPhoto = state.hasPhoto,
                            photoPreview = state.photoPreview,
                            countdown = countdown,
                            flash = flash,
                            cameraReady = cameraReady,
                            cameraError = cameraError,
                            captureRequest = captureRequest,
                            gestureActive = gestureActive,
                            onCapture = { startCapture() },
                            onReady = { cameraReady = true; cameraError = null },
                            onCaptured = viewModel::onPhotoCaptured,
                            onError = { cameraError = it },
                            onPalm = { onInteraction(); startCapture() },
                            previewRotationDegrees = previewRotationDegrees,
                        )
                        AiStep.STYLE -> StyleContent(
                            styles = state.serverStyles.filterNot { it.id in state.disabledStyleIds },
                            modelReady = state.selectedModelId.isNotBlank(),
                            previews = stylePreviews,
                            selectedId = state.selectedStyleId,
                            locked = state.status == AiStatus.UPLOADING || state.status == AiStatus.GENERATING,
                            onSelect = { viewModel.selectStyle(it); onInteraction() },
                            onPreview = { title, preview ->
                                previewTitle = title
                                previewBitmap = preview
                                onInteraction()
                            },
                        )
                        AiStep.BACKGROUND -> BackgroundContent(
                            options = viewModel.backgrounds,
                            previews = bgPreviews,
                            selectedId = state.backgroundId,
                            onSelect = { viewModel.selectBackground(it); onInteraction() },
                            onPreview = { title, preview ->
                                previewTitle = title
                                previewBitmap = preview
                                onInteraction()
                            },
                        )
                        AiStep.ACCESSORIES -> AccessoriesContent(
                            selected = state.accessories,
                            styleId = state.selectedStyleId,
                            onToggle = { viewModel.selectAccessoryExclusive(it); onInteraction() },
                            onClearGroup = { viewModel.clearAccessoryGroup(it); onInteraction() },
                            onResetPersonalization = { viewModel.resetPersonalization(); onInteraction() },
                        )
                        AiStep.FRAMED -> FramedContent(
                            preview = state.resultPreview,
                            cost = state.cost,
                            status = state.status,
                            selected = state.frameStyle,
                            palette = state.customization.palette(),
                            onSelectFrame = { viewModel.selectFrame(it); onInteraction() },
                            profiles = state.eventProfiles,
                            selectedProfileId = state.selectedEventProfileId,
                            onSelectProfile = { viewModel.selectEventProfile(it); onInteraction() },
                            decoration = state.frameDecoration,
                            onSelectDecoration = { viewModel.selectDecoration(it); onInteraction() },
                        )
                        AiStep.SHARE -> ShareContent(
                            preview = state.resultPreview,
                            deliveryUrl = deliveryUrl,
                            qr = qrBitmap,
                            starting = shareStarting,
                            onCopyLink = {
                                deliveryUrl?.let {
                                    clipboard.setText(AnnotatedString(it))
                                    scope.launch { snackbar.showSnackbar(context.getString(R.string.link_copied)) }
                                }
                                onInteraction()
                            },
                            onRetryQr = { startShare() },
                            onQuickShare = {
                                scope.launch {
                                    val file = state.resultFile ?: return@launch
                                    val uris = withContext(Dispatchers.IO) {
                                        PhotoSaver.cacheFilesForShare(context, listOf(file))
                                    }
                                    if (uris.isNullOrEmpty()) {
                                        snackbar.showSnackbar(context.getString(R.string.share_error))
                                        return@launch
                                    }
                                    val ok = PhotoSaver.launchDownload(
                                        context,
                                        uris,
                                        context.getString(R.string.share_chooser),
                                    )
                                    if (!ok) snackbar.showSnackbar(context.getString(R.string.share_error))
                                    else onInteraction()
                                }
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Bottom bar
            var finishArmed by remember { mutableStateOf(false) }
            LaunchedEffect(step) {
                finishArmed = false
                if (step == AiStep.FRAMED || step == AiStep.SHARE) delay(FINISH_GUARD_MS)
                finishArmed = true
            }
            BottomBar(
                hPad = hPad,
                step = step,
                hasPhoto = state.hasPhoto,
                capturing = countdown > 0,
                canGenerate = state.canGenerate,
                generating = state.status == AiStatus.UPLOADING || state.status == AiStatus.GENERATING,
                retryGeneration = state.status == AiStatus.ERROR,
                finishArmed = finishArmed,
                // Once the photos are paid for there is nothing left to edit
                // upstream, so the flow never walks back into style or background.
                backEnabled = step != AiStep.FRAMED && step != AiStep.SHARE &&
                    state.status != AiStatus.UPLOADING && state.status != AiStatus.GENERATING,
                onBack = {
                    onInteraction()
                    if (step == AiStep.FRAMED || step == AiStep.SHARE) return@BottomBar
                    step = when (step) {
                        AiStep.CAPTURE -> return@BottomBar exit()
                        AiStep.STYLE -> AiStep.CAPTURE
                        AiStep.BACKGROUND -> AiStep.STYLE
                        AiStep.ACCESSORIES -> AiStep.BACKGROUND
                        AiStep.FRAMED, AiStep.SHARE -> return@BottomBar
                    }
                },
                onNext = {
                    onInteraction()
                    if (step == AiStep.CAPTURE && !state.hasPhoto) return@BottomBar startCapture()
                    step = when (step) {
                        AiStep.CAPTURE -> AiStep.STYLE
                        AiStep.STYLE -> AiStep.BACKGROUND
                        AiStep.BACKGROUND -> AiStep.ACCESSORIES
                        AiStep.ACCESSORIES -> {
                            viewModel.generate()
                            step
                        }
                        AiStep.FRAMED -> AiStep.SHARE
                        AiStep.SHARE -> return@BottomBar exit()
                    }
                },
            )
        }
        if (state.status == AiStatus.UPLOADING || state.status == AiStatus.GENERATING || revealing) {
            GenerationExperience(
                status = state.status,
                photoPreview = state.photoPreview,
                stylePreview = stylePreviews[state.selectedStyleId],
                styleName = state.serverStyles.firstOrNull { it.id == state.selectedStyleId }?.name,
                result = state.resultPreview.takeIf { revealing },
                onCancel = viewModel::cancel,
            )
        }
        previewTitle?.let { title ->
            PreviewDialog(
                title = title,
                preview = previewBitmap,
                onDismiss = {
                    previewTitle = null
                    previewBitmap = null
                },
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun CaptureContent(
    hasPhoto: Boolean,
    photoPreview: Bitmap?,
    countdown: Int,
    flash: Boolean,
    cameraReady: Boolean,
    cameraError: String?,
    captureRequest: Int,
    gestureActive: Boolean,
    onCapture: () -> Unit,
    onReady: () -> Unit,
    onCaptured: (Bitmap, Boolean) -> Unit,
    onError: (String) -> Unit,
    onPalm: () -> Unit,
    previewRotationDegrees: Int,
) {
    var showPreview by remember { mutableStateOf(false) }
    if (!hasPhoto) {
        Text(
            stringResource(if (gestureActive) R.string.ai_hint_palm else R.string.ai_hint_tap),
            color = Cream.copy(alpha = 0.8f),
            fontSize = 16.sp,
        )
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .heightIn(max = 560.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(InkSoft),
    ) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            captureRequest = captureRequest,
            handDetectionEnabled = gestureActive && cameraReady,
            previewRotationDegrees = previewRotationDegrees,
            onPalmDetected = onPalm,
            onReady = onReady,
            onCaptured = onCaptured,
            onError = onError,
        )
        if (countdown > 0) {
            Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                Text("$countdown", color = Cream, fontSize = 110.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        if (flash) Box(Modifier.fillMaxSize().background(Cream))
    }
    if (cameraError != null) Text(cameraError, color = Cream.copy(alpha = 0.8f), fontSize = 13.sp)
    // Taking the photo is the bottom bar's primary action; once a photo exists the
    // inline row only holds the secondary actions.
    if (hasPhoto) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BoothSecondaryButton(
                text = stringResource(R.string.ai_retake_photo),
                onClick = onCapture,
                enabled = countdown == 0,
                icon = Icons.Rounded.Refresh,
                modifier = Modifier.weight(1f),
            )
            if (photoPreview != null) {
                BoothOutlinedButton(
                    text = stringResource(R.string.ai_view_photo),
                    onClick = { showPreview = true },
                    icon = Icons.Rounded.Visibility,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // Full-size preview in a modal: the inline thumbnail was too small to judge the shot.
    if (showPreview && photoPreview != null) {
        Dialog(onDismissRequest = { showPreview = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(InkSoft)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.ai_your_photo), color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        photoPreview.asImageBitmap(),
                        contentDescription = stringResource(R.string.ai_your_photo),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showPreview = false },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SyscomBlue),
                ) {
                    Text(stringResource(R.string.close), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StyleContent(
    styles: List<BoothStyle>,
    modelReady: Boolean,
    previews: Map<String, Bitmap>,
    selectedId: String,
    locked: Boolean,
    onSelect: (String) -> Unit,
    onPreview: (String, Bitmap?) -> Unit,
) {
    BoothSectionTitle(stringResource(R.string.ai_section_style_title))
    if (!modelReady) {
        Text(
            stringResource(R.string.ai_model_admin_hint),
            color = Cream.copy(alpha = 0.7f),
            fontSize = 15.sp,
        )
    }
    if (styles.isEmpty()) {
        Text(
            stringResource(R.string.ai_register_hint),
            color = Cream.copy(alpha = 0.7f),
            fontSize = 15.sp,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        styles.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { style ->
                    StyleCard(
                        title = style.name,
                        preview = previews[style.id],
                        selected = style.id == selectedId,
                        onClick = { if (!locked) onSelect(style.id) },
                        onPreview = { onPreview(style.name, previews[style.id]) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StyleCard(
    title: String,
    preview: Bitmap?,
    selected: Boolean,
    onClick: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (selected) Modifier.border(2.dp, SyscomBlue, RoundedCornerShape(20.dp)) else Modifier),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) SyscomBlue.copy(alpha = 0.22f) else InkSoft,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (preview != null) {
                Box {
                    Image(
                        preview.asImageBitmap(),
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    if (selected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.ai_cd_selected),
                            tint = Cream,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(22.dp),
                        )
                    }
                    IconButton(
                        onClick = onPreview,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Visibility,
                            contentDescription = stringResource(R.string.ai_cd_view, title),
                            tint = Cream,
                        )
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SyscomBlue.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(title.take(1), color = Cream, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                color = Cream,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PreviewDialog(
    title: String,
    preview: Bitmap?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(InkSoft)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = Cream, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Ink),
                contentAlignment = Alignment.Center,
            ) {
                if (preview != null) {
                    Image(
                        preview.asImageBitmap(),
                        contentDescription = stringResource(R.string.ai_cd_preview, title),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        Icons.Rounded.ImageNotSupported,
                        contentDescription = null,
                        tint = Cream.copy(alpha = 0.75f),
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SyscomBlue),
            ) {
                Text(stringResource(R.string.close), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BackgroundContent(
    options: List<BackgroundOption>,
    previews: Map<String, Bitmap>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onPreview: (String, Bitmap?) -> Unit,
) {
    BoothSectionTitle(stringResource(R.string.ai_section_background_title))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BackgroundOptionGrid(
            options = listOf(
                BackgroundOption(
                    id = BackgroundRepository.NO_BACKGROUND_ID,
                    name = stringResource(R.string.ai_background_none),
                ),
            ),
            previews = previews,
            selectedId = selectedId,
            onSelect = onSelect,
            onPreview = onPreview,
        )
        // "Cámara real" (original) is hidden in IA mode: it has no prompt and
        // would fall back to a wrong invented backdrop. Custom = hybrid exact.
        val iaOptions = options.filter {
            it.id != BackgroundRepository.NO_BACKGROUND_ID &&
                it.id != BackgroundRepository.ORIGINAL_ID
        }
        val builtin = iaOptions.filter { it.filePath == null }
        val custom = iaOptions.filter { it.filePath != null }
        if (builtin.isNotEmpty()) {
            BackgroundOptionGrid(builtin, previews, selectedId, onSelect, onPreview)
        }
        if (custom.isNotEmpty()) {
            BackgroundOptionGrid(custom, previews, selectedId, onSelect, onPreview)
        }
    }
}

@Composable
private fun BackgroundOptionGrid(
    options: List<BackgroundOption>,
    previews: Map<String, Bitmap>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onPreview: (String, Bitmap?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { option ->
                    val label = backgroundLabel(option)
                    BackgroundCard(
                        option = option,
                        preview = previews[option.id],
                        selected = option.id == selectedId,
                        onClick = { onSelect(option.id) },
                        onPreview = { onPreview(label, previews[option.id]) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BackgroundCard(
    option: BackgroundOption,
    preview: Bitmap?,
    selected: Boolean,
    onClick: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = backgroundLabel(option)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (selected) Modifier.border(2.dp, SyscomBlue, RoundedCornerShape(20.dp)) else Modifier),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) SyscomBlue.copy(alpha = 0.22f) else InkSoft,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SyscomBlue.copy(alpha = if (option.id == BackgroundRepository.NO_BACKGROUND_ID) 0.2f else 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                if (preview != null) {
                    Image(
                        preview.asImageBitmap(),
                        contentDescription = label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Rounded.ImageNotSupported,
                        contentDescription = null,
                        tint = Cream.copy(alpha = 0.82f),
                        modifier = Modifier.size(42.dp),
                    )
                }
                if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.ai_cd_selected),
                        tint = Cream,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(22.dp),
                    )
                }
                IconButton(
                    onClick = onPreview,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                ) {
                    Icon(
                        Icons.Rounded.Visibility,
                        contentDescription = stringResource(R.string.ai_cd_view, label),
                        tint = Cream,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                color = Cream,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AccessoriesContent(
    selected: Set<String>,
    styleId: String?,
    onToggle: (String) -> Unit,
    onClearGroup: (String) -> Unit,
    onResetPersonalization: () -> Unit,
) {
    val blocked = AiAccessories.blockedIds(styleId, selected)
    BoothSectionTitle(stringResource(R.string.ai_section_accessories_title))
    AiAccessories.GROUPS.forEach { group ->
        val options = group.options.filterNot { it.id in blocked }
        if (options.isEmpty()) return@forEach
        val selectedOption = options.firstOrNull { it.id in selected }
        Text(
            stringResource(group.labelRes),
            color = Cream,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        val choices = listOf<AiAccessoryChoice?>(null) +
            options.map { AiAccessoryChoice(it.id, stringResource(it.labelRes)) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            choices.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { choice ->
                        val isNone = choice == null
                        AccessoryCard(
                            title = choice?.name ?: stringResource(R.string.ai_accessory_none),
                            iconRes = accessoryIcon(choice?.id),
                            tint = accessoryTint(choice?.id),
                            selected = if (isNone) selectedOption == null else choice.id in selected,
                            onClick = {
                                if (isNone) onClearGroup(group.id) else onToggle(choice.id)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    OutlinedButton(
        onClick = onResetPersonalization,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(stringResource(R.string.ai_reset), fontSize = 16.sp)
    }
}

private data class AiAccessoryChoice(
    val id: String,
    val name: String,
)

@Composable
private fun AccessoryCard(
    title: String,
    iconRes: Int?,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (selected) Modifier.border(2.dp, SyscomBlueLight, RoundedCornerShape(20.dp)) else Modifier),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) SyscomBlue.copy(alpha = 0.3f) else InkSoft,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .padding(10.dp),
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(42.dp),
                )
            } else {
                Icon(
                    Icons.Rounded.Block,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(42.dp),
                )
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.ai_cd_selected),
                    tint = Cream,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp),
                )
            }
            Text(
                title,
                color = Cream,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

/** Generated image + frame picker on a single screen. */
@Composable
private fun FramedContent(
    preview: Bitmap?,
    cost: Double?,
    status: AiStatus,
    selected: CollageFrameStyle,
    palette: FramePalette,
    onSelectFrame: (CollageFrameStyle) -> Unit,
    profiles: List<EventProfile>,
    selectedProfileId: String,
    onSelectProfile: (String) -> Unit,
    decoration: FrameDecoration,
    onSelectDecoration: (FrameDecoration) -> Unit,
) {
    BoothSectionTitle(stringResource(R.string.ai_your_photo))
    if (preview != null) {
        Image(
            preview.asImageBitmap(),
            contentDescription = stringResource(R.string.ai_cd_framed_photo),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .heightIn(max = 480.dp)
                .clip(RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(24.dp)).background(InkSoft),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = SyscomBlueLight)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.ai_generating_photo), color = Cream.copy(alpha = 0.8f), fontSize = 16.sp)
            }
        }
    }
    cost?.let { Text(stringResource(R.string.ai_generation_cost, it.toString()), color = Cream.copy(alpha = 0.7f), fontSize = 14.sp) }
    if (status == AiStatus.ERROR) {
        Text(stringResource(R.string.ai_generation_error), color = Cream, fontSize = 15.sp)
    }
    if (profiles.size > 1) {
        BoothSectionTitle(stringResource(R.string.ai_section_brand))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            profiles.forEach { profile ->
                val isSelected = profile.id == selectedProfileId
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(role = Role.Button) { onSelectProfile(profile.id) }
                        .then(
                            if (isSelected) {
                                Modifier.border(2.dp, SyscomBlue, RoundedCornerShape(18.dp))
                            } else {
                                Modifier
                            },
                        ),
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) SyscomBlue.copy(alpha = 0.22f) else InkSoft,
                ) {
                    Text(
                        profile.name,
                        color = Cream,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
    BoothSectionTitle(stringResource(R.string.ai_section_frame))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CollageFrameStyle.entries.forEach { frame ->
            val isSelected = frame == selected
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button) { onSelectFrame(frame) }
                    .then(if (isSelected) Modifier.border(2.dp, SyscomBlue, RoundedCornerShape(18.dp)) else Modifier),
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) SyscomBlue.copy(alpha = 0.22f) else InkSoft,
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(palette.background))
                            .border(2.dp, Color(palette.accent), CircleShape),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(frame.titleRes),
                        color = Cream,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) Icon(Icons.Rounded.Check, null, tint = SyscomBlueLight)
                }
            }
        }
    }
    BoothSectionTitle(stringResource(R.string.ai_section_decoration))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FrameDecoration.entries.forEach { option ->
            val isSelected = option == decoration
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button) { onSelectDecoration(option) }
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, SyscomBlue, RoundedCornerShape(18.dp))
                        } else {
                            Modifier
                        },
                    ),
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) SyscomBlue.copy(alpha = 0.22f) else InkSoft,
            ) {
                Text(
                    stringResource(option.titleRes),
                    color = Cream,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** Dedicated share screen: full QR for everyone + Quick Share for Android. */
@Composable
private fun ShareContent(
    preview: Bitmap?,
    deliveryUrl: String?,
    qr: Bitmap?,
    starting: Boolean,
    onCopyLink: () -> Unit,
    onRetryQr: () -> Unit,
    onQuickShare: () -> Unit,
) {
    BoothSectionTitle(stringResource(R.string.ai_section_share))
    var zoomed by remember { mutableStateOf(false) }
    preview?.let {
        // Show the whole strip instead of a center crop: guests need to see the real result.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(InkSoft),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                it.asImageBitmap(),
                contentDescription = stringResource(R.string.ai_cd_final_photo),
                modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                contentScale = ContentScale.Fit,
            )
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(InkSoft)
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (qr != null) {
                Image(
                    qr.asImageBitmap(),
                    contentDescription = stringResource(R.string.ai_cd_qr),
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { zoomed = true },
                    contentScale = ContentScale.Fit,
                )
                Text(
                    text = stringResource(R.string.qr_tap_zoom),
                    color = Cream.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                )
                if (zoomed) {
                    QrZoomDialog(bitmap = qr, onDismiss = { zoomed = false })
                }
            } else {
                Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = SyscomBlueLight)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(if (starting) R.string.ai_preparing_download else R.string.ai_retrying_qr),
                            color = Cream.copy(alpha = 0.8f),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (deliveryUrl != null) {
                Text(
                    deliveryUrl,
                    color = Cream.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.ai_same_wifi),
                    color = Cream.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (!starting) {
                OutlinedButton(
                    onClick = onRetryQr,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(stringResource(R.string.ai_retry_qr), fontSize = 16.sp)
                }
            }
        }
    }
    TextButton(
        onClick = onCopyLink,
        enabled = deliveryUrl != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.copy_link), fontSize = 15.sp)
    }
    Button(
        onClick = onQuickShare,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SyscomTeal, contentColor = Ink),
    ) {
        Icon(Icons.Rounded.Share, null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.quick_share_option), fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.QrCode2, null, tint = SyscomBlueLight, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.ai_qr_works_both),
            color = Cream.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun BottomBar(
    hPad: androidx.compose.ui.unit.Dp,
    step: AiStep,
    hasPhoto: Boolean,
    capturing: Boolean,
    canGenerate: Boolean,
    generating: Boolean,
    retryGeneration: Boolean,
    finishArmed: Boolean,
    backEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val nextLabel = when (step) {
        AiStep.CAPTURE -> stringResource(if (hasPhoto) R.string.wizard_next else R.string.ai_take_photo)
        AiStep.STYLE -> stringResource(R.string.wizard_next)
        AiStep.BACKGROUND -> stringResource(R.string.wizard_next)
        AiStep.ACCESSORIES -> if (retryGeneration) stringResource(R.string.ai_regenerate) else stringResource(R.string.ai_generate)
        AiStep.FRAMED, AiStep.SHARE -> stringResource(R.string.finish)
    }
    val nextEnabled = when (step) {
        AiStep.CAPTURE -> !capturing
        AiStep.ACCESSORIES -> canGenerate && !generating
        // Arm the closing buttons late so a guest cannot skip the screen by tapping twice.
        AiStep.FRAMED, AiStep.SHARE -> finishArmed
        else -> true
    }
    val showBack = step != AiStep.FRAMED && step != AiStep.SHARE
    BoothBottomBar(
        horizontalPadding = hPad,
        primaryLabel = nextLabel,
        primaryEnabled = nextEnabled,
        onPrimary = onNext,
        backLabel = if (showBack) {
            stringResource(if (step == AiStep.CAPTURE) R.string.ai_exit else R.string.wizard_back)
        } else {
            null
        },
        backEnabled = backEnabled,
        onBack = onBack,
    )
}
