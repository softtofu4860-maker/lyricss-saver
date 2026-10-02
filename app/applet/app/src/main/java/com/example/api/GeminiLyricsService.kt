package com.example.api

import android.content.Context
import com.example.data.CachedLyrics
import com.example.data.LyricLine
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object GeminiLyricsService {
    private const val TAG = "GeminiLyricsService"
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val lyricListType = Types.newParameterizedType(List::class.java, LyricLine::class.java)
    private val lyricAdapter = moshi.adapter<List<LyricLine>>(lyricListType)

    fun generateSongId(title: String, artist: String): String {
        return "${title.trim().lowercase()}_${artist.trim().lowercase()}".replace(Regex("[^a-zA-Z0-9가-힣_]"), "")
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

        // 2. LRCLIB 검색 (가장 정확한 싱크 가사 소스)
        val query = customQuery ?: "$title $artist"
        val lrclibLrc = fetchLrclib(title, artist, durationMs) ?: searchLrclib(query)
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

        // 3. NetEase 검색
        val netEaseLrc = fetchNetEase(title, artist)
        if (!netEaseLrc.isNullOrBlank()) {
            val parsed = parseLrcLyrics(netEaseLrc, durationMs)
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

        // 4. Fallback: 스마트 가사 라인 생성 (가사 없음 표시 및 타임라인 안내)
        generateSmartFallback(songId, title, artist, durationMs)
    }

    private fun fetchLrclib(title: String, artist: String, durationMs: Long): String? {
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

    private fun searchLrclib(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://lrclib.net/api/search?q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SmartMusicScreensaver/1.0 (https://github.com/example/screensaver)")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val array = JSONArray(body)
                    if (array.length() > 0) {
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            val synced = item.optString("syncedLyrics")
                            if (synced.isNotBlank()) return synced
                        }
                        return array.getJSONObject(0).optString("plainLyrics").ifBlank { null }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchNetEase(title: String, artist: String): String? {
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
                if (!lrc.isNullOrBlank()) lrc else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun parseLrcLyrics(lrcText: String, durationMs: Long = 0L): List<LyricLine> {
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
                lines.add(LyricLine(timeSec, text))
            }
        }

        // 타임스탬프가 전혀 없는 일반 텍스트 가사일 경우 균등 분할
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

    private fun generateSmartFallback(
        songId: String,
        title: String,
        artist: String,
        durationMs: Long
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

    fun getElegantAuraColors(title: String, artist: String): String {
        // 타이틀과 아티스트의 해시를 기반으로 아름답고 조화로운 애플뮤직 스타일 오로라 팔레트 반환
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
