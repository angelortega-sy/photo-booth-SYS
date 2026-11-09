package com.photobooth.kiosk.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.network.BoothModel
import com.photobooth.kiosk.network.ServerStatusProbe
import com.photobooth.kiosk.network.UsageLimits
import com.photobooth.kiosk.network.UsageMonth
import com.photobooth.kiosk.network.UsageRequest
import com.photobooth.kiosk.network.UsageSummary
import com.photobooth.kiosk.ui.PhotoBoothViewModel
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.InkSoft
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomBlueLight
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun AdminUsageScreen(
    viewModel: PhotoBoothViewModel,
    models: List<BoothModel>,
    selectedModelId: String,
    statusProbe: ServerStatusProbe?,
    statusError: String?,
    isStatusChecking: Boolean,
    onMessage: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 12.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            AiServerAdminCard(
                viewModel = viewModel,
                models = models,
                selectedModelId = selectedModelId,
                statusProbe = statusProbe,
                statusError = statusError,
                isStatusChecking = isStatusChecking,
                onMessage = onMessage,
            )
        }
        item {
            UsageAdminCard(viewModel = viewModel)
        }
    }
}

@Composable
private fun AiServerAdminCard(
    viewModel: PhotoBoothViewModel,
    models: List<BoothModel>,
    selectedModelId: String,
    statusProbe: ServerStatusProbe?,
    statusError: String?,
    isStatusChecking: Boolean,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val identity = remember { viewModel.loadIdentity() }
    var baseUrl by remember { mutableStateOf(identity.baseUrl) }
    var enrollCode by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf(identity.deviceId) }
    var enrolling by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(deviceId) {
        if (deviceId.startsWith("dev_")) viewModel.refreshServerCatalog()
    }

    AdminCard(
        title = stringResource(R.string.admin_ai_title),
        subtitle = stringResource(R.string.admin_ai_subtitle),
    ) {
        Text(
            text = if (deviceId.startsWith("dev_")) {
                stringResource(R.string.admin_ai_status_ok, deviceId)
            } else {
                stringResource(R.string.admin_ai_status_none)
            },
            color = if (deviceId.startsWith("dev_")) SyscomBlueLight else Cream.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        InfoRow("Servidor", baseUrl.ifBlank { "—" })
        InfoRow(
            "Última prueba",
            when {
                isStatusChecking -> "Comprobando…"
                statusProbe != null -> "Respondió"
                !statusError.isNullOrBlank() -> "Error: $statusError"
                else -> "Sin comprobar"
            },
        )
        InfoRow("Latencia", statusProbe?.latencyMs?.let { "$it ms" } ?: "—")
        InfoRow(
            "Drive",
            statusProbe?.driveHealthy?.let { if (it) "Correcto" else "Error" } ?: "—",
        )
        InfoRow(
            "TOTEMEXPO",
            statusProbe?.totemExpoReady?.let { if (it) "Listo" else "No listo" } ?: "—",
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text(stringResource(R.string.admin_ai_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(
            onClick = {
                viewModel.saveServerBaseUrl(baseUrl)
                onMessage(context.getString(R.string.admin_ai_saved))
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = SyscomBlue,
                contentColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(50.dp),
        ) {
            Text(stringResource(R.string.admin_ai_save))
        }
        OutlinedTextField(
            value = enrollCode,
            onValueChange = { enrollCode = it },
            label = { Text(stringResource(R.string.admin_ai_enroll)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(
            onClick = {
                viewModel.saveServerBaseUrl(baseUrl)
                enrolling = true
                statusText = ""
                scope.launch {
                    val result = runCatching { viewModel.enrollDevice(enrollCode) }
                    enrolling = false
                    result.onSuccess { id ->
                        deviceId = id
                        enrollCode = ""
                        viewModel.refreshServerCatalog()
                        onMessage(context.getString(R.string.admin_ai_enrolled))
                    }.onFailure { error ->
                        statusText = viewModel.explainServerError(error)
                    }
                }
            },
            enabled = !enrolling && enrollCode.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = SyscomBlue,
                contentColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp),
        ) {
            Text(
                stringResource(
                    if (enrolling) R.string.admin_ai_enrolling else R.string.admin_ai_enroll_action,
                ),
            )
        }
        if (statusText.isNotBlank()) {
            Text(
                text = statusText,
                color = Color(0xFFFF7A7A),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (models.isNotEmpty()) {
            ModelSelectField(
                models = models,
                selectedModelId = selectedModelId,
                onSelect = viewModel::selectServerModel,
            )
        }
        OutlinedButton(
            onClick = {
                viewModel.saveServerBaseUrl(baseUrl)
                viewModel.refreshServerDiagnostics()
            },
            enabled = !isStatusChecking,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp),
        ) {
            Text(
                stringResource(
                    if (isStatusChecking) R.string.admin_ai_testing else R.string.admin_ai_test,
                ),
            )
        }
    }
}

/** Searchable single-select for the AI model, instead of a long radio list. */
@Composable
private fun ModelSelectField(
    models: List<BoothModel>,
    selectedModelId: String,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val selected = models.firstOrNull { it.id == selectedModelId }
    val unknownPrice = stringResource(R.string.ai_price_unknown)
    val unitImage = stringResource(R.string.ai_unit_image)
    val unitMegapixel = stringResource(R.string.ai_unit_megapixel)
    val unitToken = stringResource(R.string.ai_unit_token)

    fun priceLabel(model: BoothModel): String {
        val price = model.priceUsd
            ?.let { String.format(Locale.US, "$%.4f", it) }
            ?: unknownPrice
        val unit = when (model.priceUnit) {
            "image" -> unitImage
            "megapixel" -> unitMegapixel
            "token" -> unitToken
            else -> model.priceUnit.orEmpty()
        }
        return if (unit.isBlank()) price else "$price / $unit"
    }

    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(
            text = stringResource(R.string.ai_model_select_label),
            color = Cream,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(InkSoft)
                .border(
                    1.dp,
                    SyscomBlue.copy(alpha = 0.4f),
                    RoundedCornerShape(14.dp),
                )
                .clickable(role = Role.Button) {
                    query = ""
                    open = true
                }
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
                        text = priceLabel(selected),
                        color = Cream.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = SyscomBlueLight,
            )
        }
    }

    if (open) {
        val filtered = models.filter { model ->
            query.isBlank() ||
                model.name.contains(query, ignoreCase = true) ||
                model.id.contains(query, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { open = false },
            title = {
                Text(
                    text = stringResource(R.string.ai_model_select_title),
                    color = Cream,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.ai_model_search_hint)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    if (filtered.isEmpty()) {
                        Text(
                            text = stringResource(R.string.ai_model_search_empty),
                            color = Cream.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(8.dp),
                        )
                    } else {
                        LazyColumn(Modifier.heightIn(max = 320.dp)) {
                            items(filtered, key = { it.id }) { model ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = model.selectable) {
                                            onSelect(model.id)
                                            open = false
                                        }
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = model.name,
                                            color = if (model.selectable) Cream else Cream.copy(alpha = 0.4f),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = buildString {
                                                append(priceLabel(model))
                                                if (model.overBudget) {
                                                    append(" · ")
                                                    append(context.getString(R.string.ai_over_budget))
                                                }
                                            },
                                            color = Cream.copy(alpha = 0.55f),
                                            fontSize = 12.sp,
                                        )
                                    }
                                    if (model.id == selectedModelId) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = stringResource(R.string.ai_model_selected),
                                            tint = SyscomBlueLight,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.admin_cancel), color = Cream)
                }
            },
        )
    }
}

/** Rows of the usage log shown per batch; "load more" appends the next page. */
private const val USAGE_ROWS_PER_PAGE = 25

@Composable
private fun UsageAdminCard(viewModel: PhotoBoothViewModel) {
    var months by remember { mutableStateOf<List<UsageMonth>>(emptyList()) }
    var selectedMonth by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf<UsageSummary?>(null) }
    var limits by remember { mutableStateOf<UsageLimits?>(null) }
    var limitsFailed by remember { mutableStateOf(false) }
    var requests by remember { mutableStateOf<List<UsageRequest>>(emptyList()) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var visibleCount by remember { mutableStateOf(USAGE_ROWS_PER_PAGE) }
    var query by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun money(value: Double): String = String.format(java.util.Locale.US, "$%.4f", value)

    suspend fun load(month: String) {
        loading = true
        errorText = ""
        try {
            val loadedMonths = viewModel.loadUsageMonths()
            val chosen = month.ifBlank { loadedMonths.firstOrNull()?.month.orEmpty() }
            months = loadedMonths
            selectedMonth = chosen
            if (chosen.isBlank()) {
                summary = null
                requests = emptyList()
                nextCursor = null
            } else {
                summary = viewModel.loadUsageSummary(chosen)
                val page = viewModel.loadUsageRequests(chosen)
                requests = page.requests
                nextCursor = page.nextCursor
            }
            visibleCount = USAGE_ROWS_PER_PAGE
            limitsFailed = false
            limits = runCatching { viewModel.loadUsageLimits() }
                .onFailure { limitsFailed = true }
                .getOrNull()
        } catch (error: Exception) {
            errorText = viewModel.explainServerError(error)
        } finally {
            loading = false
        }
    }

    fun loadMore() {
        if (loadingMore) return
        if (visibleCount < requests.size) {
            visibleCount += USAGE_ROWS_PER_PAGE
            return
        }
        val cursor = nextCursor ?: return
        scope.launch {
            loadingMore = true
            runCatching { viewModel.loadUsageRequests(selectedMonth, cursor) }
                .onSuccess { page ->
                    requests = (requests + page.requests).distinctBy { it.id }
                    nextCursor = page.nextCursor
                    visibleCount += USAGE_ROWS_PER_PAGE
                }
                .onFailure { errorText = viewModel.explainServerError(it) }
            loadingMore = false
        }
    }

    LaunchedEffect(Unit) { load("") }

    val filtered = remember(requests, query) {
        val term = query.trim().lowercase()
        if (term.isBlank()) {
            requests
        } else {
            requests.filter { request ->
                request.modelId.lowercase().contains(term) ||
                    request.styleId.lowercase().contains(term) ||
                    request.status.lowercase().contains(term) ||
                    request.deviceName?.lowercase()?.contains(term) == true
            }
        }
    }
    val shown = filtered.take(visibleCount)
    val canLoadMore = visibleCount < filtered.size || nextCursor != null

    AdminCard(
        title = stringResource(R.string.admin_usage_title),
        subtitle = stringResource(R.string.admin_usage_subtitle),
    ) {
        // Real OpenRouter account numbers instead of only the booth's own log.
        Text(
            text = stringResource(R.string.admin_usage_limits_title),
            color = Cream,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        val account = limits
        if (account != null) {
            val limitUsd = account.limitUsd
            val percent = account.percentUsed
            Text(
                text = if (limitUsd != null && limitUsd > 0) {
                    stringResource(
                        R.string.admin_usage_limits_spent,
                        money(account.usageUsd),
                        money(limitUsd),
                    )
                } else {
                    stringResource(R.string.admin_usage_limits_spent_no_limit, money(account.usageUsd))
                },
                color = Cream.copy(alpha = 0.85f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (percent != null) {
                val barColor = when {
                    percent >= 90.0 -> Color(0xFFFF6B6B)
                    percent >= 70.0 -> Color(0xFFFFC24B)
                    else -> SyscomBlueLight
                }
                LinearProgressIndicator(                    progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = barColor,
                    trackColor = Cream.copy(alpha = 0.12f),
                )
                Text(
                    text = stringResource(
                        R.string.admin_usage_limits_used,
                        String.format(java.util.Locale.US, "%.0f", percent),
                    ),
                    color = barColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                account.remainingUsd?.let { remaining ->
                    Text(
                        text = stringResource(R.string.admin_usage_limits_remaining, money(remaining)),
                        color = Cream.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                    )
                }
                if (account.totalCreditsUsd != null) {
                    Text(
                        text = stringResource(
                            R.string.admin_usage_limits_credits,
                            money(account.totalUsageUsd ?: 0.0),
                            money(account.totalCreditsUsd),
                        ),
                        color = Cream.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                    )
                }
                account.rateLimitRequests?.let { count ->
                    Text(
                        text = stringResource(
                            R.string.admin_usage_limits_rate,
                            count,
                            account.rateLimitInterval ?: "min",
                        ),
                        color = Cream.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                    )
                }
                if (account.isFreeTier) {
                    Text(
                        text = stringResource(R.string.admin_usage_limits_free),
                        color = Color(0xFFFFC24B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else if (limitsFailed && !loading) {
            Text(
                text = stringResource(R.string.admin_usage_limits_unavailable),
                color = Cream.copy(alpha = 0.6f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (loading) {
            CircularProgressIndicator(
                color = SyscomBlueLight,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (months.isEmpty() && !loading) {
            Text(
                text = stringResource(R.string.admin_usage_empty),
                color = Cream.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (months.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                months.forEach { month ->
                    TextButton(onClick = {
                        scope.launch { load(month.month) }
                    }) {
                        Text(
                            text = month.month,
                            color = if (month.month == selectedMonth) SyscomBlueLight else Cream,
                            fontWeight = if (month.month == selectedMonth) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Medium
                            },
                        )
                    }
                }
            }
        }
        summary?.let { item ->
            Text(
                text = stringResource(
                    R.string.admin_usage_total,
                    String.format(java.util.Locale.US, "%.4f", item.totalCostUsd),
                    item.requestCount,
                ),
                color = Cream,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (item.failedCount > 0) {
                Text(
                    text = stringResource(R.string.admin_usage_failed, item.failedCount),
                    color = Color(0xFFFF9F7A),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (item.byDay.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.admin_usage_daily),
                    color = Cream.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
                val peak = item.byDay.maxOf { it.totalCostUsd }.coerceAtLeast(0.0001)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    item.byDay.forEach { day ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(top = 30.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(
                                        (day.totalCostUsd / peak).toFloat().coerceIn(0.04f, 1f),
                                    )
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(SyscomBlueLight.copy(alpha = 0.85f)),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = item.byDay.first().day.takeLast(2),
                        color = Cream.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                    )
                    Text(
                        text = item.byDay.last().day.takeLast(2),
                        color = Cream.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                    )
                }
            }
            if (item.byModel.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.admin_usage_top_models),
                    color = Cream.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
                val peakModel = item.byModel.maxOf { it.totalCostUsd }.coerceAtLeast(0.0001)
                item.byModel.take(3).forEach { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = model.modelId,
                            color = Cream.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = money(model.totalCostUsd),
                            color = Cream.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Cream.copy(alpha = 0.08f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(
                                    (model.totalCostUsd / peakModel).toFloat().coerceIn(0.02f, 1f),
                                )
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(999.dp))
                                .background(SyscomBlue),
                        )
                    }
                }
            }
        }

        // Log: search + compact table + "load more" instead of one giant list.
        Text(
            text = stringResource(R.string.admin_usage_log),
            color = Cream,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 14.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                visibleCount = USAGE_ROWS_PER_PAGE
            },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            placeholder = {
                Text(
                    text = stringResource(R.string.admin_usage_search),
                    fontSize = 12.sp,
                )
            },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.admin_usage_col_time),
                color = Cream.copy(alpha = 0.55f),
                fontSize = 10.sp,
                modifier = Modifier.weight(0.9f),
            )
            Text(
                text = stringResource(R.string.admin_usage_col_style),
                color = Cream.copy(alpha = 0.55f),
                fontSize = 10.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.admin_usage_col_model),
                color = Cream.copy(alpha = 0.55f),
                fontSize = 10.sp,
                modifier = Modifier.weight(1.6f),
            )
            Text(
                text = stringResource(R.string.admin_usage_col_cost),
                color = Cream.copy(alpha = 0.55f),
                fontSize = 10.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.7f),
            )
        }
        if (shown.isEmpty() && !loading) {
            Text(
                text = stringResource(R.string.admin_usage_empty_filter),
                color = Cream.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        shown.forEach { request ->
            val cost = request.costUsd ?: request.estimatedCostUsd
            val costLabel = if (cost == null) {
                stringResource(R.string.ai_price_unknown)
            } else {
                money(cost)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = usageTimeLabel(request.createdAt),
                    color = Cream.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(0.9f),
                )
                Text(
                    text = request.styleId,
                    color = Cream.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = request.modelId,
                    color = Cream.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.6f),
                )
                Text(
                    text = costLabel,
                    color = if (request.status == "success") {
                        Cream.copy(alpha = 0.85f)
                    } else {
                        Color(0xFFFF9F7A)
                    },
                    fontSize = 10.sp,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.weight(0.7f),
                )
            }
        }
        if (shown.isNotEmpty()) {
            Text(
                text = stringResource(R.string.admin_usage_showing, shown.size, filtered.size),
                color = Cream.copy(alpha = 0.45f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (canLoadMore) {
            OutlinedButton(
                onClick = { loadMore() },
                enabled = !loadingMore,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(44.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (loadingMore) {
                    CircularProgressIndicator(
                        color = SyscomBlueLight,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(stringResource(R.string.admin_usage_load_more), fontSize = 13.sp)
                }
            }
        }
        if (errorText.isNotBlank()) {
            Text(
                text = errorText,
                color = Color(0xFFFF7A7A),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** "2026-11-06T18:53:58.801Z" -> "06/11 18:53". */
private fun usageTimeLabel(createdAt: String): String {
    if (createdAt.length < 16) return createdAt
    val date = createdAt.substring(5, 10).replace('-', '/')
    return "$date ${createdAt.substring(11, 16)}"
}
