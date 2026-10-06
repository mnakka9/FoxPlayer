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

    @Test
    fun testCleanSearchTopicFusedWordsAndPrefixes() {
        assertEquals("zeus greek god", aiChatEngine.cleanSearchTopic("give detailsabout zeus greek god"))
        assertEquals("zeus", aiChatEngine.cleanSearchTopic("detailsabout zeus"))
        assertEquals("zeus greek god", aiChatEngine.cleanSearchTopic("give details about zeus greek god"))
        assertEquals("apollo", aiChatEngine.cleanSearchTopic("tellme about apollo"))
        assertEquals("zeus", aiChatEngine.cleanSearchTopic("who is zeus"))
        assertEquals("poseidon", aiChatEngine.cleanSearchTopic("can you please give details about poseidon"))
        assertEquals("hades", aiChatEngine.cleanSearchTopic("give me more info on hades"))
    }

    @Test
    fun testProcessQueryZeusGreekGod() = runBlocking {
        val result = aiChatEngine.processQuery(
            query = "give detailsabout zeus greek god",
            book = testBook,
            chapters = testChapters,
            bookmarks = emptyList(),
        )

        assertEquals(ChatSender.Assistant, result.sender)
        assertTrue(result.text.isNotBlank())
        assertTrue("Expected response to mention Zeus, got: ${result.text}", result.text.contains("Zeus", ignoreCase = true))
        assertTrue(result.sources.isNotEmpty())
        assertTrue(
            result.sources.any { it.type == "Wikipedia" || it.type == "DuckDuckGo" || it.type == "Brave Search" }
        )
    }

    @Test
    fun testBookMetadataAuthorQuery() = runBlocking {
        val result = aiChatEngine.processQuery(
            query = "who is the author",
            book = testBook,
            chapters = testChapters,
            bookmarks = emptyList(),
        )

        assertEquals(ChatSender.Assistant, result.sender)
        assertTrue(result.text.contains("Mark Torr"))
        assertEquals("Audiobook Metadata", result.sources[0].type)
    }

    @Test
    fun testBookMetadataChaptersQuery() = runBlocking {
        val result = aiChatEngine.processQuery(
            query = "how many chapters are there",
            book = testBook,
            chapters = testChapters,
            bookmarks = emptyList(),
        )

        assertEquals(ChatSender.Assistant, result.sender)
        assertTrue(result.text.contains("2 chapters"))
        assertEquals("Audiobook Metadata", result.sources[0].type)
    }

    @Test
    fun testLocalChatEngineModeFromString() {
        assertEquals(LocalChatEngineMode.FOUNDRY_LOCAL, LocalChatEngineMode.fromString("FOUNDRY_LOCAL"))
        assertEquals(LocalChatEngineMode.FOUNDRY_LOCAL, LocalChatEngineMode.fromString("foundry_local"))
        assertEquals(LocalChatEngineMode.FAST_LOCAL, LocalChatEngineMode.fromString("FAST_LOCAL"))
        assertEquals(LocalChatEngineMode.FAST_LOCAL, LocalChatEngineMode.fromString("unknown"))
        assertEquals(LocalChatEngineMode.FAST_LOCAL, LocalChatEngineMode.fromString(null))
    }

    @Test
    fun testFoundryLocalOfflineFallback() = runBlocking {
        // Engine with uncontactable endpoint and Foundry Local mode
        val engine = AiChatEngine(
            localLlmClient = LocalLlmClient(endpointUrl = "http://127.0.0.1:59999/v1"),
        )
        // With default fast local, it runs directly
        val fastResult = engine.processQuery(
            query = "define serendipity",
            book = testBook,
            chapters = testChapters,
            bookmarks = emptyList(),
        )
        assertEquals(ChatSender.Assistant, fastResult.sender)
        assertTrue(fastResult.text.isNotBlank())
    }

    @Test
    fun testFoundryIpcManagerPackageConstants() {
        assertEquals("com.microsoft.foundrylocal.app", FoundryIpcManager.FOUNDRY_APP_PACKAGE)
        assertEquals("market://details?id=com.microsoft.foundrylocal.app", FoundryIpcManager.PLAY_STORE_MARKET_URI)
    }

    @Test
    fun testFoundryDownloadStateTransitions() {
        val idle = FoundryIpcManager.FoundryDownloadState.Idle
        val downloading = FoundryIpcManager.FoundryDownloadState.Downloading("qwen2.5-0.5b", 0.45f)
        val ready = FoundryIpcManager.FoundryDownloadState.Ready("qwen2.5-0.5b")
        val error = FoundryIpcManager.FoundryDownloadState.Error("qwen2.5-0.5b", "Network error")

        assertEquals(FoundryIpcManager.FoundryDownloadState.Idle, idle)
        assertEquals("qwen2.5-0.5b", downloading.modelAlias)
        assertEquals(0.45f, downloading.progress, 0.001f)
        assertEquals("qwen2.5-0.5b", ready.modelAlias)
        assertEquals("Network error", error.message)
    }

    @Test
    fun testFoundryIpcStatusWithCachedModels() {
        val status = FoundryIpcManager.IpcStatus(
            isSupportedOs = true,
            isAppInstalled = true,
            isConnected = true,
            models = listOf("qwen2.5-0.5b", "phi-3.5-mini"),
            cachedModels = listOf("qwen2.5-0.5b"),
            loadedModels = emptyList(),
            latencyMs = 12,
            message = "Connected via IPC (12ms) · 1 model(s) downloaded",
        )
        assertTrue(status.isConnected)
        assertEquals(1, status.cachedModels.size)
        assertEquals("qwen2.5-0.5b", status.cachedModels[0])
    }

    @Test
    fun testNormalizeModelAlias() {
        assertEquals("qwen2.5-0.5b", FoundryIpcManager.normalizeModelAlias("qwen2.5-0.5b-instruct"))
        assertEquals("qwen2.5-1.5b", FoundryIpcManager.normalizeModelAlias("qwen2.5-1.5b-instruct"))
        assertEquals("qwen2.5-0.5b", FoundryIpcManager.normalizeModelAlias("qwen2.5-0.5b"))
        assertEquals("phi-3.5-mini", FoundryIpcManager.normalizeModelAlias("phi-3.5-mini"))
    }
}

