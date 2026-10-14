package com.example.data

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

object LyricsIdentity {
    fun normalize(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT)

    fun songId(title: String, artist: String): String {
        val normalizedTitle = normalize(title)
        val normalizedArtist = normalize(artist)
        val key = "${normalizedTitle.length}:$normalizedTitle${normalizedArtist.length}:$normalizedArtist"
        return MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun spotifyTrackId(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        val id = when {
            trimmed.startsWith("spotify:track:") -> trimmed.removePrefix("spotify:track:")
            trimmed.startsWith("https://open.spotify.com/track/") ->
                trimmed.removePrefix("https://open.spotify.com/track/").substringBefore('?').substringBefore('/')
            else -> trimmed
        }
        return id.takeIf { Regex("[A-Za-z0-9]{22}").matches(it) }
    }

    fun recordingId(title: String, artist: String, trackId: String? = null,
        album: String? = null, durationMs: Long = 0L): String {
        val spotifyId = spotifyTrackId(trackId)
        val key = if (spotifyId != null) "spotify:$spotifyId"
            else "${songId(title, artist)}:${normalize(album.orEmpty())}:${durationMs.coerceAtLeast(0) / 1000}"
        return MessageDigest.getInstance("SHA-256").digest(key.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun legacySongId(title: String, artist: String): String =
        "${title.trim().lowercase()}_${artist.trim().lowercase()}"
            .replace(Regex("[^a-zA-Z0-9가-힣_]"), "")

    fun matches(cached: CachedLyrics, title: String, artist: String): Boolean =
        normalize(cached.title) == normalize(title) && normalize(cached.artist) == normalize(artist)
}
