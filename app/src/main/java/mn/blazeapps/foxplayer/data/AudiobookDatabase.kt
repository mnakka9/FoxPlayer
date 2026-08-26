package mn.blazeapps.foxplayer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import mn.blazeapps.foxplayer.data.dao.BookDao
import mn.blazeapps.foxplayer.data.dao.BookmarkDao
import mn.blazeapps.foxplayer.data.dao.ChapterDao
import mn.blazeapps.foxplayer.data.entities.BookEntity
import mn.blazeapps.foxplayer.data.entities.BookmarkEntity
import mn.blazeapps.foxplayer.data.entities.ChapterEntity

@Database(
    entities = [BookEntity::class, ChapterEntity::class, BookmarkEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AudiobookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        fun create(context: Context): AudiobookDatabase =
            Room.databaseBuilder(context, AudiobookDatabase::class.java, "audiobooks.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
