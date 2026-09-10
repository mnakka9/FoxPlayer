package mn.blazeapps.foxplayer.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books", indices = [Index(value = ["treeUri"], unique = true)])
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String? = null,
    val genres: String? = null,
    val treeUri: String,
    val coverPath: String? = null,
    val lastChapterId: Long? = null,
    val lastPositionMs: Long = 0,
    val lastPlayedAt: Long = 0,
    val accessRevoked: Boolean = false,
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId"), Index(value = ["bookId", "sortIndex"])],
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val displayName: String,
    val documentUri: String,
    val durationMs: Long = 0,
    val sortIndex: Int,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId"), Index("chapterId")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val chapterId: Long,
    val positionMs: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
