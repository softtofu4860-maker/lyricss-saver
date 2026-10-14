package com.example.data

import android.content.Context
import androidx.room.withTransaction
import com.example.api.GeminiLyricsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LyricsRepository(private val context: Context) {
    private val database = LyricsDatabase.getDatabase(context)
    private val dao = database.lyricsDao()
    fun getAllSavedLyrics(): Flow<List<CachedLyrics>> = dao.getAllCachedLyrics()

    suspend fun getLyrics(
        title: String, artist: String, metadataLyrics: String? = null, durationMs: Long = 0L,
        customQuery: String? = null, forceRefresh: Boolean = false, album: String? = null, trackId: String? = null
    ): CachedLyrics = withContext(Dispatchers.IO) {
        val normalizedId = LyricsIdentity.spotifyTrackId(trackId)
        val songId = LyricsIdentity.recordingId(title, artist, normalizedId, album, durationMs)
        var cached = dao.getLyricsById(songId)
        if (cached == null) {
            val previousIds = listOf(
                LyricsIdentity.songId(title, artist), LyricsIdentity.legacySongId(title, artist),
                LyricsIdentity.recordingId(title, artist, album = album, durationMs = durationMs),
                LyricsIdentity.recordingId(title, artist, album = album),
                LyricsIdentity.recordingId(title, artist)
            ).distinct().filter { it != songId }
            database.withTransaction {
                for (oldId in previousIds) {
                    val previous = dao.getLyricsById(oldId) ?: continue
                    val sameRecording = LyricsIdentity.matches(previous, title, artist) &&
                        (previous.trackId == null || previous.trackId == normalizedId) &&
                        (previous.album.isNullOrBlank() || album.isNullOrBlank() ||
                            LyricsIdentity.normalize(previous.album) == LyricsIdentity.normalize(album)) &&
                        (previous.durationMs <= 0 || durationMs <= 0 ||
                            kotlin.math.abs(previous.durationMs - durationMs) <= 3000)
                    if (sameRecording && !GeminiLyricsService.isFallbackLyrics(previous)) {
                        cached = previous.copy(id = songId, trackId = normalizedId, album = album, durationMs = durationMs)
                        dao.insertLyrics(cached!!)
                        dao.deleteLyricsById(oldId)
                        break
                    }
                }
            }
        }
        val valid = cached?.takeIf { !GeminiLyricsService.isFallbackLyrics(it) }
        if (customQuery.isNullOrBlank() && valid != null &&
            (valid.manuallySelected || (!forceRefresh && metadataLyrics.isNullOrBlank()))) return@withContext valid
        val fetched = GeminiLyricsService.getLyricsForSong(
            context, title, artist, metadataLyrics, durationMs, customQuery, album, normalizedId)
        currentCoroutineContext().ensureActive()
        if (!GeminiLyricsService.isFallbackLyrics(fetched)) dao.insertLyrics(fetched)
        fetched
    }

    suspend fun saveLyrics(lyrics: CachedLyrics) = withContext(Dispatchers.IO) {
        val songId = LyricsIdentity.recordingId(lyrics.title, lyrics.artist, lyrics.trackId, lyrics.album, lyrics.durationMs)
        database.withTransaction {
            currentCoroutineContext().ensureActive()
            dao.insertLyrics(lyrics.copy(id = songId))
            // Remove only the matching legacy entry; another recording must remain intact.
            for (oldId in listOf(lyrics.id, LyricsIdentity.legacySongId(lyrics.title, lyrics.artist),
                LyricsIdentity.songId(lyrics.title, lyrics.artist)).distinct().filter { it != songId }) {
                val old = dao.getLyricsById(oldId)
                if (old != null && LyricsIdentity.matches(old, lyrics.title, lyrics.artist) &&
                    (old.trackId == null || old.trackId == lyrics.trackId) &&
                    (old.durationMs <= 0 || old.durationMs == lyrics.durationMs)) dao.deleteLyricsById(oldId)
            }
        }
    }

    suspend fun deleteLyrics(id: String) = withContext(Dispatchers.IO) { dao.deleteLyricsById(id) }
    suspend fun clearCache() = withContext(Dispatchers.IO) { dao.clearAll() }
}
