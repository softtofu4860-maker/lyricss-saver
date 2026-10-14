package com.example.api

import com.example.data.LyricLine

object LrcParser {
    private val timestamp = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]")
    private val offset = Regex("(?i)\\[offset:([+-]?\\d+)\\]")
    private val metadata = Regex("(?i)^\\[(?:ti|ar|al|by|re|ve|length|offset):.*]$")

    fun parseSynced(text: String): List<LyricLine> {
        val offsetSec = (offset.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L) / 1000f
        val result = mutableListOf<LyricLine>()
        for (raw in text.lines()) {
            val tags = timestamp.findAll(raw).toList()
            if (tags.isEmpty()) continue
            val words = raw.substring(tags.last().range.last + 1).trim()
            for (tag in tags) {
                val minutes = tag.groupValues[1].toInt()
                val seconds = tag.groupValues[2].toInt()
                if (seconds >= 60) continue
                val fraction = tag.groupValues[3].ifBlank { "0" }.padEnd(3, '0').toInt() / 1000f
                result.add(LyricLine((minutes * 60 + seconds + fraction + offsetSec).coerceAtLeast(0f), words))
            }
        }
        return result.distinctBy { it.timeSec to it.text }.sortedBy { it.timeSec }
    }

    fun hasValidSync(text: String, durationMs: Long = 0L): Boolean {
        val lines = parseSynced(text)
        val sung = lines.filter { it.text.isNotBlank() }
        return sung.isNotEmpty() && (sung.size == 1 || sung.map { it.timeSec }.distinct().size > 1) && lines.all {
            it.timeSec.isFinite() && (durationMs <= 0 || it.timeSec * 1000 <= durationMs + 2000)
        }
    }

    fun parsePlain(text: String): List<LyricLine> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() && !metadata.matches(it) }
            .map { LyricLine(0f, it) }

    fun parse(text: String, translation: String? = null, romanization: String? = null): List<LyricLine> {
        val timed = parseSynced(text)
        if (timed.isEmpty()) return parsePlain(text)
        val translations = translation?.let(::parseSynced).orEmpty()
        val romanizations = romanization?.let(::parseSynced).orEmpty()
        return timed.map { source ->
            source.copy(
                translation = translations.minByOrNull { kotlin.math.abs(it.timeSec - source.timeSec) }
                    ?.takeIf { kotlin.math.abs(it.timeSec - source.timeSec) < 0.08f }?.text,
                romanization = romanizations.minByOrNull { kotlin.math.abs(it.timeSec - source.timeSec) }
                    ?.takeIf { kotlin.math.abs(it.timeSec - source.timeSec) < 0.08f }?.text
            )
        }
    }
}
