package com.photobooth.kiosk.ui.admin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.network.ServerStatusProbe
import com.photobooth.kiosk.ui.PhotoBoothViewModel
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.SyscomBlueLight
import kotlinx.coroutines.launch

private val DangerColor = Color(0xFFE5484D)

/**
 * Servidor: where the booth talks to, its health, and its enrollment.
 * Unenroll only clears this device; the server keeps its record.
 */
@Composable
internal fun AdminServerScreen(
    viewModel: PhotoBoothViewModel,
    statusProbe: ServerStatusProbe?,
    statusError: String?,
    isStatusChecking: Boolean,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var savedUrl by remember { mutableStateOf(viewModel.loadIdentity().baseUrl) }
    var draftUrl by remember { mutableStateOf(savedUrl) }
    var deviceId by remember { mutableStateOf(viewModel.loadIdentity().deviceId) }
    var enrollCode by remember { mutableStateOf("") }
    var enrolling by remember { mutableStateOf(false) }
    var enrollError by remember { mutableStateOf("") }
    var confirmUnenroll by remember { mutableStateOf(false) }

    val urlChanged = draftUrl.trim() != savedUrl.trim()
    val enrolled = deviceId.startsWith("dev_")

    fun saveUrlIfChanged() {
        if (!urlChanged) return
        viewModel.saveServerBaseUrl(draftUrl)
        savedUrl = draftUrl.trim()
    }

    LaunchedEffect(deviceId) {
        if (enrolled) viewModel.refreshServerCatalog()
    }

    AdminSettingsScreen(
        dirty = urlChanged,
        onSave = {
            saveUrlIfChanged()
            viewModel.refreshServerCatalog()
            onMessage(context.getString(R.string.admin_changes_saved))
        },
        onDiscard = { draftUrl = savedUrl },
    ) {
        item {
            AdminCard(
                title = stringResource(R.string.admin_server_address_title),
                subtitle = stringResource(R.string.admin_server_address_subtitle),
            ) {
                OutlinedTextField(
                    value = draftUrl,
                    onValueChange = { draftUrl = it },
                    label = { Text(stringResource(R.string.admin_ai_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            AdminCard(title = stringResource(R.string.admin_server_status_title)) {
                InfoRow("Servidor", savedUrl.ifBlank { "—" })
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
                OutlinedButton(
                    onClick = {
                        saveUrlIfChanged()
                        viewModel.refreshServerDiagnostics()
                    },
                    enabled = !isStatusChecking,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp),
                ) {
                    Text(
                        stringResource(
                            if (isStatusChecking) R.string.admin_ai_testing else R.string.admin_ai_test,
                        ),
                    )
                }
            }
        }

        item {
            AdminCard(title = stringResource(R.string.admin_server_booth_title)) {
                if (enrolled) {
                    Text(
                        text = stringResource(R.string.admin_ai_status_ok, deviceId),
                        color = SyscomBlueLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.admin_server_unenroll_hint),
                        color = Cream.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Button(
                        onClick = { confirmUnenroll = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DangerColor,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp),
                    ) {
                        Text(stringResource(R.string.admin_server_unenroll_action))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.admin_ai_status_none),
                        color = Cream.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = enrollCode,
                        onValueChange = { enrollCode = it },
                        label = { Text(stringResource(R.string.admin_ai_enroll)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedButton(
                        onClick = {
                            saveUrlIfChanged()
                            enrolling = true
                            enrollError = ""
                            scope.launch {
                                val result = runCatching { viewModel.enrollDevice(enrollCode) }
                                enrolling = false
                                result.onSuccess { id ->
                                    deviceId = id
                                    enrollCode = ""
                                    viewModel.refreshServerCatalog()
                                    onMessage(context.getString(R.string.admin_ai_enrolled))
                                }.onFailure { error ->
                                    enrollError = viewModel.explainServerError(error)
                                }
                            }
                        },
                        enabled = !enrolling && enrollCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp),
                    ) {
                        Text(
                            stringResource(
                                if (enrolling) R.string.admin_ai_enrolling else R.string.admin_ai_enroll_action,
                            ),
                        )
                    }
                    if (enrollError.isNotBlank()) {
                        Text(
                            text = enrollError,
                            color = Color(0xFFFF7A7A),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (confirmUnenroll) {
        AlertDialog(
            onDismissRequest = { confirmUnenroll = false },
            title = {
                Text(
                    text = stringResource(R.string.admin_server_unenroll_title),
                    color = Cream,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.admin_server_unenroll_confirm),
                    color = Cream.copy(alpha = 0.85f),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.unenrollThisBooth()
                        deviceId = ""
                        enrollError = ""
                        confirmUnenroll = false
                        onMessage(context.getString(R.string.admin_server_unenrolled))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerColor,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(R.string.admin_server_unenroll_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnenroll = false }) {
                    Text(stringResource(R.string.admin_cancel), color = Cream)
                }
            },
        )
    }
}
