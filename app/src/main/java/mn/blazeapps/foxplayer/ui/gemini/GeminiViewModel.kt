package mn.blazeapps.foxplayer.ui.gemini

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mn.blazeapps.foxplayer.FoxPlayerApplication
import mn.blazeapps.foxplayer.data.auth.AuthState
import mn.blazeapps.foxplayer.data.gemini.GeminiChatMessage
import mn.blazeapps.foxplayer.data.gemini.MessageSender
import java.util.UUID

class GeminiViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FoxPlayerApplication
    private val authManager = app.container.authManager
    private val chatEngine = app.container.geminiChatEngine
    private val preferences = app.container.geminiPreferences
    private val repository = app.container.repository

    val authState: StateFlow<AuthState> = authManager.authState
    val apiKeyConfigured: Boolean get() = preferences.isApiKeyConfigured()
    val customApiKey: StateFlow<String> = preferences.customApiKey

    private val _messages = MutableStateFlow<List<GeminiChatMessage>>(emptyList())
    val messages: StateFlow<List<GeminiChatMessage>> = _messages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isContextEnabled = MutableStateFlow(true)
    val isContextEnabled: StateFlow<Boolean> = _isContextEnabled.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    var bookId: Long? = null
        private set
    var bookTitle: String? = null
        private set
    var bookAuthor: String? = null
        private set

    val suggestedPrompts: List<String>
        get() {
            val title = bookTitle
            return if (!title.isNullOrBlank()) {
                listOf(
                    "What are the central themes of $title?",
                    "Analyze the main characters in $title",
                    "Historical background and author context",
                    "Audiobooks with a similar style or atmosphere",
                )
            } else {
                listOf(
                    "Recommend an immersive sci-fi audiobook",
                    "What makes great audiobook narration?",
                    "Classic fantasy sagas worth listening to",
                    "Tips for retaining details from audiobooks",
                )
            }
        }

    fun setBookContext(id: Long?, title: String?, author: String?) {
        this.bookId = id
        this.bookTitle = title
        this.bookAuthor = author

        if (_messages.value.isEmpty()) {
            val welcomeText = if (!title.isNullOrBlank()) {
                val authorPart = if (!author.isNullOrBlank()) " by $author" else ""
                "Welcome to Gemini AI Companion! ✦\n\nI'm ready to discuss **\"$title\"**$authorPart with you. Ask me about plot developments, thematic motifs, character motivations, or author lore."
            } else {
                "Welcome to Gemini AI Companion! ✦\n\nI can analyze your audiobooks, explore plot depth, clarify complex timelines, and recommend next listens."
            }
            _messages.value = listOf(
                GeminiChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.GEMINI,
                    text = welcomeText,
                    isStreaming = false,
                )
            )
        }
    }

    fun toggleContext() {
        _isContextEnabled.value = !_isContextEnabled.value
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearChat() {
        chatEngine.resetChat()
        _messages.value = emptyList()
        setBookContext(bookId, bookTitle, bookAuthor)
    }

    fun updateApiKey(newKey: String) {
        preferences.setCustomApiKey(newKey)
        chatEngine.resetChat()
    }

    fun signInAnonymously(onComplete: ((Boolean, String?) -> Unit)? = null) {
        authManager.signInAnonymously { success, error ->
            onComplete?.invoke(success, error)
        }
    }

    fun signInWithEmail(email: String, pass: String, onComplete: (Boolean, String?) -> Unit) {
        authManager.signInWithEmail(email, pass, onComplete)
    }

    fun signUpWithEmail(email: String, pass: String, onComplete: (Boolean, String?) -> Unit) {
        authManager.signUpWithEmail(email, pass, onComplete)
    }

    fun signOut() {
        authManager.signOut()
    }

    fun sendMessage(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || _isStreaming.value) return

        if (!preferences.isApiKeyConfigured()) {
            _errorMessage.value = "Please configure your Gemini API Key in Settings or via AI Studio."
            return
        }

        val userMsg = GeminiChatMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.USER,
            text = trimmed,
        )

        val geminiMsgId = UUID.randomUUID().toString()
        val streamingMsg = GeminiChatMessage(
            id = geminiMsgId,
            sender = MessageSender.GEMINI,
            text = "...",
            isStreaming = true,
        )

        _messages.value = _messages.value + userMsg + streamingMsg
        _isStreaming.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            val result = chatEngine.streamMessage(
                userPrompt = trimmed,
                bookTitle = bookTitle,
                bookAuthor = bookAuthor,
                includeContext = _isContextEnabled.value,
            ) { _, fullTextSoFar ->
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == geminiMsgId) {
                        msg.copy(text = fullTextSoFar, isStreaming = true)
                    } else msg
                }
            }

            _isStreaming.value = false
            result.onSuccess { finalText ->
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == geminiMsgId) {
                        msg.copy(text = finalText, isStreaming = false)
                    } else msg
                }
            }.onFailure { err ->
                val errorMsg = err.localizedMessage ?: "Failed to generate response"
                _errorMessage.value = errorMsg
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == geminiMsgId) {
                        msg.copy(
                            text = "⚠️ Could not generate response: $errorMsg\n\nPlease check your internet connection or verify your Gemini API key in Settings.",
                            isStreaming = false,
                        )
                    } else msg
                }
            }
        }
    }

    fun saveNoteForBook(noteText: String, onComplete: (Boolean) -> Unit) {
        val bId = bookId ?: return onComplete(false)
        viewModelScope.launch {
            try {
                val chapters = repository.getChapters(bId)
                val targetChapter = chapters.firstOrNull() ?: return@launch onComplete(false)
                repository.addBookmark(bId, targetChapter.id, 0L, noteText)
                onComplete(true)
            } catch (_: Exception) {
                onComplete(false)
            }
        }
    }
}
