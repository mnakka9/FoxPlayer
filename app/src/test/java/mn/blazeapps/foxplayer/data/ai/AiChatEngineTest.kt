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
    fun testWebSearchDefinitionQuery() = runBlocking {
        val result = aiChatEngine.processQuery(
            query = "define serendipity",
            book = testBook,
            chapters = testChapters,
            bookmarks = emptyList(),
        )

        assertEquals(ChatSender.Assistant, result.sender)
        assertTrue(result.text.isNotBlank())
        // Should have found information from Wikipedia, DuckDuckGo, or Brave
        assertTrue(result.sources.isNotEmpty())
        assertTrue(
            result.sources.any { it.type == "Wikipedia" || it.type == "DuckDuckGo" || it.type == "Brave Search" }
        )
    }

    @Test
    fun testWebSearchGeneralTopicQuery() = runBlocking {
        val result = aiChatEngine.processQuery(
            query = "stoicism",
            book = testBook,
            chapters = testChapters,
            bookmarks = emptyList(),
        )

        assertEquals(ChatSender.Assistant, result.sender)
        assertTrue(result.text.isNotBlank())
        assertTrue(result.sources.isNotEmpty())
        assertTrue(
            result.sources.any { it.type == "Wikipedia" || it.type == "DuckDuckGo" || it.type == "Brave Search" }
        )
    }

    @Test
    fun testSanitizeCompleteSentencesDanglingPreposition() {
        val raw = "The eruption of Lake Toba occurred around 74,000 years ago, during..."
        val healed = mn.blazeapps.foxplayer.data.onnx.OnnxBookMetadataEngine.sanitizeCompleteSentences(raw)
        assertEquals("The eruption of Lake Toba occurred around 74,000 years ago.", healed)
    }

    @Test
    fun testSanitizeCompleteSentencesMultiParagraphPreservation() {
        val raw = "First paragraph with complete thought.\n\nSecond paragraph also complete."
        val healed = mn.blazeapps.foxplayer.data.onnx.OnnxBookMetadataEngine.sanitizeCompleteSentences(raw)
        assertEquals("First paragraph with complete thought.\n\nSecond paragraph also complete.", healed)
    }

    @Test
    fun testSanitizeCompleteSentencesRollbackToTerminal() {
        val raw = "Toba is a large caldera. A massive supervolcano eruption occurred during the late Pleistocene..."
        val healed = mn.blazeapps.foxplayer.data.onnx.OnnxBookMetadataEngine.sanitizeCompleteSentences(raw)
        assertTrue(healed.endsWith("."))
        assertFalse(healed.contains("..."))
    }

    @Test
    fun testStripMarkdownForPlainText() {
        val md = "### 🌐 Lake Toba\n*(Volcano in Indonesia)*\n\n**Key Details:**\n> Massive caldera lake."
        val plain = mn.blazeapps.foxplayer.ui.book.stripMarkdownForPlainText(md)
        assertFalse(plain.contains("###"))
        assertFalse(plain.contains("**"))
        assertFalse(plain.contains(">"))
        assertTrue(plain.contains("Lake Toba"))
        assertTrue(plain.contains("Massive caldera lake."))
    }

    @Test
    fun testParseSeriesDetailsWithNextBook() {
        val sampleText = "The Way of Kings is an epic fantasy novel by Brandon Sanderson and the first book in The Stormlight Archive series. It was followed by Words of Radiance in 2014."
        val parsed = aiChatEngine.parseSeriesDetails("The Way of Kings", sampleText)
        assertEquals("The Stormlight Archive", parsed.seriesName)
        assertEquals("Book 1", parsed.seriesOrder)
        assertEquals("Words of Radiance", parsed.nextBook)
        assertFalse(parsed.isStandalone)
    }

    @Test
    fun testParseSeriesDetailsStandalone() {
        val sampleText = "Elantris is a standalone novel written by Brandon Sanderson, set in the Cosmere universe."
        val parsed = aiChatEngine.parseSeriesDetails("Elantris", sampleText)
        assertTrue(parsed.isStandalone)
        assertNull(parsed.nextBook)
    }

    @Test
    fun testParseAuthorBestSellers() {
        val sampleText = "Brandon Sanderson is best known for \"Mistborn: The Final Empire\", \"Words of Radiance\", and \"The Way of Kings\"."
        val bestsellers = aiChatEngine.parseAuthorBestSellers("The Way of Kings", "Brandon Sanderson", sampleText)
        assertTrue(bestsellers.contains("Mistborn: The Final Empire"))
        assertTrue(bestsellers.contains("Words of Radiance"))
        // Current book title should be excluded
        assertFalse(bestsellers.contains("The Way of Kings"))
        // Author name should be excluded
        assertFalse(bestsellers.contains("Brandon Sanderson"))
    }
}
