package com.example.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiLyricsService
import com.example.data.CachedLyrics
import com.example.data.LyricLine
import com.example.data.LyricsRepository
import com.example.service.MediaState
import com.example.service.MediaStateHolder
import com.example.service.MusicNotificationListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LyricsRepository(application)

    val mediaState: StateFlow<MediaState> = MediaStateHolder.mediaState

    private val _lyricsUiState = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val lyricsUiState: StateFlow<LyricsUiState> = _lyricsUiState.asStateFlow()

    val savedLyrics = repository.getAllSavedLyrics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var currentSongKey: String? = null
    private var fetchJob: Job? = null

    init {
        viewModelScope.launch {
            mediaState.collect { state ->
                val title = state.title
                val artist = state.artist
                if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
                    val key = "${title}_$artist"
                    if (key != currentSongKey) {
                        currentSongKey = key
                        loadLyrics(title, artist, state.durationMs)
                    }
                } else if (title.isNullOrBlank() && artist.isNullOrBlank()) {
                    currentSongKey = null
                    _lyricsUiState.value = LyricsUiState.Idle
                }
            }
        }
    }

    fun loadLyrics(title: String, artist: String, durationMs: Long, customQuery: String? = null) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _lyricsUiState.value = LyricsUiState.Loading
            try {
                val cached = repository.getLyrics(
                    title = title,
                    artist = artist,
                    durationMs = durationMs,
                    customQuery = customQuery
                )
                val lines = GeminiLyricsService.parseJsonLyrics(cached.lyricsJson)
                val colors = parseAuraColors(cached.hexColorsJson)
                _lyricsUiState.value = LyricsUiState.Success(
                    lyrics = lines,
                    auraColors = colors
                )
            } catch (e: Exception) {
                _lyricsUiState.value = LyricsUiState.Error(e.message ?: "가사를 불러올 수 없습니다")
            }
        }
    }

    fun updateCustomLyrics(title: String, artist: String, lrcText: String, durationMs: Long) {
        viewModelScope.launch {
            val parsed = GeminiLyricsService.parseLrcLyrics(lrcText, durationMs)
            val json = org.json.JSONArray().apply {
                parsed.forEach { line ->
                    val obj = org.json.JSONObject()
                    obj.put("timeSec", line.timeSec.toDouble())
                    obj.put("text", line.text)
                    put(obj)
                }
            }.toString()

            val songId = GeminiLyricsService.generateSongId(title, artist)
            val cached = CachedLyrics(
                id = songId,
                title = title,
                artist = artist,
                lyricsJson = json,
                hexColorsJson = GeminiLyricsService.getElegantAuraColors(title, artist)
            )
            repository.saveLyrics(cached)
            _lyricsUiState.value = LyricsUiState.Success(
                lyrics = parsed,
                auraColors = parseAuraColors(cached.hexColorsJson)
            )
        }
    }

    fun deleteCachedSong(id: String) {
        viewModelScope.launch {
            repository.deleteLyrics(id)
        }
    }

    fun clearAllCache() {
        viewModelScope.launch {
            repository.clearCache()
        }
    }

    fun seekTo(positionMs: Long) {
        MediaStateHolder.setPlaybackPosition(positionMs)
        MusicNotificationListener.activeController?.transportControls?.seekTo(positionMs)
    }

    fun togglePlayPause() {
        val current = mediaState.value
        val controller = MusicNotificationListener.activeController
        if (current.isPlaying) {
            controller?.transportControls?.pause()
            MediaStateHolder.setPlayingState(false)
        } else {
            controller?.transportControls?.play()
            MediaStateHolder.setPlayingState(true)
        }
    }

    fun skipToNext() {
        MusicNotificationListener.activeController?.transportControls?.skipToNext()
    }

    fun skipToPrevious() {
        MusicNotificationListener.activeController?.transportControls?.skipToPrevious()
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
}
