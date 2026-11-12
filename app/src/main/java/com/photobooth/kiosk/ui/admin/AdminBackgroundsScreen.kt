package com.photobooth.kiosk.ui.admin

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoLibrary
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.BackgroundOption
import com.photobooth.kiosk.data.BackgroundRepository
import com.photobooth.kiosk.ui.backgroundLabel
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fondos: the backgrounds the guest can pick in AI mode. "Sin fondo" is always
 * there and cannot be turned off; the bundled and imported backgrounds have a
 * switch. The route title already names the screen, so the cards do not repeat it.
 */
@Composable
internal fun AdminBackgroundsScreen(
    backgrounds: List<BackgroundOption>,
    loadThumbnail: (String) -> Bitmap?,
    onImport: (Uri, String?) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
) {
    val context = LocalContext.current
    var renameTarget by remember { mutableStateOf<BackgroundOption?>(null) }
    var renameText by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { uri -> onImport(uri, displayNameOf(context, uri)) }
    }

    AdminScreenColumn {
        item {
            AdminCard(
                title = stringResource(R.string.admin_background_add_title),
            ) {
                Text(
                    text = stringResource(R.string.admin_background_add_hint),
                    color = Cream.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                )
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.admin_background_upload))
                }
            }
        }
        item {
            AdminCard(title = stringResource(R.string.admin_background_list_title)) {
                NoBackgroundRow()
                backgrounds
                    .filter { it.id != BackgroundRepository.ORIGINAL_ID }
                    .forEach { option ->
                        // Resolve the label in composition: backgroundLabel reads string resources.
                        val label = backgroundLabel(option)
                        BackgroundAdminRow(
                            option = option,
                            loadThumbnail = loadThumbnail,
                            onToggle = { enabled -> onToggle(option.id, enabled) },
                            onDelete = { onDelete(option.id) },
                            onRename = {
                                renameTarget = option
                                renameText = label
                            },
                        )
                    }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.admin_background_rename)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(target.id, renameText)
                        renameTarget = null
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.admin_cancel))
                }
            },
        )
    }
}

/** Always-on row for "Sin fondo": it has no switch because the guest always has it. */
@Composable
private fun NoBackgroundRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(8.dp))
                .background(Ink),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoLibrary,
                contentDescription = null,
                tint = Cream.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.ai_background_none),
                color = Cream,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.admin_background_none_hint),
                color = Cream.copy(alpha = 0.45f),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun BackgroundAdminRow(
    option: BackgroundOption,
    loadThumbnail: (String) -> Bitmap?,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
) {
    val thumbnail by produceState<Bitmap?>(initialValue = null, option.id) {
        value = withContext(Dispatchers.IO) { loadThumbnail(option.id) }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(8.dp))
                .background(Ink),
            contentAlignment = Alignment.Center,
        ) {
            val image = thumbnail
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PhotoLibrary,
                    contentDescription = null,
                    tint = Cream.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = backgroundLabel(option),
                color = Cream,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    if (option.isBuiltIn) {
                        R.string.admin_background_builtin
                    } else {
                        R.string.admin_background_custom
                    },
                ),
                color = Cream.copy(alpha = 0.45f),
                fontSize = 11.sp,
            )
        }
        if (!option.isBuiltIn) {
            IconButton(onClick = onRename) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.admin_background_rename),
                    tint = Cream.copy(alpha = 0.65f),
                )
            }
        }
        Switch(checked = option.enabled, onCheckedChange = onToggle)
        if (!option.isBuiltIn) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.admin_background_delete),
                    tint = Color(0xFFFF7A7A),
                )
            }
        }
    }
}
