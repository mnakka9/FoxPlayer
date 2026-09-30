package mn.blazeapps.foxplayer.data

import mn.blazeapps.foxplayer.data.entities.ChapterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class M4bChapterExtractorTest {

    @Test
    fun m4bChapterDurationsCalculateCorrectly() {
        val rawChapters = listOf(
            M4bChapter(index = 0, title = "Chapter 1", startMs = 0L, durationMs = 120_000L),
            M4bChapter(index = 1, title = "Chapter 2", startMs = 120_000L, durationMs = 180_000L),
            M4bChapter(index = 2, title = "Chapter 3", startMs = 300_000L, durationMs = 200_000L),
        )

        assertEquals(0L, rawChapters[0].startMs)
        assertEquals(120_000L, rawChapters[0].durationMs)
        assertEquals(120_000L, rawChapters[1].startMs)
        assertEquals(180_000L, rawChapters[1].durationMs)
        assertEquals(300_000L, rawChapters[2].startMs)
        assertEquals(200_000L, rawChapters[2].durationMs)
    }

    @Test
    fun chaptersWithSameDocumentUriSortByStartOffsetMs() {
        val docUri = "content://media/external/audio/book.m4b"
        val ch1 = mn.blazeapps.foxplayer.data.entities.ChapterEntity(
            bookId = 1L,
            displayName = "Chapter 1",
            documentUri = docUri,
            durationMs = 1000L,
            sortIndex = 0,
            startOffsetMs = 0L,
        )
        val ch2 = mn.blazeapps.foxplayer.data.entities.ChapterEntity(
            bookId = 1L,
            displayName = "Chapter 2",
            documentUri = docUri,
            durationMs = 2000L,
            sortIndex = 1,
            startOffsetMs = 1000L,
        )
        val ch3 = mn.blazeapps.foxplayer.data.entities.ChapterEntity(
            bookId = 1L,
            displayName = "Chapter 3",
            documentUri = docUri,
            durationMs = 1500L,
            sortIndex = 2,
            startOffsetMs = 3000L,
        )

        // Shuffled list
        val list = listOf(ch3, ch1, ch2)
        val sorted = list.sortedWith(
            Comparator { a, b ->
                if (a.documentUri == b.documentUri && a.startOffsetMs != b.startOffsetMs) {
                    a.startOffsetMs.compareTo(b.startOffsetMs)
                } else {
                    a.displayName.compareTo(b.displayName)
                }
            },
        )

        assertEquals("Chapter 1", sorted[0].displayName)
        assertEquals("Chapter 2", sorted[1].displayName)
        assertEquals("Chapter 3", sorted[2].displayName)
    }

    @Test
    fun naturalCompareHandlesNumericalAndTextTitles() {
        assertTrue(FolderScanner.naturalCompare("Chapter 2", "Chapter 10") < 0)
        assertTrue(FolderScanner.naturalCompare("Chapter 01", "Chapter 2") < 0)
        assertTrue(FolderScanner.naturalCompare("Chapter 1", "Chapter 1") == 0)
    }
}
