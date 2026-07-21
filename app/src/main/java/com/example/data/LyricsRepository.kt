package com.example.data

import android.content.Context
import com.example.api.GeminiLyricsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LyricsRepository(private val context: Context) {
    
    private val database = LyricsDatabase.getDatabase(context)
    private val dao = database.lyricsDao()

    fun getAllSavedLyrics(): kotlinx.coroutines.flow.Flow<List<CachedLyrics>> {
        return dao.getAllCachedLyrics()
    }

    suspend fun deleteLyrics(id: String) = withContext(Dispatchers.IO) {
        dao.deleteLyricsById(id)
    }

    suspend fun getLyrics(title: String, artist: String, metadataLyrics: String? = null, durationMs: Long = 0, customQuery: String? = null): CachedLyrics = withContext(Dispatchers.IO) {
        // Fetch from the unified service which handles Metadata, Room caching, LRCLIB, and Gemini API calls
        GeminiLyricsService.getLyricsForSong(context, title, artist, metadataLyrics, durationMs, customQuery)
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }
}
