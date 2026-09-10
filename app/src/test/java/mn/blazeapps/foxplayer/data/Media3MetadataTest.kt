package mn.blazeapps.foxplayer.data

import androidx.media3.common.MediaMetadata
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.extractor.metadata.id3.ApicFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class Media3MetadataTest {

    @Test
    fun id3FramesParseGroupingAndGenreProperly() {
        val groupingFrame = TextInformationFrame("TIT1", "Content group description", listOf("Science Fiction"))
        val genreFrame = TextInformationFrame("TCON", "Content type", listOf("Audiobook"))

        assertEquals("TIT1", groupingFrame.id)
        assertEquals("Science Fiction", groupingFrame.values.first())
        assertEquals("Audiobook", genreFrame.values.first())

        assertTrue(GenreExtractor.isGeneric(genreFrame.values.first()))
        assertFalse(GenreExtractor.isGeneric(groupingFrame.values.first()))
    }

    @Test
    fun mp4MdtaEntriesParseGroupingAndGenre() {
        val groupingBytes = "Fantasy".toByteArray(StandardCharsets.UTF_8)
        val genreBytes = "Audiobook".toByteArray(StandardCharsets.UTF_8)

        val groupingEntry = MdtaMetadataEntry("©grp", groupingBytes, 0, 1)
        val genreEntry = MdtaMetadataEntry("©gen", genreBytes, 0, 1)

        val groupingVal = String(groupingEntry.value, StandardCharsets.UTF_8)
        val genreVal = String(genreEntry.value, StandardCharsets.UTF_8)

        assertEquals("Fantasy", groupingVal)
        assertEquals("Audiobook", genreVal)

        assertTrue(GenreExtractor.isGeneric(genreVal))
        assertFalse(GenreExtractor.isGeneric(groupingVal))
    }

    @Test
    fun vorbisCommentsParseGroupingAndGenre() {
        val grouping = VorbisComment("GROUPING", "Thriller")
        val genre = VorbisComment("GENRE", "Spoken Word")

        assertEquals("Thriller", grouping.value)
        assertEquals("Spoken Word", genre.value)

        assertTrue(GenreExtractor.isGeneric(genre.value))
        assertFalse(GenreExtractor.isGeneric(grouping.value))
    }
}
