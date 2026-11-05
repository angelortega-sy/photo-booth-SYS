package com.photobooth.kiosk.data

import androidx.annotation.StringRes
import com.photobooth.kiosk.R

/** Lifecycle of a single AI styling run. Only one run is active at a time. */
enum class AiStatus {
    IDLE,
    UPLOADING,
    GENERATING,
    READY,
    ERROR,
}

/**
 * One global AI style applied to all 3 photos. The base prompt restyles only
 * the person (identity, pose, framing and clothing preserved; family friendly;
 * no text, logos or watermarks). The backend combines it with a per-photo
 * background directive: "none" (full styled scene), "prompt" (built-in scene
 * description) or "custom" (exact custom background reference preserved).
 */
data class AiStyle(
    val id: String,
    @param:StringRes val labelRes: Int,
    val prompt: String,
)

/** Optional fun accessory applied on top of the chosen style. Multi-select. */
data class AiAccessory(
    val id: String,
    @param:StringRes val labelRes: Int,
    val promptFragment: String,
)

object AiAccessories {
    val ALL: List<AiAccessory> = listOf(
        AiAccessory(
            id = "sunglasses",
            labelRes = R.string.ai_accessory_sunglasses,
            promptFragment = "stylish sunglasses matching the art style",
        ),
        AiAccessory(
            id = "hat",
            labelRes = R.string.ai_accessory_hat,
            promptFragment = "a festive party hat matching the art style",
        ),
        AiAccessory(
            id = "crown",
            labelRes = R.string.ai_accessory_crown,
            promptFragment = "an elegant golden crown matching the art style",
        ),
        AiAccessory(
            id = "flowers",
            labelRes = R.string.ai_accessory_flowers,
            promptFragment = "festive flowers and balloons around the person, matching the art style",
        ),
    )

    fun promptFor(selectedIds: Set<String>): String {
        val fragments = ALL.filter { it.id in selectedIds }.map { it.promptFragment }
        if (fragments.isEmpty()) return ""
        return "Also add these accessories to the person: " + fragments.joinToString("; ") + ". "
    }
}

object AiStyles {
    val ALL: List<AiStyle> = listOf(
        AiStyle(
            id = "ghibli",
            labelRes = R.string.ai_style_ghibli,
            prompt = "Transform the person in this photo into a Studio Ghibli anime film still, " +
                "with soft hand-painted cel shading and warm cinematic lighting. Preserve the " +
                "person's identity, face, pose, framing and clothing. " +
                "Polished illustration, family friendly, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "anime",
            labelRes = R.string.ai_style_anime,
            prompt = "Transform the person in this photo into a modern anime key visual, with clean " +
                "line art and vibrant cel shading. Preserve the person's identity, face, pose, " +
                "framing and clothing. Polished illustration, family friendly, " +
                "no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "pixar",
            labelRes = R.string.ai_style_pixar,
            prompt = "Transform the person in this photo into a Pixar-style 3D animation character " +
                "render, with soft subsurface scattering and expressive stylized features. Preserve " +
                "the person's identity, face, pose, framing and clothing. " +
                "Polished family-friendly render, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "cyberpunk",
            labelRes = R.string.ai_style_cyberpunk,
            prompt = "Transform the person in this photo into a cyberpunk neon illustration, with " +
                "glowing rim light and futuristic detail. Preserve the person's identity, face, " +
                "pose, framing and clothing. Polished illustration, " +
                "no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "comic",
            labelRes = R.string.ai_style_comic,
            prompt = "Transform the person in this photo into a vibrant pop-art comic illustration, " +
                "with bold inks and halftone shading. Preserve the person's identity, face, pose, " +
                "framing and clothing. Polished illustration, family friendly, " +
                "no text, no logos, no watermark.",
        ),
    )

    fun byId(id: String): AiStyle = ALL.firstOrNull { it.id == id } ?: ALL.first()
}
