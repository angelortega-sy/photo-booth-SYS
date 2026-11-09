package com.photobooth.kiosk.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.EventBranding
import com.photobooth.kiosk.data.EventProfile
import com.photobooth.kiosk.ui.theme.SyscomBlue

/**
 * Edits the name and the 8 [EventBranding] label fields of one event profile.
 * Opening the editor also activates the profile, so tapping a brand row keeps
 * the previous "tap to activate" behavior and [onSaveBranding] (which writes
 * the active profile, like before) always targets the profile being edited.
 */
@Composable
internal fun AdminBrandEditorScreen(
    profileId: String,
    profiles: List<EventProfile>,
    onEnsureActive: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onSaveBranding: (EventBranding) -> Unit,
    onSaved: () -> Unit,
) {
    val profile = profiles.firstOrNull { it.id == profileId }
    if (profile == null) return

    LaunchedEffect(profileId) { onEnsureActive(profileId) }

    var name by remember(profileId) { mutableStateOf(profile.name) }
    LaunchedEffect(profile.name) { name = profile.name }
    var draft by remember(profileId) { mutableStateOf(profile.branding) }
    LaunchedEffect(profile.branding) { draft = profile.branding }

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
                title = profile.name,
                subtitle = stringResource(R.string.admin_profiles_subtitle),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.admin_brand_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                BrandingField(
                    label = stringResource(R.string.admin_event_brand),
                    value = draft.brand,
                    onValueChange = { draft = draft.copy(brand = it) },
                )
                BrandingField(
                    label = stringResource(R.string.admin_event_tagline),
                    value = draft.tagline,
                    onValueChange = { draft = draft.copy(tagline = it) },
                )
                BrandingField(
                    label = stringResource(R.string.admin_event_welcome_title),
                    value = draft.welcomeTitle,
                    onValueChange = { draft = draft.copy(welcomeTitle = it) },
                    minLines = 2,
                )
                BrandingField(
                    label = stringResource(R.string.admin_event_welcome_subtitle),
                    value = draft.welcomeSubtitle,
                    onValueChange = { draft = draft.copy(welcomeSubtitle = it) },
                    minLines = 2,
                )
                BrandingField(
                    label = stringResource(R.string.admin_event_badge_headline),
                    value = draft.badgeHeadline,
                    onValueChange = { draft = draft.copy(badgeHeadline = it) },
                )
                BrandingField(
                    label = stringResource(R.string.admin_event_badge_subheadline),
                    value = draft.badgeSubheadline,
                    onValueChange = { draft = draft.copy(badgeSubheadline = it) },
                )
                BrandingField(
                    label = stringResource(R.string.admin_event_badge_tag),
                    value = draft.badgeTag,
                    onValueChange = { draft = draft.copy(badgeTag = it) },
                )
                BrandingField(
                    label = stringResource(R.string.admin_event_hashtag),
                    value = draft.hashtag,
                    onValueChange = { draft = draft.copy(hashtag = it) },
                )
                Button(
                    onClick = {
                        onRename(profileId, name)
                        onSaveBranding(draft)
                        onSaved()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SyscomBlue,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(50.dp),
                ) {
                    Text(stringResource(R.string.admin_profiles_save))
                }

                // TODO(brand-colors): color pickers, logo picker and frame previews go here.
            }
        }
    }
}
