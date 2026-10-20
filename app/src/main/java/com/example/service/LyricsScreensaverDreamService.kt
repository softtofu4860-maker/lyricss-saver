package com.example.service

import android.os.Bundle
import android.service.dreams.DreamService
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.example.ui.PerformanceEnvironment
import com.example.ui.LocalPerformanceMode
import com.example.ui.rememberBoundedArtwork
import com.example.ui.AppleMusicScreensaverView
import com.example.ui.LyricsCorrectionDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect

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
                PerformanceEnvironment {
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
    val rawMediaState by MediaStateHolder.mediaState.collectAsState()
    val artwork = rememberBoundedArtwork(rawMediaState.albumArt, LocalPerformanceMode.current.profile.artworkMaxEdge)
    val mediaState = remember(rawMediaState, artwork) { rawMediaState.copy(albumArt = artwork) }
    var lyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var showCorrectionDialog by remember { mutableStateOf(false) }
    val correctionScope = rememberCoroutineScope()
    var correctionJob by remember { mutableStateOf<Job?>(null) }
    var automaticJob by remember { mutableStateOf<Job?>(null) }

    // 곡이 바뀌면 가사 로드
    LaunchedEffect(mediaState.title, mediaState.artist, mediaState.lyrics, mediaState.durationMs) {
        automaticJob = currentCoroutineContext()[Job]
        correctionJob?.cancel()
        lyrics = emptyList()
        val title = mediaState.title
        val artist = mediaState.artist.orEmpty()
        if (!title.isNullOrBlank()) {
            try {
                repository.observeAutomaticLyrics(title, artist, mediaState.lyrics, mediaState.durationMs).collect { cached ->
                    lyrics = GeminiLyricsService.parseJsonLyrics(cached.lyricsJson)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppleMusicScreensaverView(
            mediaState = mediaState,
            lyricsLines = lyrics,
            onCloseClicked = onWakeUp
        )

        if (showCorrectionDialog) {
            LyricsCorrectionDialog(
                title = mediaState.title ?: "",
                artist = mediaState.artist ?: "",
                currentLines = lyrics,
                durationMs = mediaState.durationMs,
                onDismiss = { showCorrectionDialog = false },
                onReSearchRequested = { q ->
                    automaticJob?.cancel()
                    val title = mediaState.title ?: ""
                    val artist = mediaState.artist ?: ""
                    correctionJob?.cancel()
                    correctionJob = correctionScope.launch {
                        try {
                            val cached = repository.getLyrics(
                                title = title,
                                artist = artist,
                                durationMs = mediaState.durationMs,
                                customQuery = q
                            )
                            lyrics = GeminiLyricsService.parseJsonLyrics(cached.lyricsJson)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {}
                    }
                },
                onSaveManualLyrics = { newLines ->
                    automaticJob?.cancel()
                    lyrics = newLines
                    val title = mediaState.title ?: ""
                    val artist = mediaState.artist ?: ""
                    correctionJob?.cancel()
                    correctionJob = correctionScope.launch {
                        repository.saveManualLyrics(title, artist, newLines)
                    }
                }
            )
        }
    }
}
