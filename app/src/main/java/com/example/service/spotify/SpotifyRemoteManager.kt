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
        try {
            // Attempt to query LRCLIB / Spotify public lyrics endpoint for synced lyrics
            val cleanTitle = title.replace(Regex("(?i)\\(.*live.*\\)|\\(.*remastered.*\\)"), "").trim()
            val encodedTitle = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
            val encodedArtist = java.net.URLEncoder.encode(artist, "UTF-8")

            val apiUrl = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist"
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.setRequestProperty("User-Agent", "SpotifyRemoteApp/1.0")

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val syncedLyrics = json.optString("syncedLyrics", null)
                val plainLyrics = json.optString("plainLyrics", null)

                if (!syncedLyrics.isNullOrBlank()) {
                    Log.d(TAG, "Successfully retrieved Spotify synced lyrics!")
                    return@withContext syncedLyrics
                } else if (!plainLyrics.isNullOrBlank()) {
                    Log.d(TAG, "Successfully retrieved Spotify plain lyrics!")
                    return@withContext plainLyrics
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Spotify track lyrics", e)
        }
        return@withContext null
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
