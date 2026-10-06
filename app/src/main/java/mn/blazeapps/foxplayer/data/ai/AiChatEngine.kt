package mn.blazeapps.foxplayer.data.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mn.blazeapps.foxplayer.data.BookmarkWithChapter
import mn.blazeapps.foxplayer.data.entities.BookEntity
import mn.blazeapps.foxplayer.data.entities.ChapterEntity
import mn.blazeapps.foxplayer.data.onnx.OnnxBookMetadataEngine
import mn.blazeapps.foxplayer.data.onnx.OnnxModelManager
import mn.blazeapps.foxplayer.ui.formatDuration
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WikiSearchData(
    val title: String,
    val description: String?,
    val extract: String?,
    val snippets: List<String> = emptyList(),
)

data class DdgResultItem(
    val title: String,
    val snippet: String,
)

data class DdgSearchData(
    val heading: String?,
    val definition: String?,
    val abstractText: String?,
    val answer: String?,
    val relatedTopics: List<String> = emptyList(),
    val webResults: List<DdgResultItem> = emptyList(),
)

data class BraveSearchData(
    val snippets: List<String> = emptyList(),
)

data class BookSeriesAndAuthorInfo(
    val bookTitle: String,
    val author: String?,
    val seriesName: String? = null,
    val seriesOrder: String? = null,
    val nextBook: String? = null,
    val previousBook: String? = null,
    val isStandalone: Boolean = false,
    val authorBestSellers: List<String> = emptyList(),
    val seriesOverview: String? = null,
    val sources: List<ChatSource> = emptyList(),
    val usedOnnxModel: Boolean = false,
)

data class ParsedSeries(
    val seriesName: String? = null,
    val seriesOrder: String? = null,
    val nextBook: String? = null,
    val previousBook: String? = null,
    val isStandalone: Boolean = false,
    val overview: String? = null,
)

enum class ChatQueryScope(val displayName: String, val subtitle: String) {
    AUTO("Auto", "Smart search across notes, book info & web"),
    GENERAL("General (Web & AI)", "Live web search & AI knowledge"),
    BOOKMARK_NOTES("Bookmark Notes", "Search bookmark notes in this book"),
}

class AiChatEngine(
    private val modelManager: OnnxModelManager? = null,
    private val onnxEngine: OnnxBookMetadataEngine? = null,
    val localLlmClient: LocalLlmClient = LocalLlmClient(),
    val localChatPreferences: LocalChatPreferences? = null,
    val context: android.content.Context? = null,
) {
    companion object {
        private const val TAG = "FoxPlayer-AiChat"

        val ORDINAL_MAP = mapOf(
            "first" to "Book 1", "1st" to "Book 1",
            "second" to "Book 2", "2nd" to "Book 2",
            "third" to "Book 3", "3rd" to "Book 3",
            "fourth" to "Book 4", "4th" to "Book 4",
            "fifth" to "Book 5", "5th" to "Book 5",
            "sixth" to "Book 6", "6th" to "Book 6",
            "seventh" to "Book 7", "7th" to "Book 7",
            "eighth" to "Book 8", "8th" to "Book 8",
            "ninth" to "Book 9", "9th" to "Book 9",
            "tenth" to "Book 10", "10th" to "Book 10",
        )

        private fun logE(message: String, throwable: Throwable? = null) {
            try {
                Log.e(TAG, message, throwable)
            } catch (_: RuntimeException) {
                // Ignore in JVM unit tests where android.util.Log is not mocked
            }
        }
    }

    suspend fun processQuery(
        query: String,
        book: BookEntity?,
        chapters: List<ChapterEntity>,
        bookmarks: List<BookmarkWithChapter>,
        scope: ChatQueryScope = ChatQueryScope.AUTO,
    ): AiChatMessage = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val lower = trimmed.lowercase()

        when (scope) {
            ChatQueryScope.BOOKMARK_NOTES -> {
                return@withContext handleInternalNotes(trimmed, lower, book, bookmarks)
            }
            ChatQueryScope.GENERAL -> {
                return@withContext searchWebAndSummarize(trimmed, book)
            }
            ChatQueryScope.AUTO -> {
                // 1. Internal notes / bookmarks search
                if (isNotesQuery(lower)) {
                    return@withContext handleInternalNotes(trimmed, lower, book, bookmarks)
                }

                // 2. Direct audiobook metadata queries (Author, chapters, synopsis)
                if (book != null && isBookMetadataQuery(lower)) {
                    return@withContext handleBookMetadata(lower, book, chapters)
                }

                // 3. Multi-engine search across Wikipedia, DuckDuckGo & Brave Search + Local LLM/ONNX synthesis
                return@withContext searchWebAndSummarize(trimmed, book)
            }
        }
    }

    private fun isNotesQuery(lower: String): Boolean {
        return lower.contains("bookmark") ||
            lower.contains("note") ||
            lower.contains("marked") ||
            lower.contains("my highlights")
    }

    private fun isBookMetadataQuery(lower: String): Boolean {
        return lower.contains("who wrote") ||
            lower.contains("who is the author") ||
            lower.contains("author of this") ||
            lower.contains("what is this book about") ||
            lower.contains("book synopsis") ||
            lower.contains("book summary") ||
            lower.contains("book description") ||
            lower.contains("how many chapters") ||
            lower.contains("list chapters") ||
            lower.contains("chapter count") ||
            lower.contains("about this book")
    }

    private fun handleBookMetadata(
        lower: String,
        book: BookEntity,
        chapters: List<ChapterEntity>,
    ): AiChatMessage {
        val sb = StringBuilder()
        when {
            lower.contains("author") || lower.contains("who wrote") -> {
                sb.append("### ✍️ Author Information\n\n")
                if (!book.author.isNullOrBlank()) {
                    sb.append("**${book.title}** is written by **${book.author}**.")
                } else {
                    sb.append("The author for **${book.title}** is not listed in your metadata.")
                }
            }
            lower.contains("chapter") -> {
                sb.append("### 📚 Chapter Overview\n\n")
                sb.append("**${book.title}** contains **${chapters.size} chapters**.")
                if (chapters.isNotEmpty()) {
                    sb.append("\n\n")
                    chapters.take(5).forEachIndexed { i, ch ->
                        sb.append("• **${i + 1}. ${ch.displayName}** (`${formatDuration(ch.durationMs)}`)\n")
                    }
                    if (chapters.size > 5) {
                        sb.append("*(and ${chapters.size - 5} more chapters)*")
                    }
                }
            }
            else -> {
                sb.append("### 📖 About *${book.title}*\n\n")
                if (!book.author.isNullOrBlank()) {
                    sb.append("**Author:** ${book.author}\n\n")
                }
                if (!book.genres.isNullOrBlank()) {
                    sb.append("**Genres:** ${book.genres}\n\n")
                }
                if (!book.description.isNullOrBlank()) {
                    sb.append("**Synopsis:**\n${book.description}\n\n")
                }
                sb.append("📊 **Chapters:** ${chapters.size} total tracks")
            }
        }
        return AiChatMessage(
            sender = ChatSender.Assistant,
            text = sb.toString().trim(),
            sources = listOf(ChatSource(book.title, "Audiobook Metadata")),
        )
    }

    private fun handleInternalNotes(
        query: String,
        lower: String,
        book: BookEntity?,
        bookmarks: List<BookmarkWithChapter>,
    ): AiChatMessage {
        val bookTitle = book?.title ?: "this audiobook"

        if (bookmarks.isEmpty()) {
            return AiChatMessage(
                sender = ChatSender.Assistant,
                text = "You don't have any bookmarks or notes saved for **$bookTitle** yet.\n\n" +
                    "💡 *Tip: While listening, tap the bookmark icon to save key moments, quotes, or thoughts. You can then ask me to search your notes or summarize them here!*",
                sources = listOf(ChatSource("0 Bookmarks", "Bookmarks")),
            )
        }

        val isSummarize = lower.contains("summarize") ||
            lower.contains("summary") ||
            lower.contains("overview") ||
            lower.contains("all notes") ||
            lower.contains("list") ||
            lower.contains("recap") ||
            lower.contains("what did i note") ||
            lower.contains("show notes") ||
            lower == "search notes" ||
            lower == "search my notes"

        if (isSummarize) {
            val sb = StringBuilder()
            sb.append("### 📝 Bookmark Notes Summary for *$bookTitle*\n\n")
            sb.append("You have saved **${bookmarks.size}** bookmark${if (bookmarks.size > 1) "s" else ""}:\n\n")

            val withNotes = bookmarks.filter { it.bookmark.note.isNotBlank() }
            if (withNotes.isEmpty()) {
                sb.append("*(Your bookmarks are saved with timestamps, but without written notes.)*\n\n")
                bookmarks.forEachIndexed { idx, bm ->
                    val chapName = bm.chapter?.displayName ?: "Chapter"
                    val time = formatDuration(bm.bookmark.positionMs)
                    sb.append("• **${idx + 1}. $chapName** at `$time`\n")
                }
            } else {
                withNotes.forEachIndexed { idx, bm ->
                    val chapName = bm.chapter?.displayName ?: "Chapter"
                    val time = formatDuration(bm.bookmark.positionMs)
                    sb.append("**${idx + 1}. $chapName** · `$time`\n")
                    sb.append("> \"${bm.bookmark.note.trim()}\"\n\n")
                }

                if (withNotes.size >= 2) {
                    val distinctChapters = withNotes.mapNotNull { it.chapter?.displayName }.distinct()
                    sb.append("**Key Themes in Your Notes:**\n")
                    sb.append("You've tracked ${withNotes.size} observations across ")
                    if (distinctChapters.isNotEmpty()) {
                        sb.append("${distinctChapters.size} chapter sections (${distinctChapters.take(3).joinToString(", ")}).")
                    } else {
                        sb.append("the audiobook.")
                    }
                }
            }

            return AiChatMessage(
                sender = ChatSender.Assistant,
                text = sb.toString(),
                sources = listOf(ChatSource("${bookmarks.size} Bookmarks", "Bookmarks")),
            )
        }

        // Filter specific notes by keyword
        val stopWords = setOf("search", "my", "notes", "for", "bookmark", "bookmarks", "find", "in", "what", "did", "say", "about", "the")
        val keywords = lower.split(Regex("\\s+")).filter { it !in stopWords && it.length > 2 }

        val matching = bookmarks.filter { bm ->
            val noteText = bm.bookmark.note.lowercase()
            val chapText = bm.chapter?.displayName.orEmpty().lowercase()
            keywords.any { noteText.contains(it) || chapText.contains(it) }
        }

        if (matching.isNotEmpty()) {
            val sb = StringBuilder()
            sb.append("🔍 Found **${matching.size}** bookmark note${if (matching.size > 1) "s" else ""} matching your inquiry:\n\n")
            matching.forEach { bm ->
                val chapName = bm.chapter?.displayName ?: "Chapter"
                val time = formatDuration(bm.bookmark.positionMs)
                sb.append("• **$chapName** (`$time`):\n")
                if (bm.bookmark.note.isNotBlank()) {
                    sb.append("  > \"${bm.bookmark.note}\"\n\n")
                } else {
                    sb.append("  *(Timestamp bookmark without text note)*\n\n")
                }
            }
            return AiChatMessage(
                sender = ChatSender.Assistant,
                text = sb.toString(),
                sources = listOf(ChatSource("${matching.size} Matching Notes", "Bookmarks")),
            )
        } else {
            val sampleTopics = bookmarks
                .mapNotNull { it.bookmark.note.takeIf { n -> n.isNotBlank() } }
                .take(3)
                .joinToString("\", \"")

            val msg = if (sampleTopics.isNotBlank()) {
                "I couldn't find notes mentioning those exact terms. Your existing notes mention: *\"$sampleTopics\"*.\n\nWould you like a full summary of all your notes?"
            } else {
                "I couldn't find any written notes matching that topic among your ${bookmarks.size} saved bookmark timestamps."
            }

            return AiChatMessage(
                sender = ChatSender.Assistant,
                text = msg,
                sources = listOf(ChatSource("${bookmarks.size} Bookmarks", "Bookmarks")),
            )
        }
    }

    private suspend fun searchWebAndSummarize(
        query: String,
        book: BookEntity?,
    ): AiChatMessage = withContext(Dispatchers.IO) {
        val cleanTopic = cleanSearchTopic(query).ifBlank { query.trim() }
        val engineMode = localChatPreferences?.engineMode?.value ?: LocalChatEngineMode.FAST_LOCAL
        val foundryEndpoint = localChatPreferences?.foundryEndpoint?.value ?: LocalLlmClient.DEFAULT_ENDPOINT
        val foundryModel = localChatPreferences?.foundryModel?.value ?: LocalChatPreferences.DEFAULT_MODEL

        localLlmClient.endpointUrl = foundryEndpoint
        localLlmClient.modelName = foundryModel

        if (engineMode == LocalChatEngineMode.FOUNDRY_LOCAL) {
            // First collect live web and lore context
            val liveKnowledge = coroutineScope {
                val wDeferred = async { queryWikipedia(cleanTopic) }
                val dDeferred = async { queryDuckDuckGo(cleanTopic) }
                val bDeferred = async { queryBrave(cleanTopic) }
                val w = wDeferred.await()
                val d = dDeferred.await()
                val b = bDeferred.await()
                buildString {
                    if (!w?.extract.isNullOrBlank()) append("Wikipedia: ").append(w!!.extract).append("\n\n")
                    if (!d?.abstractText.isNullOrBlank()) append("DuckDuckGo: ").append(d!!.abstractText).append("\n\n")
                    d?.webResults?.take(3)?.forEach { append("• ").append(it.title).append(": ").append(it.snippet).append("\n") }
                    b?.snippets?.take(2)?.forEach { append("Brave Snippet: ").append(it).append("\n") }
                }
            }

            var lastFoundryError: String? = null

            // 1. Primary: Try Microsoft Foundry Local via Android IPC service app
            if (context != null && FoundryIpcManager.isSupportedOs() && FoundryIpcManager.isFoundryAppInstalled(context)) {
                val ipcResult = FoundryIpcManager.chat(
                    context = context,
                    query = query,
                    bookTitle = book?.title,
                    bookAuthor = book?.author,
                    preferredModel = foundryModel,
                    liveKnowledgeContext = liveKnowledge.takeIf { it.isNotBlank() },
                )
                if (ipcResult.isSuccess && ipcResult.text.isNotBlank()) {
                    val sources = mutableListOf(
                        ChatSource("Foundry Local IPC", ipcResult.modelUsed.ifBlank { foundryModel }),
                    )
                    if (liveKnowledge.isNotBlank()) {
                        sources.add(ChatSource("Live Web Search", "DuckDuckGo & Wikipedia & Brave"))
                    }
                    return@withContext AiChatMessage(
                        sender = ChatSender.Assistant,
                        text = ipcResult.text,
                        sources = sources,
                    )
                } else if (!ipcResult.isSuccess && !ipcResult.errorMessage.isNullOrBlank()) {
                    lastFoundryError = ipcResult.errorMessage
                }
            }

            // 2. Secondary: Try local HTTP server endpoint (e.g. PC server / port forwarding)
            if (localLlmClient.isAvailable()) {
                val agenticResult = localLlmClient.chatWithAgenticSearch(
                    query = query,
                    bookTitle = book?.title,
                    bookAuthor = book?.author,
                ) { _ -> liveKnowledge }
                if (agenticResult != null && agenticResult.first.isNotBlank()) {
                    return@withContext AiChatMessage(
                        sender = ChatSender.Assistant,
                        text = agenticResult.first,
                        sources = listOf(
                            ChatSource("Microsoft Foundry Local (Server)", foundryModel),
                            ChatSource("Live Web & Knowledge Tools", "Agentic Tool Result"),
                        ),
                    )
                }
            }

            // 3. Fallback: If Foundry Local IPC service and HTTP are offline, synthesize via Fast Local Search
            val fastResult = executeFastLocalSearch(cleanTopic, query, book)
            val fallbackNotice = when {
                context != null && !FoundryIpcManager.isFoundryAppInstalled(context) -> {
                    "\n\n> ℹ️ *Microsoft Foundry Local app is not installed on this device. Response derived via Fast Local Search. You can install **Foundry Local** (`com.microsoft.foundrylocal.app`) from Google Play.*"
                }
                context != null && !FoundryIpcManager.isSupportedOs() -> {
                    "\n\n> ℹ️ *Microsoft Foundry Local requires Android 13+ (API 33). Response derived via Fast Local Search.*"
                }
                !lastFoundryError.isNullOrBlank() -> {
                    "\n\n> ℹ️ *Microsoft Foundry Local: $lastFoundryError Falling back to Fast Local Search.*"
                }
                else -> {
                    "\n\n> ℹ️ *Microsoft Foundry Local service was not detected. Response derived via Fast Local Search. Ensure the Foundry Local app is running.*"
                }
            }
            return@withContext fastResult.copy(
                text = fastResult.text + fallbackNotice,
                sources = fastResult.sources + listOf(ChatSource("Foundry Local Fallback", if (!lastFoundryError.isNullOrBlank()) "Model Not Downloaded" else "Service Offline")),
            )
        } else {
            // Default Fast Local search engine (Wikipedia + DuckDuckGo + Brave + SmolLM2 ONNX)
            return@withContext executeFastLocalSearch(cleanTopic, query, book)
        }
    }

    private suspend fun executeFastLocalSearch(
        cleanTopic: String,
        query: String,
        book: BookEntity?,
    ): AiChatMessage = coroutineScope {
        val wikiDeferred = async { queryWikipedia(cleanTopic) }
        val ddgDeferred = async { queryDuckDuckGo(cleanTopic) }
        val braveDeferred = async { queryBrave(cleanTopic) }

        val wikiData = wikiDeferred.await()
        val ddgData = ddgDeferred.await()
        val braveData = braveDeferred.await()

        val hasAnyData = wikiData != null || ddgData != null || braveData != null

        if (!hasAnyData) {
            // Check if query matches current audiobook metadata
            if (book != null) {
                val bookMatch = handleBookFallbackSearch(query, book)
                if (bookMatch != null) return@coroutineScope bookMatch
            }

            return@coroutineScope AiChatMessage(
                sender = ChatSender.Assistant,
                text = "I searched across **Wikipedia**, **DuckDuckGo**, and **Brave Search** for **\"$query\"**, but no relevant information or definitions could be found.\n\n" +
                    "💡 *Tip: Check your network connection or try rephrasing with simpler keywords.*",
                sources = emptyList(),
            )
        }

        // Run neural LLM forward pass if SmolLM ONNX model is available locally
        var usedOnnx = false
        if (onnxEngine != null && modelManager?.isModelDownloaded() == true) {
            val prompt = buildSmolLmChatPrompt(query, wikiData, ddgData, braveData)
            usedOnnx = onnxEngine.runChatInference(prompt)
        }

        synthesizeWebResults(query, cleanTopic, wikiData, ddgData, braveData, usedOnnx)
    }

    private fun handleBookFallbackSearch(query: String, book: BookEntity): AiChatMessage? {
        val lower = query.lowercase()
        val titleMatch = lower.contains(book.title.lowercase()) || book.title.lowercase().contains(lower)
        val authorMatch = book.author?.let { lower.contains(it.lowercase()) || it.lowercase().contains(lower) } ?: false
        if (titleMatch || authorMatch || lower.contains("author") || lower.contains("synopsis") || lower.contains("plot")) {
            val sb = StringBuilder()
            sb.append("### 📖 About *${book.title}*\n\n")
            if (!book.author.isNullOrBlank()) {
                sb.append("**Author:** ${book.author}\n\n")
            }
            if (!book.genres.isNullOrBlank()) {
                sb.append("**Genres:** ${book.genres}\n\n")
            }
            if (!book.description.isNullOrBlank()) {
                sb.append("**Synopsis:**\n${book.description}\n\n")
            }
            return AiChatMessage(
                sender = ChatSender.Assistant,
                text = sb.toString().trim(),
                sources = listOf(ChatSource(book.title, "Audiobook Metadata")),
            )
        }
        return null
    }

    fun cleanSearchTopic(query: String): String {
        var s = query.trim()

        // 1. Normalize fused words caused by rapid typing / voice typing without space
        val fusedWordReplacements = listOf(
            Regex("(?i)\\bdetailsabout\\b") to "details about",
            Regex("(?i)\\bdetailsfor\\b") to "details for",
            Regex("(?i)\\bdetailsof\\b") to "details of",
            Regex("(?i)\\bdetailson\\b") to "details on",
            Regex("(?i)\\btellme\\b") to "tell me",
            Regex("(?i)\\bwhois\\b") to "who is",
            Regex("(?i)\\bwhowas\\b") to "who was",
            Regex("(?i)\\bwhatis\\b") to "what is",
            Regex("(?i)\\bwhatwas\\b") to "what was",
            Regex("(?i)\\bwhereis\\b") to "where is",
            Regex("(?i)\\bmoreabout\\b") to "more about",
            Regex("(?i)\\binfoabout\\b") to "info about",
            Regex("(?i)\\binforabout\\b") to "info about",
            Regex("(?i)\\binformationabout\\b") to "information about",
            Regex("(?i)\\bnotesabout\\b") to "notes about",
            Regex("(?i)\\bgiveme\\b") to "give me",
            Regex("(?i)\\bexplainto\\b") to "explain to",
            Regex("(?i)\\bcanu\\b") to "can you",
            Regex("(?i)\\baboutthe\\b") to "about the",
        )
        for ((p, r) in fusedWordReplacements) {
            s = s.replace(p, r)
        }

        // 2. Strip conversational inquiry prefixes
        val prefixes = listOf(
            Regex("(?i)^(?:can you\\s+)?(?:please\\s+)?(?:give\\s+(?:me\\s+)?(?:more\\s+)?)?(?:details|info|information|summary|overview|notes|facts|lore|background)\\s+(?:about|on|for|regarding|of)\\s+"),
            Regex("(?i)^(?:can you\\s+)?(?:please\\s+)?tell\\s+(?:me\\s+)?(?:more\\s+)?(?:about|on)\\s+"),
            Regex("(?i)^(?:can you\\s+)?(?:please\\s+)?(?:explain|describe|clarify|elaborate\\s+on|search\\s+(?:for|online\\s+for)?|look\\s+up|find\\s+out\\s+about)\\s+(?:the\\s+)?"),
            Regex("(?i)^(?:who\\s+(?:is|was)|what\\s+(?:is|was|are|were)|where\\s+(?:is|was))\\s+(?:the\\s+)?"),
            Regex("(?i)^(?:definition\\s+of|define|meaning\\s+of)\\s+"),
            Regex("(?i)^give\\s+details\\s+(?:about|on|for|of)?\\s*"),
            Regex("(?i)^details\\s+(?:about|on|for|of)?\\s*"),
            Regex("(?i)^give\\s+(?:me\\s+)?(?:more\\s+)?(?:info|information)\\s+(?:about|on|for|of)?\\s*"),
            Regex("(?i)^tell\\s+me\\s+about\\s+"),
            Regex("(?i)^historical\\s+context\\s+of\\s+"),
            Regex("(?i)^history\\s+of\\s+"),
        )
        for (p in prefixes) {
            s = s.replace(p, "").trim()
        }

        // 3. Strip conversational suffixes
        val suffixes = listOf(
            Regex("(?i)\\s+(?:please|mean|meaning|in\\s+real\\s+life|in\\s+history|in\\s+mythology|in\\s+the\\s+book)$"),
            Regex("(?i)\\s+details$"),
        )
        for (suffix in suffixes) {
            s = s.replace(suffix, "").trim()
        }

        return s.ifBlank { query.trim() }
    }

    fun extractCoreKeywords(text: String): String {
        val stopWords = setOf(
            "give", "details", "about", "tell", "me", "who", "what", "is", "was",
            "are", "were", "the", "a", "an", "can", "you", "please", "in", "of",
            "to", "for", "on", "with", "by", "from", "that", "this", "at", "and",
            "or", "as", "it", "its", "find", "search", "show", "get", "more", "info", "information"
        )
        val tokens = text.lowercase().split(Regex("[^a-zA-Z0-9]+")).filter { it !in stopWords && it.length > 1 }
        return tokens.joinToString(" ")
    }

    fun isCleanContentSnippet(snippet: String): Boolean {
        val s = snippet.trim()
        if (s.length < 35) return false
        // Reject URL breadcrumb paths, domains, and search engine navigation
        if (s.contains("›") || s.contains(" > ") || s.contains("http://") || s.contains("https://") ||
            s.contains(".org") || s.contains(".com") || s.contains(".net") || s.contains(".edu") ||
            s.contains(".gov") || s.contains(".html") || s.contains("Wikipedia en.") ||
            s.contains("Search the Web") || s.contains("Brave Search")
        ) {
            return false
        }
        if (s.count { it == ' ' } < 3) return false
        return true
    }

    fun queryWikipedia(topic: String): WikiSearchData? {
        val clean = topic.trim()
        if (clean.isBlank()) return null

        // 1. Primary search with cleaned topic
        var data = executeWikipediaSearch(clean)

        // 2. Query relaxation: search with core keywords if 0 hits
        if (data == null) {
            val core = extractCoreKeywords(clean)
            if (core.isNotBlank() && !core.equals(clean, ignoreCase = true)) {
                data = executeWikipediaSearch(core)
            }
        }

        // 3. OpenSearch fuzzy/prefix lookup
        if (data == null) {
            data = queryWikipediaOpenSearch(clean)
        }

        return data
    }

    private fun executeWikipediaSearch(term: String): WikiSearchData? {
        return try {
            val encoded = URLEncoder.encode(term, "UTF-8")
            val searchUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json"

            val searchConn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "FoxPlayer/1.1.0 (Android Audiobook Assistant)")
            }
            if (searchConn.responseCode != 200) {
                searchConn.disconnect()
                return null
            }
            val searchJson = JSONObject(searchConn.inputStream.bufferedReader().use { it.readText() })
            searchConn.disconnect()

            val queryObj = searchJson.optJSONObject("query")
            val searchResults = queryObj?.optJSONArray("search")

            // Check if hits exist
            if (searchResults == null || searchResults.length() == 0) {
                val suggestion = queryObj?.optJSONObject("searchinfo")?.optString("suggestion")
                if (!suggestion.isNullOrBlank() && !suggestion.equals(term, ignoreCase = true)) {
                    return executeWikipediaSearch(suggestion)
                }
                return null
            }

            val topTitle = searchResults.getJSONObject(0).optString("title")
            if (topTitle.isBlank()) return null

            val snippets = mutableListOf<String>()
            for (i in 0 until minOf(4, searchResults.length())) {
                val item = searchResults.getJSONObject(i)
                val s = item.optString("snippet")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&quot;", "\"")
                    .replace("&#039;", "'")
                    .replace("&#39;", "'")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .trim()
                if (s.isNotBlank() && s.length > 30) {
                    snippets.add(s)
                }
            }

            fetchWikipediaArticleContent(topTitle, snippets)
        } catch (e: Exception) {
            logE("Wikipedia search error for '$term'", e)
            null
        }
    }

    private fun fetchWikipediaArticleContent(topTitle: String, snippets: List<String>): WikiSearchData {
        var fullExtract: String? = null
        var description: String? = null

        // 1. Fetch REST summary
        try {
            val safeTitle = topTitle.replace(" ", "_")
            val encodedSafe = URLEncoder.encode(safeTitle, "UTF-8")
            val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedSafe"
            val sumConn = (URL(summaryUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "FoxPlayer/1.1.0 (Android Audiobook Assistant)")
            }
            if (sumConn.responseCode == 200) {
                val sumJson = JSONObject(sumConn.inputStream.bufferedReader().use { it.readText() })
                fullExtract = sumJson.optString("extract").takeIf { it.isNotBlank() }
                description = sumJson.optString("description").takeIf { it.isNotBlank() }
            }
            sumConn.disconnect()
        } catch (e: Exception) {
            logE("Wikipedia REST summary error", e)
        }

        // 2. Fetch extensive multi-paragraph extract with redirects=1
        try {
            val safeTitle = topTitle.replace(" ", "_")
            val encodedSafe = URLEncoder.encode(safeTitle, "UTF-8")
            val extractApiUrl = "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=1&exchars=2500&redirects=1&titles=$encodedSafe&format=json"
            val extConn = (URL(extractApiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "FoxPlayer/1.1.0 (Android Audiobook Assistant)")
            }
            if (extConn.responseCode == 200) {
                val extJson = JSONObject(extConn.inputStream.bufferedReader().use { it.readText() })
                val pages = extJson.optJSONObject("query")?.optJSONObject("pages")
                if (pages != null) {
                    val firstKey = pages.keys().asSequence().firstOrNull()
                    if (firstKey != null) {
                        val pageObj = pages.optJSONObject(firstKey)
                        val detailedText = pageObj?.optString("extract")?.trim()
                        if (!detailedText.isNullOrBlank() && detailedText.length > (fullExtract?.length ?: 0)) {
                            val cleanedText = detailedText
                                .replace(Regex("==+\\s*([^=]+)\\s*==+"), "\n\n**$1**\n")
                                .replace(Regex("\n{3,}"), "\n\n")
                                .trim()
                            fullExtract = cleanedText
                        }
                    }
                }
            }
            extConn.disconnect()
        } catch (e: Exception) {
            logE("Wikipedia Action API extract error", e)
        }

        return WikiSearchData(
            title = topTitle,
            description = description,
            extract = fullExtract,
            snippets = snippets,
        )
    }

    private fun queryWikipediaOpenSearch(topic: String): WikiSearchData? {
        return try {
            val encoded = URLEncoder.encode(topic, "UTF-8")
            val openSearchUrl = "https://en.wikipedia.org/w/api.php?action=opensearch&search=$encoded&limit=3&namespace=0&format=json"
            val conn = (URL(openSearchUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "FoxPlayer/1.1.0 (Android Audiobook Assistant)")
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val jsonArray = org.json.JSONArray(text)
            if (jsonArray.length() < 2) return null
            val titlesArray = jsonArray.optJSONArray(1) ?: return null
            if (titlesArray.length() == 0) return null
            val matchedTitle = titlesArray.optString(0)
            if (matchedTitle.isBlank()) return null

            fetchWikipediaArticleContent(matchedTitle, emptyList())
        } catch (e: Exception) {
            logE("Wikipedia OpenSearch error", e)
            null
        }
    }

    fun queryDuckDuckGo(topic: String): DdgSearchData? {
        val clean = topic.trim()
        if (clean.isBlank()) return null
        return try {
            val encodedQuery = "q=" + URLEncoder.encode(clean, "UTF-8")
            val url = URL("https://html.duckduckgo.com/html/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            }
            conn.outputStream.use { os ->
                os.write(encodedQuery.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return queryDuckDuckGoLiteFallback(clean)
            }
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            parseDuckDuckGoHtml(html, clean) ?: queryDuckDuckGoLiteFallback(clean)
        } catch (e: Exception) {
            logE("DuckDuckGo HTML query error for '$topic'", e)
            queryDuckDuckGoLiteFallback(clean)
        }
    }

    private fun parseDuckDuckGoHtml(html: String, query: String): DdgSearchData? {
        val snippetRegex = Regex("class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        val titleRegex = Regex("class=\"result__a\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)

        val snippetMatches = snippetRegex.findAll(html).take(6).toList()
        val titleMatches = titleRegex.findAll(html).take(6).toList()

        if (snippetMatches.isEmpty()) return null

        val snippets = mutableListOf<String>()
        val webResults = mutableListOf<DdgResultItem>()

        for (i in snippetMatches.indices) {
            val rawSnippet = snippetMatches[i].groupValues[1]
            val cleanSnippet = rawSnippet
                .replace(Regex("<[^>]+>"), "")
                .replace("&quot;", "\"")
                .replace("&#x27;", "'")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace(Regex("\\s+"), " ")
                .trim()

            val rawTitle = if (i < titleMatches.size) titleMatches[i].groupValues[1] else ""
            val cleanTitle = rawTitle
                .replace(Regex("<[^>]+>"), "")
                .replace("&quot;", "\"")
                .replace("&#x27;", "'")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .trim()

            if (cleanSnippet.length > 35 && isCleanContentSnippet(cleanSnippet)) {
                val healed = OnnxBookMetadataEngine.sanitizeCompleteSentences(cleanSnippet)
                if (healed.isNotBlank()) {
                    snippets.add(healed)
                    webResults.add(DdgResultItem(title = cleanTitle.ifBlank { query }, snippet = healed))
                }
            }
        }

        if (snippets.isEmpty()) return null

        val firstSnippet = snippets.first()
        val remaining = snippets.drop(1).take(4)
        val topTitle = webResults.firstOrNull()?.title

        return DdgSearchData(
            heading = topTitle,
            definition = null,
            abstractText = firstSnippet,
            answer = null,
            relatedTopics = remaining,
            webResults = webResults,
        )
    }

    private fun queryDuckDuckGoLiteFallback(topic: String): DdgSearchData? {
        return try {
            val encodedQuery = "q=" + URLEncoder.encode(topic, "UTF-8")
            val url = URL("https://lite.duckduckgo.com/lite/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            }
            conn.outputStream.use { os ->
                os.write(encodedQuery.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val snippetRegex = Regex("class=\"result-snippet\"[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
            val matches = snippetRegex.findAll(html).take(5).toList()
            val snippets = matches.mapNotNull { m ->
                val s = m.groupValues[1]
                    .replace(Regex("<[^>]+>"), " ")
                    .replace("&quot;", "\"")
                    .replace("&#x27;", "'")
                    .replace("&amp;", "&")
                    .trim()
                if (s.length > 35 && isCleanContentSnippet(s)) OnnxBookMetadataEngine.sanitizeCompleteSentences(s) else null
            }
            if (snippets.isEmpty()) return null

            DdgSearchData(
                heading = topic,
                definition = null,
                abstractText = snippets.first(),
                answer = null,
                relatedTopics = snippets.drop(1),
            )
        } catch (e: Exception) {
            logE("DuckDuckGo Lite fallback error", e)
            null
        }
    }

    fun queryBrave(topic: String): BraveSearchData? {
        return try {
            val encoded = URLEncoder.encode(topic, "UTF-8")
            val urlString = "https://search.brave.com/search?q=$encoded"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }
            val html = conn.inputStream.bufferedReader().use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(4096)
                var total = 0
                while (total < 120000) {
                    val n = reader.read(buf)
                    if (n == -1) break
                    sb.append(buf, 0, n)
                    total += n
                }
                sb.toString()
            }
            conn.disconnect()

            val snippets = mutableListOf<String>()

            // 1. Match content divs from modern Brave HTML
            val contentRegex = Regex("class=\"(?:content desktop-default-regular|generic-snippet|snippet-description)[^\"]*\"[^>]*>(.*?)</div>", RegexOption.DOT_MATCHES_ALL)
            for (m in contentRegex.findAll(html).take(6)) {
                val raw = m.groupValues[1]
                val clean = raw.replace(Regex("<[^>]+>"), " ")
                    .replace("&quot;", "\"")
                    .replace("&#x27;", "'")
                    .replace("&amp;", "&")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (clean.length > 40 && !clean.startsWith("Search the Web", ignoreCase = true)) {
                    val trimmed = clean.replace(Regex("^\\d+\\s+[A-Za-z]+\\s+\\d{4}\\s*-\\s*"), "")
                    if (isCleanContentSnippet(trimmed)) {
                        val healed = OnnxBookMetadataEngine.sanitizeCompleteSentences(trimmed)
                        if (healed.isNotBlank() && healed.length > 35) {
                            snippets.add(healed)
                        }
                    }
                }
            }

            // 2. Fallback to quotes if content regex was empty
            if (snippets.isEmpty()) {
                val quoteRegex = Regex("\"([A-Z][^\"\\\\]{45,250}\\.)\"")
                for (m in quoteRegex.findAll(html).take(3)) {
                    val q = m.groupValues[1]
                    if (!q.contains("Brave") && !q.contains("Search the Web") && isCleanContentSnippet(q)) {
                        val healed = OnnxBookMetadataEngine.sanitizeCompleteSentences(q)
                        if (healed.isNotBlank() && healed.length > 35) {
                            snippets.add(healed)
                        }
                    }
                }
            }

            if (snippets.isEmpty()) return null
            BraveSearchData(snippets = snippets.distinct().take(4))
        } catch (e: Exception) {
            logE("Brave Search query error", e)
            null
        }
    }

    private fun buildSmolLmChatPrompt(
        query: String,
        wiki: WikiSearchData?,
        ddg: DdgSearchData?,
        brave: BraveSearchData?,
    ): String {
        return buildString {
            append("<|im_start|>system\n")
            append("You are an intelligent knowledge assistant. Summarize the web search findings into a coherent, complete, and informative answer without cutting off sentences.\n")
            append("<|im_end|>\n")
            append("<|im_start|>user\n")
            append("Query: ").append(query).append("\n")
            if (wiki?.extract != null) {
                append("Wikipedia: ").append(OnnxBookMetadataEngine.sanitizeCompleteSentences(wiki.extract)).append("\n")
            }
            if (ddg?.abstractText != null || ddg?.definition != null) {
                append("DuckDuckGo: ").append(OnnxBookMetadataEngine.sanitizeCompleteSentences((ddg.definition ?: ddg.abstractText).orEmpty())).append("\n")
            }
            if (brave?.snippets?.isNotEmpty() == true) {
                append("Brave: ").append(OnnxBookMetadataEngine.sanitizeCompleteSentences(brave.snippets.first())).append("\n")
            }
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    }

    private fun synthesizeWebResults(
        query: String,
        cleanTopic: String,
        wiki: WikiSearchData?,
        ddg: DdgSearchData?,
        brave: BraveSearchData?,
        usedOnnx: Boolean,
    ): AiChatMessage {
        val title = wiki?.title ?: ddg?.heading ?: cleanTopic.replaceFirstChar { it.uppercase() }
        val subtitle = wiki?.description ?: ddg?.definition

        val sb = StringBuilder()
        sb.append("### 🌐 $title\n")
        if (!subtitle.isNullOrBlank()) {
            sb.append("*($subtitle)*\n\n")
        } else {
            sb.append("\n")
        }

        // 1. Core Definition & Summary
        if (!ddg?.definition.isNullOrBlank()) {
            sb.append("**Definition:** ${OnnxBookMetadataEngine.sanitizeCompleteSentences(ddg.definition)}\n\n")
        }

        val mainExtract = wiki?.extract ?: ddg?.abstractText ?: ddg?.answer
        if (!mainExtract.isNullOrBlank()) {
            sb.append(OnnxBookMetadataEngine.sanitizeCompleteSentences(mainExtract)).append("\n\n")
        }

        // 2. Key Details & Context synthesized across sources with complete sentence healing
        val keyPoints = mutableListOf<String>()
        val mainTextLower = (mainExtract ?: "").lowercase()

        // From DuckDuckGo web results (e.g. Britannica, GreekMythology, etc.)
        ddg?.webResults?.drop(1)?.forEach { item ->
            val cleaned = OnnxBookMetadataEngine.sanitizeCompleteSentences(item.snippet)
            if (cleaned.length > 35 && !mainTextLower.contains(cleaned.take(35).lowercase()) && keyPoints.none { it.take(30).equals(cleaned.take(30), ignoreCase = true) }) {
                keyPoints.add(cleaned)
            }
        }

        // From Brave Search snippets
        brave?.snippets?.forEach { snippet ->
            if (isCleanContentSnippet(snippet)) {
                val cleaned = OnnxBookMetadataEngine.sanitizeCompleteSentences(snippet)
                if (cleaned.length > 35 && !mainTextLower.contains(cleaned.take(35).lowercase()) && keyPoints.none { it.take(30).equals(cleaned.take(30), ignoreCase = true) }) {
                    keyPoints.add(cleaned)
                }
            }
        }

        // From DuckDuckGo related topics
        ddg?.relatedTopics?.forEach { topic ->
            if (isCleanContentSnippet(topic)) {
                val cleaned = OnnxBookMetadataEngine.sanitizeCompleteSentences(topic)
                if (cleaned.length > 30 && !mainTextLower.contains(cleaned.take(35).lowercase()) && keyPoints.none { it.take(30).equals(cleaned.take(30), ignoreCase = true) }) {
                    keyPoints.add(cleaned)
                }
            }
        }

        // From Wikipedia search snippets
        wiki?.snippets?.forEach { snippet ->
            if (isCleanContentSnippet(snippet)) {
                val cleaned = OnnxBookMetadataEngine.sanitizeCompleteSentences(snippet)
                if (cleaned.length > 35 && !mainTextLower.contains(cleaned.take(35).lowercase()) && keyPoints.none { it.take(30).equals(cleaned.take(30), ignoreCase = true) }) {
                    keyPoints.add(cleaned)
                }
            }
        }

        if (keyPoints.isNotEmpty()) {
            sb.append("**Key Insights & Lore:**\n")
            keyPoints.take(4).forEach { point ->
                sb.append("• ").append(point).append("\n")
            }
            sb.append("\n")
        }

        // Assemble source badges
        val sources = mutableListOf<ChatSource>()
        if (wiki != null && (!wiki.extract.isNullOrBlank() || wiki.snippets.isNotEmpty())) {
            sources.add(ChatSource(wiki.title, "Wikipedia"))
        }
        if (ddg != null && (!ddg.abstractText.isNullOrBlank() || ddg.webResults.isNotEmpty() || ddg.relatedTopics.isNotEmpty())) {
            val count = if (ddg.webResults.isNotEmpty()) "${ddg.webResults.size} Web Results" else (ddg.heading ?: "DuckDuckGo")
            sources.add(ChatSource(count, "DuckDuckGo"))
        }
        if (brave != null && brave.snippets.isNotEmpty()) {
            sources.add(ChatSource("${brave.snippets.size} Snippets", "Brave Search"))
        }
        if (usedOnnx) {
            sources.add(ChatSource("SmolLM2-360M ONNX", "On-Device Neural Model"))
        }

        return AiChatMessage(
            sender = ChatSender.Assistant,
            text = sb.toString().trim(),
            sources = sources,
        )
    }

    suspend fun fetchSeriesAndAuthorInfo(
        title: String,
        author: String?,
    ): BookSeriesAndAuthorInfo = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        val cleanAuthor = author?.trim()?.takeIf { it.isNotBlank() }

        // 1. Search for book series context across Wikipedia, DuckDuckGo, and Brave Search
        val seriesSearchQuery = if (cleanAuthor != null) "$cleanTitle $cleanAuthor book series next book" else "$cleanTitle book series next book"
        val wikiSeriesDeferred = async { queryWikipedia(cleanTitle) }
        val ddgSeriesDeferred = async { queryDuckDuckGo("$cleanTitle series next book") }
        val braveSeriesDeferred = async { queryBrave(seriesSearchQuery) }

        // 2. Search for author bestsellers if author is present
        val authorBestSellersDeferred = if (cleanAuthor != null) {
            async {
                val authorWiki = queryWikipedia(cleanAuthor)
                val authorBrave = queryBrave("$cleanAuthor best books bestsellers")
                Pair(authorWiki, authorBrave)
            }
        } else null

        val wikiData = wikiSeriesDeferred.await()
        val ddgData = ddgSeriesDeferred.await()
        val braveData = braveSeriesDeferred.await()
        val authorPair = authorBestSellersDeferred?.await()

        val allSeriesText = buildString {
            if (!wikiData?.extract.isNullOrBlank()) append(wikiData!!.extract).append(" ")
            wikiData?.snippets?.forEach { append(it).append(" ") }
            if (!ddgData?.abstractText.isNullOrBlank()) append(ddgData!!.abstractText).append(" ")
            braveData?.snippets?.forEach { append(it).append(" ") }
        }

        // Run local SmolLM forward pass if available
        var usedOnnx = false
        if (onnxEngine != null && modelManager?.isModelDownloaded() == true) {
            val prompt = buildString {
                append("<|im_start|>system\n")
                append("Identify the book series name, next book in series, and the author's best sellers from the context.\n")
                append("<|im_end|>\n")
                append("<|im_start|>user\n")
                append("Book: ").append(cleanTitle).append("\n")
                if (cleanAuthor != null) append("Author: ").append(cleanAuthor).append("\n")
                if (allSeriesText.isNotBlank()) append("Context: ").append(allSeriesText.take(500)).append("\n")
                append("<|im_end|>\n")
                append("<|im_start|>assistant\n")
            }
            usedOnnx = onnxEngine.runChatInference(prompt)
        }

        // Parse series details from aggregated text
        val parsedSeries = parseSeriesDetails(cleanTitle, allSeriesText)

        // Parse author bestsellers
        val allAuthorText = buildString {
            val authorWiki = authorPair?.first
            val authorBrave = authorPair?.second
            if (!authorWiki?.extract.isNullOrBlank()) append(authorWiki!!.extract).append(" ")
            authorWiki?.snippets?.forEach { append(it).append(" ") }
            authorBrave?.snippets?.forEach { append(it).append(" ") }
        }
        val bestsellers = parseAuthorBestSellers(cleanTitle, cleanAuthor, allAuthorText)

        val sources = mutableListOf<ChatSource>()
        if (wikiData != null) sources.add(ChatSource(wikiData.title, "Wikipedia"))
        if (ddgData != null) sources.add(ChatSource("DuckDuckGo", "Web Search"))
        if (braveData != null) sources.add(ChatSource("Brave Search", "Web Search"))
        if (authorPair?.first != null) sources.add(ChatSource(authorPair.first!!.title, "Author Wiki"))
        if (usedOnnx) sources.add(ChatSource("SmolLM2-360M", "Local LLM"))

        BookSeriesAndAuthorInfo(
            bookTitle = cleanTitle,
            author = cleanAuthor,
            seriesName = parsedSeries.seriesName,
            seriesOrder = parsedSeries.seriesOrder,
            nextBook = parsedSeries.nextBook,
            previousBook = parsedSeries.previousBook,
            isStandalone = parsedSeries.isStandalone,
            authorBestSellers = bestsellers,
            seriesOverview = parsedSeries.overview,
            sources = sources.distinctBy { it.title + it.type },
            usedOnnxModel = usedOnnx,
        )
    }

    fun parseSeriesDetails(title: String, text: String): ParsedSeries {
        if (text.isBlank()) return ParsedSeries()

        var seriesName: String? = null
        var seriesOrder: String? = null
        var nextBook: String? = null
        var previousBook: String? = null
        var isStandalone = false

        val lower = text.lowercase()
        if (lower.contains("standalone novel") || lower.contains("stand-alone novel") ||
            lower.contains("standalone book") || lower.contains("stand-alone book")) {
            isStandalone = true
        }

        // Pattern 1: "... first/second/... book in the [Series Name] series/trilogy/saga"
        val seriesRegex1 = Regex("(?i)(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|\\d+(?:st|nd|rd|th)?)\\s+(?:book|novel|installment|part|entry|volume)\\s+(?:in|of)\\s+([A-Z][A-Za-z0-9'\\s–-]+?)(?:\\s+(?:series|trilogy|saga|cycle|sequence|quartet|duology))")
        val match1 = seriesRegex1.find(text)
        if (match1 != null) {
            val ord = match1.groupValues[1].lowercase()
            seriesOrder = ORDINAL_MAP[ord] ?: "Book $ord"
            seriesName = match1.groupValues[2].trim().trimEnd(',', '.')
        }

        // Pattern 2: "Book X of [Series Name]"
        if (seriesName == null) {
            val seriesRegex2 = Regex("(?i)book\\s+(\\d+)\\s+(?:of|in)\\s+([A-Z][A-Za-z0-9'\\s–-]+?)(?:\\s+(?:series|trilogy|saga)|\\.|,|\\()")
            val match2 = seriesRegex2.find(text)
            if (match2 != null) {
                seriesOrder = "Book ${match2.groupValues[1]}"
                seriesName = match2.groupValues[2].trim().trimEnd(',', '.')
            }
        }

        // Pattern 3: "... part of the [Series Name] series"
        if (seriesName == null) {
            val seriesRegex3 = Regex("(?i)part\\s+of\\s+([A-Z][A-Za-z0-9'\\s–-]+?)\\s+(?:series|trilogy|saga|cycle)")
            val match3 = seriesRegex3.find(text)
            if (match3 != null) {
                seriesName = match3.groupValues[1].trim().trimEnd(',', '.')
            }
        }

        // Pattern for Next Book / Sequel: "Followed by [Next Book]" or "sequel ... is [Next Book]"
        val nextRegex1 = Regex("(?i)(?:followed by|succeeded by|sequel(?: is| to this book is)?|next book(?: in the series)?(?: is)?|next installment(?: is)?)\\s+[:]?\\s*[\"']?([A-Z][A-Za-z0-9'\\s–-]+?)[\"']?(?:\\s*\\(\\d{4}\\)|\\s+(?:in\\s+\\d{4}|published|released|which|and|\\.|,))")
        val nextMatch1 = nextRegex1.find(text)
        if (nextMatch1 != null) {
            val candidate = nextMatch1.groupValues[1].trim().trimEnd(',', '.')
            if (isValidBookTitle(candidate, title)) {
                nextBook = candidate
            }
        }

        // Pattern for Previous Book / Prequel: "Preceded by [Prev Book]"
        val prevRegex1 = Regex("(?i)(?:preceded by|prequel(?: is)?)\\s+[:]?\\s*[\"']?([A-Z][A-Za-z0-9'\\s–-]+?)[\"']?(?:\\s*\\(\\d{4}\\)|\\s+(?:in\\s+\\d{4}|published|released|which|and|\\.|,))")
        val prevMatch1 = prevRegex1.find(text)
        if (prevMatch1 != null) {
            val candidate = prevMatch1.groupValues[1].trim().trimEnd(',', '.')
            if (isValidBookTitle(candidate, title)) {
                previousBook = candidate
            }
        }

        // Extract a clean overview sentence if available
        var overview: String? = null
        if (seriesName != null) {
            val sentences = text.split(Regex("(?<=[.!?])\\s+"))
            overview = sentences.firstOrNull { it.contains(seriesName, ignoreCase = true) && it.length in 30..300 }?.trim()
        }

        return ParsedSeries(
            seriesName = seriesName,
            seriesOrder = seriesOrder,
            nextBook = nextBook,
            previousBook = previousBook,
            isStandalone = isStandalone && seriesName == null,
            overview = overview,
        )
    }

    fun parseAuthorBestSellers(currentTitle: String, author: String?, text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val results = mutableListOf<String>()

        // 1. Quoted titles: "The Final Empire", "Words of Radiance"
        val quotedRegex = Regex("[\"']([A-Z][A-Za-z0-9':,\\s–-]{3,50}?)[\"']")
        for (m in quotedRegex.findAll(text)) {
            val candidate = m.groupValues[1].trim().trimEnd(',', '.')
            if (isValidBookTitle(candidate, currentTitle) && (author == null || !candidate.equals(author, ignoreCase = true))) {
                if (results.none { it.equals(candidate, ignoreCase = true) }) {
                    results.add(candidate)
                }
            }
            if (results.size >= 5) break
        }

        // 2. Look for best-selling / notable works mentions if we don't have enough
        if (results.size < 3) {
            val bestSellerRegex = Regex("(?i)(?:best-selling|bestselling|notable|popular|acclaimed)\\s+(?:novels?|books?|works?)(?:\\s+include|\\s+are)?\\s+([A-Z][A-Za-z0-9',\\s–-]+?)(?:\\.|;|\n)")
            val match = bestSellerRegex.find(text)
            if (match != null) {
                val listSnippet = match.groupValues[1]
                val splitTitles = listSnippet.split(Regex(",\\s*(?:and\\s+)?|\\s+and\\s+"))
                for (t in splitTitles) {
                    val clean = t.trim().trim('"', '\'').trimEnd(',', '.')
                    if (isValidBookTitle(clean, currentTitle) && (author == null || !clean.equals(author, ignoreCase = true))) {
                        if (results.none { it.equals(clean, ignoreCase = true) }) {
                            results.add(clean)
                        }
                    }
                }
            }
        }

        return results.take(5)
    }

    private fun isValidBookTitle(candidate: String, currentTitle: String): Boolean {
        val trimmed = candidate.trim().trimEnd(',', '.', ':', ';')
        if (trimmed.length < 3 || trimmed.length > 60) return false
        if (trimmed.equals(currentTitle, ignoreCase = true)) return false
        val invalidStarts = listOf("a ", "the next", "an ", "his ", "her ", "another ", "several ", "many ", "various ")
        if (invalidStarts.any { trimmed.lowercase().startsWith(it) }) return false
        val stopWords = setOf("the", "a", "an", "novel", "book", "sequel", "prequel", "series")
        if (trimmed.lowercase() in stopWords) return false
        return true
    }
}
