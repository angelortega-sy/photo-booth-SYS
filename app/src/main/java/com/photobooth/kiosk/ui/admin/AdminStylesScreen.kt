package com.photobooth.kiosk.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.AiStyles
import com.photobooth.kiosk.network.BoothStyle
import com.photobooth.kiosk.ui.theme.Cream

@Composable
internal fun AdminStylesScreen(
    serverStyles: List<BoothStyle>,
    disabledIds: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    // Admin style list: the local catalogue plus any name the server advertises.
    val rows = AiStyles.ALL.map { style ->
        style.id to (
            serverStyles.firstOrNull { it.id == style.id }?.name
                ?: context.getString(style.labelRes)
            )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 12.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StylesCard(
                rows = rows,
                disabledIds = disabledIds,
                onToggle = onToggle,
            )
        }
    }
}

@Composable
private fun StylesCard(
    rows: List<Pair<String, String>>,
    disabledIds: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    AdminCard(
        title = stringResource(R.string.admin_styles_title),
        subtitle = stringResource(R.string.admin_styles_subtitle),
    ) {
        if (rows.isEmpty()) {
            Text(
                text = stringResource(R.string.admin_styles_empty),
                color = Cream.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
        } else {
            Text(
                text = stringResource(
                    R.string.admin_styles_count,
                    rows.count { it.first !in disabledIds },
                    rows.size,
                ),
                color = Color(0xFF9AC2F4),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            rows.forEach { (id, name) ->
                val enabled = id !in disabledIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(role = Role.Switch) { onToggle(id, !enabled) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        color = if (enabled) Cream else Cream.copy(alpha = 0.45f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = enabled, onCheckedChange = { onToggle(id, it) })
                }
            }
            Text(
                text = stringResource(R.string.admin_styles_hint),
                color = Cream.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
