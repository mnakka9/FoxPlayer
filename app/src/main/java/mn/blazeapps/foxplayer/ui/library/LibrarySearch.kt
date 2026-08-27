package mn.blazeapps.foxplayer.ui.library

import mn.blazeapps.foxplayer.data.LibraryBook

enum class LibraryFilter {
    ALL,
    NEW,
    IN_PROGRESS,
    FINISHED,
}

fun List<LibraryBook>.filterLibrary(query: String, selectedFilter: LibraryFilter): List<LibraryBook> {
    val needle = query.trim()
    return filter { book ->
        book.matchesFilter(selectedFilter) && book.matchesQuery(needle)
    }
}

fun LibraryBook.matchesFilter(filter: LibraryFilter): Boolean = when (filter) {
    LibraryFilter.ALL -> true
    LibraryFilter.NEW -> progressPercent <= 0
    LibraryFilter.IN_PROGRESS -> progressPercent in 1..99
    LibraryFilter.FINISHED -> progressPercent >= 100
}

fun LibraryBook.matchesQuery(query: String): Boolean {
    if (query.isEmpty()) return true
    if (book.title.contains(query, ignoreCase = true)) return true
    if (book.author.orEmpty().contains(query, ignoreCase = true)) return true
    return chapters.any { it.displayName.contains(query, ignoreCase = true) }
}
