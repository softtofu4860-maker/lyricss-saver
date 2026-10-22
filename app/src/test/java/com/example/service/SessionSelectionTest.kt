package com.example.service

import org.junit.Assert.*
import org.junit.Test

class SessionSelectionTest {
    @Test fun `playing session wins and old late events cannot displace newer playback`() {
        val spotify = SessionCandidate("spotify", true, 1000, true)
        val other = SessionCandidate("other", true, 2000, true)
        assertEquals("other", SessionSelection.choose(listOf(spotify, other), "spotify"))
        assertEquals("other", SessionSelection.choose(listOf(spotify.copy(updateTime = 1500), other), "other"))
        assertEquals("spotify", SessionSelection.choose(listOf(spotify, other.copy(playing = false)), "other"))
    }
    @Test fun `ties and paused playback keep the current session and empty lists clear selection`() {
        val a = SessionCandidate("a", false, 1000, true)
        val b = SessionCandidate("b", false, 1000, true)
        assertEquals("b", SessionSelection.choose(listOf(a, b), "b"))
        assertEquals("b", SessionSelection.choose(listOf(a.copy(playing = true), b.copy(playing = true)), "b"))
        assertNull(SessionSelection.choose(emptyList(), "b"))
        assertNull(SessionSelection.choose(listOf(a.copy(hasTitle = false)), "a"))
    }
}
