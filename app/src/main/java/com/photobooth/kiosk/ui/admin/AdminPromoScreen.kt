package com.photobooth.kiosk.ui.admin

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.ui.attract.ATTRACT_DELAY_MAX_MS
import com.photobooth.kiosk.ui.attract.ATTRACT_DELAY_MIN_MS
import com.photobooth.kiosk.ui.attract.ATTRACT_DELAY_STEP_MS
import com.photobooth.kiosk.ui.attract.ATTRACT_REPLAY_DELAY_MS
import com.photobooth.kiosk.ui.attract.AttractImportError
import com.photobooth.kiosk.ui.attract.AttractVideo
import com.photobooth.kiosk.ui.attract.AttractVideos
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomBlueLight
import com.photobooth.kiosk.ui.theme.SyscomTeal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Admin → Video promocional: pick which clip plays over the welcome screen and
 * how long the booth must stay untouched before it appears. New clips are
 * imported into app storage, so swapping the attract video never requires a
 * build.
 */
@Composable
internal fun AdminPromoScreen(
    videoId: String,
    idleMs: Long,
    onSave: (videoId: String, idleMs: Long) -> Unit,
    onSaved: () -> Unit,
    onImport: (Uri, String?, (AttractVideo?, AttractImportError?) -> Unit) -> Unit,
    onDelete: (String, (Boolean) -> Unit) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var draftVideoId by remember(videoId) { mutableStateOf(videoId) }
    var draftIdleMs by remember(idleMs) { mutableStateOf(idleMs) }
    var videos by remember { mutableStateOf<List<AttractVideo>>(emptyList()) }
    var thumbnails by remember { mutableStateOf<Map<String, android.graphics.Bitmap>>(emptyMap()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<AttractVideo?>(null) }

    LaunchedEffect(refreshKey) {
        val loaded = withContext(Dispatchers.IO) {
            val list = AttractVideos.list(context)
            list to list.mapNotNull { video ->
                AttractVideos.thumbnail(context, video.id)?.let { video.id to it }
            }.toMap()
        }
        videos = loaded.first
        thumbnails = loaded.second
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        onImport(uri, displayNameOf(context, uri)) { video, error ->
            importing = false
            if (video != null) {
                draftVideoId = video.id
                refreshKey++
                onMessage(context.getString(R.string.admin_promo_imported))
            } else {
                onMessage(
                    context.getString(
                        if (error == AttractImportError.TOO_LARGE) {
                            R.string.admin_promo_import_too_large
                        } else {
                            R.string.admin_promo_import_error
                        },
                    ),
                )
            }
        }
    }

    val dirty = draftVideoId != videoId || draftIdleMs != idleMs
    val delaySeconds = (draftIdleMs / 1000L).toInt()

    AdminSettingsScreen(
        dirty = dirty,
        onSave = {
            onSave(draftVideoId, draftIdleMs)
            onSaved()
        },
        onDiscard = {
            draftVideoId = videoId
            draftIdleMs = idleMs
        },
    ) {
        item {
            AdminCard(
                title = stringResource(R.string.admin_promo_videos),
                subtitle = stringResource(R.string.admin_promo_videos_hint),
            ) {
                if (videos.isEmpty()) {
                    Text(
                        text = stringResource(R.string.admin_promo_empty),
                        color = Cream.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                    )
                }
                videos.forEach { video ->
                    VideoRow(
                        video = video,
                        selected = video.id == draftVideoId,
                        thumbnail = thumbnails[video.id],
                        onSelect = { draftVideoId = video.id },
                        onDelete = { pendingDelete = video },
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { picker.launch(arrayOf("video/*")) },
                        enabled = !importing,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SyscomBlue,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.height(52.dp),
                    ) {
                        if (importing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.admin_promo_import))
                        }
                    }
                    Text(
                        text = stringResource(R.string.admin_promo_import_limit),
                        color = Cream.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                    )
                }
            }
        }

        item {
            AdminCard(
                title = stringResource(R.string.admin_promo_delay),
                subtitle = stringResource(R.string.admin_promo_delay_hint),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.admin_promo_seconds, delaySeconds),
                        color = SyscomBlueLight,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Slider(
                    value = delaySeconds.toFloat(),
                    onValueChange = { value ->
                        val stepped = (value / (ATTRACT_DELAY_STEP_MS / 1000f)).toInt() *
                            ATTRACT_DELAY_STEP_MS
                        draftIdleMs = stepped.coerceIn(ATTRACT_DELAY_MIN_MS, ATTRACT_DELAY_MAX_MS)
                    },
                    valueRange = ATTRACT_DELAY_MIN_MS / 1000f..ATTRACT_DELAY_MAX_MS / 1000f,
                    steps = ((ATTRACT_DELAY_MAX_MS - ATTRACT_DELAY_MIN_MS) /
                        ATTRACT_DELAY_STEP_MS).toInt() - 1,
                    colors = SliderDefaults.colors(
                        thumbColor = SyscomBlueLight,
                        activeTrackColor = SyscomBlue,
                        inactiveTrackColor = Cream.copy(alpha = 0.18f),
                    ),
                )
                Text(
                    text = stringResource(
                        R.string.admin_promo_replay_note,
                        (ATTRACT_REPLAY_DELAY_MS / 1000L).toInt(),
                    ),
                    color = Cream.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                )
            }
        }
    }

    pendingDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.admin_promo_delete_title)) },
            text = { Text(stringResource(R.string.admin_promo_delete_message, video.name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(video.id) { deleted ->
                        if (deleted) {
                            if (draftVideoId == video.id) draftVideoId = videoId
                            refreshKey++
                            onMessage(context.getString(R.string.admin_promo_deleted))
                        } else {
                            onMessage(context.getString(R.string.admin_promo_delete_error))
                        }
                    }
                }) {
                    Text(stringResource(R.string.admin_delete), color = Color(0xFFFF7A7A))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.admin_cancel))
                }
            },
        )
    }
}

@Composable
private fun VideoRow(
    video: AttractVideo,
    selected: Boolean,
    thumbnail: android.graphics.Bitmap?,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) SyscomBlue.copy(alpha = 0.14f) else Ink)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) SyscomBlue else Cream.copy(alpha = 0.08f),
                shape = shape,
            )
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 54.dp, height = 96.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Ink.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = thumbnail
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Cream.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = video.name,
                color = Cream,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.admin_promo_meta,
                    video.durationMs / 1000L,
                    video.sizeLabel,
                    stringResource(
                        if (video.imported) {
                            R.string.admin_promo_imported_tag
                        } else {
                            R.string.admin_promo_bundled_tag
                        },
                    ),
                ),
                color = Cream.copy(alpha = 0.58f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
            if (selected) {
                Text(
                    text = stringResource(R.string.admin_promo_in_use),
                    color = SyscomTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (video.imported) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.admin_promo_delete_title),
                    tint = Cream.copy(alpha = 0.45f),
                )
            }
        }
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = SyscomBlueLight,
                unselectedColor = Cream.copy(alpha = 0.4f),
            ),
        )
    }
}
