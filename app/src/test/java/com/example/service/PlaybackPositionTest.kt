package com.example.service

import org.junit.Assert.*
import org.junit.Test

class PlaybackPositionTest {
    @Test fun `already playing snapshot includes elapsed time and playback speed`() {
        val state = MediaState(positionMs = 60000, durationMs = 180000, isPlaying = true,
            lastUpdateTime = 1000, speed = 1.5f)
        assertEquals(66000, state.getCurrentPositionMs(5000))
        assertEquals(180000, state.getCurrentPositionMs(1000000))
    }
    @Test fun `paused stopped unknown and invalid snapshots never extrapolate`() {
        assertEquals(4000, MediaState(positionMs = 4000, isPlaying = false, lastUpdateTime = 1000).getCurrentPositionMs(5000))
        assertEquals(0, MediaState(positionMs = -1, isPlaying = true, lastUpdateTime = 1000).getCurrentPositionMs(5000))
        assertEquals(4000, MediaState(positionMs = 4000, isPlaying = true, lastUpdateTime = 0).getCurrentPositionMs(5000))
        assertEquals(4000, MediaState(positionMs = 4000, isPlaying = true, lastUpdateTime = 9000).getCurrentPositionMs(5000))
        assertEquals(4000, MediaState(positionMs = 4000, isPlaying = true, speed = Float.NaN, lastUpdateTime = 1000).getCurrentPositionMs(5000))
    }
    @Test fun `seek resume and speed change use fresh baselines independent of wall clock`() {
        val first = MediaState(positionMs = 10000, isPlaying = true, lastUpdateTime = 1000)
        val paused = first.copy(positionMs = first.getCurrentPositionMs(2000), isPlaying = false, lastUpdateTime = 2000)
        assertEquals(11000, paused.getCurrentPositionMs(9000))
        val seek = paused.copy(positionMs = 50000, isPlaying = true, lastUpdateTime = 9000, speed = 2f)
        assertEquals(52000, seek.getCurrentPositionMs(10000))
        assertEquals(50000, seek.getCurrentPositionMs(9000))
    }
}
