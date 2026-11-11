package com.photobooth.kiosk.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/**
 * ES | EN switch on the welcome screen. This is the only language control:
 * the choice is saved and used by the guest flow and by admin.
 */
@Composable
fun LanguageQuickToggle(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var english by remember { mutableStateOf(LanguageStore.isEnglish(context)) }

    LaunchedEffect(Unit) {
        if (LanguageStore.current(context) == AppLanguage.SYSTEM) {
            LanguageStore.set(context, if (english) AppLanguage.ENGLISH else AppLanguage.SPANISH)
        }
    }

    fun apply(language: AppLanguage) {
        if (LanguageStore.current(context) == language) return
        LanguageStore.set(context, language)
        english = language == AppLanguage.ENGLISH
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
            onClick = { apply(AppLanguage.SPANISH) },
        )
        QuickToggleOption(
            label = stringResource(R.string.language_short_english),
            selected = english,
            onClick = { apply(AppLanguage.ENGLISH) },
        )
    }
}

/** True when the booth is showing English, including an older "follow the device" choice. */
fun LanguageStore.isEnglish(context: Context): Boolean {
    return when (current(context)) {
        AppLanguage.ENGLISH -> true
        AppLanguage.SPANISH -> false
        AppLanguage.SYSTEM -> shownLanguage(context).startsWith("en")
    }
}

private fun shownLanguage(context: Context): String {
    val configuration = context.resources.configuration
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        configuration.locales[0].language
    } else {
        @Suppress("DEPRECATION")
        configuration.locale.language
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
