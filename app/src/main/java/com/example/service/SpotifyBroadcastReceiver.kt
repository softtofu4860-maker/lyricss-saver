package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

/**
 * Spotify Broadcast Receiver & Remote Integration Service.
 * Intercepts Spotify's real-time playback and track metadata broadcasts:
 * - "com.spotify.music.metadatachanged"
 * - "com.spotify.music.playbackstatechanged"
 * - "com.spotify.music.queuechanged"
 */
class SpotifyBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Spotify Broadcast Received: $action")

        when (action) {
            SPOTIFY_ACTION_METADATA_CHANGED,
            SPOTIFY_ACTION_PLAYBACK_STATE_CHANGED -> {
                val trackId = intent.getStringExtra("id") ?: ""
                val artist = intent.getStringExtra("artist") ?: ""
                val album = intent.getStringExtra("album") ?: ""
                val track = intent.getStringExtra("track") ?: ""
                val length = intent.getIntExtra("length", 0)
                val playing = intent.getBooleanExtra("playing", false)
                val position = intent.getIntExtra("playbackPosition", 0)

                Log.d(
                    TAG,
                    "Spotify Track Real-time Update -> ID: $trackId, Track: $track, Artist: $artist, Album: $album, Playing: $playing, Pos: $position/$length"
                )

                if (track.isNotBlank() || artist.isNotBlank()) {
                    MediaStateHolder.updateState(
                        title = if (track.isNotBlank()) track else null,
                        artist = if (artist.isNotBlank()) artist else null,
                        album = if (album.isNotBlank()) album else null,
                        isPlaying = playing,
                        positionMs = position.toLong(),
                        durationMs = length.toLong(),
                        packageName = "com.spotify.music"
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "SpotifyRemoteReceiver"
        const val SPOTIFY_ACTION_METADATA_CHANGED = "com.spotify.music.metadatachanged"
        const val SPOTIFY_ACTION_PLAYBACK_STATE_CHANGED = "com.spotify.music.playbackstatechanged"
        const val SPOTIFY_ACTION_QUEUE_CHANGED = "com.spotify.music.queuechanged"

        fun register(context: Context, receiver: SpotifyBroadcastReceiver) {
            val filter = IntentFilter().apply {
                addAction(SPOTIFY_ACTION_METADATA_CHANGED)
                addAction(SPOTIFY_ACTION_PLAYBACK_STATE_CHANGED)
                addAction(SPOTIFY_ACTION_QUEUE_CHANGED)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(receiver, filter)
                }
                Log.d(TAG, "Spotify Broadcast Receiver successfully registered dynamically.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register Spotify receiver dynamically", e)
            }
        }

        fun unregister(context: Context, receiver: SpotifyBroadcastReceiver) {
            try {
                context.unregisterReceiver(receiver)
                Log.d(TAG, "Spotify Broadcast Receiver unregistered.")
            } catch (e: Exception) {
                // Ignore if already unregistered
            }
        }
    }
}
