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

class AiChatEngine(
    private val modelManager: OnnxModelManager? = null,
    private val onnxEngine: OnnxBookMetadataEngine? = null,
) {
    companion object {
        private const val TAG = "FoxPlayer-AiChat"

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

    private fun queryWikipedia(topic: String): WikiSearchData? {
        return try {
            val encoded = URLEncoder.encode(topic, "UTF-8")
            val searchUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json"

            val searchConn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
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
            for (i in 0 until minOf(3, searchResults.length())) {
                val item = searchResults.getJSONObject(i)
                val s = item.optString("snippet")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&quot;", "\"")
                    .replace("&#039;", "'")
                    .replace("&amp;", "&")
                    .trim()
                if (s.isNotBlank() && s.length > 20) {
                    snippets.add(s)
                }
            }

            val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/${URLEncoder.encode(topTitle, "UTF-8")}"
            val sumConn = (URL(summaryUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "FoxPlayer/1.1.0")
            }
            if (sumConn.responseCode != 200) {
                sumConn.disconnect()
                return WikiSearchData(title = topTitle, description = null, extract = null, snippets = snippets)
            }
            val sumJson = JSONObject(sumConn.inputStream.bufferedReader().use { it.readText() })
            sumConn.disconnect()

            val extract = sumJson.optString("extract").takeIf { it.isNotBlank() }
            val description = sumJson.optString("description").takeIf { it.isNotBlank() }

            WikiSearchData(
                title = topTitle,
                description = description,
                extract = extract,
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
                    snippets.add(trimmedSnippet)
                }
            }

            // Fallback to quoted sentences if result-body was not parsed
            if (snippets.isEmpty()) {
                val quoteRegex = Regex("\"([A-Z][^\"\\\\]{45,250}\\.)\"")
                val quoteMatches = quoteRegex.findAll(html).take(3).toList()
                for (m in quoteMatches) {
                    val q = m.groups[1]?.value.orEmpty()
                    if (!q.contains("Brave") && !q.contains("Search the Web")) {
                        snippets.add(q)
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

        if (!wiki?.extract.isNullOrBlank()) {
            sb.append(OnnxBookMetadataEngine.sanitizeCompleteSentences(wiki.extract)).append("\n\n")
        } else if (!ddg?.abstractText.isNullOrBlank()) {
            sb.append(OnnxBookMetadataEngine.sanitizeCompleteSentences(ddg.abstractText)).append("\n\n")
        } else if (!ddg?.answer.isNullOrBlank()) {
            sb.append(OnnxBookMetadataEngine.sanitizeCompleteSentences(ddg.answer)).append("\n\n")
        }

        // 2. Key Details & Context synthesized across sources with complete sentence healing
        val keyPoints = mutableListOf<String>()

        // From Brave Search snippets
        brave?.snippets?.forEach { snippet ->
            val cleaned = OnnxBookMetadataEngine.sanitizeCompleteSentences(snippet)
            if (cleaned.length > 35 && keyPoints.none { it.take(30).equals(cleaned.take(30), ignoreCase = true) }) {
                keyPoints.add(cleaned)
            }
        }

        // From DuckDuckGo related topics
        ddg?.relatedTopics?.forEach { topic ->
            val cleaned = OnnxBookMetadataEngine.sanitizeCompleteSentences(topic)
            if (cleaned.length > 30 && keyPoints.none { it.take(30).equals(cleaned.take(30), ignoreCase = true) }) {
                keyPoints.add(cleaned)
            }
        }

        // From Wikipedia search snippets
        wiki?.snippets?.forEach { snippet ->
            val cleaned = OnnxBookMetadataEngine.sanitizeCompleteSentences(snippet)
            if (cleaned.length > 35 && keyPoints.none { it.take(30).equals(cleaned.take(30), ignoreCase = true) }) {
                keyPoints.add(cleaned)
            }
        }

        if (keyPoints.isNotEmpty()) {
            sb.append("**Key Details & Context:**\n")
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
}
