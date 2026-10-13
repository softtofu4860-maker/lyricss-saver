package com.example.data

import android.content.Context
import com.example.api.GeminiLyricsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import androidx.room.withTransaction

class LyricsRepository(private val context: Context) {
    private val database = LyricsDatabase.getDatabase(context)
    private val dao = database.lyricsDao()

    fun getAllSavedLyrics(): Flow<List<CachedLyrics>> = dao.getAllCachedLyrics()

    suspend fun getLyrics(
        title: String,
        artist: String,
        metadataLyrics: String? = null,
        durationMs: Long = 0L,
        customQuery: String? = null,
        forceRefresh: Boolean = false
    ): CachedLyrics = withContext(Dispatchers.IO) {
        val songId = GeminiLyricsService.generateSongId(title, artist)
        var cached = dao.getLyricsById(songId)
        if (cached == null) {
            // Verify stored metadata before migrating a potentially colliding legacy ID.
            val legacyId = LyricsIdentity.legacySongId(title, artist)
            database.withTransaction {
                val legacy = dao.getLyricsById(legacyId)
                if (legacy != null && LyricsIdentity.matches(legacy, title, artist)) {
                    cached = legacy.copy(id = songId)
                    dao.insertLyrics(cached!!)
                    dao.deleteLyricsById(legacyId)
                }
            }
        }
        if (!forceRefresh && customQuery.isNullOrBlank() && metadataLyrics.isNullOrBlank() &&
            cached != null && !GeminiLyricsService.isFallbackLyrics(cached!!)) {
            return@withContext cached!!
        }

        val fetched = GeminiLyricsService.getLyricsForSong(
            context = context,
            title = title,
            artist = artist,
            metadataLyrics = metadataLyrics,
            durationMs = durationMs,
            customQuery = customQuery
        )
        currentCoroutineContext().ensureActive()
        if (!GeminiLyricsService.isFallbackLyrics(fetched)) {
            dao.insertLyrics(fetched)
        }
        fetched
    }

    suspend fun saveLyrics(lyrics: CachedLyrics) = withContext(Dispatchers.IO) {
        val songId = LyricsIdentity.songId(lyrics.title, lyrics.artist)
        database.withTransaction {
            currentCoroutineContext().ensureActive()
            dao.insertLyrics(lyrics.copy(id = songId))
            val legacyId = LyricsIdentity.legacySongId(lyrics.title, lyrics.artist)
            val legacy = dao.getLyricsById(legacyId)
            if (legacy != null && LyricsIdentity.matches(legacy, lyrics.title, lyrics.artist)) {
                dao.deleteLyricsById(legacyId)
            }
        }
    }

    suspend fun deleteLyrics(id: String) = withContext(Dispatchers.IO) {
        dao.deleteLyricsById(id)
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }
}
