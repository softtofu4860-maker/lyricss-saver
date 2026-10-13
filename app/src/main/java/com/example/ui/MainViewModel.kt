package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiLyricsService
import com.example.api.LyricsMatching
import com.example.data.CachedLyrics
import com.example.data.LyricLine
import com.example.data.LyricsIdentity
import com.example.data.LyricsRepository
import com.example.service.MediaStateHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray

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
    private val _lyricsState = MutableStateFlow<LyricsUiState>(LyricsUiState.Empty)
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()
    val mediaState = MediaStateHolder.mediaState
    private val _translationMode = MutableStateFlow(com.example.data.TranslationMode.BILINGUAL)
    val translationMode: StateFlow<com.example.data.TranslationMode> = _translationMode.asStateFlow()

    fun setTranslationMode(mode: com.example.data.TranslationMode) { _translationMode.value = mode }

    fun cycleTranslationMode() {
        val modes = com.example.data.TranslationMode.values()
        _translationMode.value = modes[(modes.indexOf(_translationMode.value) + 1) % modes.size]
    }

    val savedLyrics: StateFlow<List<CachedLyrics>> = repository.getAllSavedLyrics()
        .map { list -> list.filter { !GeminiLyricsService.isFallbackLyrics(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var lastLoadedSongKey = ""
    private var lastMetadataLyrics: String? = null
    private var lyricsJob: Job? = null

    // Loading, editing and translation all share one cancellable operation.
    private fun launchLyricsOperation(block: suspend () -> Unit) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Lyrics operation failed", e)
                _lyricsState.value = LyricsUiState.Error(e.message ?: "가사를 불러오지 못했습니다.")
            }
        }
    }

    private fun success(lyrics: CachedLyrics, lines: List<LyricLine> = GeminiLyricsService.parseJsonLyrics(lyrics.lyricsJson)): LyricsUiState.Success {
        val colors = runCatching {
            val array = JSONArray(lyrics.hexColorsJson)
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(listOf("#00FFFF", "#8A2BE2"))
        return LyricsUiState.Success(lyrics, lines, colors)
    }

    init {
        viewModelScope.launch {
            mediaState.collect { state ->
                val title = state.title
                val artist = state.artist
                if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
                    val songKey = LyricsIdentity.songId(title, artist)
                    val metadataChanged = !state.lyrics.isNullOrBlank() && state.lyrics != lastMetadataLyrics
                    if (songKey != lastLoadedSongKey || metadataChanged) {
                        lastLoadedSongKey = songKey
                        lastMetadataLyrics = state.lyrics
                        loadLyrics(title, artist, state.lyrics, state.durationMs, forceRefresh = metadataChanged)
                    }
                } else {
                    lyricsJob?.cancel()
                    _lyricsState.value = LyricsUiState.Empty
                    lastLoadedSongKey = ""
                    lastMetadataLyrics = null
                }
            }
        }
    }

    fun loadLyrics(
        title: String, artist: String, metadataLyrics: String? = null, durationMs: Long = 0,
        customQuery: String? = null, forceRefresh: Boolean = false
    ) {
        launchLyricsOperation {
            _lyricsState.value = LyricsUiState.Loading
            val lyrics = repository.getLyrics(title, artist, metadataLyrics, durationMs, customQuery, forceRefresh)
            currentCoroutineContext().ensureActive()
            _lyricsState.value = success(lyrics)
        }
    }

    fun saveManualLyrics(title: String, artist: String, editedLines: List<LyricLine>) {
        val original = (_lyricsState.value as? LyricsUiState.Success)?.cachedLyrics
            ?.takeIf { LyricsIdentity.matches(it, title, artist) }
        launchLyricsOperation {
            val updated = (original ?: CachedLyrics(
                id = LyricsIdentity.songId(title, artist), title = title, artist = artist, lyricsJson = ""
            )).copy(
                id = LyricsIdentity.songId(title, artist),
                lyricsJson = GeminiLyricsService.lyricAdapter.toJson(editedLines),
                timestamp = System.currentTimeMillis()
            )
            repository.saveLyrics(updated)
            currentCoroutineContext().ensureActive()
            _lyricsState.value = success(updated, editedLines)
        }
    }

    fun clearDatabaseCache() {
        launchLyricsOperation {
            repository.clearCache()
            val current = mediaState.value
            if (!current.title.isNullOrBlank() && !current.artist.isNullOrBlank()) {
                _lyricsState.value = LyricsUiState.Loading
                val lyrics = repository.getLyrics(current.title, current.artist, current.lyrics, current.durationMs, forceRefresh = true)
                currentCoroutineContext().ensureActive()
                _lyricsState.value = success(lyrics)
            } else {
                _lyricsState.value = LyricsUiState.Empty
            }
        }
    }

    fun deleteSavedSong(songId: String) {
        viewModelScope.launch { repository.deleteLyrics(songId) }
    }

    fun loadLyricsFromLibrary(cachedLyrics: CachedLyrics) {
        launchLyricsOperation {
            val state = success(cachedLyrics)
            // Prevent the media collector from starting an automatic lookup for this library selection.
            lastLoadedSongKey = LyricsIdentity.songId(cachedLyrics.title, cachedLyrics.artist)
            lastMetadataLyrics = null
            MediaStateHolder.updateState(
                title = cachedLyrics.title, artist = cachedLyrics.artist, album = "저장된 라이브러리",
                isPlaying = false, positionMs = 0L,
                durationMs = state.parsedLines.lastOrNull()?.let { (it.timeSec * 1000).toLong() + 5000L } ?: 180000L,
                packageName = "com.example.library", lyrics = null
            )
            _lyricsState.value = state
        }
    }

    fun translateActiveLyrics() {
        val original = _lyricsState.value as? LyricsUiState.Success ?: return
        val rawLrcText = original.parsedLines.joinToString("\n") { line ->
            val millis = (line.timeSec * 1000).toLong()
            String.format(java.util.Locale.US, "[%02d:%02d.%03d]%s",
                millis / 60000, (millis / 1000) % 60, millis % 1000, line.text)
        }
        launchLyricsOperation {
            _lyricsState.value = LyricsUiState.Loading
            try {
                val translated = GeminiLyricsService.translateLyricsViaGemini(rawLrcText, getApplication())
                val translatedLines = LyricsMatching.parseTranslation(translated)
                val merged = LyricsMatching.mergeTranslation(original.parsedLines, translatedLines)
                    ?: error("번역의 가사 줄 수 또는 시간이 원문과 다릅니다. 다시 시도해 주세요.")
                val updated = original.cachedLyrics.copy(
                    id = LyricsIdentity.songId(original.cachedLyrics.title, original.cachedLyrics.artist),
                    lyricsJson = GeminiLyricsService.lyricAdapter.toJson(merged),
                    timestamp = System.currentTimeMillis()
                )
                currentCoroutineContext().ensureActive()
                repository.saveLyrics(updated)
                currentCoroutineContext().ensureActive()
                _lyricsState.value = success(updated, merged)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Preserve the visible original and cached lyrics when translation fails.
                _lyricsState.value = original
                _translationError.value = e.message ?: "번역하지 못했습니다."
            }
        }
    }

    private val _translationError = MutableStateFlow<String?>(null)
    val translationError: StateFlow<String?> = _translationError.asStateFlow()
    fun dismissTranslationError() { _translationError.value = null }
}
