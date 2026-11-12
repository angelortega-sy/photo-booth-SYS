package com.photobooth.kiosk.ui.admin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.CollageCustomization
import com.photobooth.kiosk.data.CollageFrameStyle
import com.photobooth.kiosk.data.EventBranding
import com.photobooth.kiosk.photo.PhotoFrameComposer
import com.photobooth.kiosk.ui.theme.Cream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SAMPLE_ASSET = "backgrounds/gala.webp"
private const val PREVIEW_WIDTH = 280
private const val EXPANDED_WIDTH = 720

/**
 * Holds the rendered frame previews for one event editor.
 *
 * The cache belongs to the editor screen, not to the lazy list item that shows
 * the strip. Scrolling the strip out of view therefore keeps the bitmaps, and
 * coming back does not re-render or flash. [refresh] skips work when the
 * branding and sponsors have not changed since the last render.
 */
internal class FramePreviewCache {
    val previews = mutableStateOf<List<Pair<CollageFrameStyle, Bitmap>>>(emptyList())
    private var renderedKey: Pair<EventBranding, List<String>>? = null
    private var released = false

    /** Renders all frame styles for [branding]. The old previews stay visible until the new ones are ready. */
    suspend fun refresh(context: Context, branding: EventBranding, sponsorPaths: List<String>) {
        val key = branding to sponsorPaths
        if (released || key == renderedKey) return
        val rendered = withContext(Dispatchers.Default) {
            CollageFrameStyle.entries.mapNotNull { style ->
                renderPreview(context, style, branding, PREVIEW_WIDTH, sponsorPaths)?.let { style to it }
            }
        }
        if (released) {
            rendered.forEach { it.second.recycle() }
            return
        }
        renderedKey = key
        previews.value = rendered
    }

    /** Called when the editor leaves composition. Recycles every bitmap it still holds. */
    fun release() {
        released = true
        previews.value.forEach { if (!it.second.isRecycled) it.second.recycle() }
        previews.value = emptyList()
        renderedKey = null
    }
}

/**
 * Horizontal strip of live previews, one per [CollageFrameStyle]. Pass a
 * [FramePreviewCache] owned by the editor so the bitmaps survive scrolling.
 * Tapping a preview opens it big.
 */
@Composable
internal fun FramePreviewRow(
    previews: List<Pair<CollageFrameStyle, Bitmap>>,
    branding: EventBranding,
    sponsorPaths: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var expandedStyle by remember { mutableStateOf<CollageFrameStyle?>(null) }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(previews, key = { it.first.id }) { (style, bitmap) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(style.titleRes),
                    modifier = Modifier
                        .width(118.dp)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Cream.copy(alpha = 0.04f))
                        .border(1.dp, Cream.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .clickable { expandedStyle = style },
                )
                Text(
                    text = stringResource(style.titleRes),
                    color = Cream.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }

    expandedStyle?.let { style ->
        ExpandedFramePreview(
            style = style,
            branding = branding,
            sponsorPaths = sponsorPaths,
            onDismiss = { expandedStyle = null },
        )
    }
}

@Composable
private fun ExpandedFramePreview(
    style: CollageFrameStyle,
    branding: EventBranding,
    sponsorPaths: List<String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(style, branding, sponsorPaths) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(style, branding, sponsorPaths) {
        bitmap = withContext(Dispatchers.Default) {
            renderPreview(context, style, branding, EXPANDED_WIDTH, sponsorPaths)
        }
    }

    DisposableEffect(bitmap) {
        val current = bitmap
        onDispose { current?.recycle() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF0050B14))
                .clickable(onClick = onDismiss)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            bitmap?.let { preview ->
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = stringResource(style.titleRes),
                    modifier = Modifier
                        .fillMaxHeight(0.8f)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(18.dp)),
                )
            }
            Text(
                text = stringResource(style.titleRes),
                color = Cream,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = stringResource(R.string.admin_brand_preview_close),
                color = Cream.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun renderPreview(
    context: Context,
    style: CollageFrameStyle,
    branding: EventBranding,
    width: Int,
    sponsorPaths: List<String> = emptyList(),
): Bitmap? {
    val sample = runCatching {
        context.assets.open(SAMPLE_ASSET).use { BitmapFactory.decodeStream(it) }
    }.getOrNull() ?: return null

    val composed = runCatching {
        PhotoFrameComposer.framePhoto(
            photo = sample,
            customization = CollageCustomization(
                frameStyle = style,
                eventBranding = branding,
                sponsorLogoPaths = sponsorPaths,
            ),
            previewScale = true,
        )
    }.getOrNull() ?: return null

    val height = (composed.height * (width.toFloat() / composed.width)).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(composed, width, height, true)
    if (scaled !== composed) composed.recycle()
    return scaled
}
