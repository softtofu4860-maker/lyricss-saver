package com.example.api

import java.net.URI

object NetEaseSongReference {
    /** Accept IDs and official song URLs only; never request a pasted arbitrary URL. */
    fun parse(value: String): Long? {
        val query = value.trim()
        if (query.matches(Regex("[0-9]+"))) return query.toLongOrNull()?.takeIf { it > 0 }
        val uri = runCatching { URI(query) }.getOrNull() ?: return null
        if (uri.scheme !in listOf("http", "https") ||
            uri.host?.lowercase(java.util.Locale.ROOT) !in setOf("music.163.com", "y.music.163.com")) return null
        val location = uri.path.orEmpty() + "?" + uri.rawQuery.orEmpty() + "#" + uri.rawFragment.orEmpty()
        if (!Regex("(?:^|/)song(?:[/?#]|$)").containsMatchIn(location)) return null
        return Regex("(?:[?&])id=([0-9]+)(?:[&#]|$)").find(location)?.groupValues?.get(1)
            ?.toLongOrNull()?.takeIf { it > 0 }
    }
}
