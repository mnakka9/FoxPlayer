package mn.blazeapps.foxplayer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class OnlineGenreResolver {

    suspend fun resolveOnline(title: String, author: String?): String? = withContext(Dispatchers.IO) {
        val cleanTitle = GenreExtractor.cleanTitleForSearch(title)
        if (cleanTitle.isBlank()) return@withContext null
        val cleanAuthor = GenreExtractor.cleanAuthorForSearch(author)

        // 1. Try search with title and author
        val subjectsWithAuthor = queryOpenLibrary(cleanTitle, cleanAuthor)
        val genres = GenreExtractor.cleanSubjects(subjectsWithAuthor)
        if (genres.isNotEmpty()) {
            return@withContext genres.joinToString(", ")
        }

        // 2. If nothing found and author was provided, try search with title only
        if (cleanAuthor != null) {
            val subjectsTitleOnly = queryOpenLibrary(cleanTitle, null)
            val fallbackGenres = GenreExtractor.cleanSubjects(subjectsTitleOnly)
            if (fallbackGenres.isNotEmpty()) {
                return@withContext fallbackGenres.joinToString(", ")
            }
        }

        null
    }

    private fun queryOpenLibrary(title: String, author: String?): List<String> {
        return try {
            val params = buildString {
                append("title=").append(URLEncoder.encode(title, "UTF-8"))
                if (!author.isNullOrBlank() && !author.equals("Unknown author", ignoreCase = true)) {
                    append("&author=").append(URLEncoder.encode(author, "UTF-8"))
                }
                append("&limit=3&fields=title,author_name,subject")
            }
            val url = URL("https://openlibrary.org/search.json?$params")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
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
            val allSubjects = mutableListOf<String>()

            for (i in 0 until docs.length()) {
                val doc = docs.optJSONObject(i) ?: continue
                val subjects = doc.optJSONArray("subject") ?: continue
                for (j in 0 until subjects.length()) {
                    val s = subjects.optString(j)
                    if (!s.isNullOrBlank()) {
                        allSubjects += s
                    }
                }
            }

            allSubjects
        } catch (_: Exception) {
            emptyList()
        }
    }
}
