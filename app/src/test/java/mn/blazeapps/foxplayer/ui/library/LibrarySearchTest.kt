package mn.blazeapps.foxplayer.ui.library

import mn.blazeapps.foxplayer.data.LibraryBook
import mn.blazeapps.foxplayer.data.entities.BookEntity
import mn.blazeapps.foxplayer.data.entities.ChapterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {
    private val dune = book(
        id = 1,
        title = "Dune",
        author = "Frank Herbert",
        progressChapter = 1,
        progressMs = 20_000,
        chapters = listOf("Chapter 1", "Arrakis"),
    )
    private val newBook = book(
        id = 2,
        title = "Project Hail Mary",
        author = "Andy Weir",
        chapters = listOf("Prologue", "Chapter 1"),
    )
    private val finished = book(
        id = 3,
        title = "The Hobbit",
        author = "J.R.R. Tolkien",
        progressChapter = 2,
        progressMs = 60_000,
        chapters = listOf("An Unexpected Party", "Riddles in the Dark"),
        chapterDurationMs = 30_000,
    )
    private val library = listOf(dune, newBook, finished)

    @Test
    fun emptyQueryAndAllFilterKeepsEveryBook() {
        assertEquals(library, library.filterLibrary("", LibraryFilter.ALL))
    }

    @Test
    fun searchMatchesTitleAuthorAndChapter() {
        assertEquals(listOf(dune), library.filterLibrary("dune", LibraryFilter.ALL))
        assertEquals(listOf(newBook), library.filterLibrary("weir", LibraryFilter.ALL))
        assertEquals(listOf(dune), library.filterLibrary("arrakis", LibraryFilter.ALL))
    }

    @Test
    fun searchIsCaseInsensitiveAndTrimsWhitespace() {
        assertEquals(listOf(finished), library.filterLibrary("  HOBBIT  ", LibraryFilter.ALL))
    }

    @Test
    fun filtersByProgress() {
        assertEquals(listOf(newBook), library.filterLibrary("", LibraryFilter.NEW))
        assertEquals(listOf(dune), library.filterLibrary("", LibraryFilter.IN_PROGRESS))
        assertEquals(listOf(finished), library.filterLibrary("", LibraryFilter.FINISHED))
    }

    @Test
    fun searchAndFilterCombine() {
        assertTrue(library.filterLibrary("dune", LibraryFilter.FINISHED).isEmpty())
        assertEquals(listOf(dune), library.filterLibrary("herbert", LibraryFilter.IN_PROGRESS))
    }

    private fun book(
        id: Long,
        title: String,
        author: String,
        chapters: List<String>,
        progressChapter: Int? = null,
        progressMs: Long = 0,
        chapterDurationMs: Long = 60_000,
    ): LibraryBook {
        val entity = BookEntity(
            id = id,
            title = title,
            author = author,
            treeUri = "content://tree/$id",
            lastChapterId = progressChapter?.toLong(),
            lastPositionMs = progressMs,
        )
        val chapterEntities = chapters.mapIndexed { index, name ->
            ChapterEntity(
                id = (index + 1).toLong(),
                bookId = id,
                displayName = name,
                documentUri = "content://doc/$id/$index",
                durationMs = chapterDurationMs,
                sortIndex = index,
            )
        }
        return LibraryBook(entity, chapterEntities)
    }
}
