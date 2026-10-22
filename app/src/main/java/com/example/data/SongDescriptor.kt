package com.example.data

data class SongDescriptor(val title: String, val artist: String, val album: String = "", val durationMs: Long = 0,
                          val provider: String = "", val providerId: String = "", val savedId: String? = null) {
    val legacyId: String get() = SongIdentity.id(title, artist)
    val id: String get() {
        savedId?.let { return it }
        val context = if (providerId.isNotBlank()) "provider:$provider:$providerId"
            else if (album.isNotBlank() || durationMs > 0) "album:${album.trim()}:seconds:${durationMs.coerceAtLeast(0) / 1000}"
            else return legacyId
        return SongIdentity.id(legacyId, context)
    }
}
