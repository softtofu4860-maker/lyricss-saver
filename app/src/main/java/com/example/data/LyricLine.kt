package com.example.data

data class LyricLine(
    val timeSec: Float,
    val text: String,
    val translation: String? = null,
    val romanization: String? = null,
    val words: List<LyricWord> = emptyList(),
    val endTimeSec: Float? = null
)

data class LyricWord(val startTimeSec: Float, val endTimeSec: Float, val text: String)
