package com.photobooth.kiosk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.annotation.DrawableRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import androidx.compose.ui.res.stringResource
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.InkSoft
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomBlueLight

@Composable
fun ModeSelectScreen(
    onPickAi: () -> Unit,
    onPickCollage: () -> Unit,
    onHome: () -> Unit,
) {
    val compact = LocalConfiguration.current.screenWidthDp < 600
    val hPad = if (compact) 20.dp else 40.dp

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = hPad, vertical = if (compact) 20.dp else 36.dp),
    ) {
        Column(
            Modifier
                .widthIn(max = 680.dp)
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "SYSCOM",
                color = SyscomBlueLight,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.mode_select_title),
                color = Cream,
                fontSize = if (compact) 27.sp else 34.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            ModePreviewCard(
                previewRes = R.drawable.mode_ai_styles,
                title = stringResource(R.string.mode_ai_title),
                subtitle = stringResource(R.string.mode_ai_subtitle),
                accent = SyscomBlueLight,
                onClick = onPickAi,
            )
            Spacer(Modifier.height(16.dp))
            ModePreviewCard(
                previewRes = R.drawable.mode_collage_classic,
                title = stringResource(R.string.mode_collage_title),
                subtitle = stringResource(R.string.mode_collage_subtitle),
                accent = SyscomBlue,
                onClick = onPickCollage,
            )

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onHome, modifier = Modifier.height(44.dp)) {
                Text(stringResource(R.string.home), color = Cream.copy(alpha = 0.8f), fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun ColumnScope.ModePreviewCard(
    @DrawableRes previewRes: Int,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = InkSoft,
    ) {
        Box {
            Image(
                painter = painterResource(previewRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Ink.copy(alpha = 0.94f)),
                        ),
                    )
                    .padding(20.dp),
            ) {
                Column {
                    Text(title, color = Cream, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, color = accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
