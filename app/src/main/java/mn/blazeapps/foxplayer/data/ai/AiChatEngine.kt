package mn.blazeapps.foxplayer.data.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

data class DdgSearchData(
    val heading: String?,
    val definition: String?,
    val abstractText: String?,
    val answer: String?,
    val relatedTopics: List<String> = emptyList(),
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

class AiChatEngine(
    private val modelManager: OnnxModelManager? = null,
    private val onnxEngine: OnnxBookMetadataEngine? = null,
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
    ): AiChatMessage = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val lower = trimmed.lowercase()

        // ONLY filter: internal notes / bookmarks search
        if (isNotesQuery(lower)) {
            return@withContext handleInternalNotes(trimmed, lower, book, bookmarks)
        }

        // ALL other queries: multi-engine web search across DuckDuckGo, Wikipedia & Brave Search + LLM summarization
        searchWebAndSummarize(trimmed, book)
    }

    private fun isNotesQuery(lower: String): Boolean {
        return lower.contains("bookmark") ||
            lower.contains("note") ||
            lower.contains("notes") ||
            lower.contains("marked") ||
            lower.contains("my highlights")
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
        val cleanTopic = cleanSearchTopic(query)

        // Query Wikipedia, DuckDuckGo, and Brave Search concurrently
        val wikiDeferred = async { queryWikipedia(cleanTopic) }
        val ddgDeferred = async { queryDuckDuckGo(cleanTopic) }
        val braveDeferred = async { queryBrave(cleanTopic) }

        val wikiData = wikiDeferred.await()
        val ddgData = ddgDeferred.await()
        val braveData = braveDeferred.await()

        val hasAnyData = wikiData != null || ddgData != null || braveData != null

        if (!hasAnyData) {
            return@withContext AiChatMessage(
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

    private fun cleanSearchTopic(query: String): String {
        var s = query.trim()
        val prefixes = listOf(
            Regex("(?i)^define\\s+"),
            Regex("(?i)^definition\\s+of\\s+"),
            Regex("(?i)^meaning\\s+of\\s+"),
            Regex("(?i)^what\\s+is\\s+(?:the\\s+)?"),
            Regex("(?i)^what\\s+does\\s+(?:the\\s+word\\s+)?"),
            Regex("(?i)^who\\s+(?:was|is)\\s+"),
            Regex("(?i)^tell\\s+me\\s+about\\s+"),
            Regex("(?i)^historical\\s+context\\s+of\\s+"),
            Regex("(?i)^history\\s+of\\s+"),
            Regex("(?i)^search\\s+(?:online\\s+)?(?:for\\s+)?"),
            Regex("(?i)^explain\\s+(?:the\\s+concept\\s+of\\s+|the\\s+word\\s+)?"),
        )
        for (p in prefixes) {
            s = s.replace(p, "").trim()
        }
        s = s.replace(Regex("(?i)\\s+mean(?:ing)?$"), "").trim()
        s = s.replace(Regex("(?i)\\s+in\\s+real\\s+life$"), "").trim()
        return s.ifBlank { query }
    }

    private fun isCleanContentSnippet(snippet: String): Boolean {
        val s = snippet.trim()
        if (s.length < 40) return false
        // Reject URL breadcrumb paths, domains, and search engine navigation
        if (s.contains("›") || s.contains(" > ") || s.contains("http://") || s.contains("https://") ||
            s.contains(".org") || s.contains(".com") || s.contains(".net") || s.contains(".edu") ||
            s.contains(".gov") || s.contains(".html") || s.contains("Wikipedia en.") ||
            s.contains("Search the Web") || s.contains("Brave Search")
        ) {
            return false
        }
        if (s.count { it == ' ' } < 4) return false
        return true
    }

    private fun queryWikipedia(topic: String): WikiSearchData? {
        return try {
            val encoded = URLEncoder.encode(topic, "UTF-8")
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

            val searchResults = searchJson.optJSONObject("query")?.optJSONArray("search") ?: return null
            if (searchResults.length() == 0) return null

            val topTitle = searchResults.getJSONObject(0).optString("title")
            if (topTitle.isBlank()) return null

            val snippets = mutableListOf<String>()
            for (i in 0 until minOf(4, searchResults.length())) {
                val item = searchResults.getJSONObject(i)
                val s = item.optString("snippet")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&quot;", "\"")
                    .replace("&#039;", "'")
                    .replace("&amp;", "&")
                    .trim()
                if (s.isNotBlank() && s.length > 30) {
                    snippets.add(s)
                }
            }

            val safeTitle = topTitle.replace(" ", "_")
            var fullExtract: String? = null
            var description: String? = null

            // 1. Fetch REST summary
            try {
                val encodedSafe = URLEncoder.encode(safeTitle, "UTF-8")
                val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedSafe"
                val sumConn = (URL(summaryUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
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

            // 2. Fetch extensive multi-paragraph extract from Wikipedia Action API for rich, detailed knowledge
            try {
                val encodedSafe = URLEncoder.encode(safeTitle, "UTF-8")
                val extractApiUrl = "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=1&exchars=1800&titles=$encodedSafe&format=json"
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

            WikiSearchData(
                title = topTitle,
                description = description,
                extract = fullExtract,
                snippets = snippets,
            )
        } catch (e: Exception) {
            logE("Wikipedia query error", e)
            null
        }
    }

    private fun queryDuckDuckGo(topic: String): DdgSearchData? {
        return try {
            val encoded = URLEncoder.encode(topic, "UTF-8")
            val urlString = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "FoxPlayer/1.1.0")
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            conn.disconnect()

            val heading = json.optString("Heading").takeIf { it.isNotBlank() }
            val definition = json.optString("Definition").takeIf { it.isNotBlank() }
            val abstractText = json.optString("AbstractText").takeIf { it.isNotBlank() }
            val answer = json.optString("Answer").takeIf { it.isNotBlank() }

            val related = mutableListOf<String>()
            val relArray = json.optJSONArray("RelatedTopics")
            if (relArray != null) {
                for (i in 0 until relArray.length()) {
                    val item = relArray.opt(i)
                    if (item is JSONObject) {
                        val t = item.optString("Text")
                        if (t.isNotBlank() && t.length > 25) {
                            related.add(t)
                        }
                        val subTopics = item.optJSONArray("Topics")
                        if (subTopics != null) {
                            for (j in 0 until subTopics.length()) {
                                val subItem = subTopics.optJSONObject(j) ?: continue
                                val st = subItem.optString("Text")
                                if (st.isNotBlank() && st.length > 25) {
                                    related.add(st)
                                }
                            }
                        }
                    }
                    if (related.size >= 4) break
                }
            }

            if (heading.isNullOrBlank() && definition.isNullOrBlank() && abstractText.isNullOrBlank() && answer.isNullOrBlank() && related.isEmpty()) {
                return null
            }

            DdgSearchData(
                heading = heading,
                definition = definition,
                abstractText = abstractText,
                answer = answer,
                relatedTopics = related,
            )
        } catch (e: Exception) {
            logE("DuckDuckGo API error", e)
            null
        }
    }

    private fun queryBrave(topic: String): BraveSearchData? {
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
                while (total < 100000) {
                    val n = reader.read(buf)
                    if (n == -1) break
                    sb.append(buf, 0, n)
                    total += n
                }
                sb.toString()
            }
            conn.disconnect()

            val snippets = mutableListOf<String>()

            // Extract from result-body blocks in Brave HTML
            val bodyRegex = Regex("(?s)<div class=\"result-body[^>]*>(.*?)</div>\\s*</div>")
            val bodyMatches = bodyRegex.findAll(html).take(4).toList()
            for (m in bodyMatches) {
                val raw = m.groups[1]?.value.orEmpty()
                val clean = raw.replace(Regex("<[^>]+>"), " ")
                    .replace("&quot;", "\"")
                    .replace("&#x27;", "'")
                    .replace("&amp;", "&")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (clean.length > 40 && !clean.startsWith("Search the Web", ignoreCase = true)) {
                    val trimmedSnippet = clean.replace(Regex("^\\d+\\s+[A-Za-z]+\\s+\\d{4}\\s*-\\s*"), "")
                    if (isCleanContentSnippet(trimmedSnippet)) {
                        val healed = OnnxBookMetadataEngine.sanitizeCompleteSentences(trimmedSnippet)
                        if (healed.isNotBlank() && healed.length > 35) {
                            snippets.add(healed)
                        }
                    }
                }
            }

            // Fallback to quoted sentences if result-body was not parsed
            if (snippets.isEmpty()) {
                val quoteRegex = Regex("\"([A-Z][^\"\\\\]{45,250}\\.)\"")
                val quoteMatches = quoteRegex.findAll(html).take(3).toList()
                for (m in quoteMatches) {
                    val q = m.groups[1]?.value.orEmpty()
                    if (!q.contains("Brave") && !q.contains("Search the Web") && isCleanContentSnippet(q)) {
                        val healed = OnnxBookMetadataEngine.sanitizeCompleteSentences(q)
                        if (healed.isNotBlank() && healed.length > 35) {
                            snippets.add(healed)
                        }
                    }
                }
            }

            if (snippets.isEmpty()) return null
            BraveSearchData(snippets = snippets.distinct().take(3))
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
            keyPoints.take(3).forEach { point ->
                sb.append("• ").append(point).append("\n")
            }
            sb.append("\n")
        }

        // Assemble source badges
        val sources = mutableListOf<ChatSource>()
        if (wiki != null && (!wiki.extract.isNullOrBlank() || wiki.snippets.isNotEmpty())) {
            sources.add(ChatSource(wiki.title, "Wikipedia"))
        }
        if (ddg != null && (!ddg.abstractText.isNullOrBlank() || !ddg.definition.isNullOrBlank() || ddg.relatedTopics.isNotEmpty())) {
            sources.add(ChatSource(ddg.heading ?: "Knowledge", "DuckDuckGo"))
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
