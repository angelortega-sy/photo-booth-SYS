package com.photobooth.kiosk.ui.admin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.CollageCustomization
import com.photobooth.kiosk.data.CollageFrameStyle
import com.photobooth.kiosk.data.EventBranding
import com.photobooth.kiosk.photo.PhotoFrameComposer
import com.photobooth.kiosk.ui.theme.Cream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SAMPLE_ASSET = "backgrounds/gala.webp"
private const val PREVIEW_WIDTH = 280

/**
 * Renders one live preview per [CollageFrameStyle] using the brand colors and
 * logo being edited. The previews use a bundled sample image, so they work
 * offline and never touch the guest session photos.
 */
@Composable
internal fun FramePreviewRow(
    branding: EventBranding,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var previews by remember { mutableStateOf<List<Pair<CollageFrameStyle, Bitmap>>>(emptyList()) }

    DisposableEffect(previews) {
        val rendered = previews
        onDispose { rendered.forEach { it.second.recycle() } }
    }

    LaunchedEffect(branding) {
        // Debounce so typing a hex code does not render four frames per keystroke.
        delay(220)
        previews = withContext(Dispatchers.Default) {
            val sample = context.assets.open(SAMPLE_ASSET).use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext emptyList()

            CollageFrameStyle.entries.mapNotNull { style ->
                val composed = runCatching {
                    PhotoFrameComposer.framePhoto(
                        photo = sample,
                        customization = CollageCustomization(
                            frameStyle = style,
                            eventBranding = branding,
                        ),
                        previewScale = true,
                    )
                }.getOrNull() ?: return@mapNotNull null
                val height = (composed.height * (PREVIEW_WIDTH.toFloat() / composed.width)).toInt()
                    .coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(composed, PREVIEW_WIDTH, height, true)
                if (scaled !== composed) composed.recycle()
                style to scaled
            }
        }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(previews) { (style, bitmap) ->
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(style.titleRes),
                    modifier = Modifier
                        .width(118.dp)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Cream.copy(alpha = 0.04f))
                        .border(1.dp, Cream.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
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
}
