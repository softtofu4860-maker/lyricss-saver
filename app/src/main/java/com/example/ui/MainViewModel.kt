package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiLyricsService
import com.example.data.CachedLyrics
import com.example.data.LyricLine
import com.example.data.LyricsRepository
import com.example.data.LyricsTranslation
import com.example.data.SongIdentity
import com.example.data.TranslationMode
import com.example.service.MediaStateHolder
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

class MainViewModel internal constructor(
    application: Application,
    private val repository: LyricsRepository,
    private val translate: suspend (String) -> String
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, LyricsRepository(application),
        { raw -> GeminiLyricsService.translateLyricsViaGemini(raw, application) })

    private val tag = "MainViewModel"
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val linesAdapter = GeminiLyricsService.lyricAdapter
    private val colorsAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java))

    private val _lyricsState = MutableStateFlow<LyricsUiState>(LyricsUiState.Empty)
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()
    val mediaState = MediaStateHolder.mediaState

    private val _translationMode = MutableStateFlow(TranslationMode.BILINGUAL)
    val translationMode: StateFlow<TranslationMode> = _translationMode.asStateFlow()
    fun setTranslationMode(mode: TranslationMode) { _translationMode.value = mode }
    fun cycleTranslationMode() {
        val modes = TranslationMode.entries
        _translationMode.value = modes[(modes.indexOf(_translationMode.value) + 1) % modes.size]
    }

    val savedLyrics: StateFlow<List<CachedLyrics>> = repository.getAllSavedLyrics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var lastLoadedSongKey: String? = null
    private var lastMetadataLyrics: String? = null
    private var lastLoadedDurationMs = 0L
    private var activeSongId: String? = null
    private var activeRequest: Job? = null
    private var generation = 0L

    init {
        viewModelScope.launch {
            mediaState.collect { state ->
                val title = state.title
                val artist = state.artist.orEmpty()
                if (!title.isNullOrBlank()) {
                    val id = SongIdentity.id(title, artist)
                    val newMetadata = !state.lyrics.isNullOrBlank() && state.lyrics != lastMetadataLyrics
                    val durationAvailable = lastLoadedDurationMs <= 0 && state.durationMs > 0
                    if (id != lastLoadedSongKey || newMetadata || durationAvailable) {
                        lastLoadedSongKey = id
                        lastMetadataLyrics = state.lyrics
                        lastLoadedDurationMs = state.durationMs
                        loadLyrics(title, artist, state.lyrics, state.durationMs)
                    }
                } else {
                    invalidateRequest()
                    activeSongId = null
                    lastLoadedSongKey = null
                    lastMetadataLyrics = null
                    lastLoadedDurationMs = 0L
                    _lyricsState.value = LyricsUiState.Empty
                }
            }
        }
    }

    private fun invalidateRequest(): Long {
        activeRequest?.cancel()
        return ++generation
    }

    private fun isCurrent(token: Long, id: String) = token == generation && activeSongId == id

    private fun success(lyrics: CachedLyrics): LyricsUiState.Success = LyricsUiState.Success(
        lyrics, linesAdapter.fromJson(lyrics.lyricsJson).orEmpty(),
        try { colorsAdapter.fromJson(lyrics.hexColorsJson).orEmpty() }
        catch (_: Exception) { listOf("#00FFFF", "#8A2BE2") }
    )

    private fun load(id: String, onError: LyricsUiState? = null, operation: suspend () -> LyricsUiState.Success) {
        val token = invalidateRequest()
        activeSongId = id
        _lyricsState.value = LyricsUiState.Loading
        activeRequest = viewModelScope.launch {
            try {
                val result = operation()
                currentCoroutineContext().ensureActive()
                if (isCurrent(token, id)) _lyricsState.value = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Error loading lyrics", e)
                if (isCurrent(token, id)) _lyricsState.value = onError ?: LyricsUiState.Error("Error: ${e.message}")
            }
        }
    }

    fun loadLyrics(title: String, artist: String, metadataLyrics: String? = null,
        durationMs: Long = 0, customQuery: String? = null) {
        if (customQuery.isNullOrBlank()) {
            val id = SongIdentity.id(title, artist)
            val token = invalidateRequest()
            activeSongId = id
            _lyricsState.value = LyricsUiState.Loading
            activeRequest = viewModelScope.launch {
                try {
                    repository.observeAutomaticLyrics(title, artist, metadataLyrics, durationMs).collect { lyrics ->
                        if (isCurrent(token, id)) _lyricsState.value = success(lyrics)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e(tag, "Error loading automatic lyrics", error)
                    if (isCurrent(token, id)) _lyricsState.value = LyricsUiState.Error("가사를 불러오지 못했습니다")
                }
            }
            return
        }
        load(SongIdentity.id(title, artist)) {
            success(repository.getLyrics(title, artist, metadataLyrics, durationMs, customQuery))
        }
    }

    fun saveManualLyrics(title: String, artist: String, editedLines: List<LyricLine>) {
        val id = SongIdentity.id(title, artist)
        val token = if (activeSongId == id) invalidateRequest() else generation
        viewModelScope.launch {
            try {
                val updated = repository.saveManualLyrics(title, artist, editedLines)
                if (isCurrent(token, id)) _lyricsState.value = success(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Error saving manual lyrics", e)
            }
        }
    }

    fun clearDatabaseCache() {
        val token = invalidateRequest()
        viewModelScope.launch {
            repository.clearCache()
            if (token != generation) return@launch
            val current = mediaState.value
            if (!current.title.isNullOrBlank()) {
                loadLyrics(current.title, current.artist.orEmpty(), current.lyrics, current.durationMs)
            } else _lyricsState.value = LyricsUiState.Empty
        }
    }

    fun deleteSavedSong(songId: String) {
        if (activeSongId == songId) {
            invalidateRequest()
            _lyricsState.value = LyricsUiState.Empty
        }
        viewModelScope.launch { repository.deleteLyrics(songId) }
    }

    fun loadLyricsFromLibrary(cachedLyrics: CachedLyrics) {
        invalidateRequest()
        activeSongId = cachedLyrics.id
        lastLoadedSongKey = cachedLyrics.id
        lastMetadataLyrics = null
        try {
            val result = success(cachedLyrics)
            val duration = result.parsedLines.lastOrNull()?.let { (it.timeSec * 1000).toLong() + 5000L } ?: 180000L
            lastLoadedDurationMs = duration
            _lyricsState.value = result
            MediaStateHolder.updateState(
                title = cachedLyrics.title, artist = cachedLyrics.artist, album = "저장된 라이브러리",
                isPlaying = false, positionMs = 0L,
                durationMs = duration,
                packageName = "com.example.library", lyrics = null
            )
        } catch (e: Exception) {
            _lyricsState.value = LyricsUiState.Error("Error: ${e.message}")
        }
    }

    fun translateActiveLyrics() {
        val original = _lyricsState.value as? LyricsUiState.Success ?: return
        if (original.cachedLyrics.isFallback) return
        load(original.cachedLyrics.id, onError = original) {
            val raw = LyricsTranslation.toLrc(original.parsedLines)
            val translated = translate(raw)
            if (translated == raw) return@load original
            val lines = LyricsTranslation.merge(original.parsedLines,
                GeminiLyricsService.parseLrcLyrics(translated))
            if (lines == original.parsedLines) return@load original
            val saved = repository.saveTranslation(original.cachedLyrics, lines)
            if (saved != null) success(saved) else original
        }
    }
}
