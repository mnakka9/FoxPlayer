package mn.blazeapps.foxplayer.data

import mn.blazeapps.foxplayer.data.entities.BookEntity
import mn.blazeapps.foxplayer.data.entities.BookmarkEntity
import mn.blazeapps.foxplayer.data.entities.ChapterEntity

data class LibraryBook(
    val book: BookEntity,
    val chapters: List<ChapterEntity>,
) {
    val totalDurationMs: Long get() = chapters.sumOf { it.durationMs }
    val progressFraction: Float
        get() {
            if (totalDurationMs <= 0) return 0f
            val listened = listenedMs()
            return (listened.toFloat() / totalDurationMs).coerceIn(0f, 1f)
        }

    val progressPercent: Int
        get() = (progressFraction * 100f).toInt().coerceIn(0, 100)

    fun listenedMs(): Long {
        val lastId = book.lastChapterId ?: return 0L
        var sum = 0L
        for (chapter in chapters) {
            if (chapter.id == lastId) {
                return sum + book.lastPositionMs.coerceAtLeast(0)
            }
            sum += chapter.durationMs
        }
        return 0L
    }
}

data class BookmarkWithChapter(
    val bookmark: BookmarkEntity,
    val chapter: ChapterEntity?,
)
