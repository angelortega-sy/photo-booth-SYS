package com.photobooth.kiosk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SyscomBlue = Color(0xFF0B63CE)
val SyscomBlueLight = Color(0xFF5EA8FF)
val SyscomBlueDeep = Color(0xFF063E86)
// Secondary actions (outlined buttons, secondary links). Deliberately not blue
// so the primary button is always the only blue thing on screen.
val SyscomTeal = Color(0xFF2DD4BF)
val SyscomTealSoft = Color(0xFF9BF3E8)
val Ink = Color(0xFF050B14)
val InkSoft = Color(0xFF0B1727)
val Cream = Color(0xFFF3F8FF)

private val PhotoBoothColors = darkColorScheme(
    primary = SyscomBlue,
    onPrimary = Color.White,
    secondary = SyscomTeal,
    onSecondary = Ink,
    background = Ink,
    onBackground = Cream,
    surface = InkSoft,
    onSurface = Cream,
    surfaceVariant = InkSoft,
    onSurfaceVariant = Cream.copy(alpha = 0.72f),
    error = Color(0xFFFF8A80),
)

@Composable
fun PhotoBoothTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PhotoBoothColors,
        typography = Typography(),
        content = content,
    )
}
