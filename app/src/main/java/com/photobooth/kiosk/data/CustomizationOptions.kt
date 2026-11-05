package com.photobooth.kiosk.data

import androidx.annotation.StringRes
import com.photobooth.kiosk.R

enum class CollageLayout(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
) {
    POSTER_HERO(
        id = "poster_hero",
        titleRes = R.string.layout_poster_hero,
        subtitleRes = R.string.layout_poster_hero_sub,
    ),
    STRIP_CLASSIC(
        id = "strip_classic",
        titleRes = R.string.layout_strip_classic,
        subtitleRes = R.string.layout_strip_classic_sub,
    ),
    EDITORIAL_GRID(
        id = "editorial_grid",
        titleRes = R.string.layout_editorial_grid,
        subtitleRes = R.string.layout_editorial_grid_sub,
    ),
}

enum class CollageFrameStyle(
    val id: String,
    @param:StringRes val titleRes: Int,
    val backgroundHex: Long,
    val secondaryBackgroundHex: Long,
    val accentHex: Long,
    val secondaryAccentHex: Long,
    val textPrimaryHex: Long,
    val textSecondaryHex: Long,
) {
    SYSCOM_DARK(
        id = "syscom_dark",
        titleRes = R.string.frame_syscom_dark,
        backgroundHex = 0xFF050B14,
        secondaryBackgroundHex = 0xFF0B1A30,
        accentHex = 0xFF0B63CE,
        secondaryAccentHex = 0xFF5EA8FF,
        textPrimaryHex = 0xFFF3F8FF,
        textSecondaryHex = 0xFF9AC2F4,
    ),
    CLASSIC_PEARL(
        id = "classic_pearl",
        titleRes = R.string.frame_classic_pearl,
        backgroundHex = 0xFFF4F8FF,
        secondaryBackgroundHex = 0xFFE3EEFF,
        accentHex = 0xFF0B63CE,
        secondaryAccentHex = 0xFF063E86,
        textPrimaryHex = 0xFF050B14,
        textSecondaryHex = 0xFF4A607A,
    ),
    CYBER_NEON(
        id = "cyber_neon",
        titleRes = R.string.frame_cyber_neon,
        backgroundHex = 0xFF060916,
        secondaryBackgroundHex = 0xFF101935,
        accentHex = 0xFF00E5FF,
        secondaryAccentHex = 0xFF5EA8FF,
        textPrimaryHex = 0xFFFFFFFF,
        textSecondaryHex = 0xFF7CE8FF,
    ),
    GALA_GOLD(
        id = "gala_gold",
        titleRes = R.string.frame_gala_gold,
        backgroundHex = 0xFF0B0D13,
        secondaryBackgroundHex = 0xFF171B26,
        accentHex = 0xFFE5B84B,
        secondaryAccentHex = 0xFFF6DC92,
        textPrimaryHex = 0xFFFFFBF2,
        textSecondaryHex = 0xFFD9C089,
    ),
}

enum class PhotoBorderStyle(
    val id: String,
    @param:StringRes val titleRes: Int,
) {
    CLEAN_ROUNDED(
        id = "clean_rounded",
        titleRes = R.string.border_clean_rounded,
    ),
    POLAROID_WHITE(
        id = "polaroid_white",
        titleRes = R.string.border_polaroid_white,
    ),
    TECH_HUD(
        id = "tech_hud",
        titleRes = R.string.border_tech_hud,
    ),
    MINIMAL_SHARP(
        id = "minimal_sharp",
        titleRes = R.string.border_minimal_sharp,
    ),
}

data class EventBranding(
    val brand: String = "SYSCOM",
    val tagline: String = "PHOTO BOOTH",
    val welcomeTitle: String = "Tu momento.\nEn grande.",
    val welcomeSubtitle: String = "Tres poses, tu fondo favorito y cuatro recuerdos para llevar.",
    val badgeHeadline: String = "EXPO SYSCOM 2027",
    val badgeSubheadline: String = "CABINA DE FOTOS OFICIAL",
    val badgeTag: String = "SYSCOM · 2027",
) {
    fun withBadge(preset: EventBadgePreset): EventBranding = if (preset == EventBadgePreset.CUSTOM) {
        this
    } else {
        copy(
            badgeHeadline = preset.headline,
            badgeSubheadline = preset.subheadline,
            badgeTag = preset.tagChip,
        )
    }
}

enum class EventBadgePreset(
    val id: String,
    @param:StringRes val labelRes: Int,
    val headline: String,
    val subheadline: String,
    val tagChip: String,
) {
    EXPO_2027(
        id = "expo_2027",
        labelRes = R.string.badge_expo_2027,
        headline = "EXPO SYSCOM 2027",
        subheadline = "CABINA DE FOTOS OFICIAL",
        tagChip = "SYSCOM · 2027",
    ),
    INNOVATION(
        id = "innovation",
        labelRes = R.string.badge_innovation,
        headline = "SYSCOM · INNOVACIÓN",
        subheadline = "TECNOLOGÍA QUE CONECTA EL FUTURO",
        tagChip = "TECH · EXPO",
    ),
    VIP_GUEST(
        id = "vip_guest",
        labelRes = R.string.badge_vip_guest,
        headline = "INVITADO VIP · SYSCOM",
        subheadline = "EXPERIENCIA EXCLUSIVA EXPO 2027",
        tagChip = "VIP ACCESS",
    ),
    TEAM_POWER(
        id = "team_power",
        labelRes = R.string.badge_team_power,
        headline = "EQUIPO SYSCOM",
        subheadline = "LIDERANDO LA INDUSTRIA · 2027",
        tagChip = "TEAM SYSCOM",
    ),
    MEMORIES(
        id = "memories",
        labelRes = R.string.badge_memories,
        headline = "RECUERDO OFICIAL",
        subheadline = "VIVIENDO LA EXPERIENCIA SYSCOM",
        tagChip = "MOMENTO SYSCOM",
    ),
    CUSTOM(
        id = "custom",
        labelRes = R.string.badge_custom,
        headline = "",
        subheadline = "",
        tagChip = "",
    ),
}

data class CollageCustomization(
    val layout: CollageLayout = CollageLayout.POSTER_HERO,
    val frameStyle: CollageFrameStyle = CollageFrameStyle.SYSCOM_DARK,
    val photoBorderStyle: PhotoBorderStyle = PhotoBorderStyle.CLEAN_ROUNDED,
    val badgePreset: EventBadgePreset = EventBadgePreset.EXPO_2027,
    val eventBranding: EventBranding = EventBranding(),
    val showDateStamp: Boolean = true,
)
