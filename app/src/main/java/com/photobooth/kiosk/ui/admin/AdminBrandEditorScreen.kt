package com.photobooth.kiosk.ui.admin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.BrandLogoStore
import com.photobooth.kiosk.data.EventBranding
import com.photobooth.kiosk.data.EventProfile
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.SyscomBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logoFailed by remember(profileId) { mutableStateOf(false) }

    val logoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val previous = draft.logoPath
            scope.launch {
                val path = withContext(Dispatchers.IO) { BrandLogoStore.save(context, uri) }
                if (path == null) {
                    logoFailed = true
                } else {
                    logoFailed = false
                    draft = draft.copy(logoPath = path)
                    BrandLogoStore.delete(previous)
                }
            }
        }
    }

    val logoBitmap = remember(draft.logoPath) {
        draft.logoPath?.let { path ->
            runCatching { android.graphics.BitmapFactory.decodeFile(path) }.getOrNull()
        }
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
            AdminCard(
                title = stringResource(R.string.admin_brand_colors_title),
                subtitle = stringResource(R.string.admin_brand_colors_subtitle),
            ) {
                BrandColorField(
                    label = stringResource(R.string.admin_brand_primary_color),
                    color = draft.primaryColor,
                    onColorChange = { draft = draft.copy(primaryColor = it) },
                )
                BrandColorField(
                    label = stringResource(R.string.admin_brand_secondary_color),
                    color = draft.secondaryColor,
                    onColorChange = { draft = draft.copy(secondaryColor = it) },
                )
            }
        }
        item {
            AdminCard(
                title = stringResource(R.string.admin_brand_logo_title),
                subtitle = stringResource(R.string.admin_brand_logo_hint),
            ) {
                if (logoBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Cream.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = logoBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.admin_brand_logo_title),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { logoPicker.launch(arrayOf("image/*")) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SyscomBlue,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.height(46.dp),
                    ) {
                        Text(stringResource(R.string.admin_brand_logo_choose))
                    }
                    if (draft.logoPath != null) {
                        OutlinedButton(
                            onClick = {
                                BrandLogoStore.delete(draft.logoPath)
                                draft = draft.copy(logoPath = null)
                            },
                            modifier = Modifier.height(46.dp),
                        ) {
                            Text(stringResource(R.string.admin_brand_logo_remove))
                        }
                    }
                }
                if (logoFailed) {
                    Text(
                        text = stringResource(R.string.admin_brand_logo_error),
                        color = Color(0xFFFF8A8A),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        item {
            AdminCard(
                title = stringResource(R.string.admin_brand_preview_title),
                subtitle = stringResource(R.string.admin_brand_preview_hint),
            ) {
                FramePreviewRow(branding = draft)
            }
        }
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
            }
        }
    }
}
