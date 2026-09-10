package mn.blazeapps.foxplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreExtractorTest {

    @Test
    fun isGenericRecognizesGenericAudiobookTerms() {
        assertTrue(GenreExtractor.isGeneric("Audiobook"))
        assertTrue(GenreExtractor.isGeneric("Audiobooks"))
        assertTrue(GenreExtractor.isGeneric("audio book"))
        assertTrue(GenreExtractor.isGeneric("audio books"))
        assertTrue(GenreExtractor.isGeneric("audio-book"))
        assertTrue(GenreExtractor.isGeneric("audio-books"))
        assertTrue(GenreExtractor.isGeneric("spoken word"))
        assertTrue(GenreExtractor.isGeneric("spoken"))
        assertTrue(GenreExtractor.isGeneric("speech"))
        assertTrue(GenreExtractor.isGeneric("vocal"))
        assertTrue(GenreExtractor.isGeneric("other"))
        assertTrue(GenreExtractor.isGeneric("unknown"))
        assertTrue(GenreExtractor.isGeneric("audiobook (unabridged)"))
        assertTrue(GenreExtractor.isGeneric(""))
        assertTrue(GenreExtractor.isGeneric(null))
    }

    @Test
    fun isGenericAllowsRealLiteraryGenres() {
        assertFalse(GenreExtractor.isGeneric("Science Fiction"))
        assertFalse(GenreExtractor.isGeneric("Sci-Fi"))
        assertFalse(GenreExtractor.isGeneric("Fantasy"))
        assertFalse(GenreExtractor.isGeneric("Mystery"))
        assertFalse(GenreExtractor.isGeneric("Thriller"))
        assertFalse(GenreExtractor.isGeneric("Biography & Memoir"))
        assertFalse(GenreExtractor.isGeneric("Self-Help"))
        assertFalse(GenreExtractor.isGeneric("History"))
        assertFalse(GenreExtractor.isGeneric("Horror"))
    }

    @Test
    fun cleanSubjectsMapsCanonicalGenresAndFiltersJunk() {
        val raw = listOf(
            "nyt:bestseller",
            "open library staff picks",
            "accessible book",
            "protected daisy",
            "Audiobook",
            "science fiction",
            "space opera",
            "fantasy",
            "magic",
        )
        val cleaned = GenreExtractor.cleanSubjects(raw)
        assertEquals(listOf("Science Fiction", "Fantasy"), cleaned)
    }

    @Test
    fun cleanSubjectsCapsAtThreeGenres() {
        val raw = listOf(
            "science fiction",
            "fantasy",
            "thriller",
            "mystery",
            "horror",
        )
        val cleaned = GenreExtractor.cleanSubjects(raw)
        assertEquals(3, cleaned.size)
        assertEquals(listOf("Science Fiction", "Fantasy", "Thriller"), cleaned)
    }

    @Test
    fun cleanTitleForSearchStripsAudiobookArtifacts() {
        assertEquals("Dune", GenreExtractor.cleanTitleForSearch("Dune (Unabridged)"))
        assertEquals("Project Hail Mary", GenreExtractor.cleanTitleForSearch("Project Hail Mary [Audiobook]"))
        assertEquals("The Way of Kings", GenreExtractor.cleanTitleForSearch("The Way of Kings - Unabridged"))
        assertEquals("Atomic Habits", GenreExtractor.cleanTitleForSearch("Atomic Habits [audio edition]"))
        assertEquals("Mistborn", GenreExtractor.cleanTitleForSearch("Mistborn (A Novel)"))
        assertEquals("Foundation", GenreExtractor.cleanTitleForSearch("Foundation (Book 1)"))
    }

    @Test
    fun cleanAuthorForSearchStripsNarratorDetails() {
        assertEquals("Frank Herbert", GenreExtractor.cleanAuthorForSearch("Frank Herbert (narrated by Scott Brick)"))
        assertEquals("Andy Weir", GenreExtractor.cleanAuthorForSearch("Andy Weir read by Ray Porter"))
        assertEquals("Brandon Sanderson", GenreExtractor.cleanAuthorForSearch("Brandon Sanderson [performed by GraphicAudio]"))
        assertEquals(null, GenreExtractor.cleanAuthorForSearch(""))
        assertEquals(null, GenreExtractor.cleanAuthorForSearch(null))
    }
}
