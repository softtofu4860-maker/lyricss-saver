package com.example.api

data class SongQuery(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val trackId: String? = null
)

data class LyricsCandidate(
    val provider: String,
    val providerId: String?,
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val durationMs: Long? = null,
    val rawLyrics: String,
    val translationLrc: String? = null,
    val romanizationLrc: String? = null,
    val synced: Boolean,
    val boundTrackId: String? = null
)

data class RankedLyrics(val candidate: LyricsCandidate, val score: Double)

data class LyricsSelection(
    val provider: String,
    val providerId: String?,
    val synced: Boolean
)
