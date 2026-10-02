package com.example.service

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        albumArt: Bitmap? = null
    ) {
        val oldState = _mediaState.value
        if (oldState.albumArt != null && oldState.albumArt != albumArt) {
            try {
                oldState.albumArt.recycle()
            } catch (_: Exception) {}
        }
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
            isPlaying = isPlaying,
            positionMs = current.getCurrentPositionMs(),
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
        val oldState = _mediaState.value
        if (oldState.albumArt != null) {
            try {
                oldState.albumArt.recycle()
            } catch (_: Exception) {}
        }
        _mediaState.value = MediaState()
    }
}
