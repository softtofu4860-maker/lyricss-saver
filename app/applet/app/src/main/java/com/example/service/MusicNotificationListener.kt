package com.example.service

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MusicNotificationListener : NotificationListenerService() {

    private val tag = "MusicNotificationListener"
    private lateinit var mediaSessionManager: MediaSessionManager
    private val activeCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private val handler = Handler(Looper.getMainLooper())

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        handler.post {
            updateSessions(list)
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(tag, "Notification Listener connected")
        try {
            val component = ComponentName(this, MusicNotificationListener::class.java)
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener, component)
            val controllers = mediaSessionManager.getActiveSessions(component)
            updateSessions(controllers)
        } catch (e: Exception) {
            Log.e(tag, "Error setting up session listener: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        cleanupCallbacks()
    }

    override fun onDestroy() {
        cleanupCallbacks()
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun cleanupCallbacks() {
        activeCallbacks.forEach { (controller, callback) ->
            try {
                controller.unregisterCallback(callback)
            } catch (_: Exception) {}
        }
        activeCallbacks.clear()
        activeController = null
    }

    private fun updateSessions(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            activeController = null
            return
        }

        val playingController = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull()

        activeController = playingController

        controllers.forEach { controller ->
            if (!activeCallbacks.containsKey(controller)) {
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        handleControllerUpdate(controller)
                    }

                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        handleControllerUpdate(controller)
                    }
                }
                try {
                    controller.registerCallback(callback)
                    activeCallbacks[controller] = callback
                } catch (e: Exception) {
                    Log.e(tag, "Failed to register controller callback: ${e.message}")
                }
            }
        }

        playingController?.let { handleControllerUpdate(it) }
    }

    private fun handleControllerUpdate(controller: MediaController) {
        val metadata = controller.metadata
        val playbackState = controller.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)

        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
        val position = playbackState?.position ?: 0L
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val speed = playbackState?.playbackSpeed ?: 1.0f

        val artBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        if (!title.isNullOrBlank() || !artist.isNullOrBlank()) {
            MediaStateHolder.updateState(
                title = title,
                artist = artist,
                album = album,
                isPlaying = isPlaying,
                positionMs = position,
                durationMs = duration,
                packageName = controller.packageName,
                speed = speed,
                albumArt = artBitmap
            )
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    companion object {
        @Volatile
        var activeController: MediaController? = null

        fun isNotificationServiceEnabled(context: Context): Boolean {
            val cn = ComponentName(context, MusicNotificationListener::class.java)
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            return flat.contains(cn.flattenToString())
        }
    }
}
