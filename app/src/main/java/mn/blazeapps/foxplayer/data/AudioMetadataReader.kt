package mn.blazeapps.foxplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

/**
 * Reads ID3 / MP4 / common audio tags and embedded cover art via
 * [MediaMetadataRetriever], which works with SAF content URIs.
 */
class AudioMetadataReader(private val context: Context) {

    data class Tags(
        val title: String? = null,
        val album: String? = null,
        val artist: String? = null,
        val albumArtist: String? = null,
        val genre: String? = null,
        val trackNumber: Int? = null,
        val durationMs: Long = 0L,
        val hasEmbeddedCover: Boolean = false,
    )

    fun read(uri: Uri, loadCoverHint: Boolean = true): Tags {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).clean()
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).clean()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).clean()
            val albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).clean()
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE).clean()
            val track = parseTrack(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER))
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
            val hasCover = if (loadCoverHint) {
                (retriever.embeddedPicture?.isNotEmpty() == true)
            } else {
                false
            }
            Tags(
                title = title,
                album = album,
                artist = artist,
                albumArtist = albumArtist,
                genre = genre,
                trackNumber = track,
                durationMs = duration,
                hasEmbeddedCover = hasCover,
            )
        } catch (_: Exception) {
            Tags()
        } finally {
            retriever.release()
        }
    }

    fun readEmbeddedCover(uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    companion object {
        private fun String?.clean(): String? =
            this?.trim()?.takeIf { it.isNotEmpty() }

        private fun parseTrack(raw: String?): Int? {
            if (raw.isNullOrBlank()) return null
            val first = raw.substringBefore('/').trim()
            return first.toIntOrNull()?.takeIf { it > 0 }
        }
    }
}
