package com.example.api

import org.junit.Assert.*
import org.junit.Test

class LyricsSearchRankingTest {
    private fun song(title: String = "밤", artist: String = "가수", duration: Double = 180.0,
                     synced: String? = "[00:01] 가사") = LrclibResponse(
        trackName = title, artistName = artist, duration = duration,
        syncedLyrics = synced, plainLyrics = "가사")

    @Test fun `exact plain lyrics beat synced cover and extended version`() {
        val exact = song(synced = null)
        val ranked = LyricsSearchRanking.rank(listOf(song(artist = "커버 가수"),
            song(duration = 300.0), exact), "밤", "가수", 180000)
        assertEquals(exact, ranked.first())
        assertFalse(LyricsSearchRanking.isMatch(song(artist = "다른 가수"), "밤", "가수", 180000))
        assertFalse(LyricsSearchRanking.isMatch(song(duration = 300.0), "밤", "가수", 180000))
    }

    @Test fun `unicode short titles and missing duration are supported`() {
        assertTrue(LyricsSearchRanking.isMatch(song(duration = Double.NaN), "밤", "가수", 180000))
        assertTrue(LyricsSearchRanking.isMatch(song(title = "Ａ！", artist = "BTS"), "a", "bts", 0))
        assertFalse(LyricsSearchRanking.isMatch(song(title = "밤 라이브"), "밤", "가수", 0))
    }

    @Test fun `empty and duplicate results do not crowd candidate list`() {
        val result = song()
        assertEquals(listOf(result), LyricsSearchRanking.rank(listOf(result, result,
            LrclibResponse(trackName = "빈 곡")), "밤", "가수", 180000))
        assertFalse(LyricsSearchRanking.isMatch(result.copy(instrumental = true), "밤", "가수", 0))
    }

    @Test fun `longer video is allowed only when title and artist match exactly`() {
        assertTrue(LyricsSearchRanking.isMatch(song(title = "song", artist = "artist", duration = 200.0), "song", "artist", 180000))
        assertFalse(LyricsSearchRanking.isMatch(song(title = "song", artist = "cover artist", duration = 200.0), "song", "artist", 180000))
        assertFalse(LyricsSearchRanking.isMatch(song(title = "song live", artist = "artist", duration = 200.0), "song", "artist", 180000))
        assertFalse(LyricsSearchRanking.isMatch(song(title = "song", artist = "artist", duration = 211.0), "song", "artist", 180000))
    }

    @Test fun `display decorations featured artists aliases and topic labels can match automatically`() {
        assertTrue(LyricsSearchRanking.isMatch(song(title = "Song", artist = "IU"),
            "Song (Official Music Video)", "아이유 (IU)", 180000))
        assertTrue(LyricsSearchRanking.isMatch(song(title = "Song", artist = "IU, guest"),
            "Song (feat. guest)", "IU - Topic", 180000))
        assertTrue(LyricsSearchRanking.isMatch(song(title = "밤 편지", artist = "IU"), "밤편지", "IU", 180000))
        assertFalse(LyricsSearchRanking.isMatch(song(title = "A Long Song (Live)", artist = "IU"),
            "A Long Song", "IU", 180000))
        assertFalse(LyricsSearchRanking.isMatch(song(title = "Song", artist = "cover IU"), "Song", "IU", 180000))
    }
}
