package com.example.data

data class SongDescriptor(val title: String, val artist: String, val album: String = "", val durationMs: Long = 0,
                          val provider: String = "", val providerId: String = "", val savedId: String? = null) {
    val legacyId: String get() = SongIdentity.id(title, artist)
    /** Earlier keys for the same known provider ID or a previously incomplete album snapshot. */
    val previousIds: List<String> get() = when {
        savedId != null -> emptyList()
        providerId.isNotBlank() -> buildList {
            add(SongIdentity.id(legacyId, "provider:$provider:$providerId"))
            if (album.isNotBlank() || durationMs > 0)
                add(SongIdentity.id(legacyId, "album:${album.trim()}:seconds:${durationMs.coerceAtLeast(0) / 1000}"))
            if (album.isNotBlank() && durationMs > 0) {
                add(SongIdentity.id(legacyId, "album:${album.trim()}:seconds:0"))
                add(SongIdentity.id(legacyId, "album::seconds:${durationMs / 1000}"))
            }
        }
        album.isNotBlank() && durationMs > 0 -> listOf(
            SongIdentity.id(legacyId, "album:${album.trim()}:seconds:0"),
            SongIdentity.id(legacyId, "album::seconds:${durationMs / 1000}")
        )
        else -> emptyList()
    }
    val id: String get() {
        savedId?.let { return it }
        if (providerId.isNotBlank()) return SongIdentity.id("provider:$provider", providerId)
        val context = if (album.isNotBlank() || durationMs > 0) "album:${album.trim()}:seconds:${durationMs.coerceAtLeast(0) / 1000}"
            else return legacyId
        return SongIdentity.id(legacyId, context)
    }
}
