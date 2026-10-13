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

    fun legacySongId(title: String, artist: String): String =
        "${title.trim().lowercase()}_${artist.trim().lowercase()}"
            .replace(Regex("[^a-zA-Z0-9가-힣_]"), "")

    fun matches(cached: CachedLyrics, title: String, artist: String): Boolean =
        normalize(cached.title) == normalize(title) && normalize(cached.artist) == normalize(artist)
}
