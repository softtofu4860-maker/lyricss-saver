package com.example.data

import android.content.Context
import com.example.api.GeminiLyricsService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import com.example.diagnostics.DiagnosticStage
import com.example.diagnostics.LyricsDiagnostics

internal data class LyricsRequest(
    val title: String,
    val artist: String,
    val metadataLyrics: String? = null,
    val durationMs: Long = 0L,
    val customQuery: String? = null
)

class LyricsRepository internal constructor(
    private val dao: LyricsDao,
    private val fetch: suspend (LyricsRequest) -> CachedLyrics,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    constructor(context: Context) : this(
        LyricsDatabase.getDatabase(context).lyricsDao(),
        { request -> GeminiLyricsService.getLyricsForSong(
            context.applicationContext, request.title, request.artist, request.metadataLyrics,
            request.durationMs, request.customQuery
        ) }
    )

    // Bounded, shared across the activity and DreamService. A second request for the
    // same song reuses the first successful cache write; unrelated songs can proceed.
    private fun lock(id: String) = songLocks[(id.hashCode() and Int.MAX_VALUE) % songLocks.size]

    fun getAllSavedLyrics(): Flow<List<CachedLyrics>> = dao.getAllCachedLyrics()
        .map { rows -> rows.filterNot(GeminiLyricsService::isFallbackLyrics) }

    /** A bounded retry sequence, cancelled with the song's UI request or a manual correction. */
    fun observeAutomaticLyrics(title: String, artist: String, metadataLyrics: String? = null,
                              durationMs: Long = 0L, descriptor: SongDescriptor? = null): Flow<CachedLyrics> = flow {
        val songId = SongIdentity.id(title, artist)
        for ((index, waitMs) in listOf(0L, 2000L, 8000L).withIndex()) {
            LyricsDiagnostics.record(if (waitMs > 0) DiagnosticStage.RETRY else DiagnosticStage.SEARCH_START,
                "automatic", songId, mapOf("title" to title, "artist" to artist, "durationMs" to durationMs,
                    "attempt" to index + 1, "delayMs" to waitMs))
            if (waitMs > 0) delay(waitMs)
            val result = try {
                getLyrics(title, artist, metadataLyrics, durationMs, descriptor = descriptor)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LyricsDiagnostics.record(DiagnosticStage.FAILURE, "automatic", songId, mapOf("error" to error.javaClass.simpleName))
                GeminiLyricsService.generateSmartFallback(SongIdentity.id(title, artist), title, artist, durationMs)
            }
            emit(result)
            LyricsDiagnostics.record(if (result.isFallback) DiagnosticStage.NO_MATCH else DiagnosticStage.SUCCESS,
                "automatic", songId, mapOf("attempt" to index + 1))
            if (!result.isFallback) return@flow
        }
    }.onCompletion { cause ->
        if (cause is CancellationException) LyricsDiagnostics.record(DiagnosticStage.CANCELLED, "automatic", SongIdentity.id(title, artist))
    }

    suspend fun getLyrics(
        title: String,
        artist: String,
        metadataLyrics: String? = null,
        durationMs: Long = 0L,
        customQuery: String? = null,
        descriptor: SongDescriptor? = null
    ): CachedLyrics = withContext(ioDispatcher) {
        val songId = descriptor?.id ?: SongIdentity.id(title, artist)
        val query = customQuery?.trim()?.takeIf { it.isNotEmpty() }
        LyricsDiagnostics.record(DiagnosticStage.SEARCH_START, "repository", songId,
            mapOf("mode" to if (query == null) "automatic" else "manual", "query" to query,
                "hasLyrics" to !metadataLyrics.isNullOrBlank()))
        lock(songId).withLock {
            val cached = dao.getLyricsById(songId) ?: descriptor?.takeIf { it.id != it.legacyId }?.let {
                (it.previousIds + it.legacyId).distinct().firstNotNullOfOrNull { previous ->
                    dao.claimLegacy(previous, it.id)
                }
            }
            if (query == null && cached != null && !GeminiLyricsService.isFallbackLyrics(cached) &&
                (metadataLyrics.isNullOrBlank() || cached.isUserSaved)) {
                LyricsDiagnostics.record(DiagnosticStage.CACHE_HIT, "repository", songId)
                return@withLock cached
            }
            val cacheVersion = cacheGeneration.get()
            val fetched = fetch(LyricsRequest(title, artist,
                if (query == null) metadataLyrics else null, durationMs, query))
                .copy(id = songId, isUserSaved = query != null || cached?.isUserSaved == true)
            currentCoroutineContext().ensureActive()
            if (!GeminiLyricsService.isFallbackLyrics(fetched)) {
                cacheWriteLock.withLock {
                    if (fetched.isUserSaved || cacheVersion == cacheGeneration.get()) {
                        dao.insertLyrics(fetched)
                    }
                }
            }
            fetched
        }
    }

    suspend fun saveManualLyrics(title: String, artist: String, lines: List<LyricLine>, descriptor: SongDescriptor? = null): CachedLyrics =
        withContext(ioDispatcher) {
            val id = descriptor?.id ?: SongIdentity.id(title, artist)
            lock(id).withLock {
                val existing = dao.getLyricsById(id)
                val updated = (existing ?: CachedLyrics(id, title, artist, "[]")).copy(
                    lyricsJson = GeminiLyricsService.lyricAdapter.toJson(lines.map {
                        if (it.timingSource.startsWith("manual:")) it else it.copy(timingSource = "manual:${it.timingSource}")
                    }),
                    timestamp = System.currentTimeMillis(), isUserSaved = true, isFallback = false
                )
                dao.insertLyrics(updated)
                LyricsDiagnostics.record(DiagnosticStage.SUCCESS, "manual_save", id,
                    mapOf("mode" to "manual", "count" to lines.size))
                updated
            }
        }

    suspend fun saveTranslation(original: CachedLyrics, lines: List<LyricLine>): CachedLyrics? =
        withContext(ioDispatcher) {
            lock(original.id).withLock {
                val latest = dao.getLyricsById(original.id)
                // Do not resurrect a deleted entry or overwrite an edit made during translation.
                if (latest == null || latest.lyricsJson != original.lyricsJson) return@withLock null
                val updated = latest.copy(lyricsJson = GeminiLyricsService.lyricAdapter.toJson(lines),
                    timestamp = System.currentTimeMillis(), isUserSaved = true)
                dao.insertLyrics(updated)
                updated
            }
        }

    suspend fun deleteLyrics(id: String) = withContext(ioDispatcher) {
        lock(id).withLock { dao.deleteLyricsById(id) }
    }

    suspend fun clearCache() = withContext(ioDispatcher) {
        cacheWriteLock.withLock {
            cacheGeneration.incrementAndGet()
            dao.clearAutomaticCache()
        }
    }

    private companion object {
        val songLocks = Array(64) { Mutex() }
        val cacheGeneration = AtomicLong()
        val cacheWriteLock = Mutex()
    }
}
