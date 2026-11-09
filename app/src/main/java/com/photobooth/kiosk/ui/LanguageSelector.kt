package com.photobooth.kiosk.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.AppLanguage
import com.photobooth.kiosk.data.LanguageStore
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.InkSoft
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomBlueLight

/**
 * Three-chip language picker for the admin System screen.
 * Persists the choice and recreates the host activity.
 */
@Composable
fun LanguageSelector(
    language: AppLanguage,
    onPick: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanguageChip(
            label = stringResource(R.string.language_system),
            selected = language == AppLanguage.SYSTEM,
            onClick = { onPick(AppLanguage.SYSTEM) },
        )
        LanguageChip(
            label = stringResource(R.string.language_spanish),
            selected = language == AppLanguage.SPANISH,
            onClick = { onPick(AppLanguage.SPANISH) },
        )
        LanguageChip(
            label = stringResource(R.string.language_english),
            selected = language == AppLanguage.ENGLISH,
            onClick = { onPick(AppLanguage.ENGLISH) },
        )
    }
}

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) SyscomBlue else InkSoft)
            .border(
                1.dp,
                if (selected) SyscomBlueLight else Cream.copy(alpha = 0.18f),
                RoundedCornerShape(16.dp),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Cream,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/**
 * Discreet two-state ES | EN toggle for the welcome screen.
 * Guests can switch language without entering admin.
 */
@Composable
fun LanguageQuickToggle(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(LanguageStore.current(context)) }
    val english = current == AppLanguage.ENGLISH

    fun apply(language: AppLanguage) {
        LanguageStore.set(context, language)
        current = language
        (context as? Activity)?.recreate()
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(InkSoft.copy(alpha = 0.85f))
            .border(1.dp, Cream.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickToggleOption(
            label = stringResource(R.string.language_short_spanish),
            selected = !english,
            onClick = { if (english) apply(AppLanguage.SPANISH) },
        )
        QuickToggleOption(
            label = stringResource(R.string.language_short_english),
            selected = english,
            onClick = { if (!english) apply(AppLanguage.ENGLISH) },
        )
    }
}

@Composable
private fun QuickToggleOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) SyscomBlue else Cream.copy(alpha = 0.0f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Cream else Cream.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
