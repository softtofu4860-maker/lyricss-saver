package com.example.api

import java.text.Normalizer
import java.util.Locale

/** Remove display decorations, while keeping live/remix/acoustic version identities. */
internal object SongSearchMetadata {
    private val decorations = Regex(
        "\\s*[\\[(](?:official(?:\\s+(?:music|lyric))?\\s*(?:video|audio|visualizer)?|lyrics?|가사|공식\\s*(?:뮤직\\s*)?(?:비디오|영상)|\\d{3,4}p|4k)[\\])]",
        RegexOption.IGNORE_CASE)
    private val suffix = Regex("\\s*[-|]\\s*(?:official(?:\\s+music)?\\s+(?:video|audio)|lyrics?|가사)\\s*$", RegexOption.IGNORE_CASE)
    private val featuring = Regex("\\s*(?:[\\[(]\\s*)?(?:feat\\.?|ft\\.?)\\s+.*$", RegexOption.IGNORE_CASE)
    private val separators = Regex("\\s*(?:,|;|&|/|、|，|\\s+[x×]\\s+|\\s+feat\\.?\\s+)\\s*", RegexOption.IGNORE_CASE)
    private val aliases = Regex("[()]|[\\[\\]]")
    private val punctuation = Regex("[^\\p{L}\\p{N}]+")
    private val topic = Regex("\\s*-\\s*(?:Topic|주제)$", RegexOption.IGNORE_CASE)
    private val versions = Regex("\\b(?:live|remix|acoustic|instrumental|karaoke|slowed|nightcore)\\b|라이브|리믹스|어쿠스틱", RegexOption.IGNORE_CASE)

    fun title(value: String): String = value.replace(decorations, " ").replace(suffix, "")
        .replace(featuring, "").replace(Regex("\\s+"), " ").trim()

    fun artistNames(value: String): List<String> = value.split(separators)
        .flatMap { it.split(aliases) }.map { it.replace(topic, "").trim() }.filter(String::isNotBlank).distinct()

    fun versions(value: String): Set<String> = versions.findAll(value).map {
        when (val name = it.value.lowercase(Locale.ROOT)) {
            "라이브" -> "live"
            "리믹스" -> "remix"
            "어쿠스틱" -> "acoustic"
            else -> name
        }
    }.toSet()

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(punctuation, " ").trim()

    fun queries(trackTitle: String, artist: String): List<String> {
        val cleanTitle = title(trackTitle)
        val names = artistNames(artist)
        return listOf("$cleanTitle ${names.firstOrNull().orEmpty()}".trim(),
            "$cleanTitle ${names.getOrNull(1).orEmpty()}".trim(), cleanTitle)
            .filter(String::isNotBlank).distinct().take(3)
    }
}
