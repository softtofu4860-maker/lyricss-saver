package com.example.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MusicNotificationListener : NotificationListenerService() {

    private val tag = "MusicNotificationListener"
    private lateinit var mediaSessionManager: MediaSessionManager
    private val activeCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private val handler = Handler(Looper.getMainLooper())

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        handler.post {
            updateSessions(controllers)
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
            val componentName = ComponentName(this, MusicNotificationListener::class.java)
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener, componentName)
            
            // Initial poll
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            updateSessions(controllers)
        } catch (e: Exception) {
            Log.e(tag, "Error setting up active sessions listener", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(tag, "Notification Listener disconnected")
        cleanupCallbacks()
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (e: Exception) {
            Log.e(tag, "Error removing session listener", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupCallbacks()
    }

    private fun cleanupCallbacks() {
        for ((controller, callback) in activeCallbacks) {
            try {
                controller.unregisterCallback(callback)
            } catch (e: Exception) {
                Log.e(tag, "Error unregistering callback", e)
            }
        }
        activeCallbacks.clear()
    }

    private fun updateSessions(controllers: List<MediaController>?) {
        cleanupCallbacks()
        if (controllers.isNullOrEmpty()) {
            Log.d(tag, "No active media sessions found")
            activeController = null
            MediaStateHolder.clear()
            return
        }

        Log.d(tag, "Found ${controllers.size} active sessions")
        
        // Pick the first controller that is currently playing, otherwise pick the first controller in list
        var targetController = controllers.firstOrNull { controller ->
            val state = controller.playbackState
            state != null && state.state == PlaybackState.STATE_PLAYING
        }
        
        if (targetController == null) {
            targetController = controllers.first()
        }

        // Register callback for changes on the target controller
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                Log.d(tag, "Metadata changed on target session")
                handler.post { handleControllerUpdate(targetController) }
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                Log.d(tag, "Playback state changed on target session")
                handler.post { handleControllerUpdate(targetController) }
            }
        }

        try {
            targetController.registerCallback(callback)
            activeCallbacks[targetController] = callback
            handleControllerUpdate(targetController)
        } catch (e: Exception) {
            Log.e(tag, "Error registering controller callback", e)
        }

        // Also register callbacks on all other controllers to intercept if they start playing
        controllers.forEach { controller ->
            if (controller != targetController) {
                val secCallback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        if (state != null && state.state == PlaybackState.STATE_PLAYING) {
                            Log.d(tag, "Secondary session started playing. Re-evaluating.")
                            handler.post { updateSessions(mediaSessionManager.getActiveSessions(ComponentName(this@MusicNotificationListener, MusicNotificationListener::class.java))) }
                        }
                    }
                }
                try {
                    controller.registerCallback(secCallback)
                    activeCallbacks[controller] = secCallback
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    private fun handleControllerUpdate(controller: MediaController) {
        activeController = controller
        val metadata = controller.metadata
        val state = controller.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getText(MediaMetadata.METADATA_KEY_TITLE)?.toString()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getText(MediaMetadata.METADATA_KEY_ARTIST)?.toString()
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
            ?: metadata?.getText(MediaMetadata.METADATA_KEY_ALBUM)?.toString()
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

        val isPlaying = state != null && state.state == PlaybackState.STATE_PLAYING
        val position = state?.position ?: 0L
        val speed = state?.playbackSpeed ?: 1.0f
        val packageName = controller.packageName

        val lyrics = metadata?.getString("android.media.metadata.LYRICS")
            ?: metadata?.getString("lyrics")
            ?: metadata?.getText("android.media.metadata.LYRICS")?.toString()
            ?: metadata?.getText("lyrics")?.toString()

        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        Log.d(tag, "Interception Update - Title: $title, Artist: $artist, Package: $packageName, Playing: $isPlaying, Position: $position/$duration, HasLyricsMetadata: ${!lyrics.isNullOrEmpty()}, HasAlbumArt: ${albumArt != null}")

        MediaStateHolder.updateState(
            title = title,
            artist = artist,
            album = album,
            isPlaying = isPlaying,
            positionMs = position,
            durationMs = duration,
            packageName = packageName,
            speed = speed,
            lyrics = lyrics,
            albumArt = albumArt
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // We can use this to trigger session update check when music apps post/update notifications
        if (sbn?.packageName != null) {
            try {
                val componentName = ComponentName(this, MusicNotificationListener::class.java)
                val controllers = mediaSessionManager.getActiveSessions(componentName)
                updateSessions(controllers)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        try {
            val componentName = ComponentName(this, MusicNotificationListener::class.java)
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            updateSessions(controllers)
        } catch (e: Exception) {
            // Ignore
        }
    }

    companion object {
        @Volatile
        var activeController: MediaController? = null

        fun isNotificationServiceEnabled(context: Context): Boolean {
            val cn = ComponentName(context, MusicNotificationListener::class.java)
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return flat != null && flat.contains(cn.flattenToString())
        }
    }
}
