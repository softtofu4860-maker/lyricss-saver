package com.example.api

import com.example.data.LyricLine
import com.example.data.LyricsIdentity
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

object LyricsMatching {
    const val AUTO_SELECT_SCORE = 88.0

    private fun comparable(value: String): String =
        Normalizer.normalize(LyricsIdentity.normalize(value), Normalizer.Form.NFKC)
            .filter { it.isLetterOrDigit() }

    private val featurePattern = Regex("(?i)\\s*(?:\\(|\\[)?\\b(?:feat\\.?|ft\\.?|featuring)\\s+(.+?)(?:\\)|\\])?$")
    private val versions = listOf("live", "remix", "instrumental", "karaoke", "acoustic", "remaster", "sped up", "slowed")
    private fun versionFlags(title: String): Set<String> {
        val withoutFeatures = title.replace(featurePattern, "")
        val editions = Regex("\\(([^)]*)\\)|\\[([^]\\n]*)\\]|\\s[-–—]\\s(.+)$")
            .findAll(withoutFeatures).flatMap { match -> match.groupValues.drop(1).asSequence() }
            .joinToString(" ")
        return versions.filter { version ->
            Regex("(?i)\\b${Regex.escape(version)}(?:ed)?\\b").containsMatchIn(editions)
        }.toSet()
    }

    private fun baseTitle(title: String): String {
        var base = title.replace(featurePattern, "").replace(
            Regex("(?i)\\s*(?:\\(|\\[)[^)\\]]*(?:live|remix|instrumental|karaoke|acoustic|remaster|sped up|slowed)[^)\\]]*(?:\\)|\\])"), "")
        if (versionFlags(title).isNotEmpty()) base = base.replace(Regex("\\s[-–—]\\s.+$"), "")
        return comparable(base)
    }

    private fun artists(values: List<String>): Set<String> = values.flatMap {
        it.split(Regex("(?i)\\s*(?:,|;|&|\\bfeat\\.?\\s|\\bft\\.?\\s|\\bfeaturing\\s|\\s+x\\s+)\\s*"))
    }.map(::comparable).filter { it.isNotEmpty() }.toSet()

    private fun titleSimilarity(left: String, right: String): Double {
        if (left == right) return 1.0
        if (left.isEmpty() || right.isEmpty()) return 0.0
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1,
                    previous[j] + if (left[i] == right[j]) 0 else 1)
            }
            previous = current
        }
        return 1.0 - previous.last().toDouble() / max(left.length, right.length)
    }

    /** Hard version/artist checks precede scoring; a timestamp alone never proves identity. */
    fun score(query: SongQuery, candidate: LyricsCandidate): Double? {
        val queryId = LyricsIdentity.spotifyTrackId(query.trackId)
        val boundId = LyricsIdentity.spotifyTrackId(candidate.boundTrackId)
        if (boundId != null) {
            return if (queryId != null && boundId == queryId) 100.0 else null
        }
        if (versionFlags(query.title) != versionFlags(candidate.title)) return null
        // Different named remixes must not be collapsed to the same base title.
        if ("remix" in versionFlags(query.title) && comparable(query.title) != comparable(candidate.title)) return null
        val expectedTitle = baseTitle(query.title)
        val candidateTitle = baseTitle(candidate.title)
        if (expectedTitle.isEmpty() || candidateTitle.isEmpty()) return null
        val similarity = titleSimilarity(expectedTitle, candidateTitle)
        if (similarity < 0.87 || (minOf(expectedTitle.length, candidateTitle.length) < 8 && similarity < 1.0)) return null
        val expectedArtists = artists(listOf(query.artist) +
            listOfNotNull(featurePattern.find(query.title)?.groupValues?.get(1)))
        val actualArtists = artists(candidate.artists +
            listOfNotNull(featurePattern.find(candidate.title)?.groupValues?.get(1)))
        if (expectedArtists.isEmpty() || !actualArtists.containsAll(expectedArtists)) return null

        val duration = candidate.durationMs?.takeIf { it > 0 }
        val durationScore = when {
            query.durationMs <= 0 -> 8.0
            duration == null -> 0.0
            abs(query.durationMs - duration) > 3000L -> return null
            else -> 15.0 * (1.0 - abs(query.durationMs - duration) / 6000.0)
        }
        val albumScore = when {
            query.album.isNullOrBlank() -> 2.0
            candidate.album.isNullOrBlank() -> 0.0
            comparable(query.album) == comparable(candidate.album) -> 5.0
            else -> 0.0
        }
        val artistScore = if (actualArtists == expectedArtists) 35.0 else 32.0
        return 45.0 * similarity + artistScore + durationScore + albumScore
    }

    fun rank(query: SongQuery, candidates: List<LyricsCandidate>): List<RankedLyrics> =
        candidates.filter { it.rawLyrics.isNotBlank() &&
            (!it.synced || LrcParser.hasValidSync(it.rawLyrics, query.durationMs)) }
            .mapNotNull { candidate -> score(query, candidate)?.let { RankedLyrics(candidate, it) } }
            .sortedWith(compareByDescending<RankedLyrics> { it.candidate.synced }.thenByDescending { it.score }
                .thenBy { it.candidate.provider }.thenBy { it.candidate.providerId.orEmpty() })

    fun choose(query: SongQuery, candidates: List<LyricsCandidate>): RankedLyrics? {
        val eligible = rank(query, candidates).filter { it.score >= AUTO_SELECT_SCORE }
        val best = eligible.firstOrNull() ?: return null
        val ambiguous = eligible.drop(1).any { other ->
            other.candidate.synced == best.candidate.synced && best.score - other.score < 3.0 &&
                other.candidate.provider == best.candidate.provider &&
                other.candidate.providerId != best.candidate.providerId &&
                (kotlin.math.abs((other.candidate.durationMs ?: 0) - (best.candidate.durationMs ?: 0)) > 1000 ||
                    (query.album.isNullOrBlank() && !other.candidate.album.isNullOrBlank() &&
                        !best.candidate.album.isNullOrBlank() &&
                        comparable(other.candidate.album) != comparable(best.candidate.album)))
        }
        return best.takeUnless { ambiguous }
    }

    // Compatibility for callers that need a yes/no match.
    fun matchesSong(title: String, artist: String, candidateTitle: String, candidateArtists: List<String>,
        durationMs: Long = 0L, candidateDurationMs: Long? = null): Boolean =
        score(SongQuery(title, artist, durationMs = durationMs),
            LyricsCandidate("test", null, candidateTitle, candidateArtists,
                durationMs = candidateDurationMs, rawLyrics = "", synced = false))
            ?.let { it >= AUTO_SELECT_SCORE } == true

    fun parseTranslation(lrc: String): List<LyricLine> {
        val pattern = Regex("\\[(\\d{1,2}):(\\d{2})[.:](\\d{1,3})\\](.*)")
        val lines = mutableListOf<LyricLine>()
        for (raw in lrc.lines().filter { it.isNotBlank() }) {
            val match = pattern.matchEntire(raw.trim()) ?: return emptyList()
            val (minutes, seconds, fraction, text) = match.destructured
            if (seconds.toInt() >= 60) return emptyList()
            val time = minutes.toInt() * 60 + seconds.toInt() + fraction.padEnd(3, '0').toInt() / 1000f
            lines.add(LyricLine(time, text.trim()))
        }
        return lines
    }

    /** Preserve the original text and pronunciation; reject incomplete or shifted translations. */
    fun mergeTranslation(original: List<LyricLine>, translated: List<LyricLine>): List<LyricLine>? {
        if (original.isEmpty() || original.size != translated.size) return null
        if (original.zip(translated).any { (source, target) ->
            !source.timeSec.isFinite() || !target.timeSec.isFinite() ||
                abs(source.timeSec - target.timeSec) > 0.025f || target.text.isBlank()
        }) return null
        return original.zip(translated).map { (source, target) ->
            source.copy(translation = target.text)
        }
    }
}
