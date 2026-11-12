package com.photobooth.kiosk.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.EventProfile
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomBlueLight

/** Same width for every activation control so the rows line up. */
private val ActivationSlotWidth = 150.dp
private val ActivationSlotHeight = 34.dp

/** Switch plus delete icon fit here; built-in rows show a lock in the same space. */
private val ControlSlotWidth = 104.dp

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
            AdminCard(title = stringResource(R.string.admin_profiles_list_title)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profiles.forEach { profile ->
                        EventRow(
                            profile = profile,
                            isActive = profile.id == activeProfileId,
                            onOpenEditor = { onOpenEditor(profile.id) },
                            onActivate = { onActivate(profile.id) },
                            onToggleEnabled = { onToggleEnabled(profile.id, it) },
                            onDeleteRequested = { pendingDelete = profile },
                        )
                    }
                }
            }
        }
        item {
            AdminCard(title = stringResource(R.string.admin_profiles_add)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun EventRow(
    profile: EventProfile,
    isActive: Boolean,
    onOpenEditor: () -> Unit,
    onActivate: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDeleteRequested: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isActive) SyscomBlue.copy(alpha = 0.18f) else Ink)
            .clickable(role = Role.Button, onClick = onOpenEditor)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = profile.name,
                color = Cream,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    if (isActive) R.string.admin_profiles_active else R.string.admin_profiles_tap_to_activate,
                ),
                color = Cream.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        // Fixed slot: the active event shows a blue chip, the others an outlined button.
        Box(
            modifier = Modifier.width(ActivationSlotWidth).height(ActivationSlotHeight),
            contentAlignment = Alignment.Center,
        ) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ActivationSlotHeight)
                        .clip(RoundedCornerShape(999.dp))
                        .background(SyscomBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.admin_profiles_active_chip),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onActivate,
                    modifier = Modifier.fillMaxWidth().height(ActivationSlotHeight),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = stringResource(R.string.admin_profiles_make_active),
                        fontSize = 12.sp,
                        color = SyscomBlueLight,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        // Fixed slot: switch and delete for custom events, a lock for SYSCOM.
        Box(
            modifier = Modifier.width(ControlSlotWidth),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (profile.builtIn) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = stringResource(R.string.admin_profiles_builtin_locked),
                        tint = Cream.copy(alpha = 0.4f),
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = profile.enabled,
                        onCheckedChange = onToggleEnabled,
                    )
                    IconButton(onClick = onDeleteRequested) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.admin_profiles_delete_confirm),
                            tint = Cream.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = Cream.copy(alpha = 0.4f),
        )
    }
}
