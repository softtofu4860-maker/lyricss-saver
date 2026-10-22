package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CachedLyrics::class], version = 4, exportSchema = false)
abstract class LyricsDatabase : RoomDatabase() {
    abstract fun lyricsDao(): LyricsDao

    companion object {
        @Volatile
        private var INSTANCE: LyricsDatabase? = null
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_lyrics ADD COLUMN identityBinding TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE cached_lyrics_v3 (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        lyricsJson TEXT NOT NULL,
                        bpm INTEGER NOT NULL,
                        hexColorsJson TEXT NOT NULL,
                        genre TEXT,
                        timestamp INTEGER NOT NULL,
                        isUserSaved INTEGER NOT NULL DEFAULT 0,
                        isFallback INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                // v2 did not track edits. Preserve every surviving row as user data.
                // If case/Unicode normalization merges rows, keep the most recent one.
                db.query("SELECT * FROM cached_lyrics ORDER BY timestamp ASC, id ASC").use { cursor ->
                    while (cursor.moveToNext()) {
                        fun text(name: String) = cursor.getString(cursor.getColumnIndexOrThrow(name))
                        val title = text("title")
                        val artist = text("artist")
                        val lyrics = text("lyricsJson")
                        db.execSQL(
                            "INSERT OR REPLACE INTO cached_lyrics_v3 VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?)",
                            arrayOf<Any?>(
                                SongIdentity.id(title, artist), title, artist, lyrics,
                                cursor.getInt(cursor.getColumnIndexOrThrow("bpm")), text("hexColorsJson"),
                                text("genre"), cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                                if (lyrics.contains("실시간 싱크 가사를 검색하고 있습니다") ||
                                    lyrics.contains("가사 탐색 중")) 1 else 0
                            )
                        )
                    }
                }
                db.execSQL("DROP TABLE cached_lyrics")
                db.execSQL("ALTER TABLE cached_lyrics_v3 RENAME TO cached_lyrics")
                db.execSQL("CREATE INDEX index_cached_lyrics_timestamp ON cached_lyrics(timestamp)")
            }
        }

        fun getDatabase(context: Context): LyricsDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, LyricsDatabase::class.java, "lyrics_database"
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed in the creation transaction; no detached coroutine or recursive open.
                        SampleDataProvider.getSampleSongs().forEach { song ->
                            db.execSQL(
                                "INSERT OR IGNORE INTO cached_lyrics (id,title,artist,lyricsJson,bpm,hexColorsJson,genre,timestamp,isUserSaved,isFallback) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 0)",
                                arrayOf<Any?>(song.id, song.title, song.artist, song.lyricsJson, song.bpm,
                                    song.hexColorsJson, song.genre, song.timestamp)
                            )
                        }
                    }
                })
                .build()
                .also { INSTANCE = it }
        }
    }
}
