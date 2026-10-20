package com.example.data

import com.example.api.GeminiLyricsService
import com.example.service.MediaStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SamplePlaybackTest {
    private fun song() = CachedLyrics(SongIdentity.id("demo", "artist"), "demo", "artist",
        GeminiLyricsService.lyricAdapter.toJson(listOf(LyricLine(1f, "line"))))

    @Test fun `demo playback no longer broadcasts position every quarter second`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            SampleDataProvider.playSampleSong(song())
            runCurrent()
            val initial = MediaStateHolder.mediaState.value
            advanceTimeBy(1000L)
            runCurrent()
            assertSame(initial, MediaStateHolder.mediaState.value)
            MediaStateHolder.setPlayingState(false)
            runCurrent()
            val paused = MediaStateHolder.mediaState.value
            advanceTimeBy(20000L)
            runCurrent()
            assertSame(paused, MediaStateHolder.mediaState.value)
        } finally { SampleDataProvider.stopSamplePlayback(); Dispatchers.resetMain() }
    }

    @Test fun `demo end timer never stops an external players next song`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            SampleDataProvider.playSampleSong(song())
            runCurrent()
            MediaStateHolder.updateState("real song", "artist", null, true, 0, 60000L, "music.player")
            runCurrent()
            val actual = MediaStateHolder.mediaState.value
            advanceTimeBy(20000L)
            runCurrent()
            assertSame(actual, MediaStateHolder.mediaState.value)
            assertTrue(MediaStateHolder.mediaState.value.isPlaying)
        } finally { SampleDataProvider.stopSamplePlayback(); Dispatchers.resetMain() }
    }
}
