package com.example.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import com.example.diagnostics.DiagnosticStage
import com.example.diagnostics.LyricsDiagnostics

/** The OS-authorized MediaSession list is the sole external playback authority. */
class MusicNotificationListener : NotificationListenerService() {
    private lateinit var manager: MediaSessionManager
    private val handler = Handler(Looper.getMainLooper())
    private data class Entry(val controller: MediaController, val callback: MediaController.Callback)
    private val entries = linkedMapOf<MediaSession.Token, Entry>()
    private var connected = false
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        if (connected) updateSessions(controllers.orEmpty())
    }

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }
    override fun onListenerConnected() {
        super.onListenerConnected()
        if (connected) return
        connected = true
        try {
            manager.addOnActiveSessionsChangedListener(listener, ComponentName(this, MusicNotificationListener::class.java), handler)
            updateSessions(manager.getActiveSessions(ComponentName(this, MusicNotificationListener::class.java)))
        } catch (error: Exception) { failure(error); cleanup() }
    }
    override fun onListenerDisconnected() { cleanup(); super.onListenerDisconnected() }
    override fun onDestroy() { cleanup(); super.onDestroy() }

    private fun failure(error: Exception) {
        LyricsDiagnostics.record(DiagnosticStage.FAILURE, "media_session", fields = mapOf("error" to error.javaClass.simpleName))
    }
    private fun cleanup() {
        connected = false
        handler.removeCallbacksAndMessages(null)
        runCatching { manager.removeOnActiveSessionsChangedListener(listener) }.onFailure { failure(it as? Exception ?: return@onFailure) }
        entries.values.forEach { entry ->
            runCatching { entry.controller.unregisterCallback(entry.callback) }.onFailure { failure(it as? Exception ?: return@onFailure) }
        }
        entries.clear()
        activeController = null
        MediaStateHolder.clear()
    }
    private fun updateSessions(controllers: List<MediaController>) {
        if (!connected) return
        val tokens = controllers.map { it.sessionToken }.toSet()
        entries.keys.filter { it !in tokens }.forEach { token ->
            entries.remove(token)?.let { entry ->
                runCatching { entry.controller.unregisterCallback(entry.callback) }.onFailure { failure(it as Exception) }
            }
        }
        controllers.forEach { controller ->
            val token = controller.sessionToken
            if (token !in entries) {
                val callback = object : MediaController.Callback() {
                    private fun refresh() {
                        if (connected && entries[token]?.callback === this) publishSelected()
                    }
                    override fun onMetadataChanged(metadata: MediaMetadata?) { refresh() }
                    override fun onPlaybackStateChanged(state: PlaybackState?) { refresh() }
                    override fun onSessionDestroyed() {
                        if (connected && entries[token]?.callback === this) {
                            entries.remove(token)?.let {
                                runCatching { controller.unregisterCallback(this) }.onFailure { failure(it as? Exception ?: return@onFailure) }
                            }
                            publishSelected()
                        }
                    }
                }
                try {
                    entries[token] = Entry(controller, callback)
                    controller.registerCallback(callback, handler)
                } catch (error: Exception) { entries.remove(token); failure(error) }
            }
        }
        publishSelected()
    }
    private fun publishSelected() {
        if (!connected) return
        try {
            val controllers = entries.values.map { it.controller }
            val candidates = controllers.map { controller -> SessionCandidate(
                key = controller.sessionToken.toString(),
                playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
                updateTime = controller.playbackState?.lastPositionUpdateTime ?: 0,
                hasTitle = !controller.metadata?.getText(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() ||
                    !controller.metadata?.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).isNullOrBlank() ||
                    (controller.metadata == null && controller.sessionToken == activeController?.sessionToken &&
                        !MediaStateHolder.mediaState.value.title.isNullOrBlank())
            ) }
            val key = SessionSelection.choose(candidates, activeController?.sessionToken?.toString())
            val selected = controllers.firstOrNull { it.sessionToken.toString() == key }
            if (selected == null) { activeController = null; MediaStateHolder.clear(); return }
            val previous = MediaStateHolder.mediaState.value
            val sameSession = activeController?.sessionToken == selected.sessionToken
            val metadata = selected.metadata
            val playback = selected.playbackState
            fun text(vararg keys: String): String? = keys.firstNotNullOfOrNull {
                metadata?.getText(it)?.toString()?.takeIf(String::isNotBlank)
            }
            // Missing playback-only metadata is preserved only within the same trusted session.
            val retain = metadata == null && sameSession
            val incomingTitle = text(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            val incomingId = text(MediaMetadata.METADATA_KEY_MEDIA_ID)
            val sameTrack = sameSession && (retain ||
                (!incomingId.isNullOrBlank() && incomingId == previous.providerId) ||
                (incomingId.isNullOrBlank() && previous.providerId.isNullOrBlank() && incomingTitle != null && incomingTitle == previous.title))
            activeController = selected
            MediaStateHolder.updateState(
                title = if (retain) previous.title else text(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                artist = text(MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST, MediaMetadata.METADATA_KEY_AUTHOR) ?: previous.artist.takeIf { sameTrack },
                album = text(MediaMetadata.METADATA_KEY_ALBUM) ?: previous.album.takeIf { sameTrack },
                isPlaying = playback?.state == PlaybackState.STATE_PLAYING,
                positionMs = playback?.position ?: -1,
                durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 } ?: previous.durationMs.takeIf { sameTrack } ?: 0,
                packageName = selected.packageName,
                speed = playback?.playbackSpeed ?: 0f,
                lyrics = text("android.media.metadata.LYRICS", "lyrics") ?: previous.lyrics.takeIf { sameTrack },
                albumArt = if (retain) previous.albumArt else metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART),
                lastPositionUpdateTime = playback?.lastPositionUpdateTime ?: 0,
                providerId = if (retain) previous.providerId else text(MediaMetadata.METADATA_KEY_MEDIA_ID)
            )
        } catch (error: SecurityException) { failure(error); cleanup() }
        catch (error: Exception) { failure(error) }
    }

    companion object {
        @Volatile var activeController: MediaController? = null
            private set
        fun isNotificationServiceEnabled(context: Context): Boolean {
            val expected = ComponentName(context, MusicNotificationListener::class.java)
            val enabled = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return enabled?.split(':')?.any { ComponentName.unflattenFromString(it) == expected } == true
        }
    }
}
