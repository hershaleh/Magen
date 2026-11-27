package app.magen.lock.core

/** Which apps VideoStop watches. */
enum class VideoScope { ALL, ONLY_LISTED, ALL_EXCEPT_LISTED }

/** The VideoStop rule (spec section 5.2). Off by default. */
data class VideoRule(
    val enabled: Boolean = false,
    val scope: VideoScope = VideoScope.ALL,
    val packages: Set<String> = emptySet(),
) {
    /** Whether VideoStop looks at [pkg]. Essential apps are filtered out by the caller. */
    fun applies(pkg: String): Boolean = enabled && when (scope) {
        VideoScope.ALL -> true
        VideoScope.ONLY_LISTED -> pkg in packages
        VideoScope.ALL_EXCEPT_LISTED -> pkg !in packages
    }
}

/** What a screen shows, reduced to the facts the detector looks at. Built from the accessibility tree. */
class VideoSignals(
    val viewIds: List<String> = emptyList(),
    val classNames: List<String> = emptyList(),
    val descriptions: List<String> = emptyList(),
)

/**
 * Recognises a video player on screen from its parts: well-known player view ids and classes, and the labels of
 * play controls. It sees player elements, not moving pictures, so it can miss a player that hides its controls
 * and can mistake a still image with a play button for a video. It never looks at web addresses or page content.
 */
object VideoDetector {
    private val idMarkers = listOf(
        "exo_player_view", "exo_controller", "exo_play", "exo_pause", "exo_progress",
        "player_view", "playerview", "video_player", "videoplayer", "video_view", "videoview",
        "watch_player", "texture_video", "player_control",
    )
    private val classMarkers = listOf("videoview", "exoplayer", "playerview", "styledplayerview", "textureview.video")
    private val labelMarkers = listOf(
        "play video", "pause video", "playing video", "video player", "fullscreen video", "full screen video",
        "הפעל סרטון", "השהה סרטון", "נגן וידאו", "נגן סרטונים", "סרטון בטעינה",
    )

    fun detect(signals: VideoSignals): Boolean {
        if (signals.viewIds.any { id -> idMarkers.any { id.substringAfter(":id/").lowercase().contains(it) } }) return true
        if (signals.classNames.any { c -> classMarkers.any { c.lowercase().contains(it) } }) return true
        return signals.descriptions.any { d -> labelMarkers.any { d.lowercase().contains(it) } }
    }
}
