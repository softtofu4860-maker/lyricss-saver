package com.example.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.service.MediaState

internal data class LyricsDisplay(val lines: List<LyricLine>, val estimated: Boolean, val toggle: () -> Unit)
@Composable internal fun rememberLyricsDisplay(lines: List<LyricLine>, media: MediaState): LyricsDisplay {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("lyrics_display", Context.MODE_PRIVATE) }
    val key = media.songId
    var estimated by remember(key) { mutableStateOf(prefs.getBoolean(key, false)) }
    val presented = remember(lines, estimated, media.durationMs) { LyricPresentation.display(lines, media.durationMs, estimated) }
    return LyricsDisplay(presented, estimated) { estimated = !estimated; prefs.edit().putBoolean(key, estimated).apply() }
}
@Composable internal fun LyricsTimingControls(display: LyricsDisplay, modifier: Modifier = Modifier) {
    val mode = LyricPresentation.mode(display.lines)
    Surface(modifier, color = Color(0xD9131318), shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text(mode.label, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (mode != LyricTiming.SYNCED && display.lines.isNotEmpty()) {
                TextButton(onClick = display.toggle, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text(if (display.estimated) "추정 스크롤 끄기" else "추정 스크롤 켜기", fontSize = 11.sp)
                }
            }
        }
    }
}
