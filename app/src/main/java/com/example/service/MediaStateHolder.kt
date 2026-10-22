package com.example.service

import android.os.SystemClock
import com.example.data.SongDescriptor

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
    val lastUpdateTime: Long = 0, // SystemClock.elapsedRealtime(), never wall time
    val speed: Float = 1.0f,
    val lyrics: String? = null,
    val albumArt: android.graphics.Bitmap? = null,
    val providerId: String? = null,
    val positionKnown: Boolean = positionMs >= 0
) {
    val song: SongDescriptor get() = if (packageName in listOf("com.example.sample", "com.example.library"))
        SongDescriptor(title.orEmpty(), artist.orEmpty(), savedId = if (packageName == "com.example.library") providerId else null) else SongDescriptor(title.orEmpty(), artist.orEmpty(),
            album.orEmpty(), durationMs, packageName.orEmpty(), providerId.orEmpty())
    val songId: String get() = if (packageName == "com.example.library" && !providerId.isNullOrBlank()) providerId else song.id
    fun getCurrentPositionMs(nowElapsed: Long = SystemClock.elapsedRealtime()): Long {
        val base = if (positionKnown) positionMs.coerceAtLeast(0) else 0L
        val elapsed = if (positionKnown && isPlaying && lastUpdateTime > 0 && lastUpdateTime <= nowElapsed)
            nowElapsed - lastUpdateTime else 0L
        val rate = speed.takeIf { it.isFinite() && it >= 0 } ?: 0f
        val calculated = (base.toDouble() + elapsed.toDouble() * rate).toLong().coerceAtLeast(0)
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
        albumArt: android.graphics.Bitmap? = null,
        lastPositionUpdateTime: Long = SystemClock.elapsedRealtime(),
        providerId: String? = null
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
            lastUpdateTime = lastPositionUpdateTime,
            speed = speed,
            lyrics = lyrics,
            albumArt = albumArt,
            providerId = providerId
        )
    }

    fun setPlayingState(isPlaying: Boolean) {
        val current = _mediaState.value
        _mediaState.value = current.copy(
            positionMs = current.getCurrentPositionMs(),
            isPlaying = isPlaying,
            lastUpdateTime = SystemClock.elapsedRealtime()
        )
    }

    fun setPlaybackPosition(positionMs: Long) {
        val current = _mediaState.value
        _mediaState.value = current.copy(
            positionMs = positionMs,
            positionKnown = positionMs >= 0,
            lastUpdateTime = SystemClock.elapsedRealtime()
        )
    }

    fun clear() {
        _mediaState.value = MediaState()
    }
}
