package com.photobooth.kiosk.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.InkSoft
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomBlueLight

private data class HomeCard(
    val route: AdminRoute,
    val titleRes: Int,
    val summary: String,
    val icon: ImageVector,
)

@Composable
internal fun AdminHomeScreen(
    compact: Boolean,
    brandCount: Int,
    activeBrandName: String,
    backgroundTotal: Int,
    backgroundVisible: Int,
    cameraCount: Int,
    usbCount: Int,
    styleVisible: Int,
    styleTotal: Int,
    photoCount: Int,
    networkType: String,
    ipAddress: String,
    modelLabel: String,
    appVersion: String,
    onNavigate: (AdminRoute) -> Unit,
) {
    val cards = listOf(
        HomeCard(
            route = AdminRoute.Brands,
            titleRes = R.string.admin_home_brands,
            summary = stringResource(
                R.string.admin_home_brands_summary,
                brandCount,
                activeBrandName,
            ),
            icon = Icons.Rounded.Settings,
        ),
        HomeCard(
            route = AdminRoute.Event,
            titleRes = R.string.admin_home_backgrounds,
            summary = stringResource(
                R.string.admin_home_backgrounds_summary,
                backgroundTotal,
                backgroundVisible,
            ),
            icon = Icons.Rounded.PhotoLibrary,
        ),
        HomeCard(
            route = AdminRoute.Camera,
            titleRes = R.string.admin_home_camera,
            summary = stringResource(
                R.string.admin_home_camera_summary,
                cameraCount,
                usbCount,
            ),
            icon = Icons.Rounded.CameraAlt,
        ),
        HomeCard(
            route = AdminRoute.Styles,
            titleRes = R.string.admin_home_styles,
            summary = stringResource(
                R.string.admin_home_styles_summary,
                styleVisible,
                styleTotal,
            ),
            icon = Icons.Rounded.Palette,
        ),
        HomeCard(
            route = AdminRoute.Photos,
            titleRes = R.string.admin_home_photos,
            summary = stringResource(R.string.admin_home_photos_summary, photoCount),
            icon = Icons.Rounded.PhotoLibrary,
        ),
        HomeCard(
            route = AdminRoute.Usage,
            titleRes = R.string.admin_home_usage_ai,
            summary = stringResource(R.string.admin_home_usage_summary, modelLabel),
            icon = Icons.Rounded.AttachMoney,
        ),
        HomeCard(
            route = AdminRoute.Network,
            titleRes = R.string.admin_home_network_system,
            summary = stringResource(R.string.admin_home_network_summary, networkType, ipAddress),
            icon = Icons.Rounded.Wifi,
        ),
    )

    AdminScreenColumn {
        item {
            Text(
                text = stringResource(R.string.admin_settings_group),
                color = Cream.copy(alpha = 0.68f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        val columns = if (compact) 1 else 2
        items(cards.chunked(columns)) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEach { card ->
                    AdminHomeCard(
                        title = stringResource(card.titleRes),
                        summary = card.summary,
                        icon = card.icon,
                        onClick = { onNavigate(card.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        item {
            Text(
                text = "Modo administrador · $appVersion",
                color = Cream.copy(alpha = 0.32f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun AdminHomeCard(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = 110.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(InkSoft)
            .border(1.dp, Cream.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(SyscomBlue.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SyscomBlueLight,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Cream,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                color = Cream.copy(alpha = 0.62f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = Cream.copy(alpha = 0.52f),
            modifier = Modifier.size(22.dp),
        )
    }
}
