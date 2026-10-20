package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaState(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val packageName: String? = null,
    val lastUpdateTime: Long = 0, // System.currentTimeMillis()
    val speed: Float = 1.0f,
    val lyrics: String? = null,
    val albumArt: android.graphics.Bitmap? = null
) {
    fun getCurrentPositionMs(): Long {
        if (!isPlaying) return positionMs
        val elapsed = System.currentTimeMillis() - lastUpdateTime
        val calculated = positionMs + (elapsed * speed).toLong()
        return if (durationMs > 0) calculated.coerceAtMost(durationMs) else calculated
    }
}

object MediaStateHolder {
    private val _mediaState = MutableStateFlow(MediaState())
    val mediaState: StateFlow<MediaState> = _mediaState.asStateFlow()

    fun updateState(
        title: String?,
        artist: String?,
        album: String?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        packageName: String?,
        speed: Float = 1.0f,
        lyrics: String? = null,
        albumArt: android.graphics.Bitmap? = null
    ) {
        val previous = _mediaState.value
        if (previous.title != title || previous.artist != artist ||
            (previous.durationMs <= 0 && durationMs > 0) || (previous.lyrics.isNullOrBlank() && !lyrics.isNullOrBlank())) {
            com.example.diagnostics.LyricsDiagnostics.record(com.example.diagnostics.DiagnosticStage.MEDIA_DETECTED,
                source = packageName.orEmpty(), fields = mapOf("title" to title, "artist" to artist,
                    "durationMs" to durationMs, "hasLyrics" to !lyrics.isNullOrBlank()))
        }
        // Album art is shared with Compose and MediaSession; let reference lifetimes
        // release it instead of recycling a bitmap that may still be on screen.
        _mediaState.value = MediaState(
            title = title,
            artist = artist,
            album = album,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            packageName = packageName,
            lastUpdateTime = System.currentTimeMillis(),
            speed = speed,
            lyrics = lyrics,
            albumArt = albumArt
        )
    }

    fun setPlayingState(isPlaying: Boolean) {
        val current = _mediaState.value
        _mediaState.value = current.copy(
            positionMs = current.getCurrentPositionMs(),
            isPlaying = isPlaying,
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    fun setPlaybackPosition(positionMs: Long) {
        val current = _mediaState.value
        _mediaState.value = current.copy(
            positionMs = positionMs,
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    fun clear() {
        _mediaState.value = MediaState()
    }
}
