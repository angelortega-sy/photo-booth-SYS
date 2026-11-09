package com.photobooth.kiosk.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.ui.theme.Cream

/** Palette offered as one-tap presets. Any other value can be typed as hex. */
private val COLOR_PRESETS = listOf(
    0xFF050B14L,
    0xFF0B1727L,
    0xFF0B63CEL,
    0xFF5EA8FFL,
    0xFF00E5FFL,
    0xFF7C3AEDL,
    0xFFE11D48L,
    0xFFF59E0BL,
    0xFF10B981L,
    0xFFE5B84BL,
    0xFFF3F8FFL,
    0xFF111827L,
)

/**
 * Color picker for the brand palette: a live swatch, a hex field and a row of
 * one-tap presets. Kept dependency-free on purpose (the kiosk has no color lib).
 */
@Composable
internal fun BrandColorField(
    label: String,
    color: Long,
    onColorChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(color) { mutableStateOf(formatHexColor(color)) }

    Column(modifier = modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            text = label,
            color = Cream.copy(alpha = 0.72f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(color))
                    .border(1.dp, Cream.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { raw ->
                    text = raw
                    parseHexColor(raw)?.let(onColorChange)
                },
                label = { Text("#RRGGBB") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            COLOR_PRESETS.forEach { preset ->
                val selected = preset == color
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(preset))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) Cream else Cream.copy(alpha = 0.16f),
                            shape = CircleShape,
                        )
                        .clickable { onColorChange(preset) },
                )
            }
        }
    }
}

internal fun formatHexColor(color: Long): String =
    "#%06X".format(color and 0xFFFFFF)

/** Accepts `#RRGGBB`, `RRGGBB`, `#AARRGGBB` and `AARRGGBB`. */
internal fun parseHexColor(raw: String): Long? {
    val cleaned = raw.trim().removePrefix("#")
    if (cleaned.length != 6 && cleaned.length != 8) return null
    if (cleaned.any { it.digitToIntOrNull(16) == null }) return null
    val value = cleaned.toLong(16)
    return if (cleaned.length == 6) value or 0xFF000000L else value
}
