package mn.blazeapps.foxplayer.ui.book

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import mn.blazeapps.foxplayer.FoxPlayerApplication
import mn.blazeapps.foxplayer.data.BookmarkWithChapter
import mn.blazeapps.foxplayer.data.entities.BookEntity
import mn.blazeapps.foxplayer.data.entities.BookmarkEntity
import mn.blazeapps.foxplayer.data.entities.ChapterEntity
import mn.blazeapps.foxplayer.playback.PlaybackManager
import mn.blazeapps.foxplayer.playback.PlayerUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DetailPane { Chapters, Bookmarks }

@OptIn(ExperimentalCoroutinesApi::class)
class BookViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as FoxPlayerApplication).container
    private val repository = container.repository
    val playback: PlaybackManager = container.playbackManager

    private val bookId = MutableStateFlow<Long?>(null)
    val pane = MutableStateFlow(DetailPane.Chapters)

    val book: StateFlow<BookEntity?> = bookId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.observeBook(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chapters: StateFlow<List<ChapterEntity>> = bookId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeChapters(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bookmarks: StateFlow<List<BookmarkWithChapter>> = bookId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeBookmarks(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val player: StateFlow<PlayerUiState> = playback.state

    fun open(id: Long) {
        if (bookId.value != id) {
            pane.value = DetailPane.Chapters
        }
        bookId.value = id
        viewModelScope.launch {
            ensureThisBookLoaded(autoPlay = false)
        }
    }

    fun setPane(value: DetailPane) {
        pane.value = value
    }

    fun playPause() {
        viewModelScope.launch {
            ensureThisBookLoaded(autoPlay = false)
            playback.playPause()
        }
    }

    fun seekBack() {
        if (!isCurrentBook()) return
        playback.seekBack()
    }

    fun seekForward() {
        if (!isCurrentBook()) return
        playback.seekForward()
    }

    fun seekTo(positionMs: Long) {
        if (!isCurrentBook()) return
        playback.seekTo(positionMs)
    }

    fun setSpeed(speed: Float) {
        if (!isCurrentBook()) return
        playback.setSpeed(speed)
    }

    fun jumpToChapter(index: Int) {
        viewModelScope.launch {
            ensureThisBookLoaded(autoPlay = false)
            playback.jumpToChapter(index)
        }
    }

    fun jumpToBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            ensureThisBookLoaded(autoPlay = false)
            playback.jumpToChapterId(bookmark.chapterId, bookmark.positionMs)
        }
    }

    private fun isCurrentBook(): Boolean =
        bookId.value != null && playback.state.value.bookId == bookId.value

    private suspend fun ensureThisBookLoaded(autoPlay: Boolean) {
        val id = bookId.value ?: return
        if (playback.state.value.bookId == id) return
        val loaded = repository.getBook(id) ?: return
        val chapterList = repository.getChapters(id)
        if (chapterList.isEmpty()) return
        playback.playBook(loaded, chapterList, autoPlay = autoPlay)
    }

    fun addBookmark(note: String) {
        viewModelScope.launch {
            ensureThisBookLoaded(autoPlay = false)
            val state = playback.state.value
            val id = bookId.value ?: return@launch
            if (state.bookId != id) return@launch
            val chapterId = state.chapterId ?: return@launch
            repository.addBookmark(id, chapterId, state.positionMs, note)
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmark)
        }
    }
}
