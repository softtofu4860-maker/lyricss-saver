package com.example.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.data.LyricsSyncSettings

internal data class SongSyncOffset(val millis: Long, val set: (Long) -> Unit)

@Composable
internal fun rememberSongSyncOffset(title: String?, artist: String?, songId: String = com.example.data.SongIdentity.id(title.orEmpty(), artist.orEmpty()), previousIds: List<String> = emptyList()): SongSyncOffset {
    val context = LocalContext.current
    val settings = remember(context, title, artist, songId, previousIds) { LyricsSyncSettings(context, title.orEmpty(), artist.orEmpty(), songId, previousIds) }
    var millis by remember(settings) { mutableLongStateOf(settings.get()) }
    DisposableEffect(settings) {
        val listener = settings.observe { millis = it }
        onDispose { listener.close() }
    }
    return SongSyncOffset(millis, settings::set)
}
