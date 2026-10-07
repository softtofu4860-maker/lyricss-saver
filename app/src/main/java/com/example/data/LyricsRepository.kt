package com.example.data

import android.content.Context
import com.example.api.GeminiLyricsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LyricsRepository(private val context: Context) {
    private val database = LyricsDatabase.getDatabase(context)
    private val dao = database.lyricsDao()

    fun getAllSavedLyrics(): Flow<List<CachedLyrics>> = dao.getAllCachedLyrics()

    suspend fun getLyrics(
        title: String,
        artist: String,
        metadataLyrics: String? = null,
        durationMs: Long = 0L,
        customQuery: String? = null
    ): CachedLyrics = withContext(Dispatchers.IO) {
        val songId = GeminiLyricsService.generateSongId(title, artist)
        val cached = dao.getLyricsById(songId)

        // Fallback entries are only temporary UI placeholders. Do not let them
        // block a later network lookup when the real lyrics become available.
        if (cached != null && !GeminiLyricsService.isFallbackLyrics(cached)) {
            return@withContext cached
        }

        val fetched = GeminiLyricsService.getLyricsForSong(
            context = context,
            title = title,
            artist = artist,
            metadataLyrics = metadataLyrics,
            durationMs = durationMs,
            customQuery = customQuery
        )

        // Never persist the synthetic fallback. Otherwise the next request for
        // the same song would immediately return the placeholder forever.
        if (!GeminiLyricsService.isFallbackLyrics(fetched)) {
            dao.insertLyrics(fetched)
        }

        fetched
    }

    suspend fun saveLyrics(lyrics: CachedLyrics) = withContext(Dispatchers.IO) {
        dao.insertLyrics(lyrics)
    }

    suspend fun deleteLyrics(id: String) = withContext(Dispatchers.IO) {
        dao.deleteLyricsById(id)
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }
}
