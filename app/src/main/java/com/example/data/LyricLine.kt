package com.example.data

data class LyricLine(
    val timeSec: Float,
    val text: String,
    val translation: String? = null,
    val romanization: String? = null,
    val words: List<LyricWord> = emptyList(),
    val endTimeSec: Float? = null,
    val timing: LyricTiming = LyricTiming.UNKNOWN,
    val timingSource: String = "legacy_unknown"
)

enum class LyricTiming(val label: String) {
    SYNCED("싱크 가사"), PLAIN("일반 가사"), ESTIMATED("추정 스크롤 · 정확하지 않음"), UNKNOWN("기존 가사 · 시간 출처 미확인")
}

object LyricPresentation {
    fun canSeek(line: LyricLine) = line.timing == LyricTiming.SYNCED
    fun activeIndex(lines: List<LyricLine>, seconds: Float): Int =
        if (lines.isEmpty() || lines.any { it.timing != LyricTiming.SYNCED && it.timing != LyricTiming.ESTIMATED }) -1
        else lines.indexOfLast { seconds >= it.timeSec }
    fun mode(lines: List<LyricLine>): LyricTiming = when {
        lines.isEmpty() -> LyricTiming.UNKNOWN
        lines.all { it.timing == LyricTiming.SYNCED } -> LyricTiming.SYNCED
        lines.all { it.timing == LyricTiming.ESTIMATED } -> LyricTiming.ESTIMATED
        lines.any { it.timing == LyricTiming.UNKNOWN } -> LyricTiming.UNKNOWN
        else -> LyricTiming.PLAIN
    }
    fun display(lines: List<LyricLine>, durationMs: Long, estimated: Boolean): List<LyricLine> {
        if (!estimated || durationMs <= 0 || mode(lines) == LyricTiming.SYNCED) return lines
        val step = durationMs / 1000f / (lines.size + 1)
        return lines.mapIndexed { index, line -> line.copy(timeSec = (index + 1) * step, words = emptyList(),
            timing = LyricTiming.ESTIMATED, endTimeSec = null, timingSource = "user_estimated") }
    }
}

data class LyricWord(val startTimeSec: Float, val endTimeSec: Float, val text: String)
