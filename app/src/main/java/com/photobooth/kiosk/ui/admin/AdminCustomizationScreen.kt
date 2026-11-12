package com.photobooth.kiosk.ui.admin

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.BackgroundOption
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.InkSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Thumbnail tile size shared by both hub entries, so the two images line up. */
private val HubImageWidth = 152.dp
private val HubImageHeight = 180.dp
private const val COLLAGE_COLUMNS = 3
private const val COLLAGE_ROWS = 2
private const val COLLAGE_TILE_WIDTH = 240
private const val COLLAGE_TILE_HEIGHT = 427

/**
 * Personalización: two large entries, one for the backgrounds and one for the
 * AI styles. Both images are the same ones the guest sees in the booth.
 */
@Composable
internal fun AdminCustomizationScreen(
    backgrounds: List<BackgroundOption>,
    loadThumbnail: (String) -> Bitmap?,
    onOpenBackgrounds: () -> Unit,
    onOpenAiStyles: () -> Unit,
) {
    val builtIns = remember(backgrounds) { backgrounds.filter { it.isBuiltIn } }
    val collage by produceState<ImageBitmap?>(initialValue = null, builtIns) {
        value = withContext(Dispatchers.IO) {
            val thumbnails = builtIns.mapNotNull { loadThumbnail(it.id) }
            if (thumbnails.isEmpty()) null else buildBackgroundCollage(thumbnails).asImageBitmap()
        }
    }

    AdminScreenColumn {
        item {
            CustomizationEntry(
                title = stringResource(R.string.admin_custom_backgrounds_title),
                subtitle = stringResource(R.string.admin_custom_backgrounds_subtitle),
                onClick = onOpenBackgrounds,
            ) {
                collage?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        item {
            CustomizationEntry(
                title = stringResource(R.string.admin_custom_ai_title),
                subtitle = stringResource(R.string.admin_custom_ai_subtitle),
                onClick = onOpenAiStyles,
            ) {
                Image(
                    painter = painterResource(R.drawable.mode_ai_styles),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun CustomizationEntry(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    image: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(InkSoft)
            .border(1.dp, Cream.copy(alpha = 0.08f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = HubImageWidth, height = HubImageHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(Ink),
        ) {
            image()
        }
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Cream,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = Cream.copy(alpha = 0.65f),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = Cream.copy(alpha = 0.5f),
        )
    }
}

/**
 * 3x2 grid of the bundled backgrounds, each center-cropped to a 9:16 tile. The
 * result matches [HubImageWidth] x [HubImageHeight] once cropped by the entry.
 */
internal fun buildBackgroundCollage(thumbnails: List<Bitmap>): Bitmap {
    val width = COLLAGE_COLUMNS * COLLAGE_TILE_WIDTH
    val height = COLLAGE_ROWS * COLLAGE_TILE_HEIGHT
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(android.graphics.Color.BLACK)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    thumbnails.take(COLLAGE_COLUMNS * COLLAGE_ROWS).forEachIndexed { index, source ->
        val column = index % COLLAGE_COLUMNS
        val row = index / COLLAGE_COLUMNS
        val left = column * COLLAGE_TILE_WIDTH
        val top = row * COLLAGE_TILE_HEIGHT
        val destination = RectF(
            left.toFloat(),
            top.toFloat(),
            (left + COLLAGE_TILE_WIDTH).toFloat(),
            (top + COLLAGE_TILE_HEIGHT).toFloat(),
        )
        // Center-crop the source to the tile's aspect ratio.
        val tileAspect = COLLAGE_TILE_WIDTH.toFloat() / COLLAGE_TILE_HEIGHT
        val sourceAspect = source.width.toFloat() / source.height
        val crop = if (sourceAspect > tileAspect) {
            val cropWidth = (source.height * tileAspect).toInt()
            val x = (source.width - cropWidth) / 2
            Rect(x, 0, x + cropWidth, source.height)
        } else {
            val cropHeight = (source.width / tileAspect).toInt()
            val y = (source.height - cropHeight) / 2
            Rect(0, y, source.width, y + cropHeight)
        }
        canvas.drawBitmap(source, crop, destination, paint)
    }
    return out
}
