package mn.blazeapps.foxplayer.data.search

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mn.blazeapps.foxplayer.data.GenreExtractor
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class BookCandidate(
    val title: String,
    val author: String?,
    val description: String?,
    val categories: List<String>,
    val coverUrl: String?,
    val source: String,
)

data class WebSearchResult(
    val candidates: List<BookCandidate>,
    val aggregatedGenres: List<String>,
    val bestDescription: String?,
    val bestCoverUrl: String?,
    val logs: List<String>,
)

class WebBookSearchResolver {

    companion object {
        private const val TAG = "FoxPlayer-WebSearch"
    }

    suspend fun searchBook(title: String, author: String?): WebSearchResult = withContext(Dispatchers.IO) {
        val logs = mutableListOf<String>()
        val cleanTitle = GenreExtractor.cleanTitleForSearch(title)
        val cleanAuthor = GenreExtractor.cleanAuthorForSearch(author)
        val candidates = mutableListOf<BookCandidate>()

        log(logs, "[WebSearch] Initiating search for title='$cleanTitle'${if (cleanAuthor != null) ", author='$cleanAuthor'" else ""}")

        // 1. Google Books API
        val googleResults = queryGoogleBooks(cleanTitle, cleanAuthor, logs)
        candidates += googleResults

        // 2. Open Library Search API
        val openLibResults = queryOpenLibrary(cleanTitle, cleanAuthor, logs)
        candidates += openLibResults

        // 3. Fallback to DuckDuckGo if no descriptions found
        val hasAnyDesc = candidates.any { !it.description.isNullOrBlank() }
        if (!hasAnyDesc) {
            log(logs, "[WebSearch] No descriptions in API results; running web search fallback...")
            val webSnippets = queryDuckDuckGoFallback("$cleanTitle ${cleanAuthor.orEmpty()}", logs)
            candidates += webSnippets
        }

        log(logs, "[WebSearch] Total candidate records retrieved: ${candidates.size}")

        val allCategories = candidates.flatMap { it.categories }
        val canonicalGenres = GenreExtractor.cleanSubjects(allCategories)
        log(logs, "[WebSearch] Inferred genres: ${if (canonicalGenres.isNotEmpty()) canonicalGenres.joinToString(", ") else "None (will classify via AI)"}")

        val bestDesc = candidates
            .mapNotNull { it.description?.trim() }
            .filter { it.length > 25 }
            .maxByOrNull { it.length }
            ?.let { sanitizeDescription(it) }

        if (bestDesc != null) {
            log(logs, "[WebSearch] Selected candidate description: \"${bestDesc.take(90)}...\" (${bestDesc.length} chars)")
        } else {
            log(logs, "[WebSearch] No synopsis text discovered in search candidates")
        }

        val bestCover = candidates
            .mapNotNull { it.coverUrl }
            .firstOrNull { it.isNotBlank() }

        if (bestCover != null) {
            log(logs, "[WebSearch] Best cover image URL: $bestCover")
        }

        WebSearchResult(
            candidates = candidates,
            aggregatedGenres = canonicalGenres,
            bestDescription = bestDesc,
            bestCoverUrl = bestCover,
            logs = logs,
        )
    }

    private fun log(logs: MutableList<String>, message: String) {
        logs.add(message)
        Log.i(TAG, message)
    }

    private fun queryGoogleBooks(title: String, author: String?, logs: MutableList<String>): List<BookCandidate> {
        val candidates = mutableListOf<BookCandidate>()
        try {
            val query = buildString {
                append("intitle:\"").append(title).append("\"")
                if (!author.isNullOrBlank() && !author.equals("Unknown author", ignoreCase = true)) {
                    append("+inauthor:\"").append(author).append("\"")
                }
            }
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://www.googleapis.com/books/v1/volumes?q=$encodedQuery&maxResults=3&printType=books"
            log(logs, "[GoogleBooks] Querying: $urlString")

            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; FoxPlayer/1.1.0)")
                setRequestProperty("Accept", "application/json")
            }

            val code = connection.responseCode
            if (code == 429) {
                log(logs, "[GoogleBooks] HTTP 429: Anonymous rate quota exceeded, skipping")
                connection.disconnect()
                return emptyList()
            } else if (code != HttpURLConnection.HTTP_OK) {
                log(logs, "[GoogleBooks] HTTP $code ${connection.responseMessage}")
                connection.disconnect()
                return emptyList()
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(response)
            val items = json.optJSONArray("items") ?: return emptyList()
            log(logs, "[GoogleBooks] Found ${items.length()} book items")

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val vol = item.optJSONObject("volumeInfo") ?: continue
                val itemTitle = vol.optString("title").ifBlank { title }

                val authorsList = mutableListOf<String>()
                val authorsArray = vol.optJSONArray("authors")
                if (authorsArray != null) {
                    for (j in 0 until authorsArray.length()) {
                        val a = authorsArray.optString(j)
                        if (!a.isNullOrBlank()) authorsList += a
                    }
                }
                val authorStr = authorsList.joinToString(", ").takeIf { it.isNotBlank() }
                val desc = vol.optString("description").takeIf { it.isNotBlank() }

                val categories = mutableListOf<String>()
                val catArray = vol.optJSONArray("categories")
                if (catArray != null) {
                    for (j in 0 until catArray.length()) {
                        val c = catArray.optString(j)
                        if (!c.isNullOrBlank()) categories += c
                    }
                }

                var coverUrl: String? = null
                val imageLinks = vol.optJSONObject("imageLinks")
                if (imageLinks != null) {
                    coverUrl = imageLinks.optString("extraLarge")
                        .ifBlank { imageLinks.optString("large") }
                        .ifBlank { imageLinks.optString("medium") }
                        .ifBlank { imageLinks.optString("thumbnail") }
                        .ifBlank { imageLinks.optString("smallThumbnail") }
                        .takeIf { it.isNotBlank() }
                        ?.replace("http://", "https://")
                        ?.replace("&edge=curl", "")
                }

                candidates += BookCandidate(
                    title = itemTitle,
                    author = authorStr,
                    description = desc,
                    categories = categories,
                    coverUrl = coverUrl,
                    source = "Google Books",
                )
            }
        } catch (e: Exception) {
            log(logs, "[GoogleBooks] Exception: ${e.message}")
        }
        return candidates
    }

    private fun queryOpenLibrary(title: String, author: String?, logs: MutableList<String>): List<BookCandidate> {
        val candidates = mutableListOf<BookCandidate>()
        try {
            val params = buildString {
                append("q=").append(URLEncoder.encode(title, "UTF-8"))
                if (!author.isNullOrBlank() && !author.equals("Unknown author", ignoreCase = true)) {
                    append("+").append(URLEncoder.encode(author, "UTF-8"))
                }
                append("&limit=3&fields=key,title,author_name,subject,cover_i")
            }
            val urlString = "https://openlibrary.org/search.json?$params"
            log(logs, "[OpenLibrary] Querying: $urlString")

            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "FoxPlayer/1.1.0 (Android Audiobook Player; contact@foxplayer.app)")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                log(logs, "[OpenLibrary] HTTP ${connection.responseCode}")
                connection.disconnect()
                return emptyList()
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(response)
            val docs = json.optJSONArray("docs") ?: return emptyList()
            log(logs, "[OpenLibrary] Found ${docs.length()} document matches")

            for (i in 0 until docs.length()) {
                val doc = docs.optJSONObject(i) ?: continue
                val docTitle = doc.optString("title").ifBlank { title }

                val authorsList = mutableListOf<String>()
                val authorsArray = doc.optJSONArray("author_name")
                if (authorsArray != null) {
                    for (j in 0 until authorsArray.length()) {
                        val a = authorsArray.optString(j)
                        if (!a.isNullOrBlank()) authorsList += a
                    }
                }
                val authorStr = authorsList.joinToString(", ").takeIf { it.isNotBlank() }

                val subjects = mutableListOf<String>()
                val subArray = doc.optJSONArray("subject")
                if (subArray != null) {
                    for (j in 0 until subArray.length()) {
                        val s = subArray.optString(j)
                        if (!s.isNullOrBlank()) subjects += s
                    }
                }

                val coverId = doc.optLong("cover_i", -1L)
                val coverUrl = if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-L.jpg" else null

                // Fetch full work description if available
                val workKey = doc.optString("key")
                var workDescription: String? = null
                if (workKey.isNotBlank()) {
                    workDescription = fetchOpenLibraryWorkDescription(workKey, logs)
                }

                candidates += BookCandidate(
                    title = docTitle,
                    author = authorStr,
                    description = workDescription,
                    categories = subjects,
                    coverUrl = coverUrl,
                    source = "Open Library",
                )
            }
        } catch (e: Exception) {
            log(logs, "[OpenLibrary] Exception: ${e.message}")
        }
        return candidates
    }

    private fun fetchOpenLibraryWorkDescription(workKey: String, logs: MutableList<String>): String? {
        return try {
            val urlString = "https://openlibrary.org${workKey}.json"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "FoxPlayer/1.1.0")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(text)
            val d = json.opt("description")
            val desc = when (d) {
                is String -> d
                is JSONObject -> d.optString("value")
                else -> null
            }?.takeIf { it.isNotBlank() }
            if (desc != null) {
                log(logs, "[OpenLibrary] Fetched full work synopsis for $workKey (${desc.length} chars)")
            }
            desc
        } catch (_: Exception) {
            null
        }
    }

    private fun queryDuckDuckGoFallback(query: String, logs: MutableList<String>): List<BookCandidate> {
        val candidates = mutableListOf<BookCandidate>()
        try {
            val encoded = URLEncoder.encode("$query synopsis book", "UTF-8")
            val urlString = "https://html.duckduckgo.com/html/?q=$encoded"
            log(logs, "[DuckDuckGo] Querying web fallback: $urlString")

            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }

            if (conn.responseCode != 200) {
                conn.disconnect()
                return emptyList()
            }

            val html = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val snippetRegex = Regex("class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
            val matches = snippetRegex.findAll(html).take(2).toList()

            for (m in matches) {
                val rawSnippet = m.groupValues[1]
                val clean = sanitizeDescription(rawSnippet)
                if (clean.length > 40) {
                    candidates += BookCandidate(
                        title = query,
                        author = null,
                        description = clean,
                        categories = emptyList(),
                        coverUrl = null,
                        source = "DuckDuckGo Web",
                    )
                }
            }
            log(logs, "[DuckDuckGo] Retrieved ${candidates.size} web snippets")
        } catch (e: Exception) {
            log(logs, "[DuckDuckGo] Exception: ${e.message}")
        }
        return candidates
    }

    private fun sanitizeDescription(raw: String): String {
        var clean = raw
            .replace(Regex("<[^>]*>"), " ") // Strip HTML tags
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (clean.length > 900) {
            val cut = clean.substring(0, 900)
            val lastPeriod = cut.lastIndexOf('.')
            clean = if (lastPeriod > 200) {
                cut.substring(0, lastPeriod + 1)
            } else {
                "$cut..."
            }
        }
        return clean
    }
}
