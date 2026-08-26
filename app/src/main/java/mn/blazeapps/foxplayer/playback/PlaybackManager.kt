package mn.blazeapps.foxplayer.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import mn.blazeapps.foxplayer.data.AudiobookRepository
import mn.blazeapps.foxplayer.data.entities.BookEntity
import mn.blazeapps.foxplayer.data.entities.ChapterEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class PlaybackManager(
    private val context: Context,
    private val repository: AudiobookRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private val connecting = AtomicBoolean(false)
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    fun connect() {
        if (controller != null || !connecting.compareAndSet(false, true)) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                try {
                    val mediaController = future.get()
                    controller = mediaController
                    mediaController.addListener(listener)
                    _state.update { it.copy(connected = true) }
                    publishState()
                    startTicker()
                } finally {
                    connecting.set(false)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    suspend fun playBook(book: BookEntity, chapters: List<ChapterEntity>, autoPlay: Boolean = true) {
        val mediaController = awaitController() ?: return
        val items = chapters.map { chapter ->
            MediaItem.Builder()
                .setMediaId(mediaId(book.id, chapter.id))
                .setUri(Uri.parse(chapter.documentUri))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(chapter.displayName)
                        .setAlbumTitle(book.title)
                        .setArtist(book.title)
                        .apply {
                            book.coverPath?.let { path ->
                                val file = File(path)
                                if (file.exists()) setArtworkUri(Uri.fromFile(file))
                            }
                        }
                        .setExtras(
                            android.os.Bundle().apply {
                                putLong(EXTRA_BOOK_ID, book.id)
                                putLong(EXTRA_CHAPTER_ID, chapter.id)
                            },
                        )
                        .build(),
                )
                .build()
        }
        val startIndex = chapters.indexOfFirst { it.id == book.lastChapterId }.let { index ->
            if (index >= 0) index else 0
        }
        val startPosition =
            if (chapters.getOrNull(startIndex)?.id == book.lastChapterId) {
                book.lastPositionMs.coerceAtLeast(0)
            } else {
                0L
            }
        mediaController.setMediaItems(items, startIndex, startPosition)
        mediaController.prepare()
        if (autoPlay) mediaController.play() else mediaController.pause()
        publishState()
    }

    fun playPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBack() {
        controller?.seekBack()
    }

    fun seekForward() {
        controller?.seekForward()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun jumpToChapter(index: Int, positionMs: Long = 0L) {
        val player = controller ?: return
        if (index in 0 until player.mediaItemCount) {
            player.seekTo(index, positionMs)
            player.play()
        }
    }

    fun jumpToChapterId(chapterId: Long, positionMs: Long = 0L) {
        val player = controller ?: return
        val index = (0 until player.mediaItemCount).firstOrNull { i ->
            chapterIdFromMediaId(player.getMediaItemAt(i).mediaId) == chapterId
        } ?: return
        jumpToChapter(index, positionMs)
    }

    fun currentBookId(): Long? =
        bookIdFromMediaId(controller?.currentMediaItem?.mediaId)
            ?: extras()?.getLong(EXTRA_BOOK_ID)?.takeIf { it != 0L }

    fun currentChapterId(): Long? {
        return chapterIdFromMediaId(controller?.currentMediaItem?.mediaId)
            ?: extras()?.getLong(EXTRA_CHAPTER_ID)?.takeIf { it != 0L }
    }

    suspend fun persistProgress() {
        val bookId = currentBookId() ?: return
        val chapterId = currentChapterId() ?: return
        val position = controller?.currentPosition ?: return
        repository.saveProgress(bookId, chapterId, position)
    }

    private fun extras() = controller?.currentMediaItem?.mediaMetadata?.extras

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishState()
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                events.contains(Player.EVENT_IS_PLAYING_CHANGED)
            ) {
                scope.launch { persistProgress() }
            }
        }
    }

    private fun publishState() {
        val player = controller ?: return
        val extras = extras()
        _state.update {
            it.copy(
                connected = true,
                bookId = bookIdFromMediaId(player.currentMediaItem?.mediaId)
                    ?: extras?.getLong(EXTRA_BOOK_ID)?.takeIf { id -> id != 0L },
                chapterId = chapterIdFromMediaId(player.currentMediaItem?.mediaId)
                    ?: extras?.getLong(EXTRA_CHAPTER_ID)?.takeIf { id -> id != 0L },
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeIf { d -> d > 0 } ?: 0L,
                speed = player.playbackParameters.speed,
                currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            )
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            var ticks = 0
            while (isActive) {
                delay(500)
                publishState()
                ticks++
                if (ticks % 10 == 0 && controller?.isPlaying == true) {
                    persistProgress()
                }
            }
        }
    }

    private suspend fun awaitController(): MediaController? {
        if (controller != null) return controller
        connect()
        repeat(40) {
            controller?.let { return it }
            delay(100)
        }
        return controller
    }

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_CHAPTER_ID = "chapter_id"

        fun mediaId(bookId: Long, chapterId: Long): String = "$bookId:$chapterId"

        fun bookIdFromMediaId(mediaId: String?): Long? {
            if (mediaId.isNullOrBlank() || ':' !in mediaId) return null
            return mediaId.substringBefore(':').toLongOrNull()
        }

        fun chapterIdFromMediaId(mediaId: String?): Long? {
            if (mediaId.isNullOrBlank()) return null
            return if (':' in mediaId) {
                mediaId.substringAfter(':').toLongOrNull()
            } else {
                mediaId.toLongOrNull()
            }
        }
    }
}
