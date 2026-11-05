package com.photobooth.kiosk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val titleSize = if (compact) 30.sp else 40.sp
    val cardMinHeight = if (compact) 132.dp else 168.dp

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
                "Elige tu experiencia",
                color = Cream,
                fontSize = titleSize,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = (titleSize.value + 6).sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Una sola foto con IA o tira clásica de 3 fotos.",
                color = Cream.copy(alpha = 0.7f),
                fontSize = if (compact) 15.sp else 18.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (compact) 24.dp else 36.dp))

            ModeCard(
                title = "Foto IA",
                subtitle = "1 foto · estilo + fondo + accesorios",
                badge = "Recomendado",
                minHeight = cardMinHeight,
                onClick = onPickAi,
                icon = {
                    ModeIconBadge(icon = { Icon(Icons.Rounded.AutoAwesome, null, tint = Cream, modifier = Modifier.size(34.dp)) })
                },
            )
            Spacer(Modifier.height(16.dp))
            ModeCard(
                title = "Collage clásico",
                subtitle = "3 fotos · fondos locales, sin IA",
                badge = null,
                minHeight = cardMinHeight,
                onClick = onPickCollage,
                icon = {
                    ModeIconBadge(icon = { Icon(Icons.Rounded.Collections, null, tint = Cream, modifier = Modifier.size(34.dp)) })
                },
            )

            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onHome, modifier = Modifier.height(56.dp)) {
                Text("Volver al inicio", color = Cream.copy(alpha = 0.85f), fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun ModeIconBadge(icon: @Composable () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(SyscomBlue.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    badge: String?,
    minHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(minHeight)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = InkSoft,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                if (badge != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(SyscomBlue),
                    ) {
                        Text(
                            badge,
                            color = Cream,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(title, color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Cream.copy(alpha = 0.72f), fontSize = 15.sp, lineHeight = 20.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = SyscomBlueLight, modifier = Modifier.size(32.dp))
        }
    }
}
