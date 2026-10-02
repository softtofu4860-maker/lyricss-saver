package com.example.ui

import com.example.data.LyricLine
import androidx.compose.ui.graphics.Color

sealed interface LyricsUiState {
    data object Idle : LyricsUiState
    data object Loading : LyricsUiState
    data class Success(
        val lyrics: List<LyricLine>,
        val auraColors: List<Color>,
        val isFallback: Boolean = false
    ) : LyricsUiState
    data class Error(val message: String) : LyricsUiState
}
