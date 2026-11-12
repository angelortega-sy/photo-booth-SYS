package com.photobooth.kiosk.ui.admin

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.AiStyles
import com.photobooth.kiosk.network.BoothStyle
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.InkSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Each tile needs at least this width; the grid fits as many columns as the screen allows. */
private val StyleTileMinWidth = 170.dp
private val StylePreviewHeight = 200.dp

private data class StyleRow(val id: String, val name: String)

/**
 * Estilos de IA: the same previews the guest sees in AI mode, one switch per
 * style. Changes stay in a draft until "Save changes".
 */
@Composable
internal fun AdminStylesScreen(
    serverStyles: List<BoothStyle>,
    disabledIds: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    var draftDisabled by remember(disabledIds) { mutableStateOf(disabledIds) }
    // Admin style list: the local catalogue plus any name the server advertises.
    val rows = AiStyles.ALL.map { style ->
        StyleRow(
            id = style.id,
            name = serverStyles.firstOrNull { it.id == style.id }?.name
                ?: context.getString(style.labelRes),
        )
    }

    AdminSettingsScreen(
        dirty = draftDisabled != disabledIds,
        onSave = {
            rows.forEach { row ->
                val enabled = row.id !in draftDisabled
                if (enabled != (row.id !in disabledIds)) onToggle(row.id, enabled)
            }
            onSaved()
        },
        onDiscard = { draftDisabled = disabledIds },
    ) {
        item {
            StylesGrid(
                rows = rows,
                disabledIds = draftDisabled,
                onToggle = { id, enabled ->
                    draftDisabled = if (enabled) draftDisabled - id else draftDisabled + id
                },
            )
        }
    }
}

@Composable
private fun StylesGrid(
    rows: List<StyleRow>,
    disabledIds: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    val visible = rows.count { it.id !in disabledIds }
    AdminCard(title = stringResource(R.string.admin_styles_count, visible, rows.size)) {
        if (rows.isEmpty()) {
            Text(
                text = stringResource(R.string.admin_styles_empty),
                color = Cream.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
            return@AdminCard
        }
        Text(
            text = stringResource(R.string.admin_styles_hint),
            color = Cream.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = (maxWidth / StyleTileMinWidth).toInt().coerceIn(2, 4)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.chunked(columns).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        line.forEach { row ->
                            StyleTile(
                                name = row.name,
                                styleId = row.id,
                                enabled = row.id !in disabledIds,
                                onToggle = { onToggle(row.id, it) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Keeps the last, shorter line at the same tile width.
                        repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleTile(
    name: String,
    styleId: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(InkSoft)
            .border(1.dp, Cream.copy(alpha = 0.08f), shape)
            .padding(8.dp),
    ) {
        StylePreview(
            styleId = styleId,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(StylePreviewHeight),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                color = if (enabled) Cream else Cream.copy(alpha = 0.45f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

/** The guest's own preview image, shown whole (Fit) and dimmed while the style is off. */
@Composable
private fun StylePreview(styleId: String, enabled: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val preview by produceState<ImageBitmap?>(initialValue = null, styleId) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("style_previews/$styleId.webp").use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(Ink),
        contentAlignment = Alignment.Center,
    ) {
        preview?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().alpha(if (enabled) 1f else 0.35f),
            )
        }
    }
}
