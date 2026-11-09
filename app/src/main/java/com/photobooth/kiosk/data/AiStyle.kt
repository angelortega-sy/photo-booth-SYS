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
        AiAccessory(
            id = "santa_hat",
            labelRes = R.string.ai_accessory_santa_hat,
            promptFragment = "a red Santa hat with a white fur trim and a pom-pom, sitting above " +
                "the eyebrows so the whole face stays visible",
        ),
        AiAccessory(
            id = "cowboy",
            labelRes = R.string.ai_accessory_cowboy,
            promptFragment = "a classic cowboy hat with a shaped brim and a leather band",
        ),
        AiAccessory(
            id = "flower_crown",
            labelRes = R.string.ai_accessory_flower_crown,
            promptFragment = "a delicate crown of fresh flowers resting over the hair",
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
        AiAccessory(
            id = "mustache",
            labelRes = R.string.ai_accessory_mustache,
            promptFragment = "a thick curled moustache above the upper lip",
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
        AiAccessory(
            id = "guitar",
            labelRes = R.string.ai_accessory_guitar,
            promptFragment = "an acoustic guitar held in front of the body",
        ),
        AiAccessory(
            id = "balloons",
            labelRes = R.string.ai_accessory_balloons,
            promptFragment = "a bunch of colourful party balloons floating beside the person",
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
        AiAccessory(
            id = "naruto",
            labelRes = R.string.ai_accessory_naruto,
            promptFragment = "Change only the hair and cheeks. Keep this person's face, age, skin, eyes, " +
                "nose, mouth, expression, and personality. Do not redraw them as Naruto or any anime " +
                "character. Their own hair turns spiky bright blond, and three thin whisker-like lines " +
                "appear on each cheek, with nothing else changed",
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
        AiAccessory(
            id = "gown",
            labelRes = R.string.ai_accessory_gown,
            promptFragment = "the person wears an elegant floor-length evening gown in a deep " +
                "jewel tone with a fitted bodice and a soft drape",
        ),
        AiAccessory(
            id = "tux",
            labelRes = R.string.ai_accessory_tux,
            promptFragment = "the person wears a tailored black tuxedo with satin lapels, a white " +
                "shirt and a black bow tie",
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

    private val HEAD_IDS: Set<String> = HEAD_OPTIONS.map { it.id }.toSet()
    private val HAIR_IDS: Set<String> = HAIR_OPTIONS.map { it.id }.toSet()
    private val GROUP_IDS: Map<String, Set<String>> =
        GROUPS.associate { it.id to it.options.map { option -> option.id }.toSet() }

    /**
     * A style that already builds its own headwear or outfit hides those
     * accessory groups so the guest cannot stack a sombrero on a sombrero.
     */
    private val STYLE_LOCKED_GROUPS: Map<String, Set<String>> = mapOf(
        "mariachi" to setOf("head", "outfit"),
        "catrina" to setOf("head", "outfit"),
        "xmas" to setOf("head", "outfit"),
        "noir" to setOf("head", "outfit"),
        // Cyberpunk dictates the techno jacket and forbids masks or helmets.
        "cyberpunk" to setOf("outfit"),
        // K-pop dictates gender-matched stage outfits.
        "kpop" to setOf("outfit"),
        // These styles rebuild the hair as a fixed material or silhouette,
        // so dyed spiky hair would fight the prompt.
        "lego" to setOf("hair"),
        "vinyl" to setOf("hair"),
        "actionfigure" to setOf("hair"),
        "clay" to setOf("hair"),
        "pixelart" to setOf("hair"),
        "anime" to setOf("hair"),
        "simpson" to setOf("hair"),
    )

    /** Individual ids a style blocks even though the rest of the group stays available. */
    private val STYLE_LOCKED_IDS: Map<String, Set<String>> = mapOf(
        "catrina" to setOf("mask"),
        "cyberpunk" to setOf("mask"),
    )

    /** Accessories that would fight the look the selected style already creates. */
    fun blockedForStyle(styleId: String?): Set<String> {
        if (styleId == null) return emptySet()
        val lockedGroups = STYLE_LOCKED_GROUPS[styleId].orEmpty()
        val fromGroups = lockedGroups.flatMap { GROUP_IDS[it].orEmpty() }
        return (fromGroups + STYLE_LOCKED_IDS[styleId].orEmpty()).toSet()
    }

    /**
     * Cross-group clash: dyed spiky hair and a hat cannot both sit on the head,
     * so picking one hides the other group.
     */
    fun blockedBySelection(selected: Set<String>): Set<String> = buildSet {
        if (selected.any { it in HAIR_IDS }) addAll(HEAD_IDS)
        if (selected.any { it in HEAD_IDS }) addAll(HAIR_IDS)
    }

    /** Everything the accessory cards must hide for the current style and picks. */
    fun blockedIds(styleId: String?, selected: Set<String>): Set<String> =
        blockedForStyle(styleId) + blockedBySelection(selected)

    /** Single-select within a group: toggles off when the same id is tapped again. */
    fun selectExclusive(current: Set<String>, id: String): Set<String> {
        val group = GROUP_BY_ID[id] ?: return current
        val updated = current.filterNot { GROUP_BY_ID[it] == group }.toMutableSet()
        if (id !in current) {
            updated.add(id)
            updated.removeAll(blockedBySelection(setOf(id)))
        }
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
            prompt = "Transform the person in this photo into a cinematic cyberpunk night portrait " +
                "that keeps the same face, skin tone, eyes, hairline and expression. Keep both " +
                "eyes real, with no glowing ring, and only a tiny vent below one eye plus a thin " +
                "metal seam at the temple. Replace one forearm with a chrome prosthetic hand with " +
                "visible joints. Dress them in a high-collar dark techwear jacket with reflective " +
                "cyan piping. Neon city bokeh, rain, wet asphalt reflections, teal shadows and " +
                "magenta rim light. Family friendly, no text, no logos, no watermark.",
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
                "mouth is only a simple lip line. No eyebrows, freckles, or extra marks. If the " +
                "person wears glasses, add one complete LEGO glasses piece with both lenses and " +
                "both arms, not a half frame. " +
                "Hands never become human hands: a peace sign, a thumbs-up, a wave, or " +
                "a pointing gesture keeps the same C-shaped claw and only the wrist and " +
                "arm angle change; never split a claw into separate fingers, never draw " +
                "finger lines, and never replace the claw with a realistic hand. " +
                "No sculpted nose. Each figure has " +
                "exactly two complete C-shaped hands, both fully visible even when the arms " +
                "are crossed or partly behind a neighbour. The background is empty LEGO bricks, " +
                "with zero extra minifigures. Preserve each person's hairstyle colour, pose, " +
                "spacing and clothing colours. Family friendly, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "actionfigure",
            labelRes = R.string.ai_style_actionfigure,
            prompt = "Recreate the portrait as a macro photograph of a collectible action figure on a " +
                "display base. Body and head are injection-moulded plastic: the face is sculpted " +
                "and painted, never a photograph and never real skin. Keep the person's face " +
                "structure and painted skin tone, but as a toy head with painted eyes, no pores, " +
                "no stubble hair by hair, no wet highlights and no real eyelashes; glasses, beard " +
                "and freckles become sculpted or painted details. Ball joints, hard folds, " +
                "mould seams, dark seamless backdrop. One figure per person, no extra figures. " +
                "The face must read as painted plastic, not as a real human face. Family " +
                "friendly, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "vinyl",
            labelRes = R.string.ai_style_vinyl,
            prompt = "Recreate the portrait as a glossy vinyl figure in the style of a Funko Pop on " +
                "a studio table: oversized squared head about half of the total height, flat top, " +
                "no neck, small stubby body and tiny feet. The face is toy paint, never a " +
                "photograph: two large round glossy black eyes with one white highlight each, no " +
                "mouth, no nose, no eyebrows, no real skin texture. Hair is moulded vinyl in the " +
                "person's own silhouette and colour; glasses and beard become moulded pieces. One " +
                "figure per person, pastel backdrop, no box, no human face. Family friendly, no " +
                "text, no logos, no watermark.",
        ),
        AiStyle(
            id = "catrina",
            labelRes = R.string.ai_style_catrina,
            prompt = "Recreate the portrait as an elegant Day of the Dead catrina photograph, not " +
                "gory: a refined sugar-skull makeup that follows the person's real bone structure, " +
                "delicate black filigree around the eyes, a flower on the temple and a crown of " +
                "marigolds. Black embroidered outfit with lace trim, warm candlelight, dark altar " +
                "with candles out of focus. Dress each person by their apparent gender and never " +
                "swap it: a man in a black charro jacket and trousers, a woman in a black gown. " +
                "Never put a man in a dress. Family friendly, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "mariachi",
            labelRes = R.string.ai_style_mariachi,
            prompt = "Recreate the portrait as an elegant mariachi photograph at night: a fitted " +
                "charro jacket with silver embroidery, a red bow tie and a wide-brimmed sombrero " +
                "with silver trim tilted so the whole face stays visible. Dress each person by " +
                "their apparent gender: a man in the full charro suit, a woman in an embroidered " +
                "china poblana dress or a fitted women's charro suit; never put a man in a dress. " +
                "Warm lantern light, Mexican plaza with strings " +
                "of lights out of focus. Family friendly, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "xmas",
            labelRes = R.string.ai_style_xmas,
            prompt = "Recreate the portrait as a warm Christmas card photograph: a cosy knit " +
                "sweater with a fair-isle pattern, golden string lights and candles turned into " +
                "soft bokeh, falling snow in front of the lens and a dark evergreen forest out of " +
                "focus. Keep skin texture, no hat over the eyes, no cartoon reindeer. Family " +
                "friendly, no text, no logos, no watermark.",
        ),
        AiStyle(
            id = "kpop",
            labelRes = R.string.ai_style_kpop,
            prompt = "Recreate this exact photo-booth portrait as a professional K-pop idol " +
                "editorial photograph, keeping the same number of people, their faces, " +
                "identities, and spacing. Dress each person according to their apparent " +
                "gender and never swap it: a woman becomes a K-pop girl-group idol in a " +
                "coordinated stage outfit such as a cropped jacket with wide trousers, a " +
                "fitted mini dress, or a matching two-piece in satin, sequins, or leather, " +
                "with glossy styled hair, dewy glass-skin makeup, and subtle stage " +
                "jewelry; a man becomes a K-pop boy-group idol in a tailored stage suit, " +
                "or a bomber or leather jacket over a fitted tee, with styled hair, clean " +
                "makeup, and subtle earrings. Never put a man in a dress and never put a " +
                "woman in a men's suit. Light the scene like a music-show stage: soft neon " +
                "rim light in pink, cyan, or violet, a dark stage background with soft " +
                "bokeh light sticks and a light haze, no visible crowd and no readable " +
                "text. Keep the vertical framing and a full body from head to mid-thigh or " +
                "waist, and keep their own pose and expression. It must read as a " +
                "high-end idol concept photo, not a cartoon and not a costume party. Do " +
                "not add text, logos, watermarks, or frames.",
        ),
    )

    fun byId(id: String): AiStyle = ALL.firstOrNull { it.id == id } ?: ALL.first()
}
