package com.photobooth.kiosk.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.BackgroundOption
import com.photobooth.kiosk.data.BackgroundRepository
import com.photobooth.kiosk.data.CollageFrameStyle
import com.photobooth.kiosk.data.CollageLayout
import com.photobooth.kiosk.data.EventBadgePreset
import com.photobooth.kiosk.data.PhotoBorderStyle
import com.photobooth.kiosk.photo.PhotoFrameComposer
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.InkSoft
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomBlueDeep
import com.photobooth.kiosk.ui.theme.SyscomBlueLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun StudioWizardScreen(
    state: PhotoBoothUiState,
    backgrounds: List<BackgroundOption>,
    loadThumbnail: (String) -> Bitmap?,
    enabled: Boolean,
    onStepSelected: (Int) -> Unit,
    onSelectPhotoTarget: (Int?) -> Unit,
    onSelectBackground: (String) -> Unit,
    onSelectLayout: (CollageLayout) -> Unit,
    onSelectFrameStyle: (CollageFrameStyle) -> Unit,
    onSelectBorderStyle: (PhotoBorderStyle) -> Unit,
    onSelectBadgePreset: (EventBadgePreset) -> Unit,
    onToggleDateStamp: (Boolean) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onRetake: () -> Unit,
    onHome: () -> Unit,
) {
    val activeBitmap = if (state.wizardStep == 0 && !state.applyBackgroundToAll && state.selectedPhotoIndex != null) {
        state.photoPreviews.getOrNull(state.selectedPhotoIndex) ?: state.stripPreview
    } else if (state.wizardStep == 2 && state.selectedPhotoIndex != null) {
        state.photoPreviews.getOrNull(state.selectedPhotoIndex) ?: state.stripPreview
    } else {
        state.stripPreview
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(InkSoft, Ink))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header row with title, non-blocking background segmentation badge, and retake/home
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.customization.eventBranding.brand,
                            color = SyscomBlueLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.8.sp,
                        )
                        if (state.isSegmentingAny) {
                            Spacer(Modifier.width(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SyscomBlue.copy(alpha = 0.22f))
                                    .border(1.dp, SyscomBlueLight.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                CircularProgressIndicator(
                                    color = SyscomBlueLight,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(11.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                val nextPending = ((0 until PhotoBoothUiState.PHOTO_COUNT)
                                    .firstOrNull { it !in state.segmentedIndices } ?: 2) + 1
                                Text(
                                    text = stringResource(R.string.wizard_preparing_shot, nextPending),
                                    color = Cream.copy(alpha = 0.85f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.result_title),
                        color = Cream,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }

                TextButton(onClick = onRetake, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = Cream.copy(alpha = 0.78f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.retake),
                        color = Cream.copy(alpha = 0.78f),
                        fontSize = 12.sp,
                    )
                }
                TextButton(onClick = onHome, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Rounded.Home,
                        contentDescription = null,
                        tint = Cream.copy(alpha = 0.78f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.home),
                        color = Cream.copy(alpha = 0.78f),
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 3-Step Interactive Wizard Stepper
            WizardStepperBar(
                currentStep = state.wizardStep,
                onStepSelected = onStepSelected,
            )

            Spacer(Modifier.height(10.dp))

            // Central Live Preview Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Ink.copy(alpha = 0.78f))
                    .border(1.dp, SyscomBlueLight.copy(alpha = 0.22f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (activeBitmap != null && !activeBitmap.isRecycled) {
                    val isShowingCollage = activeBitmap === state.stripPreview
                    val tapModifier = if (isShowingCollage) {
                        Modifier.pointerInput(activeBitmap, state.customization.layout) {
                            detectTapGestures { offset ->
                                val scale = minOf(
                                    size.width.toFloat() / activeBitmap.width,
                                    size.height.toFloat() / activeBitmap.height,
                                )
                                val renderedWidth = activeBitmap.width * scale
                                val renderedHeight = activeBitmap.height * scale
                                val left = (size.width - renderedWidth) / 2f
                                val top = (size.height - renderedHeight) / 2f
                                val normX = (offset.x - left) / renderedWidth
                                val normY = (offset.y - top) / renderedHeight
                                PhotoFrameComposer.photoIndexAtPosition(
                                    normalizedX = normX,
                                    normalizedY = normY,
                                    layout = state.customization.layout,
                                )?.let { tappedIndex ->
                                    onSelectPhotoTarget(tappedIndex)
                                }
                            }
                        }
                    } else {
                        Modifier
                    }

                    AnimatedContent(
                        targetState = activeBitmap,
                        transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(110)) },
                        label = "studio_live_preview",
                    ) { targetBmp ->
                        if (!targetBmp.isRecycled) {
                            Image(
                                bitmap = targetBmp.asImageBitmap(),
                                contentDescription = stringResource(R.string.photo_description),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                                    .then(tapModifier),
                            )
                        }
                    }
                } else {
                    val firstThumb = state.captureThumbnails.firstOrNull()
                    if (firstThumb != null && !firstThumb.isRecycled) {
                        Image(
                            bitmap = firstThumb.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                        )
                    } else {
                        CircularProgressIndicator(color = SyscomBlueLight)
                    }
                }

                // Floating preview mode switcher (Collage vs Individual Photos)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Ink.copy(alpha = 0.86f))
                        .border(1.dp, Cream.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val collageSelected = state.selectedPhotoIndex == null
                    PreviewTargetChip(
                        label = stringResource(R.string.result_strip_short),
                        selected = collageSelected,
                        onClick = { onSelectPhotoTarget(null) },
                    )
                    repeat(PhotoBoothUiState.PHOTO_COUNT) { idx ->
                        PreviewTargetChip(
                            label = stringResource(R.string.result_photo_short, idx + 1),
                            selected = state.selectedPhotoIndex == idx,
                            onClick = { onSelectPhotoTarget(idx) },
                        )
                    }
                }
            }

            if (state.usedFallback) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.result_fallback),
                    color = Cream.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Step-specific control panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(InkSoft.copy(alpha = 0.96f))
                    .border(1.dp, Cream.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .padding(14.dp),
            ) {
                when (state.wizardStep) {
                    0 -> StepBackgroundsPanel(
                        state = state,
                        backgrounds = backgrounds,
                        loadThumbnail = loadThumbnail,
                        onSelectPhotoTarget = onSelectPhotoTarget,
                        onSelectBackground = onSelectBackground,
                        onNext = { onStepSelected(1) },
                    )

                    1 -> StepCollageAndFramePanel(
                        state = state,
                        onSelectLayout = onSelectLayout,
                        onSelectFrameStyle = onSelectFrameStyle,
                        onSelectBorderStyle = onSelectBorderStyle,
                        onPrev = { onStepSelected(0) },
                        onNext = { onStepSelected(2) },
                    )

                    else -> StepBadgeAndDeliveryPanel(
                        state = state,
                        enabled = enabled,
                        onSelectBadgePreset = onSelectBadgePreset,
                        onToggleDateStamp = onToggleDateStamp,
                        onPrev = { onStepSelected(1) },
                        onSave = onSave,
                        onShare = onShare,
                    )
                }
            }
        }
    }
}

@Composable
private fun WizardStepperBar(
    currentStep: Int,
    onStepSelected: (Int) -> Unit,
) {
    val steps = listOf(
        stringResource(R.string.wizard_step_backgrounds),
        stringResource(R.string.wizard_step_collage),
        stringResource(R.string.wizard_step_badge),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.copy(alpha = 0.65f))
            .border(1.dp, Cream.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        steps.forEachIndexed { index, title ->
            val isSelected = index == currentStep
            val isCompleted = index < currentStep
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when {
                            isSelected -> SyscomBlue
                            isCompleted -> SyscomBlueDeep.copy(alpha = 0.55f)
                            else -> Color.Transparent
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) SyscomBlueLight else Color.Transparent,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable { onStepSelected(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    color = if (isSelected || isCompleted) Color.White else Cream.copy(alpha = 0.62f),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PreviewTargetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) SyscomBlue else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Cream.copy(alpha = 0.72f),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun StepBackgroundsPanel(
    state: PhotoBoothUiState,
    backgrounds: List<BackgroundOption>,
    loadThumbnail: (String) -> Bitmap?,
    onSelectPhotoTarget: (Int?) -> Unit,
    onSelectBackground: (String) -> Unit,
    onNext: () -> Unit,
) {
    val activeBgId = if (state.applyBackgroundToAll || state.selectedPhotoIndex == null) {
        state.selectedBackgroundId
    } else {
        state.photoBackgroundIds.getOrElse(state.selectedPhotoIndex) { state.selectedBackgroundId }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Target selector: Apply to all 3 vs individual photo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TargetScopeChip(
                text = stringResource(R.string.wizard_apply_all),
                selected = state.applyBackgroundToAll || state.selectedPhotoIndex == null,
                onClick = { onSelectPhotoTarget(null) },
            )
            repeat(PhotoBoothUiState.PHOTO_COUNT) { index ->
                val bgForPhoto = state.photoBackgroundIds.getOrNull(index)
                val bgOption = backgrounds.firstOrNull { it.id == bgForPhoto }
                val subtitle = bgOption?.name ?: ""
                TargetScopeChip(
                    text = stringResource(R.string.wizard_photo_tab, index + 1) +
                        if (subtitle.isNotEmpty()) " · $subtitle" else "",
                    selected = !state.applyBackgroundToAll && state.selectedPhotoIndex == index,
                    onClick = { onSelectPhotoTarget(index) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth().height(136.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(backgrounds, key = { it.id }) { option ->
                StudioBackgroundCard(
                    option = option,
                    selected = option.id == activeBgId,
                    loadThumbnail = loadThumbnail,
                    onClick = { onSelectBackground(option.id) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SyscomBlue,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = stringResource(R.string.wizard_next_step) + ": " + stringResource(R.string.wizard_step_collage),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TargetScopeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) SyscomBlue.copy(alpha = 0.28f) else Ink)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) SyscomBlueLight else Cream.copy(alpha = 0.16f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = if (selected) Cream else Cream.copy(alpha = 0.68f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun StudioBackgroundCard(
    option: BackgroundOption,
    selected: Boolean,
    loadThumbnail: (String) -> Bitmap?,
    onClick: () -> Unit,
) {
    val isOriginal = option.id == BackgroundRepository.ORIGINAL_ID
    val thumbnail by produceState<Bitmap?>(initialValue = null, option.id) {
        value = if (isOriginal) null else withContext(Dispatchers.IO) { loadThumbnail(option.id) }
    }
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .width(82.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(106.dp)
                .clip(shape)
                .background(Ink)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) SyscomBlueLight else Cream.copy(alpha = 0.16f),
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isOriginal) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        tint = SyscomBlueLight,
                        modifier = Modifier.size(28.dp),
                    )
                }
            } else {
                thumbnail?.let { bmp ->
                    if (!bmp.isRecycled) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
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
                        .padding(6.dp)
                        .size(18.dp)
                        .background(SyscomBlue, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = option.name,
            color = if (selected) Cream else Cream.copy(alpha = 0.65f),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StepCollageAndFramePanel(
    state: PhotoBoothUiState,
    onSelectLayout: (CollageLayout) -> Unit,
    onSelectFrameStyle: (CollageFrameStyle) -> Unit,
    onSelectBorderStyle: (PhotoBorderStyle) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.wizard_section_layout),
            color = Cream.copy(alpha = 0.78f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CollageLayout.entries.forEach { layout ->
                val selected = state.customization.layout == layout
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) SyscomBlue.copy(alpha = 0.25f) else Ink)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) SyscomBlueLight else Cream.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(14.dp),
                        )
                        .clickable { onSelectLayout(layout) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Collections,
                        contentDescription = null,
                        tint = if (selected) SyscomBlueLight else Cream.copy(alpha = 0.62f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringResource(layout.titleRes),
                        color = Cream,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(layout.subtitleRes),
                        color = Cream.copy(alpha = 0.56f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.wizard_section_frame),
            color = Cream.copy(alpha = 0.78f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CollageFrameStyle.entries.forEach { frame ->
                val selected = state.customization.frameStyle == frame
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) SyscomBlue.copy(alpha = 0.25f) else Ink)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) SyscomBlueLight else Cream.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(16.dp),
                        )
                        .clickable { onSelectFrameStyle(frame) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(frame.backgroundHex))
                            .border(2.dp, Color(frame.accentHex), CircleShape),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = stringResource(frame.titleRes),
                        color = Cream,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.wizard_section_border),
            color = Cream.copy(alpha = 0.78f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PhotoBorderStyle.entries.forEach { border ->
                val selected = state.customization.photoBorderStyle == border
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) SyscomBlue.copy(alpha = 0.25f) else Ink)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) SyscomBlueLight else Cream.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(16.dp),
                        )
                        .clickable { onSelectBorderStyle(border) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(border.titleRes),
                        color = Cream,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onPrev,
                modifier = Modifier.weight(0.4f).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Cream.copy(alpha = 0.24f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(stringResource(R.string.wizard_prev_step), fontSize = 13.sp)
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(0.6f).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SyscomBlue,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = stringResource(R.string.wizard_step_badge),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun StepBadgeAndDeliveryPanel(
    state: PhotoBoothUiState,
    enabled: Boolean,
    onSelectBadgePreset: (EventBadgePreset) -> Unit,
    onToggleDateStamp: (Boolean) -> Unit,
    onPrev: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = SyscomBlueLight,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.wizard_section_badge),
                color = Cream.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            // 1-tap toggle for date stamp
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (state.customization.showDateStamp) SyscomBlue.copy(alpha = 0.25f)
                        else Ink,
                    )
                    .border(
                        width = 1.dp,
                        color = if (state.customization.showDateStamp) SyscomBlueLight
                        else Cream.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onToggleDateStamp(!state.customization.showDateStamp) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (state.customization.showDateStamp) "Fecha: Visible" else "Fecha: Oculta",
                    color = Cream,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EventBadgePreset.entries.forEach { badge ->
                val selected = state.customization.badgePreset == badge
                val headline = if (badge == EventBadgePreset.CUSTOM) {
                    state.customization.eventBranding.badgeHeadline
                } else {
                    badge.headline
                }
                val subheadline = if (badge == EventBadgePreset.CUSTOM) {
                    state.customization.eventBranding.badgeSubheadline
                } else {
                    badge.subheadline
                }
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) SyscomBlue.copy(alpha = 0.26f) else Ink)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) SyscomBlueLight else Cream.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(14.dp),
                        )
                        .clickable { onSelectBadgePreset(badge) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = headline,
                        color = Cream,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = subheadline,
                        color = Cream.copy(alpha = 0.62f),
                        fontSize = 10.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Primary download CTA: opens the system share sheet, where Quick Share appears.
        Button(
            onClick = onShare,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SyscomBlue,
                contentColor = Color.White,
            ),
        ) {
            if (state.isExporting) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
            } else {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = stringResource(R.string.save_photo),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onPrev,
                enabled = enabled,
                modifier = Modifier.weight(0.38f).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Cream.copy(alpha = 0.24f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.wizard_prev_step), fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier.weight(0.62f).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, SyscomBlueLight.copy(alpha = 0.65f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Save,
                    contentDescription = null,
                    tint = SyscomBlueLight,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.save),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
