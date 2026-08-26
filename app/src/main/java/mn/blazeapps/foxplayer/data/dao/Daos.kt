package mn.blazeapps.foxplayer.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import mn.blazeapps.foxplayer.data.entities.BookEntity
import mn.blazeapps.foxplayer.data.entities.BookmarkEntity
import mn.blazeapps.foxplayer.data.entities.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastPlayedAt DESC, title ASC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books")
    suspend fun getAllBooks(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeBook(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE treeUri = :treeUri LIMIT 1")
    suspend fun getBookByTreeUri(treeUri: String): BookEntity?

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query(
        """
        UPDATE books SET lastChapterId = :chapterId, lastPositionMs = :positionMs, lastPlayedAt = :playedAt
        WHERE id = :bookId
        """,
    )
    suspend fun updateProgress(bookId: Long, chapterId: Long, positionMs: Long, playedAt: Long)

    @Query("UPDATE books SET coverPath = :coverPath WHERE id = :bookId")
    suspend fun updateCover(bookId: Long, coverPath: String?)

    @Query("UPDATE books SET accessRevoked = :revoked WHERE id = :bookId")
    suspend fun setAccessRevoked(bookId: Long, revoked: Boolean)

    @Query("UPDATE books SET treeUri = :treeUri, accessRevoked = 0, title = :title WHERE id = :bookId")
    suspend fun rebindFolder(bookId: Long, treeUri: String, title: String)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteById(bookId: Long)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters ORDER BY bookId ASC, sortIndex ASC")
    fun observeAll(): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY sortIndex ASC")
    fun observeChapters(bookId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY sortIndex ASC")
    suspend fun getChapters(bookId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapter(id: Long): ChapterEntity?

    @Insert
    suspend fun insert(chapter: ChapterEntity): Long

    @Insert
    suspend fun insertAll(chapters: List<ChapterEntity>): List<Long>

    @Update
    suspend fun update(chapter: ChapterEntity)

    @Query("DELETE FROM chapters WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeBookmarks(bookId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId AND chapterId NOT IN (:chapterIds)")
    suspend fun deleteOrphaned(bookId: Long, chapterIds: List<Long>)
}
