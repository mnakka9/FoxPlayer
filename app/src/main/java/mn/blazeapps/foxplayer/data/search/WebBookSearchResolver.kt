package mn.blazeapps.foxplayer.data.search

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
)

class WebBookSearchResolver {

    suspend fun searchBook(title: String, author: String?): WebSearchResult = withContext(Dispatchers.IO) {
        val cleanTitle = GenreExtractor.cleanTitleForSearch(title)
        val cleanAuthor = GenreExtractor.cleanAuthorForSearch(author)
        val candidates = mutableListOf<BookCandidate>()

        // 1. Google Books API
        candidates += queryGoogleBooks(cleanTitle, cleanAuthor)

        // 2. Open Library API
        candidates += queryOpenLibrary(cleanTitle, cleanAuthor)

        val allCategories = candidates.flatMap { it.categories }
        val canonicalGenres = GenreExtractor.cleanSubjects(allCategories)

        val bestDesc = candidates
            .mapNotNull { it.description?.trim() }
            .filter { it.length > 30 }
            .maxByOrNull { it.length }
            ?.let { sanitizeDescription(it) }

        val bestCover = candidates
            .mapNotNull { it.coverUrl }
            .firstOrNull { it.isNotBlank() }

        WebSearchResult(
            candidates = candidates,
            aggregatedGenres = canonicalGenres,
            bestDescription = bestDesc,
            bestCoverUrl = bestCover,
        )
    }

    private fun queryGoogleBooks(title: String, author: String?): List<BookCandidate> {
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
            val url = URL(urlString)

            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "FoxPlayer/1.0 (Android Audiobook Player)")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return emptyList()
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(response)
            val items = json.optJSONArray("items") ?: return emptyList()

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
        } catch (_: Exception) {
            // Ignored; fallback continues
        }
        return candidates
    }

    private fun queryOpenLibrary(title: String, author: String?): List<BookCandidate> {
        val candidates = mutableListOf<BookCandidate>()
        try {
            val params = buildString {
                append("title=").append(URLEncoder.encode(title, "UTF-8"))
                if (!author.isNullOrBlank() && !author.equals("Unknown author", ignoreCase = true)) {
                    append("&author=").append(URLEncoder.encode(author, "UTF-8"))
                }
                append("&limit=3&fields=title,author_name,subject,cover_i")
            }
            val url = URL("https://openlibrary.org/search.json?$params")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "FoxPlayer/1.0 (Android Audiobook Player; contact@foxplayer.app)")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return emptyList()
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(response)
            val docs = json.optJSONArray("docs") ?: return emptyList()

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

                candidates += BookCandidate(
                    title = docTitle,
                    author = authorStr,
                    description = null,
                    categories = subjects,
                    coverUrl = coverUrl,
                    source = "Open Library",
                )
            }
        } catch (_: Exception) {
            // Ignored
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
