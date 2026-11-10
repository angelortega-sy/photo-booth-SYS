package com.photobooth.kiosk.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomBlueLight
import com.photobooth.kiosk.ui.theme.SyscomTeal

/**
 * Set by the visible settings screen while it holds unsaved edits, so the shell
 * can ask before navigating away and losing them.
 */
internal val LocalAdminUnsavedChanges = staticCompositionLocalOf<MutableState<Boolean>> {
    mutableStateOf(false)
}

/** Standard scrolling body of every admin route: cards separated by 16 dp. */
@Composable
internal fun AdminScreenColumn(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/**
 * Settings route: edits stay in a local draft until "Save changes". The save bar
 * is always visible and only enabled while the draft differs from what is stored.
 */
@Composable
internal fun AdminSettingsScreen(
    dirty: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    saveEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val unsaved = LocalAdminUnsavedChanges.current
    SideEffect { unsaved.value = dirty }
    DisposableEffect(Unit) { onDispose { unsaved.value = false } }

    Column(Modifier.fillMaxSize()) {
        AdminScreenColumn(modifier = Modifier.weight(1f), content = content)
        AdminSaveBar(
            dirty = dirty,
            saveEnabled = saveEnabled,
            onSave = onSave,
            onDiscard = onDiscard,
        )
    }
}

@Composable
private fun AdminSaveBar(
    dirty: Boolean,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().background(Ink)) {
        HorizontalDivider(color = Cream.copy(alpha = 0.08f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(if (dirty) R.string.admin_unsaved_changes else R.string.admin_all_saved),
                color = if (dirty) SyscomTeal else Cream.copy(alpha = 0.5f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onDiscard,
                enabled = dirty,
                shape = shape,
                border = BorderStroke(1.dp, Cream.copy(alpha = if (dirty) 0.38f else 0.12f)),
                modifier = Modifier.height(52.dp),
            ) {
                Text(
                    stringResource(R.string.admin_discard_changes),
                    color = if (dirty) Cream else Cream.copy(alpha = 0.35f),
                )
            }
            Button(
                onClick = onSave,
                enabled = dirty && saveEnabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SyscomBlue,
                    contentColor = Color.White,
                    disabledContainerColor = SyscomBlue.copy(alpha = 0.22f),
                    disabledContentColor = SyscomBlueLight.copy(alpha = 0.5f),
                ),
                modifier = Modifier.height(52.dp),
            ) {
                Text(stringResource(R.string.admin_save_changes), fontWeight = FontWeight.Bold)
            }
        }
    }
}
