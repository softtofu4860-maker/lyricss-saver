package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [CachedLyrics::class], version = 3, exportSchema = false)
abstract class LyricsDatabase : RoomDatabase() {
    abstract fun lyricsDao(): LyricsDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_lyrics ADD COLUMN trackId TEXT")
                db.execSQL("ALTER TABLE cached_lyrics ADD COLUMN album TEXT")
                db.execSQL("ALTER TABLE cached_lyrics ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cached_lyrics ADD COLUMN provider TEXT")
                db.execSQL("ALTER TABLE cached_lyrics ADD COLUMN providerId TEXT")
                db.execSQL("ALTER TABLE cached_lyrics ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cached_lyrics ADD COLUMN manuallySelected INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cached_lyrics ADD COLUMN matchScore REAL")
                db.execSQL("UPDATE cached_lyrics SET manuallySelected = 1 WHERE genre = 'Manual Edit'")
            }
        }

        @Volatile
        private var INSTANCE: LyricsDatabase? = null

        fun getDatabase(context: Context): LyricsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LyricsDatabase::class.java,
                    "lyrics_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(true)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val dao = getDatabase(context).lyricsDao()
                                    SampleDataProvider.getSampleSongs().forEach {
                                        dao.insertLyrics(it)
                                    }
                                } catch (e: Exception) {}
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
