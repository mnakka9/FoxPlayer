package mn.blazeapps.foxplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.extractor.metadata.flac.PictureFrame
import androidx.media3.extractor.metadata.id3.ApicFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Reads audio tags and embedded cover art via modern Jetpack Media3 [MetadataRetriever].
 *
 * Media3 parses ID3, MP4 (.m4b/.m4a), and Ogg/Opus containers uniformly,
 * extracting both standard tags and the Grouping tag (TIT1 / ©grp / GROUPING)
 * where audiobook publishers and tools store the real literary genre.
 *
 * [MediaMetadataRetriever] is retained as a container duration provider and fast fallback.
 */
class AudioMetadataReader(private val context: Context) {

    data class Tags(
        val title: String? = null,
        val album: String? = null,
        val artist: String? = null,
        val albumArtist: String? = null,
        val genre: String? = null,
        val grouping: String? = null,
        val trackNumber: Int? = null,
        val durationMs: Long = 0L,
        val hasEmbeddedCover: Boolean = false,
    )

    fun read(uri: Uri, loadCoverHint: Boolean = true): Tags {
        var media3Title: String? = null
        var media3Album: String? = null
        var media3Artist: String? = null
        var media3AlbumArtist: String? = null
        var media3Genre: String? = null
        var media3Grouping: String? = null
        var media3TrackNumber: Int? = null
        var media3HasCover = false

        try {
            val mediaItem = MediaItem.fromUri(uri)
            val trackGroupsFuture = MetadataRetriever.retrieveMetadata(context, mediaItem)
            val trackGroups = trackGroupsFuture.get(3, TimeUnit.SECONDS)

            val builder = MediaMetadata.Builder()

            for (i in 0 until trackGroups.length) {
                val group = trackGroups[i]
                for (j in 0 until group.length) {
                    val format = group.getFormat(j)
                    val metadata = format.metadata ?: continue
                    for (k in 0 until metadata.length()) {
                        val entry = metadata.get(k)
                        entry.populateMediaMetadata(builder)

                        when (entry) {
                            is TextInformationFrame -> {
                                val text = entry.values.firstOrNull()?.clean()
                                when (entry.id.uppercase()) {
                                    "TIT1" -> if (media3Grouping == null) media3Grouping = text
                                    "TIT2" -> if (media3Title == null) media3Title = text
                                    "TALB" -> if (media3Album == null) media3Album = text
                                    "TPE1" -> if (media3Artist == null) media3Artist = text
                                    "TPE2" -> if (media3AlbumArtist == null) media3AlbumArtist = text
                                    "TCON" -> if (media3Genre == null) media3Genre = text
                                    "TRCK" -> if (media3TrackNumber == null) media3TrackNumber = parseTrack(text)
                                }
                            }
                            is MdtaMetadataEntry -> {
                                val text = try {
                                    String(entry.value, StandardCharsets.UTF_8).trimEnd('\u0000').clean()
                                } catch (_: Exception) { null }
                                val key = entry.key.lowercase()
                                when {
                                    key == "©grp" || key == "\u00a9grp" || key == "grouping" ->
                                        if (media3Grouping == null) media3Grouping = text
                                    key == "©gen" || key == "\u00a9gen" || key == "genre" ->
                                        if (media3Genre == null) media3Genre = text
                                    key == "©nam" || key == "\u00a9nam" || key == "title" ->
                                        if (media3Title == null) media3Title = text
                                    key == "©alb" || key == "\u00a9alb" || key == "album" ->
                                        if (media3Album == null) media3Album = text
                                    key == "©art" || key == "\u00a9art" || key == "artist" ->
                                        if (media3Artist == null) media3Artist = text
                                    key == "aart" || key == "albumartist" || key == "album_artist" ->
                                        if (media3AlbumArtist == null) media3AlbumArtist = text
                                }
                            }
                            is VorbisComment -> {
                                val key = entry.key.uppercase()
                                val text = entry.value.clean()
                                when (key) {
                                    "GROUPING" -> if (media3Grouping == null) media3Grouping = text
                                    "GENRE" -> if (media3Genre == null) media3Genre = text
                                    "TITLE" -> if (media3Title == null) media3Title = text
                                    "ALBUM" -> if (media3Album == null) media3Album = text
                                    "ARTIST" -> if (media3Artist == null) media3Artist = text
                                    "ALBUMARTIST" -> if (media3AlbumArtist == null) media3AlbumArtist = text
                                    "TRACKNUMBER" -> if (media3TrackNumber == null) media3TrackNumber = parseTrack(text)
                                }
                            }
                            is ApicFrame -> {
                                if (entry.pictureData.isNotEmpty()) media3HasCover = true
                            }
                            is PictureFrame -> {
                                if (entry.pictureData.isNotEmpty()) media3HasCover = true
                            }
                        }
                    }
                }
            }

            val populated = builder.build()
            if (media3Title == null) media3Title = populated.title?.toString().clean()
            if (media3Album == null) media3Album = populated.albumTitle?.toString().clean()
            if (media3Artist == null) media3Artist = populated.artist?.toString().clean()
            if (media3AlbumArtist == null) media3AlbumArtist = populated.albumArtist?.toString().clean()
            if (media3Genre == null) media3Genre = populated.genre?.toString().clean()
            if (media3TrackNumber == null) media3TrackNumber = populated.trackNumber
            if (populated.artworkData != null && populated.artworkData!!.isNotEmpty()) {
                media3HasCover = true
            }
        } catch (_: Exception) {
            // Media3 retrieval failed or timed out; will fall back to MediaMetadataRetriever below
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val legacyDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
            val legacyTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).clean()
            val legacyAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).clean()
            val legacyArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).clean()
            val legacyAlbumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).clean()
            val legacyGenre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE).clean()
            val legacyTrack = parseTrack(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER))
            val legacyHasCover = if (loadCoverHint && !media3HasCover) {
                retriever.embeddedPicture?.isNotEmpty() == true
            } else {
                media3HasCover
            }

            Tags(
                title = media3Title ?: legacyTitle,
                album = media3Album ?: legacyAlbum,
                artist = media3Artist ?: legacyArtist,
                albumArtist = media3AlbumArtist ?: legacyAlbumArtist,
                genre = media3Genre ?: legacyGenre,
                grouping = media3Grouping,
                trackNumber = media3TrackNumber ?: legacyTrack,
                durationMs = legacyDuration,
                hasEmbeddedCover = legacyHasCover,
            )
        } catch (_: Exception) {
            Tags(
                title = media3Title,
                album = media3Album,
                artist = media3Artist,
                albumArtist = media3AlbumArtist,
                genre = media3Genre,
                grouping = media3Grouping,
                trackNumber = media3TrackNumber,
                durationMs = 0L,
                hasEmbeddedCover = media3HasCover,
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    fun readEmbeddedCover(uri: Uri): ByteArray? {
        try {
            val mediaItem = MediaItem.fromUri(uri)
            val trackGroups = MetadataRetriever.retrieveMetadata(context, mediaItem).get(3, TimeUnit.SECONDS)
            for (i in 0 until trackGroups.length) {
                val group = trackGroups[i]
                for (j in 0 until group.length) {
                    val metadata = group.getFormat(j).metadata ?: continue
                    for (k in 0 until metadata.length()) {
                        when (val entry = metadata.get(k)) {
                            is ApicFrame -> if (entry.pictureData.isNotEmpty()) return entry.pictureData
                            is PictureFrame -> if (entry.pictureData.isNotEmpty()) return entry.pictureData
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fall back to legacy retriever below
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
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
