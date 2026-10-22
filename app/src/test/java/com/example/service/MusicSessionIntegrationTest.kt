package com.example.service

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Looper
import android.os.Binder
import java.lang.reflect.Proxy
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class MusicSessionIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    // Robolectric's session-manager stub returns null binders. Supply distinct fake OS tokens.
    private fun distinctToken(controller: MediaController) {
        val token = controller.sessionToken
        val field = token.javaClass.declaredFields.first { it.type.name == "android.media.session.ISessionController" }
        val binder = Binder()
        field.isAccessible = true
        field.set(token, Proxy.newProxyInstance(field.type.classLoader, arrayOf(field.type)) { _, method, _ ->
            if (method.name == "asBinder") binder else null
        })
    }
    private fun metadata(title: String, artist: String? = "artist") = MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, title)
        .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
        .putLong(MediaMetadata.METADATA_KEY_DURATION, 180000).build()
    private fun playback(state: Int, position: Long, time: Long, speed: Float = 1f) = PlaybackState.Builder()
        .setState(state, position, speed, time).build()

    @Test fun `trusted session snapshots handle mid-song start pause resume seek and metadata-free state events`() {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val session = MediaSession(context, "test")
        val controller = session.controller
        shadowOf(controller).setMetadata(metadata("one"))
        shadowOf(controller).setPlaybackState(playback(PlaybackState.STATE_PLAYING, 60000, 1000))
        shadowOf(manager).addController(controller)
        val service = Robolectric.buildService(MusicNotificationListener::class.java).create()
        try {
            service.get().onListenerConnected()
            assertEquals(64000, MediaStateHolder.mediaState.value.getCurrentPositionMs(5000))
            val callbacks = shadowOf(controller).callbacks.size
            service.get().onNotificationPosted(null)
            service.get().onNotificationRemoved(null)
            assertEquals(callbacks, shadowOf(controller).callbacks.size)
            shadowOf(controller).executeOnPlaybackStateChanged(playback(PlaybackState.STATE_PAUSED, 65000, 6000))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("one", MediaStateHolder.mediaState.value.title)
            assertEquals(65000, MediaStateHolder.mediaState.value.getCurrentPositionMs(20000))
            shadowOf(controller).executeOnPlaybackStateChanged(playback(PlaybackState.STATE_PLAYING, 10000, 20000, 2f))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(12000, MediaStateHolder.mediaState.value.getCurrentPositionMs(21000))
            shadowOf(controller).executeOnPlaybackStateChanged(playback(PlaybackState.STATE_BUFFERING, 12000, 21000))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(12000, MediaStateHolder.mediaState.value.getCurrentPositionMs(25000))
        } finally { service.destroy(); session.release(); shadowOf(manager).clearControllers() }
    }

    @Test fun `app switching late callbacks and permission reconnection keep one authoritative snapshot`() {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val first = MediaSession(context, "spotify")
        val second = MediaSession(context, "other")
        val a = first.controller
        val b = second.controller
        distinctToken(a)
        distinctToken(b)
        assertNotEquals(a.sessionToken, b.sessionToken)
        shadowOf(a).setPackageName("com.spotify.music")
        shadowOf(b).setPackageName("other.player")
        shadowOf(a).setMetadata(metadata("Spotify song"))
        shadowOf(a).setPlaybackState(playback(PlaybackState.STATE_PLAYING, 50000, 1000))
        shadowOf(b).setMetadata(metadata("Other song", null))
        shadowOf(b).setPlaybackState(playback(PlaybackState.STATE_PLAYING, 8000, 5000))
        shadowOf(manager).addController(a)
        val service = Robolectric.buildService(MusicNotificationListener::class.java).create()
        try {
            service.get().onListenerConnected()
            assertEquals("Spotify song", MediaStateHolder.mediaState.value.title)
            shadowOf(manager).addController(b)
            assertEquals("Other song", MediaStateHolder.mediaState.value.title)
            assertNull(MediaStateHolder.mediaState.value.artist)
            shadowOf(a).executeOnPlaybackStateChanged(playback(PlaybackState.STATE_PLAYING, 55000, 2000))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Other song", MediaStateHolder.mediaState.value.title)
            assertEquals(8000, MediaStateHolder.mediaState.value.positionMs)
            assertEquals(1, shadowOf(a).callbacks.size)
            service.get().onListenerDisconnected()
            assertTrue(shadowOf(a).callbacks.isEmpty())
            assertTrue(shadowOf(b).callbacks.isEmpty())
            assertNull(MusicNotificationListener.activeController)
            assertNull(MediaStateHolder.mediaState.value.title)
            service.get().onListenerConnected()
            assertEquals("Other song", MediaStateHolder.mediaState.value.title)
            assertEquals(1, shadowOf(b).callbacks.size)
        } finally { service.destroy(); first.release(); second.release(); shadowOf(manager).clearControllers() }
    }
}
