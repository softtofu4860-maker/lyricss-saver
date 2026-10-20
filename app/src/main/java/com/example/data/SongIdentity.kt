package com.example.data

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

object SongIdentity {
    fun id(title: String, artist: String): String {
        fun normalize(value: String) = Normalizer.normalize(value.trim(), Normalizer.Form.NFC)
            .lowercase(Locale.ROOT)
        val normalizedTitle = normalize(title)
        val normalizedArtist = normalize(artist)
        // Length prefixes keep separators inside titles/artists unambiguous.
        val key = "${normalizedTitle.length}:$normalizedTitle${normalizedArtist.length}:$normalizedArtist"
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        val hex = "0123456789abcdef"
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }
}
