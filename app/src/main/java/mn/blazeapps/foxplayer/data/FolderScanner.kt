package mn.blazeapps.foxplayer.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class FolderScanner(
    private val context: Context,
    private val metadata: AudioMetadataReader = AudioMetadataReader(context),
) {

    data class ScannedFolder(
        val title: String,
        val author: String?,
        val audioFiles: List<ScannedAudio>,
        val coverUri: Uri?,
        val embeddedCoverUri: Uri?,
    )

    data class ScannedAudio(
        val displayName: String,
        val uri: Uri,
        val durationMs: Long,
        val titleTag: String? = null,
        val albumTag: String? = null,
        val artistTag: String? = null,
        val albumArtistTag: String? = null,
        val trackNumber: Int? = null,
        val hasEmbeddedCover: Boolean = false,
    )

    fun scan(treeUri: Uri): ScannedFolder {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw ImportException("Could not open the selected folder.")
        if (!tree.exists() || !tree.canRead()) {
            throw ImportException("Folder access was lost. Pick the folder again.")
        }

        val children = tree.listFiles()
        val cover = findCover(children)

        val audio = mutableListOf<ScannedAudio>()
        for (child in children) {
            if (child.isFile && isAudio(child)) {
                audio += scanAudio(child, parentName = null)
            } else if (child.isDirectory) {
                val nested = child.listFiles().filter { it.isFile && isAudio(it) }
                nested.forEach { file ->
                    audio += scanAudio(file, parentName = child.name)
                }
            }
        }
        audio.sortWith(chapterComparator())

        if (audio.isEmpty()) {
            throw ImportException("No audio files found in this folder.")
        }

        val folderName = tree.name?.ifBlank { null }
        val title = resolveBookTitle(audio, folderName)
        val author = resolveAuthor(audio)
        val embeddedCoverUri = audio.firstOrNull { it.hasEmbeddedCover }?.uri

        return ScannedFolder(
            title = title,
            author = author,
            audioFiles = audio,
            coverUri = cover,
            embeddedCoverUri = embeddedCoverUri,
        )
    }

    fun canRead(treeUri: Uri): Boolean {
        val persisted = context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission
        }
        if (!persisted) return false
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        return tree.exists() && tree.canRead()
    }

    private fun scanAudio(file: DocumentFile, parentName: String?): ScannedAudio {
        val name = file.name.orEmpty()
        val base = name.substringBeforeLast('.').ifBlank { name }
        val tags = metadata.read(file.uri, loadCoverHint = true)
        val taggedTitle = tags.title
        val display = when {
            !taggedTitle.isNullOrBlank() && !parentName.isNullOrBlank() ->
                "$parentName — $taggedTitle"
            !taggedTitle.isNullOrBlank() -> taggedTitle
            !parentName.isNullOrBlank() -> "$parentName — $base"
            else -> base
        }
        return ScannedAudio(
            displayName = display,
            uri = file.uri,
            durationMs = tags.durationMs,
            titleTag = taggedTitle,
            albumTag = tags.album,
            artistTag = tags.artist,
            albumArtistTag = tags.albumArtist,
            trackNumber = tags.trackNumber,
            hasEmbeddedCover = tags.hasEmbeddedCover,
        )
    }

    private fun resolveBookTitle(audio: List<ScannedAudio>, folderName: String?): String {
        val albums = audio.mapNotNull { it.albumTag }.filter { it.isNotBlank() }
        val consensusAlbum = majority(albums)
        if (!consensusAlbum.isNullOrBlank()) return consensusAlbum
        return folderName?.ifBlank { null } ?: "Audiobook"
    }

    private fun resolveAuthor(audio: List<ScannedAudio>): String? {
        val albumArtists = audio.mapNotNull { it.albumArtistTag }.filter { it.isNotBlank() }
        majority(albumArtists)?.let { return it }
        val artists = audio.mapNotNull { it.artistTag }.filter { it.isNotBlank() }
        return majority(artists)
    }

    private fun majority(values: List<String>): String? {
        if (values.isEmpty()) return null
        val top = values.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
        return if (top.value * 2 >= values.size) top.key else values.first()
    }

    private fun chapterComparator(): Comparator<ScannedAudio> =
        Comparator { a, b ->
            val trackA = a.trackNumber
            val trackB = b.trackNumber
            when {
                trackA != null && trackB != null && trackA != trackB -> trackA.compareTo(trackB)
                trackA != null && trackB == null -> -1
                trackA == null && trackB != null -> 1
                else -> naturalCompare(a.displayName, b.displayName)
            }
        }

    private fun findCover(children: Array<DocumentFile>): Uri? {
        val named = children.firstOrNull { file ->
            file.isFile && file.name?.lowercase() in COVER_NAMES
        }?.uri
        if (named != null) return named
        return children.firstOrNull { file ->
            file.isFile && isImage(file)
        }?.uri
    }

    private fun isAudio(file: DocumentFile): Boolean {
        val name = file.name?.lowercase().orEmpty()
        if (AUDIO_EXTENSIONS.any { name.endsWith(it) }) return true
        val mime = file.type.orEmpty()
        return mime.startsWith("audio/")
    }

    private fun isImage(file: DocumentFile): Boolean {
        val name = file.name?.lowercase().orEmpty()
        if (IMAGE_EXTENSIONS.any { name.endsWith(it) }) return true
        val mime = file.type.orEmpty()
        return mime.startsWith("image/")
    }

    companion object {
        private val AUDIO_EXTENSIONS = listOf(
            ".mp3", ".m4a", ".m4b", ".flac", ".ogg", ".opus", ".aac", ".wav",
        )
        private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp")
        private val COVER_NAMES = setOf(
            "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
            "folder.jpg", "folder.jpeg", "folder.png",
        )

        internal fun naturalCompare(a: String, b: String): Int {
            val partsA = tokenize(a)
            val partsB = tokenize(b)
            val n = minOf(partsA.size, partsB.size)
            for (i in 0 until n) {
                val left = partsA[i]
                val right = partsB[i]
                val cmp = if (left.isDigits && right.isDigits) {
                    val byValue = left.text.toBigInteger().compareTo(right.text.toBigInteger())
                    if (byValue != 0) byValue else left.text.length.compareTo(right.text.length)
                } else {
                    left.text.compareTo(right.text, ignoreCase = true)
                }
                if (cmp != 0) return cmp
            }
            return partsA.size.compareTo(partsB.size)
        }

        private fun tokenize(value: String): List<Token> {
            val regex = Regex("(\\d+)|(\\D+)")
            return regex.findAll(value.lowercase()).map { match ->
                val text = match.value
                Token(text, text.all { it.isDigit() })
            }.toList()
        }

        private data class Token(val text: String, val isDigits: Boolean)
    }
}

class ImportException(message: String) : Exception(message)
