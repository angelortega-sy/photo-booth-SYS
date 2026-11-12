package com.photobooth.kiosk.ui.attract

import androidx.annotation.StringRes
import com.photobooth.kiosk.R

/**
 * On-screen caption shown over a clip for a window of its timeline. The text is
 * drawn by the app and not baked into the video, so it follows the language
 * toggle and stays crisp at any panel size.
 */
internal data class AttractCaption(
    val fromMs: Long,
    val toMs: Long,
    @StringRes val textRes: Int,
)

/**
 * Caption track for the bundled clips. Only the explanatory clip has one: the
 * fantasy loop tells its story without words. Anything the admin imports plays
 * clean, because its timeline has nothing to do with these cues.
 */
internal object AttractCaptions {
    const val EXPLAIN_VIDEO_ID = "asset:attract-explain.mp4"

    // Timings mirror SEGMENTS in tools/attract-video/assemble-explain.mjs: each
    // beat cross-dissolves into the next with a 0.55 s fade, so a cue starts the
    // moment the incoming beat is fully visible and ends before the next fade.
    private val EXPLAIN = listOf(
        AttractCaption(fromMs = 5_000, toMs = 8_400, textRes = R.string.attract_caption_collage),
        AttractCaption(fromMs = 8_800, toMs = 12_900, textRes = R.string.attract_caption_background),
        AttractCaption(fromMs = 13_200, toMs = 16_500, textRes = R.string.attract_caption_layout),
        AttractCaption(fromMs = 16_900, toMs = 18_700, textRes = R.string.attract_caption_ai),
        AttractCaption(fromMs = 18_900, toMs = 20_900, textRes = R.string.attract_caption_style),
        AttractCaption(fromMs = 21_300, toMs = 25_200, textRes = R.string.attract_caption_phone),
    )

    fun forVideo(videoId: String): List<AttractCaption> = when (videoId) {
        EXPLAIN_VIDEO_ID -> EXPLAIN
        else -> emptyList()
    }
}
