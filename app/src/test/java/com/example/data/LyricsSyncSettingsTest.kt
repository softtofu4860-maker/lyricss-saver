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
