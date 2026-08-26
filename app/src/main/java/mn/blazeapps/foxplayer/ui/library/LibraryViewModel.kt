package mn.blazeapps.foxplayer.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import mn.blazeapps.foxplayer.FoxPlayerApplication
import mn.blazeapps.foxplayer.data.ImportException
import mn.blazeapps.foxplayer.data.LibraryBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as FoxPlayerApplication).container
    private val repository = container.repository

    val books: StateFlow<List<LibraryBook>> = repository.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    init {
        viewModelScope.launch {
            repository.refreshAccessFlags()
        }
    }

    fun importFolder(uri: Uri, rebindBookId: Long? = null) {
        viewModelScope.launch {
            _importing.value = true
            try {
                repository.importFolder(uri, rebindBookId)
                _message.value = if (rebindBookId != null) "Folder access restored." else "Book added to your library."
            } catch (e: ImportException) {
                _message.value = e.message
            } catch (e: Exception) {
                _message.value = e.message ?: "Could not import that folder."
            } finally {
                _importing.value = false
            }
        }
    }

    fun removeBook(bookId: Long) {
        viewModelScope.launch {
            repository.removeBook(bookId)
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
