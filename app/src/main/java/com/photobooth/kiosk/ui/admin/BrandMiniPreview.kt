package com.photobooth.kiosk.ui.admin

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.ui.theme.Cream

private val MiniPreviewWidth = 96.dp

/**
 * Small live mock of the photo frame: brand gradient, logo or name on top and
 * the hashtag at the bottom. Drawn with Compose only, so it updates on every
 * keystroke without rendering photos.
 */
@Composable
internal fun BrandMiniPreview(
    primaryColor: Long,
    secondaryColor: Long,
    brand: String,
    hashtag: String,
    logo: Bitmap?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .width(MiniPreviewWidth)
            .aspectRatio(9f / 16f)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(primaryColor), Color(secondaryColor))))
            .border(1.dp, Cream.copy(alpha = 0.16f), shape)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            if (logo != null) {
                Image(
                    bitmap = logo.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = brand,
                    color = Cream,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = hashtag,
            color = Cream.copy(alpha = 0.85f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
