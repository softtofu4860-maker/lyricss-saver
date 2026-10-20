package com.example.data

import kotlin.math.abs

/** Standard LRC, enhanced LRC and NetEase YRC, with absolute word times in seconds. */
object LyricsParser {
    private val timestamp = Regex("\\[(\\d{1,4}):([0-5]?\\d)(?:[.:](\\d{1,3}))?]")
    private val wordTimestamp = Regex("<(\\d{1,4}):([0-5]?\\d)(?:[.:](\\d{1,3}))?>")
    private val offsetTag = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
    private val metadata = Regex("^\\[[a-zA-Z]+:.*]$")
    private val yrcLine = Regex("^\\[(\\d+),(\\d+)](.*)$")
    private val yrcWord = Regex("\\((\\d+),(\\d+),\\d+\\)")

    private fun seconds(match: MatchResult): Float = match.groupValues[1].toFloat() * 60 +
        match.groupValues[2].toFloat() + match.groupValues[3].let {
            if (it.isEmpty()) 0f else it.padEnd(3, '0').toFloat() / 1000
        }

    fun parse(text: String, durationMs: Long = 0, translation: String? = null,
              romanization: String? = null): List<LyricLine> {
        val original = parseBase(text, durationMs)
        val translations = translation?.let { parseBase(it, durationMs) }.orEmpty()
        val romanizations = romanization?.let { parseBase(it, durationMs) }.orEmpty()
        fun nearest(lines: List<LyricLine>, time: Float): String? {
            val found = lines.binarySearchBy(time) { it.timeSec }
            val insertion = if (found >= 0) found else -found - 1
            return listOfNotNull(lines.getOrNull(insertion), lines.getOrNull(insertion - 1))
                .minByOrNull { abs(it.timeSec - time) }
                ?.takeIf { abs(it.timeSec - time) < 0.25f }?.text
        }
        return original.map { line -> line.copy(
            translation = nearest(translations, line.timeSec) ?: line.translation,
            romanization = nearest(romanizations, line.timeSec) ?: line.romanization
        ) }
    }

    private fun parseBase(text: String, durationMs: Long): List<LyricLine> {
        // In LRC a positive offset makes lyrics appear earlier.
        val offset = -(offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull()?.div(1000f) ?: 0f)
        val result = mutableListOf<LyricLine>()
        val plain = mutableListOf<String>()
        var timed = false
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isBlank() || metadata.matches(line) || line.startsWith("{\"t\":")) continue
            val yrc = yrcLine.matchEntire(line)
            if (yrc != null) {
                timed = true
                val start = yrc.groupValues[1].toFloatOrNull()?.div(1000) ?: continue
                val end = start + (yrc.groupValues[2].toFloatOrNull()?.div(1000) ?: continue)
                if (!start.isFinite() || !end.isFinite()) continue
                val body = yrc.groupValues[3]
                val tags = yrcWord.findAll(body).toList()
                val words = tags.mapIndexedNotNull { index, tag ->
                    val wordStart = tag.groupValues[1].toFloatOrNull()?.div(1000) ?: return@mapIndexedNotNull null
                    val wordEnd = wordStart + (tag.groupValues[2].toFloatOrNull()?.div(1000) ?: return@mapIndexedNotNull null)
                    val word = body.substring(tag.range.last + 1, tags.getOrNull(index + 1)?.range?.first ?: body.length)
                    if (word.isEmpty() || !wordStart.isFinite() || !wordEnd.isFinite() || wordEnd < wordStart) null else
                        LyricWord((wordStart + offset).coerceAtLeast(0f), (wordEnd + offset).coerceAtLeast(0f), word)
                }
                val content = words.joinToString("") { it.text }
                if (content.isNotBlank()) result.add(LyricLine((start + offset).coerceAtLeast(0f), content,
                    words = words, endTimeSec = (end + offset).coerceAtLeast(0f)))
                continue
            }
            val tags = timestamp.findAll(line).takeWhile { match ->
                line.substring(0, match.range.first).replace(timestamp, "").isBlank()
            }.toList()
            if (tags.isEmpty()) {
                // Malformed timing tags are not plain lyrics.
                if (!line.startsWith("[")) plain.add(line)
                continue
            }
            timed = true
            val body = line.substring(tags.last().range.last + 1).trim()
            if (body.isBlank()) continue
            val wordTags = wordTimestamp.findAll(body).toList()
            val content = body.replace(wordTimestamp, "")
            tags.forEach { tag ->
                val shift = seconds(tag) - seconds(tags.first())
                val words = wordTags.mapIndexedNotNull { index, wordTag ->
                    val next = wordTags.getOrNull(index + 1) ?: return@mapIndexedNotNull null
                    val word = body.substring(wordTag.range.last + 1, next.range.first)
                    val start = seconds(wordTag) + shift + offset
                    val end = seconds(next) + shift + offset
                    if (word.isEmpty() || end < start) null else
                        LyricWord(start.coerceAtLeast(0f), end.coerceAtLeast(0f), word)
                }
                // A missing terminal marker cannot safely supply word timing for the whole line.
                val validWords = words.takeIf { it.joinToString("") { word -> word.text } == content }.orEmpty()
                result.add(LyricLine((seconds(tag) + offset).coerceAtLeast(0f), content,
                    words = validWords, endTimeSec = validWords.lastOrNull()?.endTimeSec))
            }
        }
        if (!timed) {
            val step = (if (durationMs > 0) durationMs / 1000f else 180f) / (plain.size + 1)
            return plain.mapIndexed { index, value -> LyricLine((index + 1) * step, value) }
        }
        val sorted = result.distinctBy { it.timeSec to it.text }.sortedBy { it.timeSec }
        // Only exactly equal timestamps may represent embedded original/translation pairs.
        val merged = mutableListOf<LyricLine>()
        sorted.forEach { line ->
            val previous = merged.lastOrNull()
            if (previous != null && previous.timeSec == line.timeSec && previous.translation == null &&
                previous.words.isEmpty() && line.words.isEmpty()) {
                merged[merged.lastIndex] = previous.copy(translation = line.text)
            } else merged.add(line)
        }
        return merged
    }
}
