package mn.blazeapps.foxplayer.playback

data class PlayerUiState(
    val connected: Boolean = false,
    val bookId: Long? = null,
    val chapterId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val currentIndex: Int = 0,
)
