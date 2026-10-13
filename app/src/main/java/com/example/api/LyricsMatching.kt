package com.example.api

import com.example.data.LyricLine
import com.example.data.LyricsIdentity
import kotlin.math.abs

object LyricsMatching {
    private fun comparable(value: String): String =
        LyricsIdentity.normalize(value).filter { it.isLetterOrDigit() }

    fun matchesSong(
        title: String, artist: String, candidateTitle: String, candidateArtists: List<String>,
        durationMs: Long = 0L, candidateDurationMs: Long? = null
    ): Boolean {
        val expectedTitle = comparable(title)
        val expectedArtist = comparable(artist)
        if (expectedTitle.isEmpty() || expectedArtist.isEmpty()) return false
        if (expectedTitle != comparable(candidateTitle)) return false
        if (candidateArtists.none { comparable(it) == expectedArtist }) return false
        return durationMs <= 0 || candidateDurationMs == null ||
            abs(durationMs - candidateDurationMs) <= 3000L
    }

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
