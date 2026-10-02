package com.example.api

import android.content.Context
import android.util.Log
import com.example.data.CachedLyrics
import com.example.data.LyricLine
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class LrclibResponse(
    val id: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean? = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

data class NetEaseArtist(
    val id: Long? = null,
    val name: String? = null
)

data class NetEaseSong(
    val id: Long? = null,
    val name: String? = null,
    val artists: List<NetEaseArtist>? = null
)

object GeminiLyricsService {
    private const val TAG = "GeminiLyricsService"
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val lyricListType = Types.newParameterizedType(List::class.java, LyricLine::class.java)
    val lyricAdapter: JsonAdapter<List<LyricLine>> = moshi.adapter(lyricListType)

    fun generateSongId(title: String, artist: String): String {
        return "${title.trim().lowercase()}_${artist.trim().lowercase()}".replace(Regex("[^a-zA-Z0-9가-힣_]"), "")
    }

    fun isFallbackLyrics(lyrics: CachedLyrics): Boolean {
        return lyrics.lyricsJson.contains("실시간 싱크 가사를 검색하고 있습니다") ||
                lyrics.lyricsJson.contains("가사 탐색 중")
    }

    suspend fun getLyricsForSong(
        context: Context,
        title: String,
        artist: String,
        metadataLyrics: String? = null,
        durationMs: Long = 0L,
        customQuery: String? = null
    ): CachedLyrics = withContext(Dispatchers.IO) {
        val songId = generateSongId(title, artist)

        // 1. 메타데이터에 가사가 이미 있는 경우 (LRC 형식 등)
        if (!metadataLyrics.isNullOrBlank()) {
            val parsed = parseLrcLyrics(metadataLyrics, durationMs)
            if (parsed.isNotEmpty()) {
                val json = lyricAdapter.toJson(parsed)
                return@withContext CachedLyrics(
                    id = songId,
                    title = title,
                    artist = artist,
                    lyricsJson = json,
                    hexColorsJson = getElegantAuraColors(title, artist)
                )
            }
        }

        // 2. Paxsenix0 Spotify-Lyrics-API (가장 정확한 Spotify 실시간 싱크 가사)
        try {
            val spotifyLrc = fetchSpotifyLyricsApi(title, artist)
            if (!spotifyLrc.isNullOrBlank()) {
                val parsed = parseLrcLyrics(spotifyLrc, durationMs)
                if (parsed.isNotEmpty()) {
                    val json = lyricAdapter.toJson(parsed)
                    return@withContext CachedLyrics(
                        id = songId,
                        title = title,
                        artist = artist,
                        lyricsJson = json,
                        hexColorsJson = getElegantAuraColors(title, artist)
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Spotify Lyrics API lookup error: ${e.message}")
        }

        // 3. LRCLIB 검색 (오픈소스 고품질 싱크 가사)
        val query = customQuery ?: "$title $artist"
        try {
            val lrclibLrc = fetchLrclibDirect(title, artist, durationMs) ?: fetchLrclibFirstResult(query)
            if (!lrclibLrc.isNullOrBlank()) {
                val parsed = parseLrcLyrics(lrclibLrc, durationMs)
                if (parsed.isNotEmpty()) {
                    val json = lyricAdapter.toJson(parsed)
                    return@withContext CachedLyrics(
                        id = songId,
                        title = title,
                        artist = artist,
                        lyricsJson = json,
                        hexColorsJson = getElegantAuraColors(title, artist)
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "LRCLIB lookup error: ${e.message}")
        }

        // 4. NetEase 검색 (ivLyrics 스타일 한국어 번역 tlyric + 발음 romalrc 포함)
        try {
            val netEaseResult = fetchNetEaseWithTranslation(title, artist)
            if (netEaseResult != null && !netEaseResult.lrc.isNullOrBlank()) {
                val parsed = parseLrcLyrics(
                    lrcText = netEaseResult.lrc,
                    durationMs = durationMs,
                    translationLrc = netEaseResult.tlyric,
                    romanizationLrc = netEaseResult.romalrc
                )
                if (parsed.isNotEmpty()) {
                    val json = lyricAdapter.toJson(parsed)
                    return@withContext CachedLyrics(
                        id = songId,
                        title = title,
                        artist = artist,
                        lyricsJson = json,
                        hexColorsJson = getElegantAuraColors(title, artist)
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "NetEase lookup error: ${e.message}")
        }

        // 5. Fallback: 스마트 가사 라인 생성 (가사 없음 표시 및 타임라인 안내)
        generateSmartFallback(songId, title, artist, durationMs)
    }

    /**
     * Paxsenix0 Spotify Lyrics API
     * https://github.com/Paxsenix0/Spotify-Lyrics-API
     */
    suspend fun fetchSpotifyLyricsApi(title: String, artist: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val encodedName = URLEncoder.encode(title.trim(), "UTF-8")
            val encodedArtist = URLEncoder.encode(artist.trim(), "UTF-8")
            val url = "https://spotify-lyrics-api-pi.vercel.app/?format=lrc&name=$encodedName&artist=$encodedArtist"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SmartMusicScreensaver/1.0")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                if (body.startsWith("{")) {
                    val json = JSONObject(body)
                    if (json.optBoolean("error", false)) return@use null
                    
                    // Case 1: Direct LRC property
                    val lrc = json.optString("lrc")
                    if (lrc.isNotBlank()) return@use lrc

                    // Case 2: Array of lines with startTimeMs & words
                    val lines = json.optJSONArray("lines")
                    if (lines != null && lines.length() > 0) {
                        val sb = StringBuilder()
                        for (i in 0 until lines.length()) {
                            val lineObj = lines.getJSONObject(i)
                            val words = lineObj.optString("words").trim()
                            val startMs = lineObj.optLong("startTimeMs", -1L)
                            if (startMs >= 0 && words.isNotBlank()) {
                                val min = (startMs / 60000).toInt()
                                val sec = ((startMs % 60000) / 1000).toInt()
                                val ms = ((startMs % 1000) / 10).toInt()
                                sb.append(String.format(java.util.Locale.US, "[%02d:%02d.%02d]%s\n", min, sec, ms, words))
                            }
                        }
                        if (sb.isNotEmpty()) return@use sb.toString()
                    }
                } else if (body.contains("[")) {
                    return@use body
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchSpotifyLyricsApi failed: ${e.message}")
            null
        }
    }

    private fun fetchLrclibDirect(title: String, artist: String, durationMs: Long): String? {
        return try {
            val durSec = if (durationMs > 0) durationMs / 1000 else 0
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val url = if (durSec > 0) {
                "https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTitle&duration=$durSec"
            } else {
                "https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTitle"
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SmartMusicScreensaver/1.0 (https://github.com/example/screensaver)")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val json = JSONObject(body)
                    val synced = json.optString("syncedLyrics")
                    if (synced.isNotBlank()) return synced
                    val plain = json.optString("plainLyrics")
                    if (plain.isNotBlank()) return plain
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchLrclibFirstResult(query: String): String? {
        val results = searchLrclib(query)
        for (r in results) {
            if (!r.syncedLyrics.isNullOrBlank()) return r.syncedLyrics
        }
        for (r in results) {
            if (!r.plainLyrics.isNullOrBlank()) return r.plainLyrics
        }
        return null
    }

    fun searchLrclib(query: String): List<LrclibResponse> {
        val list = mutableListOf<LrclibResponse>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://lrclib.net/api/search?q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SmartMusicScreensaver/1.0 (https://github.com/example/screensaver)")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return list
                    val array = JSONArray(body)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        list.add(
                            LrclibResponse(
                                id = item.optLong("id"),
                                trackName = item.optString("trackName"),
                                artistName = item.optString("artistName"),
                                albumName = item.optString("albumName"),
                                duration = item.optDouble("duration"),
                                instrumental = item.optBoolean("instrumental"),
                                plainLyrics = item.optString("plainLyrics").ifBlank { null },
                                syncedLyrics = item.optString("syncedLyrics").ifBlank { null }
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "searchLrclib error: ${e.message}")
        }
        return list
    }

    fun searchNetEase(query: String): List<NetEaseSong> {
        val list = mutableListOf<NetEaseSong>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://music.163.com/api/search/get/web?csrf_token=&hlpretag=&hlposttag=&s=$encoded&type=1&offset=0&total=true&limit=10"
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://music.163.com/")
                .build()

            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return list
                val body = resp.body?.string() ?: return list
                val root = JSONObject(body)
                val songs = root.optJSONObject("result")?.optJSONArray("songs") ?: return list
                for (i in 0 until songs.length()) {
                    val s = songs.getJSONObject(i)
                    val id = s.optLong("id")
                    val name = s.optString("name")
                    val artistsArray = s.optJSONArray("artists")
                    val artistsList = mutableListOf<NetEaseArtist>()
                    if (artistsArray != null) {
                        for (j in 0 until artistsArray.length()) {
                            val a = artistsArray.getJSONObject(j)
                            artistsList.add(NetEaseArtist(a.optLong("id"), a.optString("name")))
                        }
                    }
                    list.add(NetEaseSong(id, name, artistsList))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "searchNetEase error: ${e.message}")
        }
        return list
    }

    data class NetEaseLyricsBundle(
        val lrc: String?,
        val tlyric: String?,
        val romalrc: String?
    )

    private fun fetchNetEaseWithTranslation(title: String, artist: String): NetEaseLyricsBundle? {
        return try {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val searchUrl = "https://music.163.com/api/search/get/web?csrf_token=&hlpretag=&hlposttag=&s=$query&type=1&offset=0&total=true&limit=1"
            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://music.163.com/")
                .build()

            val songId = okHttpClient.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val root = JSONObject(body)
                val songs = root.optJSONObject("result")?.optJSONArray("songs") ?: return null
                if (songs.length() > 0) songs.getJSONObject(0).optLong("id") else null
            } ?: return null

            val lyricUrl = "https://music.163.com/api/song/lyric?os=pc&id=$songId&lv=-1&kv=-1&tv=-1"
            val lyricReq = Request.Builder()
                .url(lyricUrl)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://music.163.com/")
                .build()

            okHttpClient.newCall(lyricReq).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val root = JSONObject(body)
                val lrc = root.optJSONObject("lrc")?.optString("lyric")
                val tlyric = root.optJSONObject("tlyric")?.optString("lyric")
                val romalrc = root.optJSONObject("romalrc")?.optString("lyric")
                NetEaseLyricsBundle(
                    lrc = lrc?.ifBlank { null },
                    tlyric = tlyric?.ifBlank { null },
                    romalrc = romalrc?.ifBlank { null }
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchLrcmuxLyrics(title: String, artist: String): String? = withContext(Dispatchers.IO) {
        // First try Spotify Lyrics API
        val spotify = fetchSpotifyLyricsApi(title, artist)
        if (!spotify.isNullOrBlank()) return@withContext spotify

        // Fallback to NetEase
        val ne = fetchNetEaseWithTranslation(title, artist)
        if (ne != null && !ne.lrc.isNullOrBlank()) {
            return@withContext ne.lrc
        }
        null
    }

    fun parseLrcLyrics(
        lrcText: String,
        durationMs: Long = 0L,
        translationLrc: String? = null,
        romanizationLrc: String? = null
    ): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val lrcPattern = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\](.*)")
        val rawLines = lrcText.lines()
        var hasTimestamps = false

        for (rawLine in rawLines) {
            val trimmed = rawLine.trim()
            if (trimmed.isBlank() || trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") ||
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") || trimmed.startsWith("[offset:")
            ) {
                continue
            }

            val matcher = lrcPattern.matcher(trimmed)
            if (matcher.find()) {
                hasTimestamps = true
                val minutes = matcher.group(1)?.toIntOrNull() ?: 0
                val seconds = matcher.group(2)?.toIntOrNull() ?: 0
                val milliStr = matcher.group(3) ?: "0"
                val millis = when (milliStr.length) {
                    1 -> milliStr.toInt() * 100
                    2 -> milliStr.toInt() * 10
                    else -> milliStr.take(3).toInt()
                }
                val text = matcher.group(4)?.trim() ?: ""
                val timeSec = (minutes * 60) + seconds + (millis / 1000f)

                // ivLyrics dual-line bilingual format check:
                // If consecutive lines share near-identical timestamp, pair them as (text, translation)
                if (lines.isNotEmpty() && kotlin.math.abs(lines.last().timeSec - timeSec) < 0.28f &&
                    lines.last().translation == null && text.isNotBlank()
                ) {
                    val last = lines.removeAt(lines.lastIndex)
                    lines.add(last.copy(translation = text))
                } else if (text.isNotBlank()) {
                    lines.add(LyricLine(timeSec, text))
                }
            }
        }

        // Apply external translation lines (e.g. from NetEase tlyric)
        if (!translationLrc.isNullOrBlank()) {
            val transLines = parseLrcLyrics(translationLrc, durationMs)
            for (i in lines.indices) {
                val line = lines[i]
                if (line.translation == null) {
                    val matched = transLines.minByOrNull { kotlin.math.abs(it.timeSec - line.timeSec) }
                    if (matched != null && kotlin.math.abs(matched.timeSec - line.timeSec) < 0.8f && matched.text.isNotBlank()) {
                        lines[i] = line.copy(translation = matched.text)
                    }
                }
            }
        }

        // Apply external romanization lines (e.g. from NetEase romalrc)
        if (!romanizationLrc.isNullOrBlank()) {
            val romLines = parseLrcLyrics(romanizationLrc, durationMs)
            for (i in lines.indices) {
                val line = lines[i]
                if (line.romanization == null) {
                    val matched = romLines.minByOrNull { kotlin.math.abs(it.timeSec - line.timeSec) }
                    if (matched != null && kotlin.math.abs(matched.timeSec - line.timeSec) < 0.8f && matched.text.isNotBlank()) {
                        lines[i] = lines[i].copy(romanization = matched.text)
                    }
                }
            }
        }

        // Fallback for plain text lyrics without timestamps
        if (!hasTimestamps) {
            val validLines = rawLines.map { it.trim() }.filter { it.isNotBlank() }
            if (validLines.isNotEmpty()) {
                val totalSec = if (durationMs > 0) (durationMs / 1000f) else 180f
                val step = totalSec / (validLines.size + 1)
                validLines.forEachIndexed { index, s ->
                    lines.add(LyricLine((index + 1) * step, s))
                }
            }
        }

        return lines.sortedBy { it.timeSec }
    }

    fun parseJsonLyrics(jsonStr: String): List<LyricLine> {
        return try {
            lyricAdapter.fromJson(jsonStr) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun generateSmartFallback(
        songId: String,
        title: String,
        artist: String,
        durationMs: Long = 0L
    ): CachedLyrics {
        val totalSec = if (durationMs > 0) (durationMs / 1000f) else 200f
        val lines = listOf(
            LyricLine(0f, "♪ $title"),
            LyricLine(5f, artist),
            LyricLine(12f, "실시간 싱크 가사를 검색하고 있습니다"),
            LyricLine(totalSec * 0.35f, "가사 탐색 중... 화면을 탭하여 수동 검색할 수 있습니다"),
            LyricLine(totalSec * 0.75f, "♪ 즐거운 음악 감상 되세요")
        )
        val json = lyricAdapter.toJson(lines)
        return CachedLyrics(
            id = songId,
            title = title,
            artist = artist,
            lyricsJson = json,
            hexColorsJson = getElegantAuraColors(title, artist)
        )
    }

    suspend fun translateLyricsViaGemini(rawLrcText: String, context: Context): String = withContext(Dispatchers.IO) {
        val apiKey = com.example.BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext rawLrcText
        }
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val prompt = """
                Translate the following synchronized LRC lyrics into natural, poetic Korean while strictly preserving the timestamps like [mm:ss.xx].
                Return ONLY the translated LRC text, without any explanations or markdown backticks.

                $rawLrcText
            """.trimIndent()

            val jsonPayload = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext rawLrcText
                val body = resp.body?.string() ?: return@withContext rawLrcText
                val respJson = JSONObject(body)
                val candidates = respJson.optJSONArray("candidates") ?: return@withContext rawLrcText
                if (candidates.length() > 0) {
                    val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                    val text = parts?.getJSONObject(0)?.optString("text")
                    if (!text.isNullOrBlank()) {
                        return@withContext text.replace("```lrc", "").replace("```", "").trim()
                    }
                }
            }
            rawLrcText
        } catch (e: Exception) {
            Log.e(TAG, "Gemini translation error: ${e.message}")
            rawLrcText
        }
    }

    fun getElegantAuraColors(title: String, artist: String): String {
        val hash = (title + artist).hashCode()
        val palettes = listOf(
            listOf("#FF4A154B", "#FF1E1B4B", "#FF0F172A", "#FF6B21A8"),
            listOf("#FF0369A1", "#FF1E293B", "#FF0F766E", "#FF1D4ED8"),
            listOf("#FF831843", "#FF4C0519", "#FF1F2937", "#FFBE185D"),
            listOf("#FF134E4A", "#FF064E3B", "#FF0F172A", "#FF047857"),
            listOf("#FF3730A3", "#FF312E81", "#FF111827", "#FF4338CA"),
            listOf("#FF7C2D12", "#FF451A03", "#FF1C1917", "#FFB45309")
        )
        val selected = palettes[Math.abs(hash) % palettes.size]
        val jsonArray = JSONArray()
        selected.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }
}
