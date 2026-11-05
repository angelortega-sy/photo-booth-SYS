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
 * One global AI style applied to all 3 photos. The prompt instructs the
 * backend model to restyle only the person while preserving the exact
 * background, composition, pose and identity of the guest.
 */
data class AiStyle(
    val id: String,
    @param:StringRes val labelRes: Int,
    val prompt: String,
)

object AiStyles {
    val ALL: List<AiStyle> = listOf(
        AiStyle(
            id = "ghibli",
            labelRes = R.string.ai_style_ghibli,
            prompt = "Transform ONLY the person in this photo into a Studio Ghibli anime film still, " +
                "with soft hand-painted cel shading and warm cinematic lighting. Preserve exactly the " +
                "background, scene, composition, pose, framing, clothing and identity of the person. " +
                "Polished illustration, family friendly, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "anime",
            labelRes = R.string.ai_style_anime,
            prompt = "Transform ONLY the person in this photo into a modern anime key visual, with clean " +
                "line art and vibrant cel shading. Preserve exactly the background, scene, composition, " +
                "pose, framing, clothing and identity of the person. Polished illustration, family " +
                "friendly, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "pixar",
            labelRes = R.string.ai_style_pixar,
            prompt = "Transform ONLY the person in this photo into a Pixar-style 3D animation character " +
                "render, with soft subsurface scattering and expressive stylized features. Preserve " +
                "exactly the background, scene, composition, pose, framing, clothing and identity of the " +
                "person. Polished family-friendly render, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "cyberpunk",
            labelRes = R.string.ai_style_cyberpunk,
            prompt = "Transform ONLY the person in this photo into a cyberpunk neon illustration, with " +
                "glowing rim light and futuristic detail. Preserve exactly the background, scene, " +
                "composition, pose, framing, clothing and identity of the person. Polished illustration, " +
                "no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "comic",
            labelRes = R.string.ai_style_comic,
            prompt = "Transform ONLY the person in this photo into a vibrant pop-art comic illustration, " +
                "with bold inks and halftone shading. Preserve exactly the background, scene, composition, " +
                "pose, framing, clothing and identity of the person. Polished illustration, family " +
                "friendly, no text, no logos, no watermark.",
        ),
    )

    fun byId(id: String): AiStyle = ALL.firstOrNull { it.id == id } ?: ALL.first()
}
