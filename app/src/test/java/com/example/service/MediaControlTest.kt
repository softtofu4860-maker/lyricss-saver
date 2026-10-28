package com.example.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaControlTest {
    @Test fun `missing session reports failure and never runs the command`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var called = false
        assertFalse(MediaControl.perform(context) { called = true })
        assertFalse(called)
        assertTrue(ShadowToast.getTextOfLatestToast().contains("제어하지 못했습니다"))
    }
}
