package mn.blazeapps.foxplayer.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mn.blazeapps.foxplayer.FoxPlayerApplication
import mn.blazeapps.foxplayer.data.ImportException
import mn.blazeapps.foxplayer.data.LibraryBook

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as FoxPlayerApplication).container
    private val repository = container.repository

    val books: StateFlow<List<LibraryBook>> = repository.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    val filter: StateFlow<LibraryFilter> = _filter.asStateFlow()

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre: StateFlow<String?> = _selectedGenre.asStateFlow()

    val availableGenres: StateFlow<List<String>> = books.map { bookList ->
        bookList.flatMap { parseGenres(it.book.genres) }
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val visibleBooks: StateFlow<List<LibraryBook>> = combine(
        books,
        _searchQuery,
        _filter,
        _selectedGenre,
    ) { list, query, selectedFilter, genre ->
        list.filterLibrary(query, selectedFilter, genre)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    init {
        viewModelScope.launch {
            repository.refreshAccessFlags()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: LibraryFilter) {
        _filter.value = filter
    }

    fun setSelectedGenre(genre: String?) {
        _selectedGenre.value = if (_selectedGenre.value.equals(genre, ignoreCase = true)) null else genre
    }

    fun updateBookGenres(bookId: Long, genres: String) {
        viewModelScope.launch {
            repository.updateGenres(bookId, genres)
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
