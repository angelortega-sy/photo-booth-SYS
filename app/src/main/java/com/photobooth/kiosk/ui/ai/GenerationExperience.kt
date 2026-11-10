package com.photobooth.kiosk.ui.ai

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.AiStatus
import com.photobooth.kiosk.ui.components.BoothOutlinedButton
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.InkSoft
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomBlueDeep
import com.photobooth.kiosk.ui.theme.SyscomBlueLight
import com.photobooth.kiosk.ui.theme.SyscomTeal
import com.photobooth.kiosk.ui.theme.SyscomTealSoft
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val SPARKLE_COUNT = 34
private const val PHRASE_MS = 2_600L
private const val COMET_TRAIL = 16

/**
 * Full-screen "metamorphosis" shown while the AI works: an aurora background, a
 * comet running around the screen edge, rising sparkles and the guest photo being
 * scanned while the chosen style ghosts over it. When [result] arrives the card
 * flashes and reveals it before the flow moves on.
 */
@Composable
internal fun GenerationExperience(
    status: AiStatus,
    photoPreview: Bitmap?,
    stylePreview: Bitmap?,
    styleName: String?,
    draft: Bitmap?,
    result: Bitmap?,
    onCancel: () -> Unit,
) {
    val revealing = result != null
    val generating = status == AiStatus.GENERATING
    val motion = rememberInfiniteTransition(label = "generation")
    val aurora = motion.loop(14_000, "aurora")
    val comet = motion.loop(5_200, "comet")
    val sparkles = motion.loop(9_000, "sparkles")
    val scan = motion.loop(2_400, "scan")
    val ring = motion.loop(3_600, "ring")
    val morph = motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "morph",
    )

    val flash = remember { Animatable(0f) }
    LaunchedEffect(revealing) {
        if (revealing) {
            flash.snapTo(0.95f)
            flash.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) { drawAurora(aurora.value) }
        Canvas(Modifier.fillMaxSize()) { drawSparkles(sparkles.value) }
        Canvas(Modifier.fillMaxSize()) { drawEdgeComets(comet.value) }

        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!styleName.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.ai_gen_style_chip, styleName),
                    color = SyscomTealSoft,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Ink.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            MetamorphosisCard(
                photoPreview = photoPreview,
                stylePreview = stylePreview.takeIf { generating && !revealing },
                draft = draft.takeIf { !revealing },
                result = result,
                scanning = !revealing,
                scan = scan,
                ring = ring,
                morph = morph,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(
                    when {
                        revealing -> R.string.ai_gen_ready
                        generating -> R.string.ai_gen_title_alive
                        else -> R.string.ai_gen_title_preparing
                    },
                ),
                color = Cream,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            RotatingPhrase(generating = generating, revealing = revealing)
            Spacer(Modifier.weight(1f))
            if (!revealing) {
                BoothOutlinedButton(
                    text = stringResource(R.string.ai_cancel),
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (flash.value > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = flash.value }
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun MetamorphosisCard(
    photoPreview: Bitmap?,
    stylePreview: Bitmap?,
    draft: Bitmap?,
    result: Bitmap?,
    scanning: Boolean,
    scan: State<Float>,
    ring: State<Float>,
    morph: State<Float>,
) {
    val outer = RoundedCornerShape(32.dp)
    val inner = RoundedCornerShape(29.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 500.dp)
            .aspectRatio(3f / 4f),
        contentAlignment = Alignment.Center,
    ) {
        // Halo behind the card.
        Canvas(Modifier.fillMaxSize()) {
            val pulse = 0.5f + 0.5f * sin(ring.value * 2f * PI.toFloat())
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(SyscomBlue.copy(alpha = 0.35f + pulse * 0.2f), Color.Transparent),
                    center = center,
                    radius = size.maxDimension * 0.72f,
                ),
                radius = size.maxDimension * 0.72f,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(18.dp)
                .clip(outer),
        ) {
            // Rotating light ring: a sweep gradient spun behind the photo and
            // clipped by the card, so only a 3 dp border of it shows.
            Canvas(Modifier.fillMaxSize()) {
                rotate(ring.value * 360f) {
                    val side = size.maxDimension * 1.6f
                    drawRect(
                        brush = Brush.sweepGradient(
                            listOf(SyscomTeal, SyscomBlueLight, Color.Transparent, SyscomBlue, SyscomTeal),
                            center = center,
                        ),
                        topLeft = Offset(center.x - side / 2f, center.y - side / 2f),
                        size = Size(side, side),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .clip(inner)
                    .background(InkSoft),
            ) {
                Crossfade(targetState = result ?: draft ?: photoPreview, label = "cardImage") { bitmap ->
                    if (bitmap != null && !bitmap.isRecycled) {
                        Image(
                            bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.ai_cd_processing_photo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (draft == null && stylePreview != null && !stylePreview.isRecycled) {
                    Image(
                        stylePreview.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = morph.value * 0.78f },
                    )
                }
                if (scanning) {
                    Canvas(Modifier.fillMaxSize()) { drawScanBeam(scan.value) }
                }
            }
        }
    }
}

@Composable
private fun RotatingPhrase(generating: Boolean, revealing: Boolean) {
    val phrases = if (generating) {
        listOf(
            stringResource(R.string.ai_gen_phrase_1),
            stringResource(R.string.ai_gen_phrase_2),
            stringResource(R.string.ai_gen_phrase_3),
            stringResource(R.string.ai_gen_phrase_4),
            stringResource(R.string.ai_gen_phrase_5),
        )
    } else {
        listOf(stringResource(R.string.ai_gen_detail_upload))
    }
    var index by remember(generating) { mutableIntStateOf(0) }
    LaunchedEffect(generating, revealing) {
        if (revealing) return@LaunchedEffect
        while (true) {
            delay(PHRASE_MS)
            // Stop on the last phrase rather than looping back to "reading your pose".
            if (index < phrases.lastIndex) index++
        }
    }
    AnimatedContent(
        targetState = if (revealing) "" else phrases[index.coerceIn(0, phrases.lastIndex)],
        transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn(tween(420))) togetherWith
                (slideOutVertically { -it / 2 } + fadeOut(tween(300)))
        },
        label = "phrase",
    ) { phrase ->
        Text(
            text = phrase,
            color = SyscomTealSoft,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InfiniteTransition.loop(durationMs: Int, label: String): State<Float> = animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart),
    label = label,
)

/** Three slow colour blobs drifting on Lissajous paths. */
private fun DrawScope.drawAurora(progress: Float) {
    val t = progress * 2f * PI.toFloat()
    val radius = size.maxDimension * 0.62f
    listOf(
        Triple(SyscomBlue.copy(alpha = 0.55f), Offset(0.5f + 0.32f * sin(t), 0.25f + 0.12f * cos(t * 2f)), 1f),
        Triple(SyscomTeal.copy(alpha = 0.30f), Offset(0.3f + 0.25f * cos(t), 0.7f + 0.15f * sin(t * 2f)), 0.8f),
        Triple(SyscomBlueDeep.copy(alpha = 0.70f), Offset(0.75f + 0.2f * sin(t * 2f), 0.6f + 0.25f * cos(t)), 0.9f),
    ).forEach { (color, unit, scale) ->
        val center = Offset(unit.x * size.width, unit.y * size.height)
        drawCircle(
            brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius * scale),
            radius = radius * scale,
            center = center,
        )
    }
}

private fun DrawScope.drawSparkles(progress: Float) {
    val colors = listOf(Cream, SyscomTealSoft, SyscomBlueLight)
    repeat(SPARKLE_COUNT) { index ->
        val sparkle = GenerationSparkles.at(index, progress)
        val center = Offset(sparkle.x * size.width, sparkle.y * size.height)
        val core = 2.2.dp.toPx() * sparkle.scale
        val color = colors[index % colors.size]
        drawCircle(color.copy(alpha = sparkle.alpha * 0.25f), radius = core * 3.2f, center = center)
        drawCircle(color.copy(alpha = sparkle.alpha), radius = core, center = center)
    }
}

/** Two comets with fading tails chasing each other around the screen border. */
private fun DrawScope.drawEdgeComets(progress: Float) {
    val inset = 12.dp.toPx()
    val corner = 28.dp.toPx()
    val track = Size(size.width - inset * 2, size.height - inset * 2)
    drawRoundRect(
        color = SyscomBlueLight.copy(alpha = 0.16f),
        topLeft = Offset(inset, inset),
        size = track,
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = 1.5.dp.toPx()),
    )
    listOf(progress to SyscomTeal, (progress + 0.5f) to SyscomBlueLight).forEach { (head, color) ->
        for (step in 0 until COMET_TRAIL) {
            val falloff = 1f - step / COMET_TRAIL.toFloat()
            val point = GenerationCircuit.point(track.width, track.height, corner, head - step * 0.0045f)
            drawCircle(
                color = color.copy(alpha = falloff * falloff),
                radius = max(1f, 5.dp.toPx() * falloff),
                center = Offset(inset + point.x, inset + point.y),
            )
        }
    }
}

/** Holographic scan: a soft teal band with a bright leading line, top to bottom. */
private fun DrawScope.drawScanBeam(progress: Float) {
    val band = size.height * 0.24f
    val lineY = -band + progress * (size.height + band * 2f)
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, SyscomTeal.copy(alpha = 0.38f)),
            startY = lineY - band,
            endY = lineY,
        ),
        topLeft = Offset(0f, lineY - band),
        size = Size(size.width, band),
    )
    drawRect(
        color = SyscomTealSoft.copy(alpha = 0.85f),
        topLeft = Offset(0f, lineY - 1.5.dp.toPx()),
        size = Size(size.width, 2.dp.toPx()),
    )
}
