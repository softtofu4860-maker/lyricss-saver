package com.example.ui

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import com.example.data.LyricLine
import com.example.data.LyricWord
import com.example.data.PerformanceMode
import com.example.service.MediaState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimedLyricTextTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `word highlighting follows paused playback and low mode keeps plain text`() {
        val line = LyricLine(1f, "Hi there", timing = com.example.data.LyricTiming.SYNCED, words = listOf(
            LyricWord(1f, 2f, "Hi "), LyricWord(2f, 3f, "there")))
        val mode = mutableStateOf(PerformanceMode.HIGH)
        compose.setContent {
            CompositionLocalProvider(LocalPerformanceMode provides mode.value) {
                TimedLyricText(line, line.text, true, MediaState(positionMs = 1500), 0f, TextStyle())
            }
        }
        val styled = compose.onNodeWithText("Hi there").fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertEquals(Color(0xFF67E8F9), styled.spanStyles.first().item.color)
        compose.runOnIdle { mode.value = PerformanceMode.LOW }
        val plain = compose.onNodeWithText("Hi there").fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertTrue(plain.spanStyles.isEmpty())
        assertEquals(line.text, plain.text)
    }
}
