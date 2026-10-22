package com.example.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.data.LyricLine
import com.example.service.MediaState

internal fun highlightedLyrics(line: LyricLine, timeSec: Float): AnnotatedString = buildAnnotatedString {
    line.words.forEach { word ->
        val color = when {
            timeSec >= word.endTimeSec -> Color.White
            timeSec >= word.startTimeSec -> Color(0xFF67E8F9)
            else -> Color.White.copy(alpha = 0.4f)
        }
        withStyle(SpanStyle(color = color)) { append(word.text) }
    }
}

/** Only the active line samples playback; low power mode uses normal line highlighting. */
@Composable
internal fun TimedLyricText(line: LyricLine, text: String, active: Boolean,
                            mediaState: MediaState?, offsetSec: Float,
                            style: TextStyle, modifier: Modifier = Modifier) {
    val timed = active && line.timing == com.example.data.LyricTiming.SYNCED && LocalPerformanceMode.current.profile.richEffects && mediaState != null &&
        line.words.isNotEmpty() && text == line.text && line.words.joinToString("") { it.text } == text
    if (timed) {
        val position by rememberPlaybackPosition(mediaState!!)
        Text(highlightedLyrics(line, position / 1000f + offsetSec), style = style, modifier = modifier)
    } else Text(text, style = style, modifier = modifier)
}
