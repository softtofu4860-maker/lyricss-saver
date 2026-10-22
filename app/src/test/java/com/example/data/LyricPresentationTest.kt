package com.example.data

import com.example.api.GeminiLyricsService
import org.junit.Assert.*
import org.junit.Test

class LyricPresentationTest {
    @Test fun `plain lyrics have no sync highlight or seek before or after cache roundtrip`() {
        val plain = LyricsParser.parse("one\ntwo", 60000)
        val restored = GeminiLyricsService.parseJsonLyrics(GeminiLyricsService.lyricAdapter.toJson(plain))
        assertEquals(LyricTiming.PLAIN, LyricPresentation.mode(restored))
        assertEquals(-1, LyricPresentation.activeIndex(restored, 50f))
        assertFalse(restored.any { LyricPresentation.canSeek(it) })
        assertEquals("plain_text", restored.first().timingSource)
        val estimated = LyricPresentation.display(restored, 60000, true)
        assertEquals(LyricTiming.ESTIMATED, LyricPresentation.mode(estimated))
        assertEquals(0, LyricPresentation.activeIndex(estimated, 25f))
        assertFalse(estimated.any { LyricPresentation.canSeek(it) })
        assertEquals(restored, LyricPresentation.display(restored, 60000, false))
    }
    @Test fun `real LRC timing still highlights seeks and never gets replaced by estimates`() {
        val synced = LyricsParser.parse("[00:01]one\n[00:03]two")
        assertEquals(0, LyricPresentation.activeIndex(synced, 2f))
        assertTrue(synced.all { LyricPresentation.canSeek(it) })
        assertEquals(synced, LyricPresentation.display(synced, 60000, true))
        val legacy = GeminiLyricsService.parseJsonLyrics("[{\"timeSec\":4.0,\"text\":\"legacy\"}]")
        assertEquals(LyricTiming.UNKNOWN, LyricPresentation.mode(legacy))
        assertEquals(-1, LyricPresentation.activeIndex(legacy, 50f))
    }
    @Test fun `plain translation uses unique transport markers and preserves original times and order`() {
        val original = LyricsParser.parse("first\nsecond", 60000)
        val raw = LyricsTranslation.toLrc(original)
        assertTrue(raw.contains("[00:00.00] first"))
        assertTrue(raw.contains("[00:01.00] second"))
        val merged = LyricsTranslation.merge(original, LyricsParser.parse("[00:00]첫째\n[00:01]둘째"))
        assertEquals(listOf("첫째", "둘째"), merged.map { it.translation })
        assertEquals(original.map { it.timeSec }, merged.map { it.timeSec })
        assertEquals(original.map { it.timing }, merged.map { it.timing })
    }
}
