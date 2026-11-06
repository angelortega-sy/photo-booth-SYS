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

/** Optional fun accessory applied on top of the chosen style. Single-select per group in new flow. */
data class AiAccessory(
    val id: String,
    @param:StringRes val labelRes: Int,
    val promptFragment: String,
)

/** One accessory group: only one option (or none) can be selected per group. */
data class AiAccessoryGroup(
    val id: String,
    @param:StringRes val labelRes: Int,
    val options: List<AiAccessory>,
)

object AiAccessories {
    val HEAD_OPTIONS: List<AiAccessory> = listOf(
        AiAccessory(
            id = "cap",
            labelRes = R.string.ai_accessory_cap,
            promptFragment = "a casual cap matching the art style",
        ),
        AiAccessory(
            id = "headband",
            labelRes = R.string.ai_accessory_headband,
            promptFragment = "a festive headband matching the art style",
        ),
        AiAccessory(
            id = "crown",
            labelRes = R.string.ai_accessory_crown,
            promptFragment = "an elegant golden crown matching the art style",
        ),
        AiAccessory(
            id = "party_hat",
            labelRes = R.string.ai_accessory_hat,
            promptFragment = "a festive party hat matching the art style",
        ),
        AiAccessory(
            id = "bunny_ears",
            labelRes = R.string.ai_accessory_bunny_ears,
            promptFragment = "cute bunny ears matching the art style",
        ),
    )

    val FACE_OPTIONS: List<AiAccessory> = listOf(
        AiAccessory(
            id = "sunglasses",
            labelRes = R.string.ai_accessory_sunglasses,
            promptFragment = "stylish sunglasses matching the art style",
        ),
        AiAccessory(
            id = "mask",
            labelRes = R.string.ai_accessory_mask,
            promptFragment = "an elegant party mask matching the art style",
        ),
    )

    val EXTRA_OPTIONS: List<AiAccessory> = listOf(
        AiAccessory(
            id = "flowers",
            labelRes = R.string.ai_accessory_flowers,
            promptFragment = "festive flowers and balloons around the person, matching the art style",
        ),
        AiAccessory(
            id = "scarf",
            labelRes = R.string.ai_accessory_scarf,
            promptFragment = "a colorful scarf matching the art style",
        ),
        AiAccessory(
            id = "lei",
            labelRes = R.string.ai_accessory_lei,
            promptFragment = "a festive flower lei necklace matching the art style",
        ),
    )

    val HAIR_OPTIONS: List<AiAccessory> = listOf(
        AiAccessory(
            id = "ssj_yellow",
            labelRes = R.string.ai_accessory_ssj_yellow,
            promptFragment = "Change only the hair. Keep this person's face, age, skin, eyes, nose, " +
                "mouth, expression, and personality. Do not redraw them as Goku or any anime " +
                "character. Their own hair stands straight up in tall sharp Super Saiyan spikes " +
                "turned glowing golden yellow, with a faint golden aura behind the hair only",
        ),
        AiAccessory(
            id = "ssj_red",
            labelRes = R.string.ai_accessory_ssj_red,
            promptFragment = "Change only the hair. Keep this person's face, age, skin, eyes, nose, " +
                "mouth, expression, and personality. Do not redraw them as Goku or any anime " +
                "character. Their own hair stands straight up in tall sharp Super Saiyan spikes " +
                "turned deep crimson red, with a faint crimson aura behind the hair only",
        ),
        AiAccessory(
            id = "ssj_blue",
            labelRes = R.string.ai_accessory_ssj_blue,
            promptFragment = "Change only the hair. Keep this person's face, age, skin, eyes, nose, " +
                "mouth, expression, and personality. Do not redraw them as Goku or any anime " +
                "character. Their own hair stands straight up in tall sharp Super Saiyan spikes " +
                "turned icy electric blue, with a faint cyan aura behind the hair only",
        ),
        AiAccessory(
            id = "ssj_rose",
            labelRes = R.string.ai_accessory_ssj_rose,
            promptFragment = "Change only the hair. Keep this person's face, age, skin, eyes, nose, " +
                "mouth, expression, and personality. Do not redraw them as Goku or any anime " +
                "character. Their own hair stands straight up in tall sharp Super Saiyan spikes " +
                "turned vivid purple, with a faint purple aura behind the hair only",
        ),
    )

    val OUTFIT_OPTIONS: List<AiAccessory> = listOf(
        AiAccessory(
            id = "gi_syscom",
            labelRes = R.string.ai_accessory_gi_syscom,
            promptFragment = "the person wears a martial-arts gi with a loose kimono-style wrap top, " +
                "a wide belt and a round chest badge with a bold letter S",
        ),
        AiAccessory(
            id = "bulma_style",
            labelRes = R.string.ai_accessory_bulma_style,
            promptFragment = "the person wears a retro sci-fi adventurer outfit: a short cropped " +
                "jacket, shorts, gloves and a scarf, styled like a confident heroine",
        ),
    )

    val ALL: List<AiAccessory> =
        HEAD_OPTIONS + FACE_OPTIONS + EXTRA_OPTIONS + HAIR_OPTIONS + OUTFIT_OPTIONS

    val GROUPS: List<AiAccessoryGroup> = listOf(
        AiAccessoryGroup("head", R.string.ai_accessory_group_head, HEAD_OPTIONS),
        AiAccessoryGroup("face", R.string.ai_accessory_group_face, FACE_OPTIONS),
        AiAccessoryGroup("extra", R.string.ai_accessory_group_extra, EXTRA_OPTIONS),
        AiAccessoryGroup("hair", R.string.ai_accessory_group_hair, HAIR_OPTIONS),
        AiAccessoryGroup("outfit", R.string.ai_accessory_group_outfit, OUTFIT_OPTIONS),
    )

    private val GROUP_BY_ID: Map<String, String> = buildMap {
        HEAD_OPTIONS.forEach { put(it.id, "head") }
        FACE_OPTIONS.forEach { put(it.id, "face") }
        EXTRA_OPTIONS.forEach { put(it.id, "extra") }
        HAIR_OPTIONS.forEach { put(it.id, "hair") }
        OUTFIT_OPTIONS.forEach { put(it.id, "outfit") }
    }

    /** Single-select within a group: toggles off when the same id is tapped again. */
    fun selectExclusive(current: Set<String>, id: String): Set<String> {
        val group = GROUP_BY_ID[id] ?: return current
        val updated = current.filterNot { GROUP_BY_ID[it] == group }.toMutableSet()
        if (id !in current) updated.add(id)
        return updated
    }

    fun promptFor(selectedIds: Set<String>): String {
        val fragments = ALL.filter { it.id in selectedIds }.map { it.promptFragment }
        if (fragments.isEmpty()) return ""
        return "Also add these accessories to the person: " + fragments.joinToString("; ") + ". "
    }

    /** Legacy multi-toggle kept for the old 3-photo flow; new IA flow uses [selectExclusive]. */
    fun toggleLegacy(current: Set<String>, id: String): Set<String> {
        if (ALL.none { it.id == id }) return current
        val updated = current.toMutableSet()
        if (!updated.add(id)) updated.remove(id)
        return updated
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
            id = "comic",
            labelRes = R.string.ai_style_comic,
            prompt = "Transform the person in this photo into a vibrant pop-art comic illustration, " +
                "with bold inks and halftone shading. Preserve the person's identity, face, pose, " +
                "framing and clothing. Polished illustration, family friendly, " +
                "no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "oil",
            labelRes = R.string.ai_style_oil,
            prompt = "Transform the person in this photo into a traditional oil painting on linen, " +
                "with visible impasto brush strokes and palette-knife highlights. Preserve the " +
                "person's identity, face, pose, framing and clothing. " +
                "Family friendly, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "dragonball",
            labelRes = R.string.ai_style_dragonball,
            prompt = "Transform the person in this photo into a 1990s Dragon Ball anime cel, with " +
                "brush-pen line art and flat cel colors. Preserve the person's identity, face, " +
                "pose, framing and clothing; do not turn them into an existing character. " +
                "Family friendly, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "simpson",
            labelRes = R.string.ai_style_simpson,
            prompt = "Redraw every person in this photo as a classic Simpsons frame, not a generic " +
                "yellow cartoon. Give each one their own Simpsons personality: their expression, " +
                "a unique hair silhouette, and the show's mouth shapes. Use the oversized head, " +
                "huge overbite muzzle, enormous white eyes, four-finger hands, tube limbs, and " +
                "one flat yellow. Do not make a group look like copies. Do not turn them into " +
                "an existing character. Family friendly, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "pixelart",
            labelRes = R.string.ai_style_pixelart,
            prompt = "Redraw the person in this photo as a 16-bit pixel art portrait on a strict " +
                "pixel grid, with a limited palette, hard blocky edges and no anti-aliasing. " +
                "Preserve the person's identity, face, pose, framing and clothing. " +
                "Family friendly, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "cyberpunk",
            labelRes = R.string.ai_style_cyberpunk,
            prompt = "Transform the person in this photo into a cyberpunk portrait with a glowing " +
                "optical eye implant, chrome face plates and a mechanical prosthetic hand, lit by " +
                "neon magenta and cyan light. Preserve the person's identity, face, pose, framing " +
                "and clothing colors. Family friendly, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "clay",
            labelRes = R.string.ai_style_clay,
            prompt = "Rebuild the person in this photo as a handmade stop-motion clay figure on a " +
                "miniature set, with matte modelling clay, thumbprints and sculpted hair. Preserve " +
                "the person's identity, face, pose, framing and clothing colors. " +
                "Family friendly, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "noir",
            labelRes = R.string.ai_style_noir,
            prompt = "Turn the person in this photo into a 1940s black and white film noir " +
                "portrait, with silver-gelatin tonality, hard directional light, fine grain and a " +
                "dark backdrop. Preserve the person's identity, face, pose, framing and clothing. " +
                "Family friendly, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "vhs",
            labelRes = R.string.ai_style_vhs,
            prompt = "Make the person in this photo look like a frame from a 1990s VHS camcorder " +
                "tape: soft focus, interlace lines, tape noise, magenta colour cast and blown " +
                "highlights. Preserve the person's identity, face, pose, framing and clothing. " +
                "Family friendly, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "watercolor",
            labelRes = R.string.ai_style_watercolor,
            prompt = "Paint the person in this photo as a watercolour on cold-press paper, with " +
                "transparent wet-in-wet washes, visible paper grain and unpainted white " +
                "highlights. Preserve the person's identity, face, pose, framing and clothing " +
                "colors. Family friendly, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "lego",
            labelRes = R.string.ai_style_lego,
            prompt = "Rebuild every person in the photo, and the whole scene, as photographed LEGO " +
                "minifigures on a brick-built background. Make one minifigure for each person: do " +
                "not drop anyone in a group and do not add anyone who was not there. Each figure " +
                "uses the classic yellow head. Replace the eyes with exactly one pair of small " +
                "black dots printed into the plastic, not drawn on top of the real eyes. The " +
                "mouth is only a simple lip line. No eyebrows, freckles, glasses, or extra marks. " +
                "No sculpted nose. Each figure has " +
                "exactly two complete C-shaped hands, both fully visible even when the arms " +
                "are crossed or partly behind a neighbour. The background is empty LEGO bricks, " +
                "with zero extra minifigures. Preserve each person's hairstyle colour, pose, " +
                "spacing and clothing colours. Family friendly, no text, no logos, no watermark.",
        ),
    )

    fun byId(id: String): AiStyle = ALL.firstOrNull { it.id == id } ?: ALL.first()
}
