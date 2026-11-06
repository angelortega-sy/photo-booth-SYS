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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSingleFlow(
    onHome: () -> Unit,
    onInteraction: () -> Unit,
    viewModel: AiSingleViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
    LaunchedEffect(state.error) {
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
                Column(
                    Modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = hPad)
                        .verticalScroll(rememberScrollState()),
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
                            styles = state.serverStyles,
                            modelReady = state.selectedModelId.isNotBlank(),
                            previews = stylePreviews,
                            selectedId = state.selectedStyleId,
                            locked = state.status == AiStatus.UPLOADING || state.status == AiStatus.GENERATING,
                            onSelect = { viewModel.selectStyle(it); onInteraction() },
                        )
                        AiStep.BACKGROUND -> BackgroundContent(
                            options = viewModel.backgrounds,
                            previews = bgPreviews,
                            selectedId = state.backgroundId,
                            onSelect = { viewModel.selectBackground(it); onInteraction() },
                        )
                        AiStep.ACCESSORIES -> AccessoriesContent(
                            selected = state.accessories,
                            status = state.status,
                            progress = state.progress,
                            onToggle = { viewModel.selectAccessoryExclusive(it); onInteraction() },
                            onClearGroup = { viewModel.clearAccessoryGroup(it); onInteraction() },
                            onCancel = { viewModel.cancel() },
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
            BottomBar(
                hPad = hPad,
                step = step,
                canContinueFromCapture = state.hasPhoto,
                canGenerate = state.canGenerate,
                generating = state.status == AiStatus.UPLOADING || state.status == AiStatus.GENERATING,
                onBack = {
                    onInteraction()
                    step = when (step) {
                        AiStep.CAPTURE -> return@BottomBar exit()
                        AiStep.STYLE -> AiStep.CAPTURE
                        AiStep.BACKGROUND -> AiStep.STYLE
                        AiStep.ACCESSORIES -> AiStep.BACKGROUND
                        AiStep.FRAMED -> AiStep.ACCESSORIES
                        AiStep.SHARE -> AiStep.FRAMED
                    }
                },
                onNext = {
                    onInteraction()
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
    photoPreview?.let { preview ->
        Image(
            preview.asImageBitmap(),
            contentDescription = "Vista previa",
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(18.dp)),
            contentScale = ContentScale.Crop,
        )
    }
    Button(
        onClick = onCapture,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SyscomBlue),
    ) {
        Text(if (hasPhoto) "Repetir foto" else "Tomar foto", fontSize = 19.sp, fontWeight = FontWeight.Bold)
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
        styles.forEach { style ->
            StyleCard(
                title = style.name,
                subtitle = style.id,
                preview = previews[style.id],
                selected = style.id == selectedId,
                onClick = { if (!locked) onSelect(style.id) },
            )
        }
    }
}

@Composable
private fun StyleCard(
    title: String,
    subtitle: String,
    preview: Bitmap?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (selected) Modifier.border(2.dp, SyscomBlue, RoundedCornerShape(20.dp)) else Modifier),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) SyscomBlue.copy(alpha = 0.22f) else InkSoft,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (preview != null) {
                Image(
                    preview.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SyscomBlue.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(title.take(1), color = Cream, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Cream.copy(alpha = 0.7f), fontSize = 14.sp)
            }
            if (selected) Icon(Icons.Rounded.Check, null, tint = SyscomBlueLight, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun BackgroundContent(
    options: List<BackgroundOption>,
    previews: Map<String, Bitmap>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    SectionTitle("Fondo (opcional)", "Sin fondo = la IA crea toda la escena.")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val noneSelected = selectedId == BackgroundRepository.NO_BACKGROUND_ID
        BigOptionRow(
            title = "Sin fondo",
            subtitle = "La IA genera toda la escena.",
            preview = null,
            icon = {
                Icon(
                    Icons.Rounded.ImageNotSupported, null,
                    tint = Cream.copy(alpha = 0.85f), modifier = Modifier.size(30.dp),
                )
            },
            selected = noneSelected,
            onClick = { onSelect(BackgroundRepository.NO_BACKGROUND_ID) },
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
            builtin.forEach { option ->
                BigOptionRow(
                    title = option.name,
                    subtitle = "Integrado · la IA lo recrea",
                    preview = previews[option.id],
                    icon = null,
                    selected = option.id == selectedId,
                    onClick = { onSelect(option.id) },
                )
            }
        }
        Text("Fondos personalizados · se conservan exactos", color = Cream.copy(alpha = 0.7f), fontSize = 14.sp)
        if (custom.isEmpty()) {
            Text(
                "Aún no hay. Impórtalos en Admin → Evento.",
                color = Cream.copy(alpha = 0.55f),
                fontSize = 14.sp,
            )
        }
        custom.forEach { option ->
            BigOptionRow(
                title = option.name,
                subtitle = "Personalizado · solo la persona cambia",
                preview = previews[option.id],
                icon = null,
                selected = option.id == selectedId,
                onClick = { onSelect(option.id) },
            )
        }
    }
}

@Composable
private fun BigOptionRow(
    title: String,
    subtitle: String,
    preview: Bitmap?,
    icon: (@Composable () -> Unit)?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (selected) Modifier.border(2.dp, SyscomBlue, RoundedCornerShape(20.dp)) else Modifier),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) SyscomBlue.copy(alpha = 0.22f) else InkSoft,
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (preview != null) {
                Image(
                    preview.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(14.dp))
            } else if (icon != null) {
                icon()
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Cream, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Cream.copy(alpha = 0.7f), fontSize = 14.sp)
            }
            if (selected) Icon(Icons.Rounded.Check, null, tint = SyscomBlueLight, modifier = Modifier.size(28.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccessoriesContent(
    selected: Set<String>,
    status: AiStatus,
    progress: Int,
    onToggle: (String) -> Unit,
    onClearGroup: (String) -> Unit,
    onCancel: () -> Unit,
) {
    SectionTitle("Accesorios", "Solo 1 por grupo. Toca de nuevo para quitar.")
    AiAccessories.GROUPS.forEach { group ->
        Text(stringResource(group.labelRes), color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val noneSelected = selected.none { id -> group.options.any { it.id == id } }
            FilterChip(
                selected = noneSelected,
                onClick = { onClearGroup(group.id) },
                label = { Text("🚫 " + stringResource(R.string.ai_accessory_none), fontSize = 16.sp) },
                modifier = Modifier.height(60.dp),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SyscomBlue),
            )
            group.options.forEach { option ->
                FilterChip(
                    selected = option.id in selected,
                    onClick = { onToggle(option.id) },
                    label = { Text(accessoryEmoji(option.id) + " " + stringResource(option.labelRes), fontSize = 16.sp) },
                    modifier = Modifier.height(60.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SyscomBlue),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
    if (status == AiStatus.UPLOADING || status == AiStatus.GENERATING) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(26.dp), color = SyscomBlueLight)
            Spacer(Modifier.width(12.dp))
            Text(
                if (status == AiStatus.UPLOADING) "Subiendo foto…"
                else "Creando estilo… $progress%",
                color = Cream,
                fontSize = 16.sp,
            )
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(60.dp)) {
            Text(stringResource(R.string.ai_cancel), fontSize = 17.sp)
        }
    } else {
        Text(
            "Al continuar se genera tu foto con IA (tarda ~30 s).",
            color = Cream.copy(alpha = 0.65f),
            fontSize = 14.sp,
        )
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
    preview?.let {
        Image(
            it.asImageBitmap(),
            contentDescription = "Tu foto final",
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop,
        )
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
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit,
                )
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
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val nextLabel = when (step) {
        AiStep.CAPTURE -> "Continuar"
        AiStep.STYLE -> "Continuar"
        AiStep.BACKGROUND -> "Continuar"
        AiStep.ACCESSORIES -> "Generar con IA"
        AiStep.FRAMED -> "Terminar"
        AiStep.SHARE -> "Finalizar"
    }
    val nextEnabled = when (step) {
        AiStep.CAPTURE -> canContinueFromCapture
        AiStep.ACCESSORIES -> canGenerate && !generating
        else -> true
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ink)
            .padding(horizontal = hPad, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.weight(1f).height(68.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Text(if (step == AiStep.CAPTURE) "Salir" else "Atrás", fontSize = 18.sp)
        }
        Button(
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier.weight(1.4f).height(68.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SyscomBlue),
        ) {
            Text(nextLabel, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
