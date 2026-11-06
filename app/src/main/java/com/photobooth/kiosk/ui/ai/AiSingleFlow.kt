package com.photobooth.kiosk.ui.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import com.photobooth.kiosk.data.CollageFrameStyle
import com.photobooth.kiosk.delivery.LocalDeliveryServer
import com.photobooth.kiosk.network.BoothStyle
import com.photobooth.kiosk.photo.PhotoSaver
import com.photobooth.kiosk.photo.QrCodeGenerator
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.InkSoft
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomBlueLight
import com.photobooth.kiosk.ui.QrZoomDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FINISH_GUARD_MS = 3_000L

private enum class AiStep(val index: Int, val title: String) {
    CAPTURE(0, "Foto"),
    STYLE(1, "Estilo"),
    BACKGROUND(2, "Fondo"),
    ACCESSORIES(3, "Accesorios"),
    FRAMED(4, "Marco"),
    SHARE(5, "Compartir"),
}

private const val TOTAL_STEPS = 6

/** Emoji icon per accessory: no new assets, always renders. */
private fun accessoryEmoji(id: String): String = when (id) {
    "cap" -> "🧢"
    "headband" -> "🎀"
    "crown" -> "👑"
    "party_hat" -> "🎉"
    "bunny_ears" -> "🐰"
    "sunglasses" -> "😎"
    "mask" -> "🎭"
    "flowers" -> "🌸"
    "scarf" -> "🧣"
    "lei" -> "🌺"
    else -> "✨"
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
    viewModel: AiSingleViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The admin may have switched styles on or off since the last guest run.
    LaunchedEffect(Unit) { viewModel.refreshStyleSettings() }
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
    var showGenerationReview by remember { mutableStateOf(false) }

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
                srv.start(listOf(file), "Cabina IA")
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
            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPad, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Foto IA", color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Paso ${step.index + 1} de $TOTAL_STEPS · ${step.title}",
                        color = Cream.copy(alpha = 0.65f),
                        fontSize = 14.sp,
                    )
                }
                IconButton(onClick = { exit() }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Salir", tint = Cream)
                }
            }
            LinearProgressIndicator(
                progress = { (step.index + 1) / TOTAL_STEPS.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPad)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = SyscomBlue,
                trackColor = InkSoft,
            )
            Spacer(Modifier.height(8.dp))

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
                            onCaptured = { bmp, rot, mirror -> viewModel.onPhotoCaptured(bmp, rot, mirror) },
                            onError = { cameraError = it },
                            onPalm = { onInteraction(); startCapture() },
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
                            onToggle = { viewModel.selectAccessoryExclusive(it); onInteraction() },
                            onClearGroup = { viewModel.clearAccessoryGroup(it); onInteraction() },
                            onResetPersonalization = { viewModel.resetPersonalization(); onInteraction() },
                        )
                        AiStep.FRAMED -> FramedContent(
                            preview = state.resultPreview,
                            cost = state.cost,
                            status = state.status,
                            selected = state.frameStyle,
                            onSelectFrame = { viewModel.selectFrame(it); onInteraction() },
                        )
                        AiStep.SHARE -> ShareContent(
                            preview = state.resultPreview,
                            deliveryUrl = deliveryUrl,
                            qr = qrBitmap,
                            starting = shareStarting,
                            onCopyLink = {
                                deliveryUrl?.let {
                                    clipboard.setText(AnnotatedString(it))
                                    scope.launch { snackbar.showSnackbar("Enlace copiado") }
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
                canContinueFromCapture = state.hasPhoto,
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
                    step = when (step) {
                        AiStep.CAPTURE -> AiStep.STYLE
                        AiStep.STYLE -> AiStep.BACKGROUND
                        AiStep.BACKGROUND -> AiStep.ACCESSORIES
                        AiStep.ACCESSORIES -> {
                            showGenerationReview = true
                            step
                        }
                        AiStep.FRAMED -> AiStep.SHARE
                        AiStep.SHARE -> return@BottomBar exit()
                    }
                },
            )
        }
        if (state.status == AiStatus.UPLOADING || state.status == AiStatus.GENERATING) {
            GenerationExperience(
                status = state.status,
                photoPreview = state.photoPreview,
                onCancel = viewModel::cancel,
            )
        }
        if (showGenerationReview) {
            val styleName = state.serverStyles.firstOrNull { it.id == state.selectedStyleId }?.name
                ?: AiStyles.ALL.firstOrNull { it.id == state.selectedStyleId }
                    ?.let { context.getString(it.labelRes) }
                ?: state.selectedStyleId
            val backgroundName = viewModel.backgrounds.firstOrNull { it.id == state.backgroundId }?.name
                ?: context.getString(R.string.ai_background_none)
            val accessoryNames = AiAccessories.ALL
                .filter { it.id in state.accessories }
                .map { context.getString(it.labelRes) }
            GenerationReviewDialog(
                styleName = styleName,
                backgroundName = backgroundName,
                accessoryNames = accessoryNames,
                onEdit = { showGenerationReview = false },
                onConfirm = {
                    showGenerationReview = false
                    viewModel.generate()
                    onInteraction()
                },
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
    onCaptured: (Bitmap, Int, Boolean) -> Unit,
    onError: (String) -> Unit,
    onPalm: () -> Unit,
) {
    var showPreview by remember { mutableStateOf(false) }
    Text(
        if (hasPhoto) "Foto lista. Revisa y continúa."
        else if (gestureActive) "Muestra la palma o toca el botón."
        else "Toca el botón para tomar tu foto.",
        color = Cream.copy(alpha = 0.8f),
        fontSize = 16.sp,
    )
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
    if (hasPhoto && photoPreview != null) {
        OutlinedButton(
            onClick = { showPreview = true },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Icon(Icons.Rounded.Visibility, contentDescription = null, tint = Cream)
            Spacer(Modifier.width(8.dp))
            Text("Ver mi foto", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Cream)
        }
    }
    Button(
        onClick = onCapture,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SyscomBlue),
    ) {
        Text(if (hasPhoto) "Repetir foto" else "Tomar foto", fontSize = 19.sp, fontWeight = FontWeight.Bold)
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
                Text("Tu foto", color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                        contentDescription = "Tu foto",
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
                    Text("Cerrar", fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
    SectionTitle("Elige el estilo", "Los estilos salen del servidor.")
    if (!modelReady) {
        Text(
            "El modelo se configura en el administrador.",
            color = Cream.copy(alpha = 0.7f),
            fontSize = 15.sp,
        )
    }
    if (styles.isEmpty()) {
        Text(
            "Registra el tótem en el administrador para cargar los estilos.",
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
                            contentDescription = "Seleccionado",
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
                            contentDescription = "Ver $title",
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
private fun GenerationReviewDialog(
    styleName: String,
    backgroundName: String,
    accessoryNames: List<String>,
    onEdit: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onEdit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(InkSoft)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Revisa tu foto", color = Cream, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Confirma tu selección antes de generar.",
                color = Cream.copy(alpha = 0.72f),
                fontSize = 15.sp,
            )
            ReviewSelectionRow("Estilo", styleName)
            ReviewSelectionRow("Fondo", backgroundName)
            ReviewSelectionRow(
                "Accesorios",
                accessoryNames.takeIf { it.isNotEmpty() }?.joinToString() ?: "Sin accesorios",
            )
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Volver a editar", fontSize = 16.sp)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SyscomBlue),
            ) {
                Text("Confirmar y generar", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReviewSelectionRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink)
            .padding(14.dp),
    ) {
        Text(label, color = Cream.copy(alpha = 0.62f), fontSize = 13.sp)
        Text(value, color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                        contentDescription = "Vista previa de $title",
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
                Text("Cerrar", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GenerationExperience(
    status: AiStatus,
    photoPreview: Bitmap?,
    onCancel: () -> Unit,
) {
    var progress by remember(status) {
        mutableIntStateOf(if (status == AiStatus.UPLOADING) 12 else 45)
    }
    LaunchedEffect(status) {
        while (progress < if (status == AiStatus.UPLOADING) 38 else 92) {
            delay(if (progress < 70) 420 else 850)
            progress += if (progress < 70) 2 else 1
        }
    }

    val generating = status == AiStatus.GENERATING
    val title = if (generating) "Tu foto está cobrando vida" else "Preparando tu foto"
    val detail = when {
        !generating -> "Subiendo una copia segura para transformarla."
        progress < 62 -> "La IA está aplicando el estilo que elegiste."
        progress < 78 -> "Afinando colores, rostro y detalles."
        else -> "Dando los últimos toques. Ya casi está lista."
    }
    val estimated = if (generating) "Esto suele tomar entre 15 y 20 segundos." else "Solo tomará un momento."
    val stages = listOf("Foto lista", "Estilo en proceso", "Detalles finales")
    val currentStage = when {
        !generating -> 0
        progress < 78 -> 1
        else -> 2
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.96f))
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(126.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxSize(),
                    color = SyscomBlueLight,
                    trackColor = InkSoft,
                    strokeWidth = 9.dp,
                )
                photoPreview?.let { preview ->
                    Image(
                        preview.asImageBitmap(),
                        contentDescription = "Foto en proceso",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(94.dp)
                            .clip(RoundedCornerShape(22.dp)),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                title,
                color = Cream,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                detail,
                color = Cream.copy(alpha = 0.8f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                estimated,
                color = Cream.copy(alpha = 0.58f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = SyscomBlueLight,
                trackColor = InkSoft,
            )
            Spacer(Modifier.height(10.dp))
            Text("$progress%", color = Cream, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(26.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stages.forEachIndexed { index, stage ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(
                                    if (index <= currentStage) SyscomBlue else InkSoft,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (index < currentStage) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Cream,
                                    modifier = Modifier.size(14.dp),
                                )
                            } else {
                                Text(
                                    "${index + 1}",
                                    color = Cream,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stage,
                            color = if (index <= currentStage) Cream else Cream.copy(alpha = 0.42f),
                            fontSize = 14.sp,
                            fontWeight = if (index == currentStage) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(stringResource(R.string.ai_cancel), fontSize = 16.sp)
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
    SectionTitle("Fondo (opcional)", "Sin fondo = la IA crea toda la escena.")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BackgroundOptionGrid(
            options = listOf(
                BackgroundOption(
                    id = BackgroundRepository.NO_BACKGROUND_ID,
                    name = "Sin fondo",
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
            Text("Integrados · la IA recrea la escena", color = Cream.copy(alpha = 0.7f), fontSize = 14.sp)
            BackgroundOptionGrid(builtin, previews, selectedId, onSelect, onPreview)
        }
        if (custom.isNotEmpty()) {
            Text("Fondos personalizados", color = Cream.copy(alpha = 0.7f), fontSize = 14.sp)
            BackgroundOptionGrid(custom, previews, selectedId, onSelect, onPreview)
        } else {
            Text(
                "Aún no hay. Impórtalos en Admin → Evento.",
                color = Cream.copy(alpha = 0.55f),
                fontSize = 14.sp,
            )
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
                    BackgroundCard(
                        option = option,
                        preview = previews[option.id],
                        selected = option.id == selectedId,
                        onClick = { onSelect(option.id) },
                        onPreview = { onPreview(option.name, previews[option.id]) },
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
                        contentDescription = option.name,
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
                        contentDescription = "Seleccionado",
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
                        contentDescription = "Ver ${option.name}",
                        tint = Cream,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                option.name,
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
    onToggle: (String) -> Unit,
    onClearGroup: (String) -> Unit,
    onResetPersonalization: () -> Unit,
) {
    SectionTitle("Accesorios", "Combina uno de cada categoría para hacer tu foto única.")
    AiAccessories.GROUPS.forEach { group ->
        val selectedOption = group.options.firstOrNull { it.id in selected }
        Text(
            stringResource(group.labelRes),
            color = Cream,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            selectedOption?.let { "Elegiste: ${stringResource(it.labelRes)}" }
                ?: "Sin accesorio seleccionado",
            color = Cream.copy(alpha = 0.62f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(2.dp))
        val choices = listOf<AiAccessoryChoice?>(null) +
            group.options.map { AiAccessoryChoice(it.id, stringResource(it.labelRes)) }
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
                            emoji = if (isNone) "✨" else accessoryEmoji(choice.id),
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
    Text(
        "Al continuar, la IA combinará tus accesorios con el estilo y el fondo elegidos.",
        color = Cream.copy(alpha = 0.62f),
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = onResetPersonalization,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text("Restablecer personalización", fontSize = 16.sp)
    }
}

private data class AiAccessoryChoice(
    val id: String,
    val name: String,
)

@Composable
private fun AccessoryCard(
    title: String,
    emoji: String,
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
            Text(
                emoji,
                fontSize = 42.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Seleccionado",
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
    onSelectFrame: (CollageFrameStyle) -> Unit,
) {
    SectionTitle("Elige tu marco", "La foto ya está lista.")
    if (preview != null) {
        Image(
            preview.asImageBitmap(),
            contentDescription = "Foto con marco",
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
                Text("Generando tu foto…", color = Cream.copy(alpha = 0.8f), fontSize = 16.sp)
            }
        }
    }
    cost?.let { Text("Costo de generación: $${it}", color = Cream.copy(alpha = 0.7f), fontSize = 14.sp) }
    if (status == AiStatus.ERROR) {
        Text("Hubo un error. Vuelve a accesorios e intenta de nuevo.", color = Cream, fontSize = 15.sp)
    }
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
    SectionTitle("Comparte tu foto", "Escanea el código con cualquier celular.")
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
                contentDescription = "Tu foto final",
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
                    contentDescription = "QR de descarga",
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
                            if (starting) "Preparando descarga…" else "Tocando reintentar…",
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
                    "Conéctate a la misma red WiFi del tótem y escanea.",
                    color = Cream.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (!starting) {
                OutlinedButton(onClick = onRetryQr, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Reintentar código QR", fontSize = 16.sp)
                }
            }
        }
    }
    OutlinedButton(
        onClick = onCopyLink,
        enabled = deliveryUrl != null,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text("Copiar enlace", fontSize = 17.sp)
    }
    Button(
        onClick = onQuickShare,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SyscomBlue),
    ) {
        Icon(Icons.Rounded.Share, null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text("Quick Share · Android", fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.QrCode2, null, tint = SyscomBlueLight, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "El QR funciona con iPhone y Android.",
            color = Cream.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Cream.copy(alpha = 0.7f), fontSize = 15.sp)
    }
}

@Composable
private fun BottomBar(
    hPad: androidx.compose.ui.unit.Dp,
    step: AiStep,
    canContinueFromCapture: Boolean,
    canGenerate: Boolean,
    generating: Boolean,
    retryGeneration: Boolean,
    finishArmed: Boolean,
    backEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val nextLabel = when (step) {
        AiStep.CAPTURE -> "Continuar"
        AiStep.STYLE -> "Continuar"
        AiStep.BACKGROUND -> "Continuar"
        AiStep.ACCESSORIES -> if (retryGeneration) "Reintentar con IA" else "Generar con IA"
        AiStep.FRAMED, AiStep.SHARE -> "Finalizar"
    }
    val nextEnabled = when (step) {
        AiStep.CAPTURE -> canContinueFromCapture
        AiStep.ACCESSORIES -> canGenerate && !generating
        // Arm the closing buttons late so a guest cannot skip the screen by tapping twice.
        AiStep.FRAMED, AiStep.SHARE -> finishArmed
        else -> true
    }
    val showBack = step != AiStep.FRAMED && step != AiStep.SHARE
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ink)
            .padding(horizontal = hPad, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showBack) {
            OutlinedButton(
                onClick = onBack,
                enabled = backEnabled,
                modifier = Modifier.weight(1f).height(68.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(if (step == AiStep.CAPTURE) "Salir" else "Atrás", fontSize = 18.sp)
            }
        }
        Button(
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier.weight(if (showBack) 1.4f else 1f).height(68.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SyscomBlue),
        ) {
            Text(nextLabel, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
