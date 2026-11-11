package com.photobooth.kiosk.ui.collage

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photobooth.kiosk.R
import com.photobooth.kiosk.camera.CameraPreview
import com.photobooth.kiosk.data.BackgroundOption
import com.photobooth.kiosk.data.CollageFrameStyle
import com.photobooth.kiosk.data.CollageLayout
import com.photobooth.kiosk.ui.backgroundLabel
import com.photobooth.kiosk.data.FrameDecoration
import com.photobooth.kiosk.data.PhotoBorderStyle
import com.photobooth.kiosk.data.palette
import com.photobooth.kiosk.photo.PhotoSaver
import com.photobooth.kiosk.photo.QrCodeGenerator
import com.photobooth.kiosk.ui.PhotoBoothUiState
import com.photobooth.kiosk.ui.PhotoBoothViewModel
import com.photobooth.kiosk.ui.QrZoomDialog
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class CollageStep(val index: Int, val titleRes: Int) {
    CAPTURE(0, R.string.collage_step_capture),
    BACKGROUND(1, R.string.collage_step_background),
    DESIGN(2, R.string.collage_step_design),
    SHARE(3, R.string.collage_step_share),
}

private const val TOTAL_STEPS = 4
private const val COUNTDOWN_SECONDS = 5
private const val COUNTDOWN_TICK_MS = 1_000L

/**
 * Three-photo collage flow. Uses the exact same shell, countdown and delivery
 * screen as the AI flow (`AiSingleFlow`): top bar with step counter, progress
 * bar, fixed bottom bar and an inline QR on the last step. The only additions
 * are the three shots and the swipeable result pager.
 */
@Composable
fun CollageFlow(
    viewModel: PhotoBoothViewModel,
    onHome: () -> Unit,
    onInteraction: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val compact = LocalConfiguration.current.screenWidthDp < 600

    var step by remember { mutableStateOf(CollageStep.CAPTURE) }
    var captureRequest by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var cameraReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var flash by remember { mutableStateOf(false) }
    var sequenceActive by remember { mutableStateOf(false) }
    var poseBanner by remember { mutableStateOf<String?>(null) }
    var viewPhotoIndex by remember { mutableStateOf<Int?>(null) }
    var deliveryUrl by remember { mutableStateOf<String?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var shareStarting by remember { mutableStateOf(false) }
    var finishArmed by remember { mutableStateOf(false) }
    var backgroundPreviews by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }

    val hPad = if (compact) 18.dp else 32.dp
    val maxWidth = if (compact) 560.dp else 680.dp

    fun closeDelivery() {
        deliveryUrl = null
        qrBitmap?.recycle()
        qrBitmap = null
        shareStarting = false
        viewModel.stopDelivery()
    }

    fun exit() {
        closeDelivery()
        viewModel.clearResult()
        viewModel.endSession()
        onHome()
    }

    // Background thumbnails for the picker step.
    LaunchedEffect(state.backgroundRevision, state.backgrounds.size, state.capturedCount) {
        val options = state.backgrounds
        val loaded = withContext(Dispatchers.IO) {
            options.mapNotNull { option ->
                runCatching { viewModel.loadThumbnail(option.id)?.let { option.id to it } }.getOrNull()
            }.toMap()
        }
        // Thumbnails are owned by BackgroundRepository's cache: never recycle them
        // here or the AI flow loses its previews too.
        backgroundPreviews = loaded
    }

    LaunchedEffect(Unit) { viewModel.refreshBackgrounds() }

    LaunchedEffect(state.capturedCount) {
        if (state.capturedCount == 0) {
            sequenceActive = false
            poseBanner = null
            countdown = 0
        } else if (state.capturedCount >= PhotoBoothUiState.PHOTO_COUNT) {
            sequenceActive = false
            poseBanner = null
            countdown = 0
        }
    }

    // Same 3-2-1 as the AI flow, repeated once per shot. When a single slot is
    // being retaken the countdown still runs but only that photo is replaced.
    LaunchedEffect(sequenceActive, state.capturedCount, state.retakeIndex, cameraReady) {
        if (!sequenceActive || !cameraReady) return@LaunchedEffect
        val retake = state.retakeIndex
        val shotIndex = retake ?: state.capturedCount
        if (retake == null && shotIndex >= PhotoBoothUiState.PHOTO_COUNT) {
            sequenceActive = false
            return@LaunchedEffect
        }
        onInteraction()
        if (retake == null && shotIndex > 0) {
            poseBanner = context.getString(
                R.string.collage_pose_banner,
                shotIndex + 1,
                PhotoBoothUiState.PHOTO_COUNT,
            )
            delay(1_250L)
            poseBanner = null
        }
        viewModel.beginCapture()
        for (sec in COUNTDOWN_SECONDS downTo 1) {
            countdown = sec
            delay(COUNTDOWN_TICK_MS)
        }
        countdown = 0
        flash = true
        delay(110L)
        flash = false
        onInteraction()
        captureRequest += 1
    }

    fun startSequence() {
        if (!cameraReady || state.isProcessing || state.isCapturing || sequenceActive || countdown > 0) return
        if (state.capturedCount >= PhotoBoothUiState.PHOTO_COUNT && state.retakeIndex == null) return
        sequenceActive = true
    }

    /** Throws away the three photos and starts the sequence from scratch. */
    fun retakeAll() {
        if (state.isProcessing || state.isCapturing || sequenceActive || countdown > 0) return
        viewPhotoIndex = null
        viewModel.clearResult()
        sequenceActive = true
        onInteraction()
    }

    /** Replaces only the visible photo, keeping the other two. */
    fun retakeOne(index: Int) {
        if (index !in 0 until state.capturedCount) return
        if (state.isProcessing || state.isCapturing || sequenceActive || countdown > 0) return
        viewPhotoIndex = null
        viewModel.beginRetake(index)
        sequenceActive = true
        onInteraction()
    }

    fun startShare() {
        if (shareStarting || deliveryUrl != null) return
        scope.launch {
            shareStarting = true
            deliveryUrl = null
            qrBitmap?.recycle()
            qrBitmap = null
            val session = viewModel.startDelivery()
            if (session == null) {
                shareStarting = false
                snackbar.showSnackbar(context.getString(R.string.qr_error))
                return@launch
            }
            deliveryUrl = session.pageUrl
            qrBitmap = withContext(Dispatchers.IO) {
                QrCodeGenerator.generate(session.pageUrl, 880)
            }
            shareStarting = false
            onInteraction()
        }
    }

    // Same behaviour as the AI flow: the QR starts as soon as the step opens.
    LaunchedEffect(step) {
        if (step == CollageStep.SHARE) {
            startShare()
        }
        finishArmed = false
        if (step == CollageStep.SHARE) {
            // Arm the closing buttons late so a guest cannot skip the QR by tapping twice.
            delay(1_500L)
            finishArmed = true
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbar.showSnackbar(message)
            viewModel.clearError()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopDelivery()
            qrBitmap?.recycle()
            qrBitmap = null
        }
    }

    val gestureActive = state.palmCaptureEnabled &&
        step == CollageStep.CAPTURE &&
        cameraReady &&
        state.capturedCount < PhotoBoothUiState.PHOTO_COUNT &&
        countdown == 0 &&
        !sequenceActive

    val canContinueFromCapture = state.capturedCount >= PhotoBoothUiState.PHOTO_COUNT &&
        !state.isProcessing && !sequenceActive && state.retakeIndex == null

    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(Modifier.fillMaxSize()) {
            BoothFlowHeader(
                title = stringResource(R.string.result_title),
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
                LaunchedEffect(step) { contentScroll.scrollTo(0) }
                Column(
                    Modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .then(if (step == CollageStep.CAPTURE) Modifier.fillMaxHeight() else Modifier)
                        .padding(horizontal = hPad)
                        .then(
                            // The capture step owns the whole height: the camera
                            // grows with the screen instead of scrolling.
                            if (step == CollageStep.CAPTURE) Modifier
                            else Modifier.verticalScroll(contentScroll),
                        ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (step) {
                        CollageStep.CAPTURE -> CollageCaptureContent(
                            state = state,
                            countdown = countdown,
                            flash = flash,
                            cameraReady = cameraReady,
                            cameraError = cameraError,
                            captureRequest = captureRequest,
                            sequenceActive = sequenceActive,
                            poseBanner = poseBanner,
                            gestureActive = gestureActive,
                            viewIndex = viewPhotoIndex,
                            onViewPhoto = { index -> viewPhotoIndex = if (viewPhotoIndex == index) null else index },
                            onRetakeOne = { index -> retakeOne(index) },
                            onRetakeAll = { retakeAll() },
                            onReady = {
                                cameraReady = true
                                cameraError = null
                            },
                            onCaptured = { bitmap, mirror ->
                                cameraReady = true
                                cameraError = null
                                viewModel.processPhoto(bitmap, mirror)
                            },
                            onError = { message ->
                                sequenceActive = false
                                poseBanner = null
                                countdown = 0
                                viewModel.captureFailed()
                                cameraReady = false
                                cameraError = message
                            },
                            onPalm = {
                                onInteraction()
                                startSequence()
                            },
                            onShutter = {
                                onInteraction()
                                startSequence()
                            },
                        )

                        CollageStep.BACKGROUND -> CollageBackgroundContent(
                            options = state.backgrounds,
                            previews = backgroundPreviews,
                            state = state,
                            onSelectAll = {
                                viewModel.setApplyBackgroundToAll(true)
                                viewModel.selectResult(null)
                                onInteraction()
                            },
                            onSelectPhoto = { index ->
                                viewModel.setApplyBackgroundToAll(false)
                                viewModel.selectResult(index)
                                viewModel.setApplyBackgroundToAll(false)
                                onInteraction()
                            },
                            onSelectBackground = { id ->
                                viewModel.changeBackground(id)
                                onInteraction()
                            },
                        )

                        CollageStep.DESIGN -> CollageDesignContent(
                            state = state,
                            onSelectFrame = {
                                viewModel.selectFrameStyle(it)
                                onInteraction()
                            },
                            onSelectDecoration = {
                                viewModel.selectDecoration(it)
                                onInteraction()
                            },
                            onSelectLayout = {
                                viewModel.selectCollageLayout(it)
                                onInteraction()
                            },
                            onSelectBorder = {
                                viewModel.selectPhotoBorderStyle(it)
                                onInteraction()
                            },
                        )

                        CollageStep.SHARE -> CollageShareContent(
                            state = state,
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
                                    val files = viewModel.ensureExportedFiles()
                                    if (files.isNullOrEmpty()) {
                                        snackbar.showSnackbar(context.getString(R.string.share_error))
                                        return@launch
                                    }
                                    val uris = withContext(Dispatchers.IO) {
                                        PhotoSaver.cacheFilesForShare(context, files)
                                    }
                                    val launched = if (uris.isNullOrEmpty()) {
                                        false
                                    } else {
                                        PhotoSaver.launchDownload(
                                            context = context,
                                            uris = uris,
                                            chooserTitle = context.getString(R.string.share_chooser),
                                        )
                                    }
                                    if (launched) {
                                        exit()
                                    } else {
                                        snackbar.showSnackbar(context.getString(R.string.share_error))
                                    }
                                }
                                onInteraction()
                            },
                        )
                    }
                }
            }

            val shotsDone = state.capturedCount >= PhotoBoothUiState.PHOTO_COUNT
            val captureBusy = sequenceActive || state.isProcessing || state.isCapturing || countdown > 0
            CollageBottomBar(
                hPad = hPad,
                step = step,
                shotsDone = shotsDone,
                nextEnabled = when (step) {
                    CollageStep.CAPTURE -> shotsDone && canContinueFromCapture
                    CollageStep.SHARE -> finishArmed
                    else -> true
                },
                onBack = {
                    step = when (step) {
                        CollageStep.CAPTURE -> {
                            exit()
                            CollageStep.CAPTURE
                        }
                        CollageStep.BACKGROUND -> CollageStep.CAPTURE
                        CollageStep.DESIGN -> CollageStep.BACKGROUND
                        CollageStep.SHARE -> CollageStep.DESIGN
                    }
                    onInteraction()
                },
                onNext = {
                    when (step) {
                        CollageStep.CAPTURE -> if (shotsDone) step = CollageStep.BACKGROUND
                        CollageStep.BACKGROUND -> step = CollageStep.DESIGN
                        CollageStep.DESIGN -> step = CollageStep.SHARE
                        CollageStep.SHARE -> exit()
                    }
                    onInteraction()
                },
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ColumnScope.CollageCaptureContent(
    state: PhotoBoothUiState,
    countdown: Int,
    flash: Boolean,
    cameraReady: Boolean,
    cameraError: String?,
    captureRequest: Int,
    sequenceActive: Boolean,
    poseBanner: String?,
    gestureActive: Boolean,
    viewIndex: Int?,
    onViewPhoto: (Int) -> Unit,
    onRetakeOne: (Int) -> Unit,
    onRetakeAll: () -> Unit,
    onReady: () -> Unit,
    onCaptured: (Bitmap, Boolean) -> Unit,
    onError: (String) -> Unit,
    onPalm: () -> Unit,
    onShutter: () -> Unit,
) {
    val done = state.capturedCount >= PhotoBoothUiState.PHOTO_COUNT
    val viewing = viewIndex?.takeIf { it < state.capturedCount }
    val hint = when {
        viewing != null -> stringResource(R.string.collage_photo_label, viewing + 1)
        done -> stringResource(R.string.collage_captured_all)
        gestureActive -> stringResource(R.string.ai_hint_palm)
        else -> stringResource(R.string.ai_hint_tap)
    }
    Text(hint, color = Cream.copy(alpha = 0.8f), fontSize = 16.sp)

    Box(Modifier.fillMaxWidth().weight(1f)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(InkSoft),
        ) {
            when {
                viewing != null -> {
                    val visible = state.rawPhotoPreviews.getOrNull(viewing)
                        ?: state.captureThumbnails.getOrNull(viewing)
                    if (visible != null && !visible.isRecycled) {
                        Image(
                            bitmap = visible.asImageBitmap(),
                            contentDescription = stringResource(R.string.collage_photo_label, viewing + 1),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }

                done -> {
                    // Only the raw shots belong in the capture step: the strip
                    // with frames is built later, in the design step.
                    val donePager = rememberPagerState(pageCount = { PhotoBoothUiState.PHOTO_COUNT })
                    HorizontalPager(
                        state = donePager,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val shot = state.rawPhotoPreviews.getOrNull(page)
                            ?: state.captureThumbnails.getOrNull(page)
                        if (shot != null && !shot.isRecycled) {
                            Image(
                                bitmap = shot.asImageBitmap(),
                                contentDescription = stringResource(R.string.collage_photo_label, page + 1),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.collage_photo_label, donePager.currentPage + 1),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Ink.copy(alpha = 0.72f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Cream,
                        fontSize = 13.sp,
                    )
                }

                else -> CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    captureRequest = captureRequest,
                    handDetectionEnabled = gestureActive,
                    onPalmDetected = onPalm,
                    adjustments = state.cameraAdjustments,
                    onReady = onReady,
                    onCaptured = onCaptured,
                    onError = onError,
                )
            }
            val showShutter = viewing == null &&
                !done &&
                !sequenceActive &&
                countdown == 0 &&
                cameraReady &&
                cameraError == null &&
                !state.isCapturing &&
                !state.isProcessing
            if (showShutter) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(Cream.copy(alpha = 0.28f))
                        .border(2.dp, Cream.copy(alpha = 0.7f), CircleShape)
                        .clickable(role = Role.Button, onClick = onShutter),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.CameraAlt,
                        contentDescription = stringResource(R.string.capture),
                        tint = Cream,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            if (viewing != null) {
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Ink.copy(alpha = 0.72f))
                        .clickable { onViewPhoto(viewing) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Close, null, tint = Cream, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.collage_back_to_camera), color = Cream, fontSize = 13.sp)
                }
            }
            if (countdown > 0) {
                Box(
                    Modifier.fillMaxSize().background(Ink.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$countdown", color = Cream, fontSize = 110.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            poseBanner?.let { banner ->
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(18.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Ink.copy(alpha = 0.72f))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text(banner, color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (flash) Box(Modifier.fillMaxSize().background(Cream))
        }

        // Slots float over the camera so the preview keeps the full width.
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .width(64.dp)
                .alpha(0.72f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(PhotoBoothUiState.PHOTO_COUNT) { index ->
                val thumb = state.captureThumbnails.getOrNull(index)
                val isViewing = viewing == index
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (index == state.capturedCount && !done) SyscomBlue.copy(alpha = 0.35f) else InkSoft,
                        )
                        .border(
                            width = if (isViewing) 2.dp else 1.dp,
                            color = when {
                                isViewing -> SyscomBlue
                                thumb != null -> SyscomBlueLight
                                index == state.capturedCount && !done -> Cream
                                else -> Cream.copy(alpha = 0.25f)
                            },
                            shape = RoundedCornerShape(14.dp),
                        )
                        .clickable(enabled = thumb != null) { onViewPhoto(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumb != null && !thumb.isRecycled) {
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = stringResource(R.string.collage_photo_label, index + 1),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            "${index + 1}",
                            color = Cream.copy(alpha = 0.45f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
    if (cameraError != null) {
        Text(cameraError, color = Cream.copy(alpha = 0.8f), fontSize = 13.sp)
    }

    // Taking the photos is the bottom bar's primary action; the inline row only
    // holds the secondary actions, exactly like the AI capture step.
    val busy = sequenceActive || state.isProcessing || state.isCapturing || countdown > 0
    if (viewing != null || done) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (viewing != null) {
                BoothSecondaryButton(
                    text = stringResource(R.string.collage_retake_one),
                    onClick = { onRetakeOne(viewing) },
                    enabled = !busy,
                    icon = Icons.Rounded.Refresh,
                    modifier = Modifier.weight(1f),
                )
            }
            BoothOutlinedButton(
                text = stringResource(R.string.collage_retake_three),
                onClick = onRetakeAll,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CollageBackgroundContent(
    options: List<BackgroundOption>,
    previews: Map<String, Bitmap>,
    state: PhotoBoothUiState,
    onSelectAll: () -> Unit,
    onSelectPhoto: (Int) -> Unit,
    onSelectBackground: (String) -> Unit,
) {
    val targetIndex = if (state.applyBackgroundToAll) null else state.selectedPhotoIndex
    BoothSectionTitle(stringResource(R.string.collage_step_background))
    Text(
        stringResource(R.string.collage_background_hint),
        color = Cream.copy(alpha = 0.7f),
        fontSize = 15.sp,
    )
    // One big shot at a time: swipe to review the three photos and tap the one
    // that should take the next background. No frames are drawn here on purpose.
    val pager = rememberPagerState(pageCount = { PhotoBoothUiState.PHOTO_COUNT })
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(0.56f)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(20.dp))
                .background(InkSoft),
        ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val preview = state.rawPhotoPreviews.getOrNull(page)
                ?: state.captureThumbnails.getOrNull(page)
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { onSelectPhoto(page) },
                contentAlignment = Alignment.Center,
            ) {
                if (preview != null && !preview.isRecycled) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = stringResource(R.string.collage_photo_label, page + 1),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.collage_photo_label, pager.currentPage + 1),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Ink.copy(alpha = 0.72f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            color = Cream,
            fontSize = 13.sp,
        )
        if (targetIndex == pager.currentPage) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .size(26.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(SyscomBlue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, null, tint = Cream, modifier = Modifier.size(16.dp))
            }
        }
    }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TargetChip(
            label = stringResource(R.string.collage_all_photos),
            selected = targetIndex == null,
            onClick = onSelectAll,
            modifier = Modifier.weight(1f),
        )
        repeat(PhotoBoothUiState.PHOTO_COUNT) { index ->
            TargetChip(
                label = stringResource(R.string.collage_photo_label, index + 1),
                selected = targetIndex == index,
                onClick = { onSelectPhoto(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    // Backgrounds stay small squares: the big canvas above belongs to the photos.
    options.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            row.forEach { option ->
                val selected = if (targetIndex == null) {
                    state.photoBackgroundIds.all { it == option.id }
                } else {
                    state.photoBackgroundIds.getOrNull(targetIndex) == option.id
                }
                BackgroundTile(
                    title = backgroundLabel(option),
                    preview = previews[option.id],
                    selected = selected,
                    onClick = { onSelectBackground(option.id) },
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun CollageDesignContent(
    state: PhotoBoothUiState,
    onSelectFrame: (CollageFrameStyle) -> Unit,
    onSelectDecoration: (FrameDecoration) -> Unit,
    onSelectLayout: (CollageLayout) -> Unit,
    onSelectBorder: (PhotoBorderStyle) -> Unit,
) {
    val preview = state.resultBitmap ?: state.stripPreview
    BoothSectionTitle(stringResource(R.string.ai_section_frame))
    if (preview != null && !preview.isRecycled) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = stringResource(R.string.result_title),
                modifier = Modifier
                    .height(360.dp)
                    .aspectRatio(preview.width.toFloat() / preview.height.toFloat())
                    .clip(RoundedCornerShape(20.dp))
                    .background(InkSoft),
                contentScale = ContentScale.Fit,
            )
        }
    }
    Text(
        stringResource(R.string.collage_design_hint),
        color = Cream.copy(alpha = 0.7f),
        fontSize = 15.sp,
    )
    val palette = state.customization.palette()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CollageFrameStyle.entries.forEach { style ->
            val selected = style == state.customization.frameStyle
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSelectFrame(style) }
                    .then(
                        if (selected) Modifier.border(2.dp, SyscomBlue, RoundedCornerShape(18.dp))
                        else Modifier,
                    ),
                color = InkSoft,
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(palette.accent)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(style.titleRes),
                        color = Cream,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) Icon(Icons.Rounded.Check, null, tint = SyscomBlueLight)
                }
            }
        }
    }

    BoothSectionTitle(stringResource(R.string.ai_section_decoration))
    ChipRow(
        options = FrameDecoration.entries.map { stringResource(it.titleRes) to (it == state.customization.decoration) },
        onClick = { index -> onSelectDecoration(FrameDecoration.entries[index]) },
    )

    BoothSectionTitle(stringResource(R.string.collage_section_layout))
    ChipRow(
        options = CollageLayout.entries.map { stringResource(it.titleRes) to (it == state.customization.layout) },
        onClick = { index -> onSelectLayout(CollageLayout.entries[index]) },
    )

    BoothSectionTitle(stringResource(R.string.collage_section_border))
    ChipRow(
        options = PhotoBorderStyle.entries.map { stringResource(it.titleRes) to (it == state.customization.photoBorderStyle) },
        onClick = { index -> onSelectBorder(PhotoBorderStyle.entries[index]) },
    )
}

@Composable
private fun CollageShareContent(
    state: PhotoBoothUiState,
    deliveryUrl: String?,
    qr: Bitmap?,
    starting: Boolean,
    onCopyLink: () -> Unit,
    onRetryQr: () -> Unit,
    onQuickShare: () -> Unit,
) {
    BoothSectionTitle(stringResource(R.string.ai_section_share))
    val photoLabels = List(state.photoPreviews.size) { index ->
        stringResource(R.string.collage_photo_label, index + 1)
    }
    val stripLabel = stringResource(R.string.collage_strip_label)
    val pages: List<Pair<String, Bitmap>> = buildList {
        state.photoPreviews.forEachIndexed { index, bitmap ->
            add(photoLabels[index] to bitmap)
        }
        state.stripPreview?.let { add(stripLabel to it) }
    }
    if (pages.isNotEmpty()) {
        val pagerState = rememberPagerState(pageCount = { pages.size })
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val (label, bitmap) = pages[page]
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier
                        .height(if (page == pages.lastIndex) 380.dp else 320.dp)
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                        .clip(RoundedCornerShape(20.dp))
                        .background(InkSoft),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(8.dp))
                Text(label, color = Cream.copy(alpha = 0.75f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pages.size) { index ->
                val active = index == pagerState.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 9.dp else 7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) SyscomBlueLight else Cream.copy(alpha = 0.3f)),
                )
            }
        }
        Text(
            stringResource(R.string.collage_swipe_hint),
            color = Cream.copy(alpha = 0.55f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    var zoomed by remember { mutableStateOf(false) }
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
                            text = stringResource(
                                if (starting) R.string.ai_preparing_download else R.string.ai_retrying_qr,
                            ),
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
private fun CollageBottomBar(
    hPad: androidx.compose.ui.unit.Dp,
    step: CollageStep,
    shotsDone: Boolean,
    nextEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val nextLabel = when (step) {
        // Shutter sits on the preview; the bar only advances after all three shots.
        CollageStep.CAPTURE -> if (shotsDone) stringResource(R.string.wizard_next) else null
        CollageStep.SHARE -> stringResource(R.string.finish)
        else -> stringResource(R.string.wizard_next)
    }
    BoothBottomBar(
        horizontalPadding = hPad,
        primaryLabel = nextLabel,
        primaryEnabled = nextEnabled,
        onPrimary = onNext,
        backLabel = stringResource(if (step == CollageStep.CAPTURE) R.string.ai_exit else R.string.wizard_back),
        onBack = onBack,
    )
}

@Composable
private fun TargetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .then(
                if (selected) Modifier.border(2.dp, SyscomBlue, RoundedCornerShape(14.dp))
                else Modifier.border(1.dp, Cream.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
            ),
        color = if (selected) SyscomBlue.copy(alpha = 0.22f) else InkSoft,
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            label,
            color = Cream,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        )
    }
}

@Composable
private fun BackgroundCard(
    title: String,
    preview: Bitmap?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .then(if (selected) Modifier.border(2.dp, SyscomBlue, RoundedCornerShape(20.dp)) else Modifier),
        color = InkSoft,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .background(Ink),
                contentAlignment = Alignment.Center,
            ) {
                if (preview != null && !preview.isRecycled) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                if (selected) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(26.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(SyscomBlue),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = Cream, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Text(
                title,
                color = Cream,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun BackgroundTile(
    title: String,
    preview: Bitmap?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .then(if (selected) Modifier.border(2.dp, SyscomBlue, RoundedCornerShape(14.dp)) else Modifier),
        color = InkSoft,
        shape = RoundedCornerShape(14.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .background(Ink),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null && !preview.isRecycled) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // "Sin fondo" has no artwork: show a camera glyph instead of an
                // empty box with a title floating in it.
                Icon(
                    Icons.Rounded.PhotoCamera,
                    contentDescription = title,
                    tint = Cream.copy(alpha = 0.55f),
                    modifier = Modifier.size(38.dp),
                )
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(20.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(SyscomBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, null, tint = Cream, modifier = Modifier.size(13.dp))
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Ink.copy(alpha = 0.62f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text(
                    title,
                    color = Cream,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ChipRow(
    options: List<Pair<String, Boolean>>,
    onClick: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEachIndexed { index, (label, selected) ->
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { onClick(index) }
                    .then(
                        if (selected) Modifier.border(2.dp, SyscomBlue, RoundedCornerShape(999.dp))
                        else Modifier.border(1.dp, Cream.copy(alpha = 0.18f), RoundedCornerShape(999.dp)),
                    ),
                color = if (selected) SyscomBlue.copy(alpha = 0.22f) else InkSoft,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    label,
                    color = Cream,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
    }
}
