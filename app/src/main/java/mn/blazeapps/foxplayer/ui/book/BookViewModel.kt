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
import mn.blazeapps.foxplayer.data.ai.AiChatMessage
import mn.blazeapps.foxplayer.data.ai.BookSeriesAndAuthorInfo
import mn.blazeapps.foxplayer.data.ai.ChatQueryScope
import mn.blazeapps.foxplayer.data.ai.ChatSender
import mn.blazeapps.foxplayer.data.ai.ChatSource
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

    val chatMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val isChatGenerating = MutableStateFlow(false)

    val bookSeriesInfo = MutableStateFlow<BookSeriesAndAuthorInfo?>(null)
    val isFetchingBookInfo = MutableStateFlow(false)

    fun open(id: Long) {
        if (bookId.value != id) {
            pane.value = DetailPane.Chapters
            bookSeriesInfo.value = null
            initChatForBook()
        } else if (chatMessages.value.isEmpty()) {
            initChatForBook()
        }
        bookId.value = id
        viewModelScope.launch {
            ensureThisBookLoaded(autoPlay = false)
        }
    }

    fun loadBookInfo(forceRefresh: Boolean = false) {
        val id = bookId.value ?: return
        if (!forceRefresh && bookSeriesInfo.value != null) return
        if (isFetchingBookInfo.value) return

        viewModelScope.launch {
            isFetchingBookInfo.value = true
            try {
                val info = repository.fetchBookSeriesInfo(id)
                bookSeriesInfo.value = info
            } catch (_: Exception) {
            } finally {
                isFetchingBookInfo.value = false
            }
        }
    }

    fun initChatForBook() {
        val currentBook = book.value
        val title = currentBook?.title ?: "this audiobook"
        chatMessages.value = listOf(
            AiChatMessage(
                sender = ChatSender.Assistant,
                text = "Hello! I am your AI Audiobook Companion for **$title**.\n\n" +
                    "Here are a few things you can ask me:\n" +
                    "• 📝 **\"Summarize my notes\"** — review and organize your bookmark notes\n" +
                    "• 📖 **\"Define [word]\"** — explore definitions & literary expressions\n" +
                    "• 🌐 **\"Historical context of [topic]\"** — search encyclopedia lore online\n" +
                    "• 🔍 **\"What is this book about?\"** — explore plot, themes & characters\n\n" +
                    "How can I assist your listening today?",
                sources = listOf(ChatSource("AI Assistant", "Companion")),
            )
        )
    }

    fun sendChatMessage(query: String, scope: ChatQueryScope = ChatQueryScope.AUTO) {
        val trimmed = query.trim()
        if (trimmed.isBlank() || isChatGenerating.value) return
        val id = bookId.value ?: return

        val userMsg = AiChatMessage(
            sender = ChatSender.User,
            text = trimmed,
        )
        chatMessages.value = chatMessages.value + userMsg

        viewModelScope.launch {
            isChatGenerating.value = true
            try {
                val response = repository.processAiChat(
                    bookId = id,
                    query = trimmed,
                    bookmarksList = bookmarks.value,
                    scope = scope,
                )
                chatMessages.value = chatMessages.value + response
            } catch (e: Exception) {
                chatMessages.value = chatMessages.value + AiChatMessage(
                    sender = ChatSender.Assistant,
                    text = "I encountered an error processing your inquiry: ${e.message}",
                    sources = listOf(ChatSource("Error", "System")),
                )
            } finally {
                isChatGenerating.value = false
            }
        }
    }

    fun clearChat() {
        initChatForBook()
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
        viewModelScope.launch {
            ensureThisBookLoaded(autoPlay = false)
            playback.setSpeed(speed)
        }
    }

    val modelDownloadState = repository.onnxModelManager.downloadState

    fun isModelDownloaded(): Boolean = repository.onnxModelManager.isModelDownloaded()
    fun getModelSizeBytes(): Long = repository.onnxModelManager.getModelSizeBytes()
    fun getFreeSpaceBytes(): Long = repository.onnxModelManager.getFreeSpaceBytes()

    fun downloadOnnxModel() {
        viewModelScope.launch {
            repository.onnxModelManager.downloadModel()
        }
    }

    fun deleteOnnxModel(): Boolean = repository.onnxModelManager.deleteModel()

    suspend fun enrichBookMetadata(customQuery: String? = null): mn.blazeapps.foxplayer.data.onnx.EnrichedBookMetadata? {
        val currentBook = book.value ?: return null
        return repository.enrichBookMetadata(currentBook.id, customQuery)
    }

    fun applyEnrichedMetadata(genres: String?, description: String?, coverUrl: String?) {
        val id = bookId.value ?: return
        viewModelScope.launch {
            repository.applyEnrichedMetadata(id, genres, description, coverUrl)
        }
    }

    fun updateDescription(description: String) {
        val id = bookId.value ?: return
        viewModelScope.launch {
            repository.updateDescription(id, description)
        }
    }

    fun updateGenres(genres: String) {
        val id = bookId.value ?: return
        viewModelScope.launch {
            repository.updateGenres(id, genres)
        }
    }

    suspend fun autoDetectGenres(): String? {
        val currentBook = book.value ?: return null
        return repository.fetchOnlineGenre(currentBook.title, currentBook.author)
    }

    fun rescanChapters() {
        val id = bookId.value ?: return
        viewModelScope.launch {
            repository.rescanBookChapters(id)
        }
    }

    fun jumpToChapter(index: Int) {
        viewModelScope.launch {
            ensureThisBookLoaded(autoPlay = false)
            playback.jumpToChapter(index)
        }
    }

    fun jumpToBookmark(bookmark: BookmarkEntity) {
        pane.value = DetailPane.Chapters
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
