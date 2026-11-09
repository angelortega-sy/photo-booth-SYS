package com.photobooth.kiosk.ui.admin

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.admin.NetworkAddress
import com.photobooth.kiosk.ui.theme.Cream

@Composable
internal fun AdminNetworkScreen(
    networkType: String,
    addresses: List<NetworkAddress>,
    onOpenSystem: () -> Unit,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 12.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            NetworkCard(
                networkType = networkType,
                addresses = addresses,
                onOpenNetworkSettings = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                    }
                },
                onOpenHotspotSettings = {
                    val intent = Intent("android.settings.WIFI_HOTSPOT_SETTINGS")
                    val opened = runCatching { context.startActivity(intent) }.isSuccess
                    if (!opened) {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                        }
                    }
                },
            )
        }
        item {
            OutlinedButton(
                onClick = onOpenSystem,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.admin_tab_system))
            }
        }
    }
}

@Composable
private fun NetworkCard(
    networkType: String,
    addresses: List<NetworkAddress>,
    onOpenNetworkSettings: () -> Unit,
    onOpenHotspotSettings: () -> Unit,
) {
    AdminCard(
        title = stringResource(R.string.admin_section_network),
        subtitle = stringResource(R.string.admin_section_network_hint),
    ) {
        InfoRow("Conexión", networkType)
        if (addresses.isEmpty()) {
            Text(
                text = "Sin IP local. Conecta el kiosko a Wi-Fi o Ethernet.",
                color = androidx.compose.ui.graphics.Color(0xFFFFB86B),
                fontSize = 13.sp,
            )
        } else {
            addresses.forEach { address ->
                InfoRow(address.interfaceName, address.address)
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onOpenNetworkSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.admin_open_network),
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            OutlinedButton(
                onClick = onOpenHotspotSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.admin_open_hotspot),
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "El QR de descarga usará una de estas direcciones. La descarga por hotspot se integra en la siguiente fase.",
            color = Cream.copy(alpha = 0.45f),
            fontSize = 12.sp,
        )
    }
}
