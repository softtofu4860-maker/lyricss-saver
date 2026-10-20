package com.example.api

import kotlin.math.abs

/** Metadata matching is independent of lyric availability: a synced cover is still a cover. */
object LyricsSearchRanking {
    private fun normalize(value: String): String = SongSearchMetadata.normalize(value)

    private fun similarity(expected: String, actual: String?): Double {
        val left = normalize(expected)
        val right = normalize(actual.orEmpty())
        if (left.isBlank() || right.isBlank()) return 0.0
        if (left == right) return 1.0
        if (left.replace(" ", "") == right.replace(" ", "")) return 1.0
        val a = left.split(' ').toSet()
        val b = right.split(' ').toSet()
        return 2.0 * a.intersect(b).size / (a.size + b.size)
    }

    private fun titleSimilarity(expected: String, actual: String?): Double =
        similarity(SongSearchMetadata.title(expected), SongSearchMetadata.title(actual.orEmpty()))

    private fun artistSimilarity(expected: String, actual: String?): Double {
        val expectedNames = SongSearchMetadata.artistNames(expected)
        val actualNames = SongSearchMetadata.artistNames(actual.orEmpty())
        return expectedNames.maxOfOrNull { name -> actualNames.maxOfOrNull { similarity(name, it) } ?: 0.0 } ?: 0.0
    }

    fun isMatch(result: LrclibResponse, title: String, artist: String, durationMs: Long): Boolean =
        rejectionReason(result, title, artist, durationMs) == null

    internal fun rejectionReason(result: LrclibResponse, title: String, artist: String, durationMs: Long): String? {
        val duration = result.duration?.takeIf { it.isFinite() && it > 0 }
        val lengthMatches = durationMs <= 0 || duration == null ||
            abs(duration - durationMs / 1000.0) <= 12
        return when {
            result.instrumental == true -> "instrumental"
            SongSearchMetadata.versions(title) != SongSearchMetadata.versions(result.trackName.orEmpty()) -> "version_mismatch"
            titleSimilarity(title, result.trackName) < 0.75 -> "title_mismatch"
            artist.isNotBlank() && artistSimilarity(artist, result.artistName) < 0.8 -> "artist_mismatch"
            !lengthMatches -> "duration_mismatch"
            else -> null
        }
    }

    fun rank(results: List<LrclibResponse>, title: String, artist: String, durationMs: Long): List<LrclibResponse> =
        results.filter { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
            .distinctBy { listOf(it.trackName, it.artistName, it.duration, it.syncedLyrics, it.plainLyrics) }
            .sortedByDescending { result ->
                val duration = result.duration?.takeIf { it.isFinite() && it > 0 }
                val lengthScore = if (durationMs > 0 && duration != null)
                    (1.0 - abs(duration - durationMs / 1000.0) / 12).coerceIn(0.0, 1.0) else 0.0
                titleSimilarity(title, result.trackName) * 60 + artistSimilarity(artist, result.artistName) * 30 +
                    lengthScore * 8 + (if (!result.syncedLyrics.isNullOrBlank()) 2 else 0)
            }
}
