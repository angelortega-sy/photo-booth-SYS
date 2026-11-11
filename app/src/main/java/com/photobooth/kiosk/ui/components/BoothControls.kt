package com.photobooth.kiosk.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photobooth.kiosk.R
import com.photobooth.kiosk.ui.theme.Cream
import com.photobooth.kiosk.ui.theme.Ink
import com.photobooth.kiosk.ui.theme.InkSoft
import com.photobooth.kiosk.ui.theme.SyscomBlue
import com.photobooth.kiosk.ui.theme.SyscomTeal

/**
 * Guest-flow controls shared by the AI and collage modes. The sizes here are the
 * design standard (docs/superpowers/specs/2026-11-09-ui-design-standard.md):
 * change them here, never per screen.
 */
object BoothDimens {
    val BarButtonHeight = 68.dp
    val InlineButtonHeight = 60.dp
    val ButtonRadius = 20.dp
    val CardRadius = 20.dp
    val PreviewRadius = 24.dp
}

private val ButtonShape = RoundedCornerShape(BoothDimens.ButtonRadius)

/** Title, "Step n of N · name", close button and progress bar. */
@Composable
fun BoothFlowHeader(
    title: String,
    stepIndex: Int,
    totalSteps: Int,
    stepTitle: String,
    horizontalPadding: Dp,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.ai_step_counter, stepIndex + 1, totalSteps, stepTitle),
                color = Cream.copy(alpha = 0.65f),
                fontSize = 14.sp,
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ai_exit), tint = Cream)
        }
    }
    LinearProgressIndicator(
        progress = { (stepIndex + 1) / totalSteps.toFloat() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .height(6.dp)
            .clip(RoundedCornerShape(999.dp)),
        color = SyscomBlue,
        trackColor = InkSoft,
    )
    Spacer(Modifier.height(8.dp))
}

/**
 * The fixed bottom bar: optional outlined back on the left, the screen's only
 * blue action on the right. Every guest step renders it, including capture.
 */
@Composable
fun BoothBottomBar(
    horizontalPadding: Dp,
    primaryLabel: String?,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    backLabel: String? = null,
    backEnabled: Boolean = true,
    onBack: () -> Unit = {},
) {
    val captureOnly = primaryLabel == null
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ink)
            .padding(horizontal = horizontalPadding, vertical = if (captureOnly) 8.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (backLabel != null) {
            BoothOutlinedButton(
                text = backLabel,
                onClick = onBack,
                enabled = backEnabled,
                height = if (captureOnly) 52.dp else BoothDimens.BarButtonHeight,
                modifier = if (captureOnly) Modifier.widthIn(max = 220.dp) else Modifier.weight(1f),
            )
        }
        if (primaryLabel != null) {
            BoothPrimaryButton(
                text = primaryLabel,
                onClick = onPrimary,
                enabled = primaryEnabled,
                modifier = Modifier.weight(if (backLabel != null) 1.4f else 1f),
            )
        }
    }
}

@Composable
fun BoothPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = BoothDimens.BarButtonHeight,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = SyscomBlue, contentColor = Color.White),
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** Filled teal: the one secondary action that still deserves weight (e.g. "Retake"). */
@Composable
fun BoothSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = BoothDimens.InlineButtonHeight,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = SyscomTeal,
            contentColor = Ink,
            disabledContainerColor = SyscomTeal.copy(alpha = 0.4f),
            disabledContentColor = Ink.copy(alpha = 0.6f),
        ),
    ) {
        ButtonContent(text, icon, FontWeight.Bold)
    }
}

/** Outlined, never blue: back, view, retake-all and other low-weight actions. */
@Composable
fun BoothOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = BoothDimens.InlineButtonHeight,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = ButtonShape,
        border = BorderStroke(1.dp, Cream.copy(alpha = if (enabled) 0.38f else 0.14f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Cream,
            disabledContentColor = Cream.copy(alpha = 0.38f),
        ),
    ) {
        ButtonContent(text, icon, FontWeight.SemiBold)
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?, weight: FontWeight) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text, fontSize = 17.sp, fontWeight = weight)
}

@Composable
fun BoothSectionTitle(title: String, subtitle: String = "") {
    Column {
        Text(title, color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = Cream.copy(alpha = 0.7f), fontSize = 15.sp)
        }
    }
}
