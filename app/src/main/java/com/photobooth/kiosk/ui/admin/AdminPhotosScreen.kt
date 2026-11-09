package com.photobooth.kiosk.ui.admin

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.photobooth.kiosk.admin.PhotoLibrary
import com.photobooth.kiosk.admin.SavedPhoto
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.InkSoft
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomBlueLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AdminPhotosScreen(
    photos: List<SavedPhoto>,
    photoPermissionGranted: Boolean,
    compact: Boolean,
    saveToDevice: Boolean,
    onSaveToDeviceChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoToDelete by remember { mutableStateOf<SavedPhoto?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }

    fun deletePhoto(photo: SavedPhoto) {
        scope.launch {
            val deleted = withContext(Dispatchers.IO) {
                PhotoLibrary.delete(context, photo.uri)
            }
            photoToDelete = null
            onRefresh()
            onMessage(
                if (deleted) {
                    context.getString(R.string.admin_photo_deleted)
                } else {
                    context.getString(R.string.admin_delete_error)
                },
            )
        }
    }

    fun deleteAllPhotos() {
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                PhotoLibrary.deleteAll(context)
            }
            showDeleteAll = false
            onRefresh()
            onMessage(
                context.getString(R.string.admin_photos_deleted, count),
            )
        }
    }

    val photoColumns = if (compact) 2 else 4

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
                title = stringResource(R.string.admin_photos_save_title),
                subtitle = stringResource(R.string.admin_photos_save_hint),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.admin_photos_save_label),
                        color = Cream,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = saveToDevice,
                        onCheckedChange = onSaveToDeviceChange,
                    )
                }
            }
        }
        item {
            PhotosHeader(
                photoCount = photos.size,
                permissionGranted = photoPermissionGranted,
                compact = compact,
                onRefresh = onRefresh,
                onRequestPermission = onRequestPermission,
                onDeleteAll = { showDeleteAll = true },
            )
        }
        if (!photoPermissionGranted) {
            item {
                AdminCard(
                    title = stringResource(R.string.admin_section_photos),
                    subtitle = stringResource(R.string.admin_permission_photos),
                ) {
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SyscomBlue,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(stringResource(R.string.admin_permission_photos))
                    }
                }
            }
        } else if (photos.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.admin_no_photos),
                    color = Cream.copy(alpha = 0.55f),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            items(photos.chunked(photoColumns)) { rowPhotos ->
                PhotoRow(
                    photos = rowPhotos,
                    columns = photoColumns,
                    onDelete = { photoToDelete = it },
                )
            }
        }
    }

    photoToDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title = { Text(stringResource(R.string.admin_delete_photo_title)) },
            text = { Text(stringResource(R.string.admin_delete_photo_message)) },
            confirmButton = {
                TextButton(onClick = { deletePhoto(photo) }) {
                    Text(stringResource(R.string.admin_delete), color = Color(0xFFFF7A7A))
                }
            },
            dismissButton = {
                TextButton(onClick = { photoToDelete = null }) {
                    Text(stringResource(R.string.admin_cancel))
                }
            },
        )
    }

    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text(stringResource(R.string.admin_delete_all_title)) },
            text = { Text(stringResource(R.string.admin_delete_all_message)) },
            confirmButton = {
                TextButton(onClick = ::deleteAllPhotos) {
                    Text(stringResource(R.string.admin_delete_all), color = Color(0xFFFF7A7A))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }) {
                    Text(stringResource(R.string.admin_cancel))
                }
            },
        )
    }
}

@Composable
private fun PhotosHeader(
    photoCount: Int,
    permissionGranted: Boolean,
    compact: Boolean,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Text(
            text = if (permissionGranted) {
                "$photoCount foto(s) en la galería"
            } else {
                stringResource(R.string.admin_permission_photos)
            },
            color = Cream.copy(alpha = 0.68f),
            fontSize = 14.sp,
        )
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!permissionGranted) {
                    TextButton(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.admin_permission_photos), color = SyscomBlueLight)
                    }
                } else {
                    TextButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = Cream.copy(alpha = 0.70f),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.admin_refresh), color = Cream.copy(alpha = 0.70f))
                    }
                    TextButton(
                        onClick = onDeleteAll,
                        enabled = photoCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.admin_delete_all), color = Color(0xFFFF7A7A))
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!permissionGranted) {
                    TextButton(onClick = onRequestPermission) {
                        Text(stringResource(R.string.admin_permission_photos), color = SyscomBlueLight)
                    }
                } else {
                    TextButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = Cream.copy(alpha = 0.70f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.admin_refresh), color = Cream.copy(alpha = 0.70f))
                    }
                    TextButton(onClick = onDeleteAll, enabled = photoCount > 0) {
                        Text(stringResource(R.string.admin_delete_all), color = Color(0xFFFF7A7A))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoRow(
    photos: List<SavedPhoto>,
    columns: Int,
    onDelete: (SavedPhoto) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        photos.forEach { photo ->
            PhotoCard(
                photo = photo,
                onDelete = onDelete,
                modifier = Modifier.weight(1f),
            )
        }
        repeat(columns - photos.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PhotoCard(
    photo: SavedPhoto,
    onDelete: (SavedPhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, photo.uri) {
        value = withContext(Dispatchers.IO) {
            PhotoLibrary.thumbnail(context, photo.uri)
        }
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(14.dp))
                .background(InkSoft),
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = photo.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PhotoLibrary,
                    contentDescription = null,
                    tint = Cream.copy(alpha = 0.35f),
                    modifier = Modifier
                        .size(34.dp)
                        .align(Alignment.Center),
                )
            }
            IconButton(
                onClick = { onDelete(photo) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(Ink.copy(alpha = 0.72f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.admin_delete),
                    tint = Color(0xFFFF8A8A),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = formatPhotoDate(photo.dateAdded),
            color = Cream.copy(alpha = 0.48f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}
