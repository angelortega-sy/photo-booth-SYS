package com.photobooth.kiosk.ui.admin

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.photobooth.kiosk.R
import com.photobooth.kiosk.admin.CameraOption
import com.photobooth.kiosk.admin.DeviceSummary
import com.photobooth.kiosk.admin.NetworkAddress
import com.photobooth.kiosk.admin.SavedPhoto
import com.photobooth.kiosk.admin.UsbDeviceOption
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.InkSoft

internal fun photoPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

internal fun hasPhotoPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, photoPermission()) == PackageManager.PERMISSION_GRANTED

internal data class AdminData(
    val photos: List<SavedPhoto>,
    val cameras: List<CameraOption>,
    val usbDevices: List<UsbDeviceOption>,
    val addresses: List<NetworkAddress>,
    val networkType: String,
    val summary: DeviceSummary?,
)

@Composable
internal fun AdminHeader(
    title: String,
    subtitle: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.admin_back),
                    tint = Cream,
                )
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Cream,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = Cream.copy(alpha = 0.62f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.admin_close),
                tint = Cream.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
internal fun AdminCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(InkSoft)
            .border(1.dp, Cream.copy(alpha = 0.08f), shape)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = title,
            color = Cream,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = Cream.copy(alpha = 0.55f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(modifier = Modifier.padding(top = 14.dp))
        content()
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = Cream.copy(alpha = 0.52f),
            fontSize = 14.sp,
            modifier = Modifier.widthIn(min = 92.dp, max = 160.dp),
        )
        Spacer(modifier = Modifier.padding(end = 12.dp))
        Text(
            text = value,
            color = Cream,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun BrandingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = minLines == 1,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

internal fun formatPhotoDate(epochSeconds: Long): String =
    java.text.SimpleDateFormat("dd MMM · HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(epochSeconds * 1000))

@Composable
internal fun AdminRouteTitle(route: AdminRoute, editorProfileName: String?): Pair<String, String> {
    return when (route) {
        AdminRoute.Home -> stringResource(R.string.admin_title) to
            stringResource(R.string.admin_subtitle)
        AdminRoute.Brands -> stringResource(R.string.admin_profiles_title) to
            stringResource(R.string.admin_profiles_subtitle)
        is AdminRoute.BrandEditor -> stringResource(R.string.admin_brand_editor_title) to
            (editorProfileName ?: "")
        AdminRoute.Event -> stringResource(R.string.admin_tab_event) to
            stringResource(R.string.admin_nav_event_description)
        AdminRoute.Camera -> stringResource(R.string.admin_section_camera) to
            stringResource(R.string.admin_section_camera_hint)
        AdminRoute.Styles -> stringResource(R.string.admin_tab_styles) to
            stringResource(R.string.admin_nav_styles_description)
        AdminRoute.Photos -> stringResource(R.string.admin_section_photos) to
            stringResource(R.string.admin_nav_photos_description)
        AdminRoute.Usage -> stringResource(R.string.admin_home_usage_ai) to
            stringResource(R.string.admin_nav_usage_description)
        AdminRoute.Network -> stringResource(R.string.admin_home_network_system) to
            stringResource(R.string.admin_nav_network_description)
        AdminRoute.System -> stringResource(R.string.admin_tab_system) to
            stringResource(R.string.admin_nav_system_description)
    }
}
