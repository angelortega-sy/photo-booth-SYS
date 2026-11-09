package com.photobooth.kiosk.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.InkSoft
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomBlueLight

internal enum class PinMode { CREATE, VERIFY }

@Composable
internal fun PinScreen(
    mode: PinMode,
    verify: (String) -> Boolean,
    onCancel: () -> Unit,
    onPinReady: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val creating = mode == PinMode.CREATE
    val current = if (confirming) confirmPin else pin

    fun addDigit(digit: String) {
        error = null
        if (confirming) {
            if (confirmPin.length < 8) confirmPin += digit
        } else if (pin.length < 8) {
            pin += digit
        }
    }

    fun backspace() {
        error = null
        if (confirming) {
            confirmPin = confirmPin.dropLast(1)
        } else {
            pin = pin.dropLast(1)
        }
    }

    fun submit() {
        when {
            creating && !confirming -> {
                if (pin.length < 4) {
                    error = "Usa al menos 4 dígitos"
                } else {
                    confirming = true
                    error = null
                }
            }

            creating && confirming -> {
                if (confirmPin.length < 4) {
                    error = "Usa al menos 4 dígitos"
                } else if (confirmPin != pin) {
                    error = "Los PIN no coinciden"
                    confirmPin = ""
                } else {
                    onPinReady(pin)
                }
            }

            else -> {
                if (pin.length < 4) {
                    error = "Usa al menos 4 dígitos"
                } else if (verify(pin)) {
                    onPinReady(pin)
                } else {
                    error = "PIN incorrecto"
                    pin = ""
                }
            }
        }
    }

    BackHandler {
        if (confirming) {
            confirming = false
            confirmPin = ""
            error = null
        } else {
            onCancel()
        }
    }

    val title = when {
        creating && confirming -> stringResource(R.string.admin_pin_confirm)
        creating -> stringResource(R.string.admin_pin_create)
        else -> stringResource(R.string.admin_pin_enter)
    }
    val hint = when {
        creating && confirming -> stringResource(R.string.admin_pin_hint_confirm)
        creating -> stringResource(R.string.admin_pin_hint_create)
        else -> stringResource(R.string.admin_pin_hint_enter)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (confirming) {
                        confirming = false
                        confirmPin = ""
                        error = null
                    } else {
                        onCancel()
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.admin_cancel),
                        tint = Cream,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "SYSCOM",
                    color = SyscomBlueLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                )
            }

            Spacer(Modifier.weight(0.6f))

            Box(
                modifier = Modifier
                    .size(86.dp)
                    .background(SyscomBlue.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = SyscomBlueLight,
                    modifier = Modifier.size(38.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = title,
                color = Cream,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = hint,
                color = Cream.copy(alpha = 0.58f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.height(18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // iPhone-style: only the digits typed so far, one blue dot each, nothing while empty.
                repeat(current.length) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(SyscomBlueLight, CircleShape),
                    )
                }
            }

            if (error != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = error.orEmpty(),
                    color = Color(0xFFFF7A7A),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.weight(0.5f))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PinKeyRow(listOf("1", "2", "3"), onDigit = ::addDigit)
                PinKeyRow(listOf("4", "5", "6"), onDigit = ::addDigit)
                PinKeyRow(listOf("7", "8", "9"), onDigit = ::addDigit)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PinKey(
                        label = "",
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                contentDescription = "Borrar",
                                tint = Cream,
                                modifier = Modifier.size(26.dp),
                            )
                        },
                        onClick = ::backspace,
                    )
                    PinKey(label = "0", onClick = { addDigit("0") })
                    PinKey(
                        label = "",
                        highlighted = true,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Continuar",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        },
                        onClick = ::submit,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            TextButton(onClick = onCancel) {
                Text(
                    text = stringResource(R.string.admin_cancel),
                    color = Cream.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun PinKeyRow(
    digits: List<String>,
    onDigit: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        digits.forEach { digit ->
            PinKey(label = digit, onClick = { onDigit(digit) })
        }
    }
}

@Composable
private fun PinKey(
    label: String,
    highlighted: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(if (highlighted) SyscomBlue else InkSoft)
            .border(
                1.dp,
                if (highlighted) SyscomBlue else Cream.copy(alpha = 0.10f),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            icon()
        } else {
            Text(
                text = label,
                color = Cream,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
