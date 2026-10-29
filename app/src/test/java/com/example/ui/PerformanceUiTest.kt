package com.example.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.PerformanceMode
import com.example.data.PerformanceSettings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PerformanceUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() {
        context.getSharedPreferences("screensaver_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(com.example.data.DisplayPreferences.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `settings buttons persist selection and react to another consumers change`() {
        PerformanceSettings(context).setMode(PerformanceMode.HIGH)
        compose.setContent { MaterialTheme { PerformanceEnvironment { PerformanceModeSettings() } } }
        compose.onNodeWithText("고사양").assertIsSelected()
        compose.onNodeWithText("저사양").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(PerformanceMode.LOW, PerformanceSettings(context).mode()) }
        compose.runOnIdle { PerformanceSettings(context).setMode(PerformanceMode.HIGH) }
        compose.onNodeWithText("고사양").assertIsSelected()
    }

    @Test fun `effects stop when inactive or paused and resume without time jumps`() {
        compose.mainClock.autoAdvance = false
        val active = mutableStateOf(true)
        val playing = mutableStateOf(true)
        lateinit var phase: State<Float>
        compose.setContent {
            CompositionLocalProvider(LocalPerformanceMode provides PerformanceMode.HIGH,
                LocalEffectsActive provides active.value) {
                phase = rememberEffectPhase(playing.value, periodSeconds = 10f)
            }
        }
        compose.mainClock.advanceTimeBy(1000L)
        val moving = compose.runOnIdle { phase.value }
        assertTrue(moving > 0f)
        compose.runOnIdle { active.value = false }
        compose.mainClock.advanceTimeBy(32L)
        val stopped = compose.runOnIdle { phase.value }
        compose.mainClock.advanceTimeBy(1000L)
        compose.runOnIdle { assertEquals(stopped, phase.value, 0f); active.value = true }
        compose.mainClock.advanceTimeBy(32L)
        compose.runOnIdle { }
        compose.mainClock.advanceTimeBy(100L)
        compose.runOnIdle { assertTrue("phase=${phase.value}, stopped=$stopped", phase.value > stopped); playing.value = false }
        compose.mainClock.advanceTimeBy(32L)
        val paused = compose.runOnIdle { phase.value }
        compose.mainClock.advanceTimeBy(1000L)
        compose.runOnIdle { assertEquals(paused, phase.value, 0f) }
    }

    @Test fun `environment responds to screen off and lifecycle stop`() {
        val owner = object : androidx.lifecycle.LifecycleOwner {
            override val lifecycle = androidx.lifecycle.LifecycleRegistry.createUnsafe(this)
        }
        owner.lifecycle.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
        val power = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        org.robolectric.Shadows.shadowOf(power).setIsInteractive(true)
        var active = false
        compose.setContent {
            CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                PerformanceEnvironment {
                    val current = LocalEffectsActive.current
                    SideEffect { active = current }
                }
            }
        }
        compose.runOnIdle { assertTrue(active); owner.lifecycle.currentState = androidx.lifecycle.Lifecycle.State.CREATED }
        compose.runOnIdle { assertFalse(active); owner.lifecycle.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        compose.runOnIdle { assertTrue(active) }
        compose.runOnIdle {
            org.robolectric.Shadows.shadowOf(power).setIsInteractive(false)
            context.sendBroadcast(android.content.Intent(android.content.Intent.ACTION_SCREEN_OFF))
        }
        compose.runOnIdle { assertFalse(active) }
        compose.runOnIdle {
            org.robolectric.Shadows.shadowOf(power).setIsInteractive(true)
            context.sendBroadcast(android.content.Intent(android.content.Intent.ACTION_SCREEN_ON))
        }
        compose.runOnIdle { assertTrue(active) }
    }

}
