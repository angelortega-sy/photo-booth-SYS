package com.photobooth.kiosk.ui.attract

import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.photobooth.kiosk.R
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.SyscomBlue
import kotlinx.coroutines.delay

/**
 * Full-screen attract video shown over the welcome screen while the booth is
 * idle. The blue tap button stays on screen for the whole clip, so the loop
 * reads as an interactive screen instead of a plain video, and grows into the
 * real call to action when the clip ends. The first tap dismisses everything;
 * if nobody taps, the clip replays after a long poster hold. A missing or
 * broken clip dismisses itself so the booth falls back to the welcome screen
 * instead of showing a black rectangle.
 */
@Composable
fun AttractOverlay(
    visible: Boolean,
    videoId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val context = LocalContext.current
    var finished by remember { mutableStateOf(false) }
    val player = remember { MediaPlayer() }
    val descriptor = remember { mutableStateOf<AssetFileDescriptor?>(null) }

    val captions = remember(videoId) { AttractCaptions.forVideo(videoId) }
    var positionMs by remember { mutableStateOf(0L) }
    var shownCaption by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { player.release() }
            runCatching { descriptor.value?.close() }
        }
    }

    LaunchedEffect(videoId) {
        finished = false
        player.setVolume(0f, 0f)

        val opened = runCatching {
            when (val source = AttractVideos.resolvedOrDefault(context, videoId)) {
                is AttractSource.Asset -> {
                    context.assets.openFd("attract/${source.fileName}").also { fd ->
                        descriptor.value = fd
                        player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    }
                }

                is AttractSource.Stored ->
                    player.setDataSource(source.file.absolutePath)

                null -> error("no attract clip")
            }
        }

        if (opened.isFailure) {
            onDismiss()
            return@LaunchedEffect
        }

        player.setOnPreparedListener { mp ->
            runCatching {
                mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            }
            mp.seekTo(0)
            mp.start()
        }
        player.setOnCompletionListener { finished = true }

        runCatching { player.prepareAsync() }.onFailure { onDismiss() }
    }

    LaunchedEffect(finished) {
        if (!finished) return@LaunchedEffect
        delay(ATTRACT_REPLAY_DELAY_MS)
        runCatching {
            player.seekTo(0)
            player.start()
        }
        finished = false
    }

    // The captions are not part of the file, so the app needs to know where the
    // playback head is. Cheap poll: only runs for clips that have a caption track.
    LaunchedEffect(captions) {
        if (captions.isEmpty()) return@LaunchedEffect
        while (true) {
            positionMs = runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
            delay(120)
        }
    }

    val caption = if (finished) {
        null
    } else {
        captions.firstOrNull { positionMs >= it.fromMs && positionMs < it.toMs }
    }

    // Keep the last text while the exit animation runs, otherwise it blanks out.
    LaunchedEffect(caption) {
        caption?.let { shownCaption = it.textRes }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(
                        object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                runCatching { player.setSurface(holder.surface) }
                            }

                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                runCatching { player.setSurface(null) }
                            }
                        },
                    )
                }
            },
        )

        AnimatedVisibility(
            visible = finished,
            enter = fadeIn(tween(700)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.White.copy(alpha = 0.94f),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 56.dp, vertical = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = finished,
                enter = fadeIn(tween(700)),
            ) {
                Text(
                    text = stringResource(R.string.attract_slogan),
                    color = Ink,
                    fontSize = 46.sp,
                    lineHeight = 54.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            if (finished) {
                Spacer(modifier = Modifier.height(28.dp))
            }
            TapButton(expanded = finished)
        }

        AnimatedVisibility(
            visible = caption != null,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { height -> height / 2 },
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 40.dp, end = 40.dp, bottom = 158.dp),
        ) {
            Text(
                text = shownCaption?.let { stringResource(it) }.orEmpty(),
                color = Ink,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                style = TextStyle(shadow = Shadow(color = Color.White, blurRadius = 26f)),
            )
        }
    }
}

/**
 * The tap hint that lives over the clip. Blue and big enough to read from a few
 * meters, but it breathes slowly so it does not fight the video for attention.
 * When the clip ends it grows into the real call to action and stops breathing.
 */
@Composable
private fun TapButton(expanded: Boolean) {
    val breath = rememberInfiniteTransition(label = "attract-tap")
    val breathing by breath.animateFloat(
        initialValue = 0.78f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "attract-tap-alpha",
    )

    val horizontalPadding by animateDpAsState(
        targetValue = if (expanded) 46.dp else 34.dp,
        animationSpec = tween(600),
        label = "attract-tap-hpad",
    )
    val verticalPadding by animateDpAsState(
        targetValue = if (expanded) 22.dp else 16.dp,
        animationSpec = tween(600),
        label = "attract-tap-vpad",
    )
    val fontSize by animateFloatAsState(
        targetValue = if (expanded) 28f else 24f,
        animationSpec = tween(600),
        label = "attract-tap-font",
    )

    Box(
        modifier = Modifier
            .alpha(if (expanded) 1f else breathing)
            .clip(RoundedCornerShape(999.dp))
            .background(SyscomBlue)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        Text(
            text = stringResource(R.string.attract_cta),
            color = Cream,
            fontSize = fontSize.sp,
            fontWeight = if (expanded) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
