package com.example.api

import com.example.data.LyricLine
import com.example.data.LyricsParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import com.example.data.SongIdentity
import com.example.diagnostics.DiagnosticStage
import com.example.diagnostics.LyricsDiagnostics

/** Both lookup paths share the manual search APIs and still validate each candidate. */
internal class AutomaticLyricsSearch(
    private val direct: suspend (String, String, Long) -> String?,
    private val lrclib: suspend (String) -> List<LrclibResponse>,
    private val netEase: suspend (String) -> List<NetEaseSong>,
    private val loadNetEase: suspend (Long) -> List<LyricLine>
) {
    suspend fun find(title: String, artist: String, durationMs: Long): List<LyricLine> {
        val songId = SongIdentity.id(title, artist)
        fun event(stage: DiagnosticStage, source: String, fields: Map<String, Any?> = emptyMap()) =
            LyricsDiagnostics.record(stage, source, songId, fields)
        val queries = SongSearchMetadata.queries(title, artist)
        val fromLrclib = withTimeoutOrNull(10000) {
            val cleanTitle = SongSearchMetadata.title(title)
            val primaryArtist = SongSearchMetadata.artistNames(artist).firstOrNull().orEmpty()
            if (primaryArtist.isNotBlank()) {
                event(DiagnosticStage.QUERY, "lrclib_exact", mapOf("title" to cleanTitle, "artist" to primaryArtist))
                val exact = attempt { direct(cleanTitle, primaryArtist, durationMs) }
                    ?.let { LyricsParser.parse(it, durationMs) }.orEmpty()
                if (exact.isNotEmpty()) {
                    event(DiagnosticStage.MATCH, "lrclib_exact", mapOf("count" to exact.size))
                    return@withTimeoutOrNull exact
                }
            }
            for (query in queries) {
                event(DiagnosticStage.QUERY, "lrclib", mapOf("query" to query))
                val results = attempt { lrclib(query) }.orEmpty()
                val candidates = LyricsSearchRanking.rank(results, title, artist, durationMs)
                    .filter { LyricsSearchRanking.isMatch(it, title, artist, durationMs) }
                val reasons = results.groupingBy {
                    LyricsSearchRanking.rejectionReason(it, title, artist, durationMs)
                        ?: if (it.syncedLyrics.isNullOrBlank() && it.plainLyrics.isNullOrBlank()) "empty_lyrics" else "matched"
                }.eachCount()
                event(DiagnosticStage.CANDIDATES, "lrclib", mapOf("count" to results.size,
                    "accepted" to candidates.size, "reason" to reasons.toString()))
                // Without artist metadata, avoid guessing between different same-title songs.
                if (artist.isBlank() && candidates.map { SongSearchMetadata.normalize(it.artistName.orEmpty()) }.distinct().size != 1) {
                    event(DiagnosticStage.NO_MATCH, "lrclib", mapOf("reason" to "missing_or_ambiguous_artist"))
                    continue
                }
                for (candidate in candidates) {
                    val lines = LyricsParser.parse(candidate.syncedLyrics?.takeIf(String::isNotBlank)
                        ?: candidate.plainLyrics.orEmpty(), durationMs)
                    if (lines.isNotEmpty()) {
                        event(DiagnosticStage.MATCH, "lrclib", mapOf("count" to lines.size))
                        return@withTimeoutOrNull lines
                    }
                }
            }
            emptyList()
        }
        if (fromLrclib == null) event(DiagnosticStage.SOURCE_TIMEOUT, "lrclib")
        if (!fromLrclib.isNullOrEmpty()) return fromLrclib

        val fromNetEase = withTimeoutOrNull(8000) {
            val visited = mutableSetOf<Long>()
            for (query in queries) {
                event(DiagnosticStage.QUERY, "netease", mapOf("query" to query))
                val results = attempt { netEase(query) }.orEmpty()
                val candidates = results.filter { song ->
                    LyricsSearchRanking.isMatch(LrclibResponse(trackName = song.name,
                        artistName = song.artists.orEmpty().joinToString(", ") { it.name.orEmpty() }), title, artist, 0)
                }
                event(DiagnosticStage.CANDIDATES, "netease", mapOf("count" to results.size, "accepted" to candidates.size))
                if (artist.isBlank() && candidates.map { it.artists.orEmpty().map { a -> a.name }.toSet() }.distinct().size != 1) continue
                for (song in candidates.take(3)) {
                    val id = song.id ?: continue
                    if (!visited.add(id)) continue
                    val lines = attempt { loadNetEase(id) }.orEmpty()
                    if (lines.isNotEmpty()) {
                        event(DiagnosticStage.MATCH, "netease", mapOf("count" to lines.size))
                        return@withTimeoutOrNull lines
                    }
                    event(DiagnosticStage.NO_MATCH, "netease", mapOf("reason" to "empty_lyrics"))
                }
            }
            emptyList()
        }
        if (fromNetEase == null) event(DiagnosticStage.SOURCE_TIMEOUT, "netease")
        if (fromNetEase.isNullOrEmpty()) event(DiagnosticStage.NO_MATCH, "automatic")
        return fromNetEase.orEmpty()
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            LyricsDiagnostics.record(DiagnosticStage.FAILURE, source = "search", fields = mapOf("error" to error.javaClass.simpleName))
            null
        }
}
