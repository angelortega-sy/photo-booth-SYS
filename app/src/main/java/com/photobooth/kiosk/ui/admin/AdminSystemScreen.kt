package com.photobooth.kiosk.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.LanguageStore
import com.photobooth.kiosk.ui.LanguageSelector
import com.photobooth.kiosk.ui.theme.Cream

@Composable
internal fun AdminSystemScreen(
    onChangePin: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 12.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
        item {
            val context = LocalContext.current
            var language by remember { mutableStateOf(LanguageStore.current(context)) }
            AdminCard(
                title = stringResource(R.string.language_title),
                subtitle = stringResource(R.string.language_subtitle),
            ) {
                LanguageSelector(
                    language = language,
                    onPick = { picked ->
                        LanguageStore.set(context, picked)
                        language = picked
                        (context as? Activity)?.recreate()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

