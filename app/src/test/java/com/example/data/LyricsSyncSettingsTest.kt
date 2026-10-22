package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LyricsSyncSettingsTest {
    @Test fun `legacy offset is preserved and claimed by only the first recording`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("lyrics_sync", 0).edit().clear().commit()
        val old = LyricsSyncSettings(context, "song", "artist")
        old.set(850)
        val studio = SongDescriptor("song", "artist", "studio", 180000)
        val live = studio.copy(album = "live", durationMs = 240000)
        assertEquals(850L, LyricsSyncSettings(context, "song", "artist", studio.id).get())
        assertEquals(0L, LyricsSyncSettings(context, "song", "artist", live.id).get())
        assertEquals(850L, old.get())
    }
    @Test fun `offset is persisted per song shared across consumers and bounded`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("lyrics_sync", Context.MODE_PRIVATE).edit().clear().commit()
        val first = LyricsSyncSettings(context, "밤", "가수")
        val second = LyricsSyncSettings(context, "밤", "가수")
        var observed = 0L
        val subscription = second.observe { observed = it }
        first.set(500)
        assertEquals(500L, second.get())
        assertEquals(500L, observed)
        assertEquals(0L, LyricsSyncSettings(context, "다른 곡", "가수").get())
        first.set(Long.MAX_VALUE)
        assertEquals(10000L, second.get())
        first.set(-Long.MAX_VALUE)
        assertEquals(-10000L, second.get())
        first.set(0)
        assertEquals(0L, LyricsSyncSettings(context, "밤", "가수").get())
        subscription.close()
    }
}
