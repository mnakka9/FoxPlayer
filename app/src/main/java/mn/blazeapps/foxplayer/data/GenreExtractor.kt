package mn.blazeapps.foxplayer.data

object GenreExtractor {

    private val GENERIC_GENRES = setOf(
        "audiobook",
        "audiobooks",
        "audio book",
        "audio books",
        "audio-book",
        "audio-books",
        "spoken word",
        "spoken",
        "speech",
        "vocal",
        "other",
        "unknown",
        "general",
        "books & spoken",
        "books & spoken word",
        "spoken audio",
        "audio",
        "podcast",
        "soundtrack",
        "sound effects",
        "sound recording",
        "non-music",
        "book",
        "books",
    )

    private val JUNK_PREFIXES = listOf(
        "nyt:",
        "award:",
        "new york times",
        "long now",
        "large type",
        "accessible book",
        "protected daisy",
        "in library",
        "open library",
        "overdrive",
        "reading level",
        "published",
        "internet archive",
        "borrow",
        "cd",
        "cassette",
        "unabridged",
        "abridged",
    )

    private val CANONICAL_MAPPINGS = listOf(
        Regex("(?i)\\b(sci-fi|science fiction|space opera|hard sci-fi|interplanetary)\\b") to "Science Fiction",
        Regex("(?i)\\b(fantasy|epic fantasy|urban fantasy|magic|sword and sorcery)\\b") to "Fantasy",
        Regex("(?i)\\b(thriller|suspense|psychological thriller|espionage|spy)\\b") to "Thriller",
        Regex("(?i)\\b(mystery|detective|whodunit|cozy mystery)\\b") to "Mystery",
        Regex("(?i)\\b(horror|ghost stories|supernatural|gothic)\\b") to "Horror",
        Regex("(?i)\\b(romance|romantic fiction|love stories)\\b") to "Romance",
        Regex("(?i)\\b(historical fiction|historical)\\b") to "Historical Fiction",
        Regex("(?i)\\b(biography|memoir|autobiography|diaries|letters)\\b") to "Biography & Memoir",
        Regex("(?i)\\b(self-help|personal growth|motivation|habit|success)\\b") to "Self-Help",
        Regex("(?i)\\b(business|economics|finance|entrepreneurship|investing|management)\\b") to "Business",
        Regex("(?i)\\b(psychology|behavior|mental health|mind)\\b") to "Psychology",
        Regex("(?i)\\b(philosophy|ethics|logic)\\b") to "Philosophy",
        Regex("(?i)\\b(history|world war|civilization|military history|ancient history)\\b") to "History",
        Regex("(?i)\\b(true crime|murder|serial killers)\\b") to "True Crime",
        Regex("(?i)\\b(action & adventure|adventure|survival|sea stories)\\b") to "Adventure",
        Regex("(?i)\\b(humor|comedy|satire|parody)\\b") to "Humor",
        Regex("(?i)\\b(classics|classic literature)\\b") to "Classics",
        Regex("(?i)\\b(young adult|ya fiction|teen fiction)\\b") to "Young Adult",
        Regex("(?i)\\b(dystopian|dystopia|post-apocalyptic)\\b") to "Dystopian",
        Regex("(?i)\\b(cyberpunk|steampunk)\\b") to "Cyberpunk",
        Regex("(?i)\\b(science|physics|astronomy|biology|nature|evolution)\\b") to "Science",
        Regex("(?i)\\b(politics|political science|government)\\b") to "Politics",
        Regex("(?i)\\b(health|wellness|nutrition|fitness|medicine)\\b") to "Health & Wellness",
        Regex("(?i)\\b(spirituality|religion|meditation|buddhism|christianity|theology)\\b") to "Spirituality",
        Regex("(?i)\\b(travel|essays|journalism)\\b") to "Travel & Essays",
    )

    fun isGeneric(genre: String?): Boolean {
        if (genre.isNullOrBlank()) return true
        val normalized = genre.trim().lowercase()
        return normalized in GENERIC_GENRES ||
            normalized.startsWith("audiobook") ||
            normalized.startsWith("audio book") ||
            normalized.startsWith("audio-book")
    }

    fun cleanSubjects(rawSubjects: List<String>): List<String> {
        val matchedGenres = mutableListOf<String>()

        for (raw in rawSubjects) {
            val lower = raw.trim().lowercase()
            if (JUNK_PREFIXES.any { lower.startsWith(it) }) continue
            if (isGeneric(lower)) continue

            for ((regex, canonical) in CANONICAL_MAPPINGS) {
                if (regex.containsMatchIn(raw) && canonical !in matchedGenres) {
                    matchedGenres += canonical
                    break
                }
            }
            if (matchedGenres.size >= 3) break
        }

        if (matchedGenres.isEmpty()) {
            for (raw in rawSubjects) {
                val clean = raw.substringAfterLast('/').substringAfterLast(',').trim()
                val lower = clean.lowercase()
                if (clean.length in 3..25 &&
                    !JUNK_PREFIXES.any { lower.startsWith(it) } &&
                    !isGeneric(lower) &&
                    !lower.contains("place") &&
                    !lower.contains("bestseller") &&
                    !lower.contains("reviewed") &&
                    !lower.contains("fiction,") &&
                    !lower.contains("imaginary")
                ) {
                    val formatted = clean.replaceFirstChar { it.uppercase() }
                    if (formatted !in matchedGenres) {
                        matchedGenres += formatted
                    }
                }
                if (matchedGenres.size >= 3) break
            }
        }

        return matchedGenres.take(3)
    }

    fun cleanTitleForSearch(title: String): String {
        var clean = title.trim()
        clean = clean.replace(Regex("(?i)\\s*[\\[(](?:unabridged|abridged|audiobook|audio\\s+edition)[\\])]"), "")
        clean = clean.replace(Regex("(?i)\\s*[\\[(](?:a\\s+novel|book\\s*\\d+)[\\])]"), "")
        clean = clean.replace(Regex("(?i)\\s*-\\s*(?:unabridged|audiobook)"), "")
        return clean.trim().ifBlank { title.trim() }
    }

    fun cleanAuthorForSearch(author: String?): String? {
        if (author.isNullOrBlank()) return null
        var clean = author.trim()
        clean = clean.replace(Regex("(?i)\\s*[\\[(](?:narrated|read|narrator|performed)\\s+by[^\\])]*[\\])]"), "")
        clean = clean.replace(Regex("(?i)\\s*(?:read|narrated)\\s+by\\s+.*"), "")
        return clean.trim().takeIf { it.isNotBlank() }
    }
}
