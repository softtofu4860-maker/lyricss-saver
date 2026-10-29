package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class DisplayPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val legacy get() = context.getSharedPreferences("screensaver_prefs", 0)
    private val target get() = context.getSharedPreferences(DisplayPreferences.FILE, 0)
    @Before fun clear() { legacy.edit().clear().commit(); target.edit().clear().commit() }
    @After fun cleanup() = clear()

    @Test fun `copies display preferences but never copies the old api key`() {
        legacy.edit().putString("screensaver_theme", "CLASSIC_NEON")
            .putFloat("font_scale", 1.2f).putString("gemini_api_key", "private-key").commit()
        val prefs = DisplayPreferences.get(context)
        assertEquals("CLASSIC_NEON", prefs.getString("screensaver_theme", null))
        assertEquals(1.2f, prefs.getFloat("font_scale", 0f), 0f)
        assertFalse(prefs.contains("gemini_api_key"))
        assertEquals("private-key", legacy.getString("gemini_api_key", null))
    }

    @Test fun `restored settings take precedence and migration does not overwrite later changes`() {
        legacy.edit().putString("screensaver_theme", "old").commit()
        target.edit().putString("screensaver_theme", "restored").commit()
        val prefs = DisplayPreferences.get(context)
        assertEquals("restored", prefs.getString("screensaver_theme", null))
        prefs.edit().putString("screensaver_theme", "new").commit()
        assertEquals("new", DisplayPreferences.get(context).getString("screensaver_theme", null))
    }
}
