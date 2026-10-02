package com.example.data

data class LyricLine(
    val timeSec: Float,
    val text: String,
    val translation: String? = null,
    val romanization: String? = null
)
