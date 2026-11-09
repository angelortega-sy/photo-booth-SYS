package com.photobooth.kiosk.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.photobooth.kiosk.data.BackgroundOption

/**
 * Built-in backgrounds carry their string resource so the label always follows
 * the app language and not the device locale used when the repository was built.
 * Custom imports have no resource and fall back to the name the admin typed.
 */
@Composable
fun backgroundLabel(option: BackgroundOption): String =
    option.nameRes?.let { stringResource(it) } ?: option.name
