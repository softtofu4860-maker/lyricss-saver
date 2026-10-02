package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

class SpotifyBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Spotify Broadcast Received: $action")

        if (action == SPOTIFY_ACTION_METADATA_CHANGED || action == SPOTIFY_ACTION_PLAYBACK_STATE_CHANGED) {
            val track = intent.getStringExtra("track") ?: ""
            val artist = intent.getStringExtra("artist") ?: ""
            val album = intent.getStringExtra("album") ?: ""
            val length = intent.getIntExtra("length", 0)
            val playing = intent.getBooleanExtra("playing", false)
            val position = intent.getIntExtra("playbackPosition", 0)

            if (track.isNotBlank() || artist.isNotBlank()) {
                MediaStateHolder.updateState(
                    title = track.ifBlank { null },
                    artist = artist.ifBlank { null },
                    album = album.ifBlank { null },
                    isPlaying = playing,
                    positionMs = position.toLong(),
                    durationMs = length.toLong(),
                    packageName = "com.spotify.music"
                )
            }
        }
    }

    companion object {
        const val SPOTIFY_ACTION_METADATA_CHANGED = "com.spotify.music.metadatachanged"
        const val SPOTIFY_ACTION_PLAYBACK_STATE_CHANGED = "com.spotify.music.playbackstatechanged"
        const val SPOTIFY_ACTION_QUEUE_CHANGED = "com.spotify.music.queuechanged"
        private const val TAG = "SpotifyRemoteReceiver"

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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register receiver: ${e.message}")
            }
        }

        fun unregister(context: Context, receiver: SpotifyBroadcastReceiver) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister receiver: ${e.message}")
            }
        }
    }
}
