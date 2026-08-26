package mn.blazeapps.foxplayer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import mn.blazeapps.foxplayer.data.entities.BookEntity
import mn.blazeapps.foxplayer.data.entities.BookmarkEntity
import mn.blazeapps.foxplayer.data.entities.ChapterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AudiobookRepository(
    private val context: Context,
    db: AudiobookDatabase,
    private val scanner: FolderScanner,
    private val covers: CoverResolver,
) {
    private val books = db.bookDao()
    private val chapters = db.chapterDao()
    private val bookmarks = db.bookmarkDao()

    fun observeLibrary(): Flow<List<LibraryBook>> =
        combine(books.observeBooks(), chapters.observeAll()) { bookList, chapterList ->
            val chapterMap = chapterList.groupBy { it.bookId }
            bookList.map { book -> LibraryBook(book, chapterMap[book.id].orEmpty()) }
        }

    fun observeBook(bookId: Long): Flow<BookEntity?> = books.observeBook(bookId)

    fun observeChapters(bookId: Long): Flow<List<ChapterEntity>> = chapters.observeChapters(bookId)

    fun observeBookmarks(bookId: Long): Flow<List<BookmarkWithChapter>> =
        combine(bookmarks.observeBookmarks(bookId), chapters.observeChapters(bookId)) { marks, chaps ->
            val byId = chaps.associateBy { it.id }
            marks.map { BookmarkWithChapter(it, byId[it.chapterId]) }
        }

    suspend fun getBook(bookId: Long): BookEntity? = books.getBook(bookId)

    suspend fun getChapters(bookId: Long): List<ChapterEntity> = chapters.getChapters(bookId)

    suspend fun getChapter(chapterId: Long): ChapterEntity? = chapters.getChapter(chapterId)

    suspend fun importFolder(treeUri: Uri, rebindBookId: Long? = null): Long = withContext(Dispatchers.IO) {
        persistReadPermission(treeUri)
        val scanned = scanner.scan(treeUri)
        val existing = when {
            rebindBookId != null -> books.getBook(rebindBookId)
            else -> books.getBookByTreeUri(treeUri.toString())
        }
        val bookId = if (existing != null) {
            books.update(
                existing.copy(
                    title = scanned.title,
                    author = scanned.author,
                    treeUri = treeUri.toString(),
                    accessRevoked = false,
                ),
            )
            existing.id
        } else {
            books.insert(
                BookEntity(
                    title = scanned.title,
                    author = scanned.author,
                    treeUri = treeUri.toString(),
                ),
            )
        }

        upsertChapters(bookId, scanned.audioFiles, existing)

        val coverPath = covers.resolve(
            bookId = bookId,
            folderCover = scanned.coverUri,
            embeddedCoverUri = scanned.embeddedCoverUri,
            chapterUris = scanned.audioFiles.map { it.uri },
        )
        books.updateCover(bookId, coverPath)
        bookId
    }

    private suspend fun upsertChapters(
        bookId: Long,
        audioFiles: List<FolderScanner.ScannedAudio>,
        previousBook: BookEntity?,
    ) {
        val oldChapters = chapters.getChapters(bookId)
        val oldByUri = oldChapters.associateBy { it.documentUri }
        val newUris = audioFiles.map { it.uri.toString() }.toSet()
        val usedIds = mutableSetOf<Long>()

        audioFiles.forEachIndexed { index, file ->
            val uri = file.uri.toString()
            val existing = oldByUri[uri]
                ?: oldChapters.firstOrNull { chapter ->
                    chapter.id !in usedIds &&
                        chapter.displayName == file.displayName &&
                        chapter.documentUri !in newUris
                }
            if (existing != null) {
                usedIds += existing.id
                chapters.update(
                    existing.copy(
                        displayName = file.displayName,
                        documentUri = uri,
                        durationMs = file.durationMs,
                        sortIndex = index,
                    ),
                )
            } else {
                val inserted = chapters.insert(
                    ChapterEntity(
                        bookId = bookId,
                        displayName = file.displayName,
                        documentUri = uri,
                        durationMs = file.durationMs,
                        sortIndex = index,
                    ),
                )
                usedIds += inserted
            }
        }

        val removedIds = oldChapters.map { it.id }.filter { it !in usedIds }
        if (removedIds.isNotEmpty()) {
            chapters.deleteByIds(removedIds)
        }

        val remaining = chapters.getChapters(bookId)
        val remainingIds = remaining.map { it.id }
        if (remainingIds.isEmpty()) {
            bookmarks.deleteForBook(bookId)
        } else {
            bookmarks.deleteOrphaned(bookId, remainingIds)
        }

        if (previousBook?.lastChapterId == null) return
        val lastStillValid = remaining.any { it.id == previousBook.lastChapterId }
        if (lastStillValid) return

        val previousChapter = oldChapters.firstOrNull { it.id == previousBook.lastChapterId }
        val remapped = remaining.firstOrNull { chapter ->
            chapter.documentUri == previousChapter?.documentUri ||
                chapter.displayName == previousChapter?.displayName
        }
        val nextId = remapped?.id ?: remaining.firstOrNull()?.id
        val nextPosition = if (remapped != null) previousBook.lastPositionMs else 0L
        if (nextId != null) {
            books.updateProgress(bookId, nextId, nextPosition, previousBook.lastPlayedAt)
        }
    }

    suspend fun removeBook(bookId: Long) = withContext(Dispatchers.IO) {
        covers.deleteCover(bookId)
        books.deleteById(bookId)
    }

    suspend fun refreshAccessFlags() = withContext(Dispatchers.IO) {
        books.getAllBooks().forEach { book ->
            val readable = scanner.canRead(Uri.parse(book.treeUri))
            if (book.accessRevoked != !readable) {
                books.setAccessRevoked(book.id, !readable)
            }
        }
    }

    suspend fun saveProgress(bookId: Long, chapterId: Long, positionMs: Long) {
        books.updateProgress(bookId, chapterId, positionMs.coerceAtLeast(0), System.currentTimeMillis())
    }

    suspend fun addBookmark(bookId: Long, chapterId: Long, positionMs: Long, note: String) {
        bookmarks.insert(
            BookmarkEntity(
                bookId = bookId,
                chapterId = chapterId,
                positionMs = positionMs,
                note = note.trim(),
            ),
        )
    }

    suspend fun deleteBookmark(bookmark: BookmarkEntity) {
        bookmarks.delete(bookmark)
    }

    private fun persistReadPermission(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (_: SecurityException) {
            // Persistable permission is optional if the tree is already readable.
        }
    }
}
