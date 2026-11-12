package com.photobooth.kiosk.ui.admin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.data.BackgroundOption
import com.photobooth.kiosk.data.BrandLogoStore
import com.photobooth.kiosk.data.CachedProviderLogo
import com.photobooth.kiosk.data.EventBranding
import com.photobooth.kiosk.data.EventProfile
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.SyscomBlueLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Edits the name, colors, logo and the [EventBranding] label fields of one
 * event profile. Saving only touches this profile; use the "make primary"
 * action (here or in the brand list) to switch the whole booth to it.
 *
 * Section order: event data, colors with a live preview, Drive, logo,
 * sponsors, frame preview. Welcome-screen text is no longer edited here; the
 * welcome screen only uses the brand name.
 */
@Composable
internal fun AdminBrandEditorScreen(
    profileId: String,
    profiles: List<EventProfile>,
    activeProfileId: String,
    onEnsureActive: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onSaveBranding: (String, EventBranding) -> Unit,
    onSaved: () -> Unit,
    providerLogos: List<CachedProviderLogo> = emptyList(),
    backgrounds: List<BackgroundOption> = emptyList(),
    loadThumbnail: (String) -> Bitmap? = { null },
    onImportBackground: (Uri, String?) -> Unit = { _, _ -> },
    onToggleBackground: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteBackground: (String) -> Unit = {},
    onRenameBackground: (String, String) -> Unit = { _, _ -> },
) {
    val profile = profiles.firstOrNull { it.id == profileId }
    if (profile == null) return

    val isActive = profileId == activeProfileId

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
                    draft = draft.copy(logoPath = path, brandLogoId = null)
                    // Only an unsaved pick can go now; the stored logo waits for "Save".
                    if (previous != profile.branding.logoPath) BrandLogoStore.delete(previous)
                }
            }
        }
    }

    val dirty = name != profile.name || draft != profile.branding

    val logoBitmap = remember(draft.logoPath) {
        draft.logoPath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
    }
    val providerLibrary = remember(providerLogos) {
        providerLogos.map { logo -> logo to BitmapFactory.decodeFile(logo.path) }
    }

    AdminSettingsScreen(
        dirty = dirty,
        onSave = {
            val storedLogo = profile.branding.logoPath
            if (name != profile.name) onRename(profileId, name)
            if (draft != profile.branding) onSaveBranding(profileId, draft)
            if (storedLogo != draft.logoPath) BrandLogoStore.delete(storedLogo)
            onSaved()
        },
        onDiscard = {
            if (draft.logoPath != profile.branding.logoPath) BrandLogoStore.delete(draft.logoPath)
            name = profile.name
            draft = profile.branding
        },
    ) {
        item {
            AdminCard(
                title = stringResource(R.string.admin_brand_active_title),
                subtitle = if (isActive) null else stringResource(R.string.admin_brand_active_hint),
            ) {
                if (isActive) {
                    Text(
                        stringResource(R.string.admin_brand_is_active),
                        color = SyscomBlueLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    OutlinedButton(
                        onClick = { onEnsureActive(profileId) },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                    ) {
                        Text(stringResource(R.string.admin_profiles_make_active))
                    }
                }
            }
        }
        item {
            AdminCard(title = stringResource(R.string.admin_event_data_title)) {
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
            }
        }
        item {
            AdminCard(
                title = stringResource(R.string.admin_brand_colors_title),
                subtitle = stringResource(R.string.admin_brand_colors_subtitle),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                    BrandMiniPreview(
                        primaryColor = draft.primaryColor,
                        secondaryColor = draft.secondaryColor,
                        brand = draft.brand,
                        hashtag = draft.hashtag,
                        logo = logoBitmap,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
        item {
            AdminCard(title = stringResource(R.string.admin_drive_title)) {
                BrandingField(
                    label = stringResource(R.string.admin_event_drive_folder),
                    value = draft.driveFolderName,
                    onValueChange = { draft = draft.copy(driveFolderName = it) },
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
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { logoPicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.height(46.dp),
                    ) {
                        Text(stringResource(R.string.admin_brand_logo_choose))
                    }
                    if (draft.logoPath != null) {
                        OutlinedButton(
                            onClick = {
                                if (draft.logoPath != profile.branding.logoPath) {
                                    BrandLogoStore.delete(draft.logoPath)
                                }
                                draft = draft.copy(logoPath = null, brandLogoId = null)
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
                title = stringResource(R.string.admin_sponsor_title),
                subtitle = stringResource(R.string.admin_sponsor_subtitle),
            ) {
                if (providerLibrary.isEmpty()) {
                    Text(
                        text = stringResource(R.string.admin_sponsor_empty),
                        color = Cream.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    providerLibrary.forEach { (logo, bitmap) ->
                        val enabled = logo.id in draft.sponsorLogoIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Cream.copy(alpha = 0.06f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = logo.name,
                                        contentScale = ContentScale.Fit,
                                        filterQuality = FilterQuality.High,
                                        modifier = Modifier.fillMaxSize().padding(6.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = logo.name,
                                color = Cream,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(
                                onClick = {
                                    val copied = BrandLogoStore.copyFile(context, File(logo.path))
                                        ?: return@OutlinedButton
                                    if (draft.logoPath != profile.branding.logoPath) {
                                        BrandLogoStore.delete(draft.logoPath)
                                    }
                                    draft = draft.copy(logoPath = copied, brandLogoId = logo.id)
                                },
                                modifier = Modifier.height(34.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.admin_sponsor_use_as_brand),
                                    color = SyscomBlueLight,
                                    fontSize = 12.sp,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = enabled,
                                onCheckedChange = { on ->
                                    draft = draft.copy(
                                        sponsorLogoIds = if (on) {
                                            (draft.sponsorLogoIds + logo.id).distinct()
                                        } else {
                                            draft.sponsorLogoIds - logo.id
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        item {
            AdminCard(
                title = stringResource(R.string.admin_brand_preview_title),
                subtitle = stringResource(R.string.admin_brand_preview_hint),
            ) {
                FramePreviewRow(
                    branding = draft,
                    sponsorPaths = draft.sponsorLogoIds.mapNotNull { id ->
                        providerLogos.firstOrNull { it.id == id }?.path
                    },
                )
            }
        }
        item {
            AdminCard(
                title = stringResource(R.string.admin_backgrounds_title),
                subtitle = stringResource(R.string.admin_backgrounds_subtitle),
            ) {
                BackgroundsAdminCard(
                    backgrounds = backgrounds,
                    loadThumbnail = loadThumbnail,
                    onImport = onImportBackground,
                    onToggle = onToggleBackground,
                    onDelete = onDeleteBackground,
                    onRename = onRenameBackground,
                )
            }
        }
    }
}
