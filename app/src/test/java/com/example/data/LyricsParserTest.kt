package com.example.data

import com.example.api.GeminiLyricsService
import org.junit.Assert.*
import org.junit.Test

class LyricsParserTest {
    @Test fun `repeated timestamps expand with offset and metadata never become lyrics`() {
        val parsed = LyricsParser.parse("[ti:sample]\n[offset:250]\n[00:01.5][00:03.050]Hello\n[00:04.0]World")
        assertEquals(listOf("Hello", "Hello", "World"), parsed.map { it.text })
        assertEquals(listOf(1.25f, 2.8f, 3.75f), parsed.map { it.timeSec })
        assertTrue(LyricsParser.parse("[ti:sample]\n[ar:artist]\n[offset:0]").isEmpty())
    }

    @Test fun `fast adjacent lines are not swallowed as translations`() {
        val parsed = LyricsParser.parse("[00:01.00]one\n[00:01.10]two\n[00:02.00]three\n[00:02.00]셋")
        assertEquals(3, parsed.size)
        assertEquals("two", parsed[1].text)
        assertEquals("셋", parsed[2].translation)
    }

    @Test fun `enhanced LRC uses absolute word timing including repeated lines`() {
        val parsed = LyricsParser.parse("[00:01][00:11]<00:01>Hi <00:02>there<00:03>")
        assertEquals(listOf("Hi there", "Hi there"), parsed.map { it.text })
        assertEquals(listOf(1f, 2f), parsed[0].words.map { it.startTimeSec })
        assertEquals(listOf(11f, 12f), parsed[1].words.map { it.startTimeSec })
        assertEquals(13f, parsed[1].endTimeSec!!, 0f)
        val incomplete = LyricsParser.parse("[00:01]<00:01>Hello <00:02>world").single()
        assertEquals("Hello world", incomplete.text)
        assertTrue(incomplete.words.isEmpty())
    }

    @Test fun `YRC preserves spaces word durations and attached translation romanization`() {
        val parsed = LyricsParser.parse("{\"t\":0,\"c\":[]}\n[1000,2000](1000,500,0)Hi (1500,1500,0)there", 10000,
            "[00:01]안녕", "[00:01]annyeong").single()
        assertEquals("Hi there", parsed.text)
        assertEquals("안녕", parsed.translation)
        assertEquals("annyeong", parsed.romanization)
        assertEquals(2, parsed.words.size)
        assertEquals(3f, parsed.words.last().endTimeSec, 0f)
    }

    @Test fun `positive offset appears earlier clamps and malformed tags are not rendered as lyrics`() {
        val parsed = LyricsParser.parse("[offset:2000]\n[00:01.00]Hello\n[broken timestamp]")
        assertEquals(0f, parsed.single().timeSec, 0f)
        assertEquals(listOf("one", "two"), LyricsParser.parse("[ar:Artist]\none\n\ntwo", 9000).map { it.text })
        val plain = LyricsParser.parse("one\ntwo", 9000)
        assertEquals(listOf(0f, 0f), plain.map { it.timeSec })
        assertTrue(plain.all { it.timing == LyricTiming.PLAIN })
    }

    @Test fun `word timings survive JSON cache and old cached lines still deserialize`() {
        val parsed = LyricsParser.parse("[1000,500](1000,500,0)hi")
        assertEquals(parsed, GeminiLyricsService.parseJsonLyrics(GeminiLyricsService.lyricAdapter.toJson(parsed)))
        val old = GeminiLyricsService.parseJsonLyrics("[{\"timeSec\":1.0,\"text\":\"old\"}]").single()
        assertTrue(old.words.isEmpty())
        assertNull(old.endTimeSec)
    }
}
