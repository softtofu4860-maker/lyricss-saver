package com.example.data

import org.junit.Assert.*
import org.junit.Test

class LyricsTranslationTest {
    @Test fun `merges by timestamp without replacing original text timing or romanization`() {
        val original = listOf(LyricLine(1.234f, "hello", romanization = "hello"),
            LyricLine(3f, "world", translation = "기존 번역"), LyricLine(7f, "last"))
        val translated = listOf(LyricLine(7f, "마지막"), LyricLine(1.23f, "안녕"), LyricLine(50f, "무관"))
        val merged = LyricsTranslation.merge(original, translated)
        assertEquals(original.map { it.text }, merged.map { it.text })
        assertEquals(original.map { it.timeSec }, merged.map { it.timeSec })
        assertEquals("hello", merged[0].romanization)
        assertEquals(listOf("안녕", "기존 번역", "마지막"), merged.map { it.translation })
    }

    @Test fun `rounds timestamp across minute boundary and excludes existing translation`() {
        assertEquals("[01:00.00] original", LyricsTranslation.toLrc(
            listOf(LyricLine(59.999f, "original", translation = "번역"))))
    }
}
