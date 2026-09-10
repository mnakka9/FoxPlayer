package mn.blazeapps.foxplayer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import mn.blazeapps.foxplayer.data.dao.BookDao
import mn.blazeapps.foxplayer.data.dao.BookmarkDao
import mn.blazeapps.foxplayer.data.dao.ChapterDao
import mn.blazeapps.foxplayer.data.entities.BookEntity
import mn.blazeapps.foxplayer.data.entities.BookmarkEntity
import mn.blazeapps.foxplayer.data.entities.ChapterEntity

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN genres TEXT DEFAULT NULL")
    }
}

@Database(
    entities = [BookEntity::class, ChapterEntity::class, BookmarkEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AudiobookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        fun create(context: Context): AudiobookDatabase =
            Room.databaseBuilder(context, AudiobookDatabase::class.java, "audiobooks.db")
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
    }
}
