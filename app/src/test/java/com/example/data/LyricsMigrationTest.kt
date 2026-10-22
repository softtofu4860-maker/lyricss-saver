package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LyricsMigrationTest {
    @Test fun `upgrades real v2 database preserving lyrics metadata translations and saved entries`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val lyrics = """[{"timeSec":1.5,"text":"原文","translation":"번역","romanization":"발음"}]"""
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("""
                CREATE TABLE cached_lyrics (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL,
                artist TEXT NOT NULL, lyricsJson TEXT NOT NULL, bpm INTEGER NOT NULL,
                hexColorsJson TEXT NOT NULL, genre TEXT, timestamp INTEGER NOT NULL)
            """.trimIndent())
            old.execSQL("CREATE INDEX index_cached_lyrics_timestamp ON cached_lyrics(timestamp)")
            old.execSQL("INSERT INTO cached_lyrics VALUES ('old_id', '夜に駆ける', '歌手', ?, 140, '[\"#112233\"]', 'Manual Edit', 123)",
                arrayOf(lyrics))
            old.execSQL("INSERT INTO cached_lyrics VALUES ('other_id', '晴天', '周杰倫', ?, 90, '[\"#445566\"]', NULL, 456)",
                arrayOf(lyrics))
            old.execSQL("INSERT INTO cached_lyrics VALUES ('fallback', 'missing', 'artist', ?, 120, '[]', NULL, 789)",
                arrayOf("실시간 싱크 가사를 검색하고 있습니다"))
            old.version = 2
        }
        val database = Room.databaseBuilder(context, LyricsDatabase::class.java, name)
            .addMigrations(LyricsDatabase.MIGRATION_2_3, LyricsDatabase.MIGRATION_3_4).allowMainThreadQueries().build()
        try {
            val dao = database.lyricsDao()
            val japanese = dao.getLyricsById(SongIdentity.id("夜に駆ける", "歌手"))!!
            assertEquals(lyrics, japanese.lyricsJson)
            assertEquals(140, japanese.bpm)
            assertEquals("Manual Edit", japanese.genre)
            assertEquals(123L, japanese.timestamp)
            assertEquals("[\"#112233\"]", japanese.hexColorsJson)
            assertTrue(japanese.isUserSaved)
            assertNull(dao.getLyricsById("old_id"))
            val chinese = dao.getLyricsById(SongIdentity.id("晴天", "周杰倫"))!!
            assertNull(chinese.genre)
            assertNotEquals(japanese.id, chinese.id)
            assertTrue(dao.getLyricsById(SongIdentity.id("missing", "artist"))!!.isFallback)
            dao.clearAutomaticCache()
            assertEquals(japanese, dao.getLyricsById(japanese.id))
            assertEquals(chinese, dao.getLyricsById(chinese.id))
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test fun `new database seeds complete protected sample library on first open`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "seed-test.db"
        context.deleteDatabase(name)
        // Exercise the actual production callback, not an in-memory builder without callbacks.
        val database = LyricsDatabase.getDatabase(context)
        val samples = SampleDataProvider.getSampleSongs()
        for (sample in samples) {
            assertTrue(database.lyricsDao().getLyricsById(sample.id)!!.isUserSaved)
        }
        database.lyricsDao().clearAutomaticCache()
        for (sample in samples) assertNotNull(database.lyricsDao().getLyricsById(sample.id))
    }
}
