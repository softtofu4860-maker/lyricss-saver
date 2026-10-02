package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_lyrics",
    indices = [Index(value = ["timestamp"])]
)
data class CachedLyrics(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String,
    val lyricsJson: String,
    val bpm: Int = 120,
    val hexColorsJson: String = "[\"#FF1E3A8A\", \"#FF3B82F6\", \"#FF06B6D4\"]",
    val genre: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
