package com.example.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object LyricsTranslation {
    fun toLrc(lines: List<LyricLine>): String = lines.mapIndexed { index, line ->
        val centiseconds = (if (line.timing == LyricTiming.PLAIN) index * 100L else (line.timeSec * 100.0).roundToLong()).coerceAtLeast(0)
        String.format(Locale.ROOT, "[%02d:%02d.%02d] %s", centiseconds / 6000,
            centiseconds % 6000 / 100, centiseconds % 100, line.text)
    }.joinToString("\n")

    fun merge(original: List<LyricLine>, translated: List<LyricLine>): List<LyricLine> {
        if (translated.isEmpty()) return original
        val sorted = translated.sortedBy { it.timeSec }
        // Binary search avoids scanning the entire translation for every original line.
        return original.mapIndexed { index, line ->
            val marker = if (line.timing == LyricTiming.PLAIN) index.toFloat() else line.timeSec
            val result = sorted.binarySearchBy(marker) { it.timeSec }
            val insertion = if (result >= 0) result else -result - 1
            val match = listOfNotNull(sorted.getOrNull(insertion), sorted.getOrNull(insertion - 1))
                .minByOrNull { abs(it.timeSec - marker) }
            if (match != null && abs(match.timeSec - marker) <= 0.02f && match.text.isNotBlank()) {
                line.copy(translation = match.text)
            } else line
        }
    }
}
