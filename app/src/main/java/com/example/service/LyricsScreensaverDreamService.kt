package com.example.service

import android.content.Context
import android.service.dreams.DreamService
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
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
import com.example.data.LyricLine
import com.example.data.LyricsRepository
import com.example.ui.ImmersiveScreensaverView
import com.example.ui.VisualizerMode
import com.example.ui.theme.MyApplicationTheme
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*

class LyricsScreensaverDreamService : DreamService() {

    private val tag = "LyricsDreamService"
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var repository: LyricsRepository
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    // ViewTree owners to make Jetpack Compose work in non-Activity window
    private val lifecycleOwner = SimpleLifecycleOwner()
    private val savedStateRegistryOwner = SimpleSavedStateRegistryOwner(lifecycleOwner)
    private val viewModelStoreOwner = SimpleViewModelStoreOwner()

    private val spotifyReceiver = SpotifyBroadcastReceiver()

    override fun onCreate() {
        super.onCreate()
        repository = LyricsRepository(applicationContext)
        lifecycleOwner.onCreate()
        SpotifyBroadcastReceiver.register(this, spotifyReceiver)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        
        // Daydream screensaver configuration
        isInteractive = true
        isFullscreen = true
        
        // Automatically lower screen brightness in screensaver mode to save battery and prevent OLED burn-in
        try {
            window?.attributes = window?.attributes?.apply {
                screenBrightness = 0.20f // Low brightness (20%) for screensaver mode
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to set screen brightness", e)
        }
        
        lifecycleOwner.onStart()
        lifecycleOwner.onResume()

        val composeView = ComposeView(this).apply {
            setContent {
                MyApplicationTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF06060F)
                    ) {
                        ScreensaverContent()
                    }
                }
            }
        }

        // Hook up view tree lifecycle/savedstate/viewmodel store owners
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        composeView.setViewTreeViewModelStoreOwner(viewModelStoreOwner)

        setContentView(composeView)
    }

    override fun onDetachedFromWindow() {
        lifecycleOwner.onPause()
        lifecycleOwner.onStop()
        super.onDetachedFromWindow()
    }

    override fun onDestroy() {
        lifecycleOwner.onDestroy()
        viewModelStoreOwner.clear()
        serviceScope.cancel()
        SpotifyBroadcastReceiver.unregister(this, spotifyReceiver)
        super.onDestroy()
    }

    @Composable
    private fun ScreensaverContent() {
        val mediaState by MediaStateHolder.mediaState.collectAsState()
        
        var parsedLines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
        var vibeColors by remember { mutableStateOf<List<Color>>(listOf(Color(0xFF00FFFF), Color(0xFF8A2BE2))) }
        var bpm by remember { mutableStateOf(100) }
        var visualizerMode by remember { mutableStateOf(VisualizerMode.NEBULA_RING) }

        // React to song changes to fetch the synced lyrics
        LaunchedEffect(mediaState.title, mediaState.artist) {
            val title = mediaState.title
            val artist = mediaState.artist
            
            if (!title.isNullOrEmpty() && !artist.isNullOrEmpty()) {
                try {
                    Log.d(tag, "Screensaver fetching lyrics for: $title by $artist")
                    val lyricsData = withContext(Dispatchers.IO) {
                        repository.getLyrics(title, artist, mediaState.lyrics, mediaState.durationMs)
                    }
                    
                    // Parse lines
                    val typeLines = Types.newParameterizedType(List::class.java, LyricLine::class.java)
                    val linesAdapter = moshi.adapter<List<LyricLine>>(typeLines)
                    parsedLines = linesAdapter.fromJson(lyricsData.lyricsJson) ?: emptyList()

                    // Parse colors
                    val typeColors = Types.newParameterizedType(List::class.java, String::class.java)
                    val colorsAdapter = moshi.adapter<List<String>>(typeColors)
                    val colorsList = colorsAdapter.fromJson(lyricsData.hexColorsJson) ?: listOf("#00FFFF", "#8A2BE2")
                    vibeColors = colorsList.map { parseHexColor(it) }
                    
                    bpm = lyricsData.bpm
                } catch (e: Exception) {
                    Log.e(tag, "Error loading lyrics in screensaver", e)
                    // Generate fallback
                    val songId = com.example.api.GeminiLyricsService.generateSongId(title, artist)
                    val fallback = com.example.api.GeminiLyricsService.generateSmartFallback(songId, title, artist)
                    
                    val typeLines = Types.newParameterizedType(List::class.java, LyricLine::class.java)
                    val linesAdapter = moshi.adapter<List<LyricLine>>(typeLines)
                    parsedLines = try { linesAdapter.fromJson(fallback.lyricsJson) ?: emptyList() } catch (ex: Exception) { emptyList() }
                    
                    val typeColors = Types.newParameterizedType(List::class.java, String::class.java)
                    val colorsAdapter = moshi.adapter<List<String>>(typeColors)
                    val hexColors = try { colorsAdapter.fromJson(fallback.hexColorsJson) ?: listOf("#00FFFF", "#8A2BE2") } catch (ex: Exception) { listOf("#00FFFF", "#8A2BE2") }
                    vibeColors = hexColors.map { parseHexColor(it) }
                    
                    bpm = 100
                }
            } else {
                parsedLines = emptyList()
                vibeColors = listOf(Color(0xFF00FFFF), Color(0xFF8A2BE2))
                bpm = 100
            }
        }

        ImmersiveScreensaverView(
            mediaState = mediaState,
            lyricsLines = parsedLines,
            vibeColors = vibeColors,
            bpm = bpm,
            visualizerMode = visualizerMode,
            onCloseClicked = {
                // To dismiss screensaver, standard way is calling wakeUp() or finish()
                try {
                    wakeUp()
                } catch (e: Exception) {
                    // Safe wakeup
                }
            },
            onModeChanged = { visualizerMode = it }
        )
    }

    private fun parseHexColor(hex: String): Color {
        return try {
            val cleanHex = hex.trim().replace("#", "")
            val longVal = cleanHex.toLong(16)
            if (cleanHex.length == 6) {
                Color(0xFF000000 or longVal)
            } else if (cleanHex.length == 8) {
                Color(longVal)
            } else {
                Color(0xFF00FFFF)
            }
        } catch (e: Exception) {
            Color(0xFF00FFFF)
        }
    }
}

// Helper owners for Compose in Service
private class SimpleLifecycleOwner : LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    fun onCreate() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }
    fun onStart() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }
    fun onResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    fun onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }
    fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }
    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

private class SimpleSavedStateRegistryOwner(
    private val lifecycleOwner: LifecycleOwner
) : SavedStateRegistryOwner {
    private val controller = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
    override val lifecycle: Lifecycle get() = lifecycleOwner.lifecycle

    init {
        controller.performRestore(null)
    }
}

private class SimpleViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    fun clear() {
        viewModelStore.clear()
    }
}
