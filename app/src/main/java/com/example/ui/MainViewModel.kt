package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CachedLyrics
import com.example.data.LyricLine
import com.example.data.LyricsRepository
import com.example.service.MediaStateHolder
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface LyricsUiState {
    object Empty : LyricsUiState
    object Loading : LyricsUiState
    data class Success(
        val cachedLyrics: CachedLyrics,
        val parsedLines: List<LyricLine>,
        val vibeColors: List<String>
    ) : LyricsUiState
    data class Error(val message: String) : LyricsUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "MainViewModel"
    private val repository = LyricsRepository(application)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val _lyricsState = MutableStateFlow<LyricsUiState>(LyricsUiState.Empty)
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()

    val mediaState = MediaStateHolder.mediaState

    val savedLyrics: StateFlow<List<CachedLyrics>> = repository.getAllSavedLyrics()
        .map { list ->
            list.filter { !com.example.api.GeminiLyricsService.isFallbackLyrics(it) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var lastLoadedSongKey = ""
    private var lastMetadataLyrics: String? = null

    init {
        // Automatically fetch lyrics when intercepted song changes or metadata lyrics become available
        viewModelScope.launch {
            mediaState.collect { state ->
                val title = state.title
                val artist = state.artist
                val metadataLyrics = state.lyrics
                if (!title.isNullOrEmpty() && !artist.isNullOrEmpty()) {
                    val songKey = "${artist.lowercase()}_${title.lowercase()}"
                    if (songKey != lastLoadedSongKey || (metadataLyrics != null && lastMetadataLyrics == null)) {
                        lastLoadedSongKey = songKey
                        lastMetadataLyrics = metadataLyrics
                        loadLyrics(title, artist, metadataLyrics, state.durationMs)
                    }
                } else {
                    _lyricsState.value = LyricsUiState.Empty
                    lastLoadedSongKey = ""
                    lastMetadataLyrics = null
                }
            }
        }
    }

    fun loadLyrics(title: String, artist: String, metadataLyrics: String? = null, durationMs: Long = 0, customQuery: String? = null) {
        viewModelScope.launch {
            _lyricsState.value = LyricsUiState.Loading
            try {
                Log.d(tag, "Fetching lyrics in ViewModel for: $title by $artist, customQuery: $customQuery")
                val lyricsData = repository.getLyrics(title, artist, metadataLyrics, durationMs, customQuery)
                
                // Parse lines JSON
                val typeLines = Types.newParameterizedType(List::class.java, LyricLine::class.java)
                val linesAdapter = moshi.adapter<List<LyricLine>>(typeLines)
                val lines = linesAdapter.fromJson(lyricsData.lyricsJson) ?: emptyList()

                // Parse colors JSON
                val typeColors = Types.newParameterizedType(List::class.java, String::class.java)
                val colorsAdapter = moshi.adapter<List<String>>(typeColors)
                val colors = colorsAdapter.fromJson(lyricsData.hexColorsJson) ?: listOf("#00FFFF", "#8A2BE2")

                _lyricsState.value = LyricsUiState.Success(lyricsData, lines, colors)
            } catch (e: Exception) {
                Log.e(tag, "Error loading lyrics in ViewModel", e)
                _lyricsState.value = LyricsUiState.Error("Error: ${e.message}")
            }
        }
    }

    fun saveManualLyrics(title: String, artist: String, editedLines: List<LyricLine>) {
        viewModelScope.launch {
            try {
                val songId = com.example.api.GeminiLyricsService.generateSongId(title, artist)
                val database = com.example.data.LyricsDatabase.getDatabase(getApplication())
                val dao = database.lyricsDao()
                
                // Retrieve existing entry to preserve other fields like bpm, colors, genre
                val existing = dao.getLyricsById(songId)
                
                val typeLines = Types.newParameterizedType(List::class.java, LyricLine::class.java)
                val linesAdapter = moshi.adapter<List<LyricLine>>(typeLines)
                val newLyricsJson = linesAdapter.toJson(editedLines)
                
                val updatedLyrics = if (existing != null) {
                    existing.copy(
                        lyricsJson = newLyricsJson,
                        timestamp = System.currentTimeMillis()
                    )
                } else {
                    val colorsAdapter = moshi.adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))
                    val defaultColors = listOf("#00FFFF", "#8A2BE2")
                    CachedLyrics(
                        id = songId,
                        title = title,
                        artist = artist,
                        lyricsJson = newLyricsJson,
                        bpm = 100,
                        hexColorsJson = colorsAdapter.toJson(defaultColors),
                        genre = "Manual Edit",
                        timestamp = System.currentTimeMillis()
                    )
                }
                
                dao.insertLyrics(updatedLyrics)
                
                // Update active state
                val colorsAdapter = moshi.adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))
                val colors = try { colorsAdapter.fromJson(updatedLyrics.hexColorsJson) ?: listOf("#00FFFF", "#8A2BE2") } catch (e: Exception) { listOf("#00FFFF", "#8A2BE2") }
                
                _lyricsState.value = LyricsUiState.Success(updatedLyrics, editedLines, colors)
            } catch (e: Exception) {
                Log.e(tag, "Error saving manual lyrics", e)
            }
        }
    }

    fun clearDatabaseCache() {
        viewModelScope.launch {
            repository.clearCache()
            // Force reload current song if active
            val current = mediaState.value
            if (!current.title.isNullOrEmpty() && !current.artist.isNullOrEmpty()) {
                loadLyrics(current.title, current.artist, current.lyrics, current.durationMs)
            } else {
                _lyricsState.value = LyricsUiState.Empty
            }
        }
    }

    fun deleteSavedSong(songId: String) {
        viewModelScope.launch {
            repository.deleteLyrics(songId)
        }
    }

    fun loadLyricsFromLibrary(cachedLyrics: CachedLyrics) {
        viewModelScope.launch {
            _lyricsState.value = LyricsUiState.Loading
            try {
                // Parse lines JSON
                val typeLines = Types.newParameterizedType(List::class.java, LyricLine::class.java)
                val linesAdapter = moshi.adapter<List<LyricLine>>(typeLines)
                val lines = linesAdapter.fromJson(cachedLyrics.lyricsJson) ?: emptyList()

                // Parse colors JSON
                val typeColors = Types.newParameterizedType(List::class.java, String::class.java)
                val colorsAdapter = moshi.adapter<List<String>>(typeColors)
                val colors = colorsAdapter.fromJson(cachedLyrics.hexColorsJson) ?: listOf("#00FFFF", "#8A2BE2")

                _lyricsState.value = LyricsUiState.Success(cachedLyrics, lines, colors)
                
                // Update mediaState so the player card matches this selected song!
                com.example.service.MediaStateHolder.updateState(
                    title = cachedLyrics.title,
                    artist = cachedLyrics.artist,
                    album = "저장된 라이브러리",
                    isPlaying = false,
                    positionMs = 0L,
                    durationMs = if (lines.isNotEmpty()) (lines.last().timeSec * 1000).toLong() + 5000L else 180000L,
                    packageName = "com.example.library", // Indicate loaded from library
                    lyrics = null
                )
            } catch (e: Exception) {
                Log.e(tag, "Error loading lyrics from library", e)
                _lyricsState.value = LyricsUiState.Error("Error: ${e.message}")
            }
        }
    }

    fun translateActiveLyrics() {
        val currentState = _lyricsState.value
        if (currentState !is LyricsUiState.Success) return

        val originalLyrics = currentState.cachedLyrics
        val originalLines = currentState.parsedLines
        val originalColors = currentState.vibeColors

        // Convert parsedLines to raw LRC format
        val rawLrcText = originalLines.joinToString("\n") { line ->
            val totalSec = line.timeSec
            val min = (totalSec / 60).toInt()
            val sec = (totalSec % 60).toInt()
            val ms = ((totalSec % 1) * 100).toInt()
            java.util.Locale.US.let { locale ->
                String.format(locale, "[%02d:%02d.%02d] %s", min, sec, ms, line.text)
            }
        }

        viewModelScope.launch {
            _lyricsState.value = LyricsUiState.Loading
            try {
                val translatedRaw = com.example.api.GeminiLyricsService.translateLyricsViaGemini(rawLrcText, getApplication())
                val parsedLines = com.example.api.GeminiLyricsService.parseLrcLyrics(translatedRaw, 0L)
                
                if (parsedLines.isNotEmpty()) {
                    val title = originalLyrics.title
                    val artist = originalLyrics.artist
                    saveManualLyrics(title, artist, parsedLines)
                } else {
                    _lyricsState.value = LyricsUiState.Success(originalLyrics, originalLines, originalColors)
                }
            } catch (e: Exception) {
                Log.e(tag, "Gemini translation failed", e)
                _lyricsState.value = LyricsUiState.Success(originalLyrics, originalLines, originalColors)
            }
        }
    }
}
