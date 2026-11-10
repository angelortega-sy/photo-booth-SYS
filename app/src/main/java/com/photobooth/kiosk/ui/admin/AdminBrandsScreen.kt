package com.photobooth.kiosk.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.EventProfile
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomBlueLight

@Composable
internal fun AdminBrandsScreen(
    profiles: List<EventProfile>,
    activeProfileId: String,
    onToggleEnabled: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: (String) -> Unit,
    onActivate: (String) -> Unit,
    onOpenEditor: (String) -> Unit,
) {
    var newBrandName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<EventProfile?>(null) }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.admin_profiles_delete_title)) },
            text = {
                Text(stringResource(R.string.admin_profiles_delete_body, profile.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(profile.id)
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.admin_profiles_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.admin_profiles_cancel))
                }
            },
        )
    }

    AdminScreenColumn {
        item {
            AdminCard(
                title = stringResource(R.string.admin_profiles_title),
                subtitle = stringResource(R.string.admin_profiles_subtitle),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profiles.forEach { profile ->
                        val isActive = profile.id == activeProfileId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isActive) SyscomBlue.copy(alpha = 0.18f) else Ink)
                                .clickable(role = Role.Button) { onOpenEditor(profile.id) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        profile.name,
                                        color = Cream,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (isActive) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.admin_brand_active_badge),
                                            color = Ink,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(SyscomBlueLight)
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                Text(
                                    stringResource(
                                        if (isActive) {
                                            R.string.admin_profiles_active
                                        } else {
                                            R.string.admin_profiles_tap_to_activate
                                        },
                                    ),
                                    color = Cream.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                )
                            }
                            if (!isActive) {
                                OutlinedButton(
                                    onClick = { onActivate(profile.id) },
                                    modifier = Modifier.height(34.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 12.dp,
                                        vertical = 0.dp,
                                    ),
                                ) {
                                    Text(
                                        stringResource(R.string.admin_profiles_make_active),
                                        fontSize = 12.sp,
                                        color = SyscomBlueLight,
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            if (profile.builtIn) {
                                Text(
                                    "SYSCOM",
                                    color = SyscomBlueLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            } else {
                                Switch(
                                    checked = profile.enabled,
                                    onCheckedChange = { onToggleEnabled(profile.id, it) },
                                )
                                IconButton(onClick = { pendingDelete = profile }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = stringResource(R.string.admin_profiles_delete_confirm),
                                        tint = Cream.copy(alpha = 0.7f),
                                    )
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = Cream.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newBrandName,
                        onValueChange = { newBrandName = it },
                        label = { Text(stringResource(R.string.admin_profiles_new_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = newBrandName.isNotBlank(),
                        onClick = {
                            onAdd(newBrandName)
                            newBrandName = ""
                        },
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.admin_profiles_add),
                            tint = SyscomBlueLight,
                        )
                    }
                }
            }
        }
    }
}
