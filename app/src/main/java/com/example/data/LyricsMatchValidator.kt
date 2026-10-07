package com.example.data

/** Conservative matching helpers: prefer no lyrics over lyrics from another song. */
object LyricsMatchValidator {
    private val removableSuffix = Regex("\\s*\\((?:official|lyrics?|audio|video|remastered|remix|live|acoustic|radio|edit|version|ver\\.?)[^)]*\\)\\s*$", RegexOption.IGNORE_CASE)
    private val punctuation = Regex("[^\\p{L}\\p{N}]+")

    fun normalize(value: String): String = value.lowercase().replace(removableSuffix, "").replace(punctuation, "").trim()

    fun titleMatches(expected: String, actual: String?): Boolean {
        if (actual.isNullOrBlank()) return false
        val a = normalize(expected); val b = normalize(actual)
        return a.isNotEmpty() && b.isNotEmpty() && (a == b || a.contains(b) || b.contains(a))
    }

    fun titleSimilar(expected: String, actual: String?): Boolean {
        if (actual.isNullOrBlank()) return false
        val a = normalize(expected); val b = normalize(actual)
        if (a.isEmpty() || b.isEmpty()) return false
        val shorter = minOf(a.length, b.length).toFloat(); val longer = maxOf(a.length, b.length).toFloat()
        return shorter / longer >= 0.72f
    }

    fun artistMatches(expected: String, actual: String?): Boolean {
        if (actual.isNullOrBlank()) return false
        val actualNormalized = normalize(actual)
        val parts = expected.split("&", ",", "/", " feat. ", " feat ", " ft. ", " ft ", " featuring ", " x ")
            .map(::normalize).filter { it.isNotEmpty() }
        return parts.any { it == actualNormalized || actualNormalized.contains(it) || it.contains(actualNormalized) }
    }
}
