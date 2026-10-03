package mn.blazeapps.foxplayer.data.ai

import mn.blazeapps.foxplayer.data.BookmarkWithChapter
import mn.blazeapps.foxplayer.data.entities.BookEntity
import mn.blazeapps.foxplayer.data.entities.BookmarkEntity
import mn.blazeapps.foxplayer.data.entities.ChapterEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AiChatEngineTest {

    private lateinit var aiChatEngine: AiChatEngine

    private val testBook = BookEntity(
        id = 1L,
        title = "Storm Mage",
        author = "Mark Torr",
        treeUri = "content://tree/test",
        genres = "Science Fiction, Fantasy",
        description = "Surf, sun, and power over lightning itself.",
    )

    private val testChapters = listOf(
        ChapterEntity(
            id = 10L,
            bookId = 1L,
            displayName = "Chapter 1",
            documentUri = "content://file/1",
            durationMs = 180000L,
            sortIndex = 0,
        ),
        ChapterEntity(
            id = 20L,
            bookId = 1L,
            displayName = "Chapter 2",
            documentUri = "content://file/2",
            durationMs = 240000L,
            sortIndex = 1,
        ),
    )

    @Before
    fun setUp() {
        aiChatEngine = AiChatEngine()
    }

    @Test
    fun testBookmarkSummaryWithEmptyBookmarks() = runBlocking {
        val result = aiChatEngine.processQuery(
            query = "Summarize my bookmark notes",
            book = testBook,
            chapters = testChapters,
            bookmarks = emptyList(),
        )

        assertEquals(ChatSender.Assistant, result.sender)
        assertTrue(result.text.contains("don't have any bookmarks"))
    }

    @Test
    fun testBookmarkSummaryWithPopulatedNotes() = runBlocking {
        val bookmarks = listOf(
            BookmarkWithChapter(
                bookmark = BookmarkEntity(id = 1L, bookId = 1L, chapterId = 10L, positionMs = 60000L, note = "First encounter with mermaid"),
                chapter = testChapters[0],
            ),
            BookmarkWithChapter(
                bookmark = BookmarkEntity(id = 2L, bookId = 1L, chapterId = 20L, positionMs = 120000L, note = "Learns lightning bending"),
                chapter = testChapters[1],
            ),
        )

        val result = aiChatEngine.processQuery(
            query = "Summarize all my notes",
            book = testBook,
            chapters = testChapters,
            bookmarks = bookmarks,
        )

        assertEquals(ChatSender.Assistant, result.sender)
        assertTrue(result.text.contains("Bookmark Notes Summary"))
        assertTrue(result.text.contains("First encounter with mermaid"))
        assertTrue(result.text.contains("Learns lightning bending"))
        assertEquals("Bookmarks", result.sources[0].type)
    }

    @Test
    fun testBookmarkSearchKeyword() = runBlocking {
        val bookmarks = listOf(
            BookmarkWithChapter(
                bookmark = BookmarkEntity(id = 1L, bookId = 1L, chapterId = 10L, positionMs = 60000L, note = "First encounter with mermaid"),
                chapter = testChapters[0],
            ),
            BookmarkWithChapter(
                bookmark = BookmarkEntity(id = 2L, bookId = 1L, chapterId = 20L, positionMs = 120000L, note = "Learns lightning bending"),
                chapter = testChapters[1],
            ),
        )

        val result = aiChatEngine.processQuery(
            query = "did I make a note about lightning?",
            book = testBook,
            chapters = testChapters,
            bookmarks = bookmarks,
        )

        assertEquals(ChatSender.Assistant, result.sender)
        assertTrue(result.text.contains("Learns lightning bending"))
    }

    @Test
    fun testBookMetadataQuery() = runBlocking {
        val result = aiChatEngine.processQuery(
            query = "What is this book about?",
            book = testBook,
            chapters = testChapters,
            bookmarks = emptyList(),
        )

        assertEquals(ChatSender.Assistant, result.sender)
        assertTrue(result.text.contains("Storm Mage"))
        assertTrue(result.text.contains("Mark Torr"))
        assertTrue(result.text.contains("Surf, sun, and power over lightning itself"))
    }

    @Test
    fun testDictionaryQuery() = runBlocking {
        val result = aiChatEngine.processQuery(
            query = "define epiphany",
            book = testBook,
            chapters = testChapters,
            bookmarks = emptyList(),
        )

        assertEquals(ChatSender.Assistant, result.sender)
        assertTrue(result.text.isNotBlank())
        assertTrue(result.sources.isNotEmpty())
        val isDictionary = result.sources.any { it.type == "Dictionary" }
        val isFallback = result.sources.any { it.type == "Book Context" }
        assertTrue(isDictionary || isFallback)
    }
}
