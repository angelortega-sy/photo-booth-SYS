package com.photobooth.kiosk.ui.admin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.ui.theme.Cream

@Composable
internal fun AdminSystemScreen(
    onChangePin: () -> Unit,
) {
    AdminScreenColumn {
        item {
            AdminCard(
                title = stringResource(R.string.admin_security_title),
                subtitle = stringResource(R.string.admin_security_subtitle),
            ) {
                InfoRow("PIN", stringResource(R.string.admin_security_pin_state))
                Text(
                    text = stringResource(R.string.admin_security_hint),
                    color = Cream.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedButton(
                    onClick = onChangePin,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text(stringResource(R.string.admin_change_pin))
                }
            }
        }
    }
}
