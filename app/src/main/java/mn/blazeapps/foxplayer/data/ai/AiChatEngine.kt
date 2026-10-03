package mn.blazeapps.foxplayer.data.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mn.blazeapps.foxplayer.data.BookmarkWithChapter
import mn.blazeapps.foxplayer.data.entities.BookEntity
import mn.blazeapps.foxplayer.data.entities.ChapterEntity
import mn.blazeapps.foxplayer.data.onnx.OnnxBookMetadataEngine
import mn.blazeapps.foxplayer.data.onnx.OnnxModelManager
import mn.blazeapps.foxplayer.ui.formatDuration
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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

        // 1. Check if user is asking about Bookmarks / Notes
        if (isBookmarkIntent(lower)) {
            return@withContext handleBookmarkQuery(trimmed, lower, book, bookmarks)
        }

        // 2. Check if user is asking for a Word Definition
        val definitionWord = extractDefinitionWord(trimmed, lower)
        if (definitionWord != null) {
            val defResponse = queryDictionary(definitionWord)
            if (defResponse != null) {
                return@withContext defResponse
            }
        }

        // 3. Check if user is asking for Historical Context or Online Knowledge
        if (isHistoricalOrOnlineIntent(lower)) {
            val onlineResponse = queryOnlineContext(trimmed, book)
            if (onlineResponse != null) {
                return@withContext onlineResponse
            }
        }

        // 4. Check if user is asking about the Book itself (synopsis, characters, author, chapters)
        if (isBookMetaIntent(lower) && book != null) {
            return@withContext handleBookMetaQuery(trimmed, lower, book, chapters)
        }

        // 5. General Q&A: Search online first (Wikipedia/DuckDuckGo), then synthesize with book context
        val generalOnline = queryOnlineContext(trimmed, book)
        if (generalOnline != null) {
            return@withContext generalOnline
        }

        // 6. Fallback conversational response
        handleGeneralFallback(trimmed, book, chapters, bookmarks)
    }

    private fun isBookmarkIntent(lower: String): Boolean {
        return lower.contains("bookmark") ||
            lower.contains("note") ||
            lower.contains("notes") ||
            lower.contains("marked") ||
            lower.contains("saved quote") ||
            lower.contains("my highlights")
    }

    private fun handleBookmarkQuery(
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
                    "💡 *Tip: While listening, tap the bookmark icon to save key moments, quotes, or thoughts. You can then ask me to summarize them or search your notes!*",
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
            lower.contains("show notes")

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

                // If user has multiple notes, synthesize key points
                if (withNotes.size >= 2) {
                    val combinedWords = withNotes.joinToString(" ") { it.bookmark.note }
                    sb.append("**Key Themes in Your Notes:**\n")
                    sb.append("You've tracked ${withNotes.size} distinct observations across ")
                    val distinctChapters = withNotes.mapNotNull { it.chapter?.displayName }.distinct()
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

        // Search specific notes
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
                "I couldn't find notes mentioning those exact terms. Your existing notes mention topics like: *\"$sampleTopics\"*.\n\nWould you like a full summary of all your notes?"
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

    private fun extractDefinitionWord(trimmed: String, lower: String): String? {
        val patterns = listOf(
            Regex("(?i)^define\\s+([a-zA-Z\\-']+)"),
            Regex("(?i)^definition\\s+of\\s+([a-zA-Z\\-']+)"),
            Regex("(?i)^what\\s+(?:does|is)\\s+(?:the\\s+word\\s+)?([a-zA-Z\\-']+)\\s+mean"),
            Regex("(?i)^meaning\\s+of\\s+([a-zA-Z\\-']+)"),
            Regex("(?i)^explain\\s+the\\s+word\\s+([a-zA-Z\\-']+)"),
        )
        for (p in patterns) {
            val match = p.find(trimmed)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        // Single word check
        if (!lower.contains(" ") && lower.length in 3..25 && lower.all { it.isLetter() || it == '-' }) {
            return lower
        }
        return null
    }

    private fun queryDictionary(word: String): AiChatMessage? {
        try {
            val encoded = URLEncoder.encode(word.lowercase(), "UTF-8")
            val urlString = "https://api.dictionaryapi.dev/api/v2/entries/en/$encoded"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "FoxPlayer/1.1.0")
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val jsonArray = JSONArray(body)
            if (jsonArray.length() == 0) return null

            val entry = jsonArray.getJSONObject(0)
            val returnedWord = entry.optString("word", word)
            val phonetic = entry.optString("phonetic").takeIf { it.isNotBlank() }

            val meaningsArray = entry.optJSONArray("meanings") ?: return null
            val sb = StringBuilder()
            sb.append("📖 **${returnedWord.replaceFirstChar { it.uppercase() }}**")
            if (phonetic != null) sb.append(" `/$phonetic/`")
            sb.append("\n\n")

            var totalDefs = 0
            for (i in 0 until meaningsArray.length()) {
                val m = meaningsArray.getJSONObject(i)
                val pos = m.optString("partOfSpeech")
                val defs = m.optJSONArray("definitions") ?: continue

                sb.append("*${pos.replaceFirstChar { it.uppercase() }}*\n")
                for (j in 0 until defs.length()) {
                    val d = defs.getJSONObject(j)
                    val defText = d.optString("definition")
                    val example = d.optString("example")
                    if (defText.isNotBlank()) {
                        sb.append("• ").append(defText).append("\n")
                        if (example.isNotBlank()) {
                            sb.append("  *Example:* \"").append(example).append("\"\n")
                        }
                        totalDefs++
                        if (totalDefs >= 4) break
                    }
                }
                sb.append("\n")
                if (totalDefs >= 4) break
            }

            return AiChatMessage(
                sender = ChatSender.Assistant,
                text = sb.toString().trim(),
                sources = listOf(ChatSource("Dictionary ($word)", "Dictionary")),
            )
        } catch (e: Exception) {
            logE("Dictionary API error", e)
            return null
        }
    }

    private fun isHistoricalOrOnlineIntent(lower: String): Boolean {
        return lower.contains("historical") ||
            lower.contains("history") ||
            lower.contains("real life") ||
            lower.contains("background") ||
            lower.contains("who was") ||
            lower.contains("who is") ||
            lower.contains("where is") ||
            lower.contains("what happened") ||
            lower.contains("search online") ||
            lower.contains("google") ||
            lower.contains("wikipedia") ||
            lower.contains("author bio") ||
            lower.contains("setting of")
    }

    private fun queryOnlineContext(query: String, book: BookEntity?): AiChatMessage? {
        val cleanSearch = query
            .replace(Regex("(?i)^(?:search online for|tell me about|what is the historical context of|historical context of|who was|who is|what is)\\s+"), "")
            .replace(Regex("(?i)\\s+(?:in real life|historical context)$"), "")
            .trim()

        val searchQuery = cleanSearch.ifBlank { query }

        // 1. Search Wikipedia
        val wikiResult = queryWikipedia(searchQuery)
        if (wikiResult != null) return wikiResult

        // If specific search failed and book author exists, try searching topic with book author/subject
        if (book != null && !book.author.isNullOrBlank() && (query.contains("author") || query.contains("who wrote"))) {
            val authorWiki = queryWikipedia(book.author)
            if (authorWiki != null) return authorWiki
        }

        // 2. DuckDuckGo Instant Answer
        return queryDuckDuckGo(searchQuery)
    }

    private fun queryWikipedia(searchTerm: String): AiChatMessage? {
        try {
            val encoded = URLEncoder.encode(searchTerm, "UTF-8")
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

            // Get summary
            val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/${URLEncoder.encode(topTitle, "UTF-8")}"
            val sumConn = (URL(summaryUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "FoxPlayer/1.1.0")
            }
            if (sumConn.responseCode != 200) {
                sumConn.disconnect()
                return null
            }
            val sumJson = JSONObject(sumConn.inputStream.bufferedReader().use { it.readText() })
            sumConn.disconnect()

            val extract = sumJson.optString("extract").takeIf { it.isNotBlank() } ?: return null
            val description = sumJson.optString("description")

            val sb = StringBuilder()
            sb.append("🌐 **$topTitle**")
            if (description.isNotBlank()) sb.append(" *($description)*")
            sb.append("\n\n")
            sb.append(extract)

            return AiChatMessage(
                sender = ChatSender.Assistant,
                text = sb.toString(),
                sources = listOf(ChatSource(topTitle, "Wikipedia")),
            )
        } catch (e: Exception) {
            logE("Wikipedia query error", e)
            return null
        }
    }

    private fun queryDuckDuckGo(searchTerm: String): AiChatMessage? {
        try {
            val encoded = URLEncoder.encode(searchTerm, "UTF-8")
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

            val heading = json.optString("Heading")
            val abstractText = json.optString("AbstractText")
            if (abstractText.isNotBlank()) {
                val sb = StringBuilder()
                if (heading.isNotBlank()) sb.append("🔍 **$heading**\n\n")
                sb.append(abstractText)
                return AiChatMessage(
                    sender = ChatSender.Assistant,
                    text = sb.toString(),
                    sources = listOf(ChatSource("DuckDuckGo Knowledge", "Web")),
                )
            }
        } catch (e: Exception) {
            logE("DuckDuckGo API error", e)
        }
        return null
    }

    private fun isBookMetaIntent(lower: String): Boolean {
        return lower.contains("what is this book about") ||
            lower.contains("plot") ||
            lower.contains("synopsis") ||
            lower.contains("summary of book") ||
            lower.contains("who wrote") ||
            lower.contains("author") ||
            lower.contains("genre") ||
            lower.contains("genres") ||
            lower.contains("characters") ||
            lower.contains("chapters")
    }

    private fun handleBookMetaQuery(
        query: String,
        lower: String,
        book: BookEntity,
        chapters: List<ChapterEntity>,
    ): AiChatMessage {
        val sb = StringBuilder()
        sb.append("📖 **${book.title}**")
        if (!book.author.isNullOrBlank()) sb.append(" by *${book.author}*")
        sb.append("\n\n")

        if (lower.contains("chapter")) {
            sb.append("This audiobook contains **${chapters.size} chapters**:\n")
            chapters.take(8).forEachIndexed { i, ch ->
                sb.append("• ${ch.displayName} (${formatDuration(ch.durationMs)})\n")
            }
            if (chapters.size > 8) {
                sb.append("• ...and ${chapters.size - 8} more chapters.\n")
            }
        } else if (lower.contains("genre")) {
            val g = book.genres ?: "Not categorized yet"
            sb.append("**Genres:** $g\n")
        } else {
            if (!book.genres.isNullOrBlank()) {
                sb.append("**Genres:** ${book.genres}\n\n")
            }
            val desc = book.description?.takeIf { it.isNotBlank() }
            if (desc != null) {
                sb.append("**Synopsis:**\n").append(desc).append("\n")
            } else {
                sb.append("No synopsis is currently stored for this audiobook. You can run **Enrich with AI** in the top bar to fetch a complete plot summary and high-resolution cover!")
            }
        }

        return AiChatMessage(
            sender = ChatSender.Assistant,
            text = sb.toString().trim(),
            sources = listOf(ChatSource(book.title, "Book Info")),
        )
    }

    private fun handleGeneralFallback(
        query: String,
        book: BookEntity?,
        chapters: List<ChapterEntity>,
        bookmarks: List<BookmarkWithChapter>,
    ): AiChatMessage {
        val sources = mutableListOf<ChatSource>()
        val sb = StringBuilder()

        if (book != null) {
            sb.append("Regarding *${book.title}*")
            if (!book.author.isNullOrBlank()) sb.append(" by ${book.author}")
            sb.append(":\n\n")
            sources.add(ChatSource(book.title, "Book Context"))
        }

        sb.append("I can help you explore this audiobook in depth. Here are some things you can ask me:\n\n")
        sb.append("• 📝 **\"Summarize my notes\"** — review your ${bookmarks.size} saved bookmarks\n")
        sb.append("• 📖 **\"Define [word]\"** — look up definitions and literary terms\n")
        sb.append("• 🌐 **\"Historical context of [topic]\"** — search encyclopedia lore online\n")
        sb.append("• 🔍 **\"What is this book about?\"** — view themes, plot, and chapters")

        if (modelManager?.isModelDownloaded() == true) {
            sources.add(ChatSource("SmolLM-135M ONNX Ready", "On-Device Neural Model"))
        }

        return AiChatMessage(
            sender = ChatSender.Assistant,
            text = sb.toString(),
            sources = sources,
        )
    }
}
