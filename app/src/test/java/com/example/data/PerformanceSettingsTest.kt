package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PerformanceSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun setUp() { context.getSharedPreferences("screensaver_prefs", Context.MODE_PRIVATE).edit().clear().commit() }
    @After fun tearDown() { setUp() }

    @Test fun `low memory devices default low while capable devices default high`() {
        val gib = 1024L * 1024 * 1024
        assertEquals(PerformanceMode.LOW, PerformanceMode.defaultForDevice(false, 2 * gib))
        assertEquals(PerformanceMode.LOW, PerformanceMode.defaultForDevice(false, 3 * gib))
        assertEquals(PerformanceMode.LOW, PerformanceMode.defaultForDevice(true, 8 * gib))
        assertEquals(PerformanceMode.HIGH, PerformanceMode.defaultForDevice(false, 8 * gib))
    }

    @Test fun `selection survives new settings instance and notifies another consumer`() {
        val first = PerformanceSettings(context)
        val other = PerformanceSettings(context)
        val changes = mutableListOf<PerformanceMode>()
        val observation = other.observe { changes.add(it) }
        try {
            first.setMode(PerformanceMode.LOW)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(PerformanceMode.LOW, PerformanceSettings(context).mode())
            assertEquals(PerformanceMode.LOW, changes.last())
            first.setMode(PerformanceMode.HIGH)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(PerformanceMode.HIGH, changes.last())
        } finally { observation.close() }
        val count = changes.size
        first.setMode(PerformanceMode.LOW)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(count, changes.size)
    }

    @Test fun `unknown or corrupt stored setting falls back safely`() {
        val prefs = context.getSharedPreferences("screensaver_prefs", Context.MODE_PRIVATE)
        val expected = PerformanceSettings(context).mode()
        prefs.edit().putString(PerformanceSettings.KEY, "UNKNOWN").commit()
        assertEquals(expected, PerformanceSettings(context).mode())
        prefs.edit().putInt(PerformanceSettings.KEY, 42).commit()
        assertEquals(expected, PerformanceSettings(context).mode())
    }

    @Test fun `low profile bounds workload while both profiles limit resource use`() {
        val low = PerformanceMode.LOW.profile
        val high = PerformanceMode.HIGH.profile
        assertEquals(30, low.effectFps)
        assertEquals(60, high.effectFps)
        assertFalse(low.richEffects)
        assertTrue(high.richEffects)
        assertTrue(low.particleCount < high.particleCount)
        assertTrue(low.barCount < high.barCount)
        assertTrue(low.waveCount < high.waveCount)
        assertTrue(low.artworkMaxEdge < high.artworkMaxEdge)
        assertTrue(high.artworkMaxEdge <= 1024)
        assertTrue(low.progressIntervalMs <= 250L)
    }
}
