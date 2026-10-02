package com.example.service

import android.graphics.Bitmap

data class MediaState(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val packageName: String? = null,
    val lastUpdateTime: Long = 0L,
    val speed: Float = 1.0f,
    val lyrics: String? = null,
    val albumArt: Bitmap? = null
) {
    fun getCurrentPositionMs(): Long {
        if (!isPlaying) return positionMs
        val elapsed = System.currentTimeMillis() - lastUpdateTime
        val calculated = positionMs + (elapsed * speed).toLong()
        return if (durationMs > 0) calculated.coerceAtMost(durationMs) else calculated
    }
}
