package com.example.data

/**
 * Conservative matching helpers used before accepting search results.
 * This intentionally rejects weak matches instead of displaying lyrics
 * from a different song.
 */
object LyricsMatchValidator {
    private val versionSuffixes = Regex("\\s*\\((?:official|lyrics?|audio|video|remastered|remix|live|acoustic|radio|edit|version|ver\\.?)[^)]*\\)\\s*$", RegexOption.IGNORE_CASE)
    private val punctuation = Regex("[^\\p{L}\\p{N}]+")

    fun normalize(value: String): String {
        return value
            .lowercase()
            .replace(versionSuffixes, "")
            .replace(punctuation, "")
            .trim()
    }

    fun titleMatches(expected: String, actual: String?): Boolean {
        if (actual.isNullOrBlank()) return false
        val a = normalize(expected)
        val b = normalize(actual)
        if (a.isEmpty() || b.isEmpty()) return false
        return a == b || a.contains(b) || b.contains(a)
    }

    fun artistMatches(expected: String, actual: String?): Boolean {
        if (actual.isNullOrBlank()) return false
        val expectedParts = expected
            .split("&", ",", "/", " feat. ", " feat ", " ft. ", " ft ", " featuring ", " x ")
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
        val actualNormalized = normalize(actual)
        if (expectedParts.isEmpty() || actualNormalized.isEmpty()) return false
        return expectedParts.any { it == actualNormalized || actualNormalized.contains(it) || it.contains(actualNormalized) }
    }
}
