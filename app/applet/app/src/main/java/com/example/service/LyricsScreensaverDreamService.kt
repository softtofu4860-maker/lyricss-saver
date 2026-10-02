package com.example.service

import android.content.Context
import android.os.Bundle
import android.service.dreams.DreamService
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.api.GeminiLyricsService
import com.example.data.LyricLine
import com.example.data.LyricsRepository
import com.example.ui.components.AppleMusicLandscapeScreensaver
import com.example.ui.components.LyricsCorrectionDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

class LyricsScreensaverDreamService : DreamService(),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStoreInstance = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val spotifyReceiver = SpotifyBroadcastReceiver()
    private lateinit var repository: LyricsRepository

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = viewModelStoreInstance
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(Bundle())
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        repository = LyricsRepository(this)
        SpotifyBroadcastReceiver.register(this, spotifyReceiver)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = true

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val composeView = ComposeView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setViewTreeLifecycleOwner(this@LyricsScreensaverDreamService)
            setViewTreeViewModelStoreOwner(this@LyricsScreensaverDreamService)
            setViewTreeSavedStateRegistryOwner(this@LyricsScreensaverDreamService)

            setContent {
                DreamScreensaverScreen(
                    repository = repository,
                    onWakeUp = {
                        try {
                            wakeUp()
                        } catch (_: Exception) {}
                    }
                )
            }
        }

        setContentView(composeView)
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDetachedFromWindow()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStoreInstance.clear()
        serviceScope.cancel()
        SpotifyBroadcastReceiver.unregister(this, spotifyReceiver)
        super.onDestroy()
    }
}

@Composable
private fun DreamScreensaverScreen(
    repository: LyricsRepository,
    onWakeUp: () -> Unit
) {
    val mediaState by MediaStateHolder.mediaState.collectAsState()
    var lyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var auraColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    var showCorrectionDialog by remember { mutableStateOf(false) }

    // 재생 시간 주기적 갱신
    var ticker by remember { mutableStateOf(0L) }
    LaunchedEffect(mediaState.isPlaying) {
        while (mediaState.isPlaying) {
            delay(250)
            ticker = System.currentTimeMillis()
        }
    }

    // 곡이 바뀌면 가사 로드
    LaunchedEffect(mediaState.title, mediaState.artist) {
        val title = mediaState.title
        val artist = mediaState.artist
        if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
            try {
                val cached = repository.getLyrics(title, artist, durationMs = mediaState.durationMs)
                lyrics = GeminiLyricsService.parseJsonLyrics(cached.lyricsJson)
                auraColors = parseAuraColors(cached.hexColorsJson)
            } catch (_: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppleMusicLandscapeScreensaver(
            mediaState = mediaState,
            lyrics = lyrics,
            auraColors = auraColors,
            onSeekTo = { pos ->
                MediaStateHolder.setPlaybackPosition(pos)
                MusicNotificationListener.activeController?.transportControls?.seekTo(pos)
            },
            onTogglePlayPause = {
                val controller = MusicNotificationListener.activeController
                if (mediaState.isPlaying) {
                    controller?.transportControls?.pause()
                    MediaStateHolder.setPlayingState(false)
                } else {
                    controller?.transportControls?.play()
                    MediaStateHolder.setPlayingState(true)
                }
            },
            onSkipToNext = {
                MusicNotificationListener.activeController?.transportControls?.skipToNext()
            },
            onSkipToPrevious = {
                MusicNotificationListener.activeController?.transportControls?.skipToPrevious()
            },
            onOpenCorrectionDialog = {
                showCorrectionDialog = true
            },
            onClose = onWakeUp
        )

        if (showCorrectionDialog) {
            LyricsCorrectionDialog(
                initialTitle = mediaState.title ?: "",
                initialArtist = mediaState.artist ?: "",
                onDismiss = { showCorrectionDialog = false },
                onSearchWithQuery = { q ->
                    // 수동 검색
                    val title = mediaState.title ?: ""
                    val artist = mediaState.artist ?: ""
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val cached = repository.getLyrics(
                                title = title,
                                artist = artist,
                                durationMs = mediaState.durationMs,
                                customQuery = q
                            )
                            lyrics = GeminiLyricsService.parseJsonLyrics(cached.lyricsJson)
                            auraColors = parseAuraColors(cached.hexColorsJson)
                        } catch (_: Exception) {}
                    }
                },
                onSaveCustomLrc = { lrc ->
                    val title = mediaState.title ?: ""
                    val artist = mediaState.artist ?: ""
                    val parsed = GeminiLyricsService.parseLrcLyrics(lrc, mediaState.durationMs)
                    lyrics = parsed
                }
            )
        }
    }
}

private fun parseAuraColors(hexJson: String): List<Color> {
    return try {
        val array = JSONArray(hexJson)
        val list = mutableListOf<Color>()
        for (i in 0 until array.length()) {
            val hex = array.getString(i).replace("#", "")
            val colorInt = hex.toLong(16)
            val finalColor = if (hex.length == 6) {
                Color(0xFF000000 or colorInt)
            } else {
                Color(colorInt)
            }
            list.add(finalColor)
        }
        if (list.isEmpty()) getDefaultColors() else list
    } catch (_: Exception) {
        getDefaultColors()
    }
}

private fun getDefaultColors(): List<Color> {
    return listOf(
        Color(0xFF1E1B4B),
        Color(0xFF312E81),
        Color(0xFF0F172A),
        Color(0xFF4C1D95)
    )
}
