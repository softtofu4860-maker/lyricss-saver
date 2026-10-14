package com.example.service.spotify

import android.content.Context
import android.util.Log
import com.example.service.MediaStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Spotify Remote Manager & Metadata Integration.
 * Connects Spotify playback metadata and real-time Spotify track/lyrics retrieval.
 */
object SpotifyRemoteManager {

    private const val TAG = "SpotifyRemoteManager"

    data class SpotifyTrackMeta(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val coverUrl: String? = null
    )

    /**
     * Resolves Spotify track metadata and synced lyrics from Spotify API or open lyrics services.
     */
    suspend fun fetchSpotifyTrackLyrics(
        context: Context,
        trackId: String,
        title: String,
        artist: String
    ): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching Spotify track lyrics for Spotify ID: $trackId, Title: $title, Artist: $artist")
        val query = com.example.api.SongQuery(title, artist, trackId = trackId)
        com.example.api.LyricsMatching.choose(query,
            com.example.api.GeminiLyricsService.searchCandidates(query))?.candidate?.rawLyrics
    }

    /**
     * Trigger real-time sync with current Spotify MediaState
     */
    fun syncWithSpotifyState(
        title: String,
        artist: String,
        album: String?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long
    ) {
        MediaStateHolder.updateState(
            title = title,
            artist = artist,
            album = album,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            packageName = "com.spotify.music"
        )
    }
}
