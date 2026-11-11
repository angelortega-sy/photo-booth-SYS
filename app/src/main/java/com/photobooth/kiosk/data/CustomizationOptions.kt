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

/** Silhouette and text layout of a frame; the palette alone does not define the look. */
enum class FrameShape(val id: String) {
    /** Cut-corner tech panel, brand pill top-left, footer text left of a circular seal. */
    TECH(id = "tech"),

    /** White print with a square photo and a centered caption underneath. */
    POLAROID(id = "polaroid"),

    /** Chamfered panel, brand running vertically, HUD footer with a top rule. */
    CYBER(id = "cyber"),

    /** Arched photo window framed by stars, centered serif-style title. */
    GALA(id = "gala"),
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
    val shape: FrameShape,
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
        shape = FrameShape.TECH,
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
        shape = FrameShape.POLAROID,
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
        shape = FrameShape.CYBER,
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
        shape = FrameShape.GALA,
    ),
}

enum class FrameDecoration(
    val id: String,
    @param:StringRes val titleRes: Int,
) {
    NONE(id = "none", titleRes = R.string.decoration_none),
    CORNERS(id = "corners", titleRes = R.string.decoration_corners),
    CONFETTI(id = "confetti", titleRes = R.string.decoration_confetti),
    TECH(id = "tech", titleRes = R.string.decoration_tech),
    FESTIVE(id = "festive", titleRes = R.string.decoration_festive),
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
    val hashtag: String = "#SYSCOM",
    val primaryColor: Long = 0xFF050B14,
    val secondaryColor: Long = 0xFF0B63CE,
    val logoPath: String? = null,
    /** Catalog logo used as the brand mark. Shared across booths; [logoPath] is only local. */
    val brandLogoId: String? = null,
    /** True when the server has a custom brand-logo file for this event. */
    val hasRemoteLogo: Boolean = false,
    /** Provider logo ids drawn as sponsors in the frame header. */
    val sponsorLogoIds: List<String> = emptyList(),
    /** Base name for the Drive folder; the app appends the session timestamp. */
    val driveFolderName: String = "",
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
    /** Absolute paths of the sponsor logo files to draw, resolved for the active event. */
    val sponsorLogoPaths: List<String> = emptyList(),
    val showDateStamp: Boolean = true,
    val decoration: FrameDecoration = FrameDecoration.NONE,
)

/** Brand-owned colors for a frame. The frame style only picks the shape. */
data class FramePalette(
    val background: Long,
    val secondaryBackground: Long,
    val accent: Long,
    val secondaryAccent: Long,
    val textPrimary: Long,
    val textSecondary: Long,
)

private const val PALETTE_DARK_TEXT = 0xFF050B14
private const val PALETTE_LIGHT_TEXT = 0xFFF7FAFF
private const val PALETTE_LUMINANCE_THRESHOLD = 0.55

/** Derives every frame color from the event brand's primary/secondary colors. */
fun CollageCustomization.palette(): FramePalette {
    val background = eventBranding.primaryColor
    val accent = eventBranding.secondaryColor
    val backgroundDark = luminance(background) < PALETTE_LUMINANCE_THRESHOLD
    val secondaryBackground = blend(
        background,
        if (backgroundDark) 0xFFFFFFFF else 0xFF000000,
        0.08f,
    )
    val secondaryAccent = blend(
        accent,
        if (backgroundDark) 0xFFFFFFFF else 0xFF000000,
        0.35f,
    )
    val textPrimary = if (backgroundDark) PALETTE_LIGHT_TEXT else PALETTE_DARK_TEXT
    val textSecondary = blend(textPrimary, background, 0.35f)
    return FramePalette(
        background = background,
        secondaryBackground = secondaryBackground,
        accent = accent,
        secondaryAccent = secondaryAccent,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
    )
}

private fun channel(color: Long, shift: Int): Float = ((color shr shift) and 0xFF) / 255f

private fun luminance(color: Long): Double {
    fun linear(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * linear(channel(color, 16)) +
        0.7152 * linear(channel(color, 8)) +
        0.0722 * linear(channel(color, 0))
}

private fun blend(first: Long, second: Long, ratio: Float): Long {
    val t = ratio.coerceIn(0f, 1f)
    fun mix(shift: Int): Long =
        ((channel(first, shift) * (1f - t) + channel(second, shift) * t) * 255f + 0.5f)
            .toInt().coerceIn(0, 255).toLong()
    return (0xFF000000L or (mix(16) shl 16) or (mix(8) shl 8) or mix(0))
}
