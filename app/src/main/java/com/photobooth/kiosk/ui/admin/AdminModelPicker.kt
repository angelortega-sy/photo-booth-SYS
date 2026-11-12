package com.photobooth.kiosk.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.photobooth.kiosk.R
import com.photobooth.kiosk.network.BoothModel
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.InkSoft
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomBlueLight
import com.photobooth.kiosk.ui.theme.SyscomTeal
import java.util.Locale

private val FavoriteStarColor = Color(0xFFFFC24B)

/** Filters of the model picker. Every model is an image model, so "per image" is the useful split. */
private enum class ModelFilter { ALL, FAVORITES, PER_IMAGE }

/** Modelo card: the model used for every AI photo, with the picker dialog behind it. */
@Composable
internal fun ModelCard(
    models: List<BoothModel>,
    selectedModelId: String,
    savedModelId: String,
    favoriteIds: Set<String>,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val formatPrice = rememberPriceFormatter()
    val selected = models.firstOrNull { it.id == selectedModelId }
    val saved = models.firstOrNull { it.id == savedModelId }

    AdminCard(
        title = stringResource(R.string.admin_model_card_title),
        subtitle = stringResource(R.string.admin_model_card_subtitle),
    ) {
        if (models.isEmpty()) {
            Text(
                text = stringResource(R.string.ai_models_empty),
                color = Cream.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
            return@AdminCard
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(InkSoft)
                .border(1.dp, SyscomBlue.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .clickable(role = Role.Button) { open = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = selected?.name ?: stringResource(R.string.ai_model_none),
                    color = if (selected != null) Cream else Cream.copy(alpha = 0.5f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected != null) {
                    Text(
                        text = formatPrice(selected),
                        color = Cream.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = SyscomBlueLight,
            )
        }
        if (selectedModelId != savedModelId) {
            Text(
                text = stringResource(
                    R.string.admin_model_pending,
                    saved?.name ?: stringResource(R.string.ai_model_none),
                ),
                color = SyscomTeal,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (open) {
        ModelPickerDialog(
            models = models,
            selectedModelId = selectedModelId,
            favoriteIds = favoriteIds,
            onSelect = {
                onSelect(it)
                open = false
            },
            onToggleFavorite = onToggleFavorite,
            onDismiss = { open = false },
        )
    }
}

/** Favoritos card: one tap on a chip selects that model. Stars are managed in the picker. */
@Composable
internal fun ModelFavoritesCard(
    models: List<BoothModel>,
    selectedModelId: String,
    favoriteIds: Set<String>,
    onSelect: (String) -> Unit,
) {
    val favorites = models.filter { it.id in favoriteIds }
    AdminCard(
        title = stringResource(R.string.admin_favorites_title),
        subtitle = stringResource(R.string.admin_favorites_subtitle),
    ) {
        if (favorites.isEmpty()) {
            Text(
                text = stringResource(R.string.admin_favorites_empty),
                color = Cream.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
            return@AdminCard
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            favorites.forEach { model ->
                ModelChip(
                    name = model.name,
                    selected = model.id == selectedModelId,
                    enabled = model.selectable,
                    onClick = { onSelect(model.id) },
                )
            }
        }
    }
}

@Composable
private fun ModelChip(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) SyscomBlue.copy(alpha = 0.3f) else InkSoft)
            .border(
                1.dp,
                if (selected) SyscomBlueLight else Cream.copy(alpha = 0.12f),
                shape,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = SyscomBlueLight,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(6.dp))
        }
        Text(
            text = name,
            color = Cream,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ModelPickerDialog(
    models: List<BoothModel>,
    selectedModelId: String,
    favoriteIds: Set<String>,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val formatPrice = rememberPriceFormatter()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ModelFilter.ALL) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val term = query.trim()
    val filtered = models.filter { model ->
        val matchesQuery = term.isBlank() ||
            model.name.contains(term, ignoreCase = true) ||
            model.id.contains(term, ignoreCase = true) ||
            model.provider.contains(term, ignoreCase = true)
        val matchesFilter = when (filter) {
            ModelFilter.ALL -> true
            ModelFilter.FAVORITES -> model.id in favoriteIds
            ModelFilter.PER_IMAGE -> model.priceUnit == "image"
        }
        matchesQuery && matchesFilter
    }
    // The current model is always pinned at the top when it matches the filters.
    val current = filtered.firstOrNull { it.id == selectedModelId }
    val rest = filtered.filter { it.id != selectedModelId }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.94f),
        title = {
            Text(
                text = stringResource(R.string.ai_model_select_title),
                color = Cream,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth().height(480.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.ai_model_search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = stringResource(R.string.admin_model_clear_search),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModelChip(
                        name = stringResource(R.string.admin_model_filter_all),
                        selected = filter == ModelFilter.ALL,
                        enabled = true,
                        onClick = { filter = ModelFilter.ALL },
                    )
                    ModelChip(
                        name = stringResource(R.string.admin_model_filter_favorites),
                        selected = filter == ModelFilter.FAVORITES,
                        enabled = true,
                        onClick = { filter = ModelFilter.FAVORITES },
                    )
                    ModelChip(
                        name = stringResource(R.string.admin_model_filter_image),
                        selected = filter == ModelFilter.PER_IMAGE,
                        enabled = true,
                        onClick = { filter = ModelFilter.PER_IMAGE },
                    )
                }
                Spacer(Modifier.height(8.dp))

                val emptyText = when {
                    models.isEmpty() -> R.string.ai_models_empty
                    filter == ModelFilter.FAVORITES && favoriteIds.isEmpty() ->
                        R.string.admin_model_empty_favorites
                    else -> R.string.ai_model_search_empty
                }
                if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(emptyText),
                        color = Cream.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp),
                    )
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        if (current != null) {
                            item(key = "current") {
                                Text(
                                    text = stringResource(R.string.admin_model_current),
                                    color = SyscomBlueLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                                )
                                ModelRow(
                                    model = current,
                                    isCurrent = true,
                                    isFavorite = current.id in favoriteIds,
                                    priceLabel = formatPrice(current),
                                    onSelect = { onSelect(current.id) },
                                    onToggleFavorite = {
                                        onToggleFavorite(current.id, current.id !in favoriteIds)
                                    },
                                )
                            }
                        }
                        items(rest, key = { it.id }) { model ->
                            ModelRow(
                                model = model,
                                isCurrent = false,
                                isFavorite = model.id in favoriteIds,
                                priceLabel = formatPrice(model),
                                onSelect = { onSelect(model.id) },
                                onToggleFavorite = {
                                    onToggleFavorite(model.id, model.id !in favoriteIds)
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.admin_cancel), color = Cream)
            }
        },
    )
}

@Composable
private fun ModelRow(
    model: BoothModel,
    isCurrent: Boolean,
    isFavorite: Boolean,
    priceLabel: String,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val overBudget = if (model.overBudget) stringResource(R.string.ai_over_budget) else null
    val details = listOfNotNull(
        model.provider.takeIf { it.isNotBlank() },
        priceLabel,
        overBudget,
    ).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) SyscomBlue.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(enabled = model.selectable, onClick = onSelect)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = model.name,
                color = if (model.selectable) Cream else Cream.copy(alpha = 0.4f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = details,
                color = Cream.copy(alpha = 0.6f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.ai_model_selected),
                tint = SyscomBlueLight,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = stringResource(
                    if (isFavorite) R.string.admin_favorite_remove else R.string.admin_favorite_add,
                ),
                tint = if (isFavorite) FavoriteStarColor else Cream.copy(alpha = 0.5f),
            )
        }
    }
}

/** Builds "$0.0123 / image" style labels, with localized units and the unknown-price fallback. */
@Composable
private fun rememberPriceFormatter(): (BoothModel) -> String {
    val unknownPrice = stringResource(R.string.ai_price_unknown)
    val unitImage = stringResource(R.string.ai_unit_image)
    val unitMegapixel = stringResource(R.string.ai_unit_megapixel)
    val unitToken = stringResource(R.string.ai_unit_token)
    return remember(unknownPrice, unitImage, unitMegapixel, unitToken) {
        { model: BoothModel ->
            val price = model.priceUsd
                ?.let { String.format(Locale.US, "$%.4f", it) }
                ?: unknownPrice
            val unit = when (model.priceUnit) {
                "image" -> unitImage
                "megapixel" -> unitMegapixel
                "token" -> unitToken
                else -> model.priceUnit.orEmpty()
            }
            if (unit.isBlank()) price else "$price / $unit"
        }
    }
}
