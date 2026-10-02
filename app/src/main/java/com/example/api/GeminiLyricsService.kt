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

data class NetEaseArtist(val id: Long? = null, val name: String? = null)
data class NetEaseSong(val id: Long? = null, val name: String? = null, val artists: List<NetEaseArtist>? = null)

object GeminiLyricsService {
    private const val TAG = "GeminiLyricsService"
    private val okHttpClient = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val lyricListType = Types.newParameterizedType(List::class.java, LyricLine::class.java)
    val lyricAdapter: JsonAdapter<List<LyricLine>> = moshi.adapter(lyricListType)

    fun generateSongId(title: String, artist: String): String = "${title.trim().lowercase()}_${artist.trim().lowercase()}".replace(Regex("[^a-zA-Z0-9가-힣_]"), "")
    fun isFallbackLyrics(lyrics: CachedLyrics): Boolean = lyrics.lyricsJson.contains("실시간 싱크 가사를 검색하고 있습니다") || lyrics.lyricsJson.contains("가사 탐색 중")

    suspend fun getLyricsForSong(context: Context, title: String, artist: String, metadataLyrics: String? = null, durationMs: Long = 0L, customQuery: String? = null): CachedLyrics = withContext(Dispatchers.IO) {
        val songId = generateSongId(title, artist)
        if (!metadataLyrics.isNullOrBlank()) {
            val parsed = parseLrcLyrics(metadataLyrics, durationMs)
            if (parsed.isNotEmpty()) return@withContext cached(songId, title, artist, parsed)
        }
        try {
            val spotify = fetchSpotifyLyricsApi(title, artist)
            if (!spotify.isNullOrBlank()) {
                val parsed = parseLrcLyrics(spotify, durationMs)
                if (parsed.isNotEmpty()) return@withContext cached(songId, title, artist, parsed)
            }
        } catch (e: Exception) { Log.w(TAG, "Spotify lookup error: ${e.message}") }

        try {
            val direct = fetchLrclibDirectResult(title, artist, durationMs)
            val best = direct ?: findBestLrclibResult(customQuery ?: "$title $artist", title, artist, durationMs)
            if (best != null) {
                val raw = best.syncedLyrics ?: best.plainLyrics
                if (!raw.isNullOrBlank()) {
                    val parsed = parseLrcLyrics(raw, durationMs)
                    if (parsed.isNotEmpty()) return@withContext cached(songId, title, artist, parsed)
                }
            }
        } catch (e: Exception) { Log.w(TAG, "LRCLIB lookup error: ${e.message}") }

        try {
            val netEaseResult = fetchNetEaseWithTranslation(title, artist, durationMs)
            if (netEaseResult != null && !netEaseResult.lrc.isNullOrBlank()) {
                val parsed = parseLrcLyrics(netEaseResult.lrc, durationMs, netEaseResult.tlyric, netEaseResult.romalrc)
                if (parsed.isNotEmpty()) return@withContext cached(songId, title, artist, parsed)
            }
        } catch (e: Exception) { Log.w(TAG, "NetEase lookup error: ${e.message}") }
        generateSmartFallback(songId, title, artist, durationMs)
    }

    private fun cached(id: String, title: String, artist: String, lines: List<LyricLine>): CachedLyrics = CachedLyrics(id, title, artist, lyricAdapter.toJson(lines), getElegantAuraColors(title, artist))

    private fun fetchLrclibDirectResult(title: String, artist: String, durationMs: Long): LrclibResponse? {
        return try {
            val t = URLEncoder.encode(title, "UTF-8"); val a = URLEncoder.encode(artist, "UTF-8")
            val d = if (durationMs > 0) "&duration=${durationMs / 1000}" else ""
            val req = Request.Builder().url("https://lrclib.net/api/get?artist_name=$a&track_name=$t$d").header("User-Agent", "SmartMusicScreensaver/1.0").build()
            okHttpClient.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return null
                val j = JSONObject(r.body?.string() ?: return null)
                LrclibResponse(j.optLong("id"), j.optString("trackName"), j.optString("artistName"), j.optString("albumName"), j.optDouble("duration"), j.optBoolean("instrumental"), j.optString("plainLyrics").ifBlank { null }, j.optString("syncedLyrics").ifBlank { null })
            }
        } catch (_: Exception) { null }
    }

    private fun findBestLrclibResult(query: String, title: String, artist: String, durationMs: Long): LrclibResponse? {
        val results = searchLrclib(query)
        return results.map { it to scoreLrclib(it, title, artist, durationMs) }
            .filter { it.second >= 70 }
            .maxByOrNull { it.second }?.first
    }

    private fun scoreLrclib(r: LrclibResponse, title: String, artist: String, durationMs: Long): Int {
        var score = 0
        if (LyricsMatchValidator.titleMatches(title, r.trackName)) score += 45
        else if (LyricsMatchValidator.titleSimilar(title, r.trackName)) score += 25
        if (LyricsMatchValidator.artistMatches(artist, r.artistName)) score += 35
        if (!r.syncedLyrics.isNullOrBlank()) score += 15
        if (durationMs > 0 && r.duration != null && r.duration > 0) {
            val diff = kotlin.math.abs(r.duration * 1000.0 - durationMs)
            score += when {
                diff <= 2000 -> 15
                diff <= 5000 -> 10
                diff <= 15000 -> 5
                else -> -20
            }
        }
        return score
    }

    suspend fun fetchSpotifyLyricsApi(title: String, artist: String): String? = withContext(Dispatchers.IO) {
        try {
            val n = URLEncoder.encode(title.trim(), "UTF-8"); val a = URLEncoder.encode(artist.trim(), "UTF-8")
            val req = Request.Builder().url("https://spotify-lyrics-api-pi.vercel.app/?format=lrc&name=$n&artist=$a").header("User-Agent", "SmartMusicScreensaver/1.0").build()
            okHttpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                if (body.startsWith("{")) {
                    val json = JSONObject(body)
                    if (json.optBoolean("error", false)) return@use null
                    val lrc = json.optString("lrc")
                    if (lrc.isNotBlank()) return@use lrc
                    val lines = json.optJSONArray("lines") ?: return@use null
                    val sb = StringBuilder()
                    for (i in 0 until lines.length()) {
                        val o = lines.getJSONObject(i); val words = o.optString("words").trim(); val ms = o.optLong("startTimeMs", -1)
                        if (ms >= 0 && words.isNotBlank()) sb.append(String.format(java.util.Locale.US, "[%02d:%02d.%02d]%s\n", ms / 60000, (ms % 60000) / 1000, (ms % 1000) / 10, words))
                    }
                    if (sb.isNotEmpty()) return@use sb.toString()
                } else if (body.contains("[")) return@use body
                null
            }
        } catch (_: Exception) { null }
    }

    fun searchLrclib(query: String): List<LrclibResponse> {
        val list = mutableListOf<LrclibResponse>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder().url("https://lrclib.net/api/search?q=$encoded").header("User-Agent", "SmartMusicScreensaver/1.0").build()
            okHttpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return list
                val array = JSONArray(response.body?.string() ?: return list)
                for (i in 0 until array.length()) {
                    val j = array.getJSONObject(i)
                    list.add(LrclibResponse(j.optLong("id"), j.optString("trackName"), j.optString("artistName"), j.optString("albumName"), j.optDouble("duration"), j.optBoolean("instrumental"), j.optString("plainLyrics").ifBlank { null }, j.optString("syncedLyrics").ifBlank { null }))
                }
            }
        } catch (e: Exception) { Log.w(TAG, "searchLrclib error: ${e.message}") }
        return list
    }

    data class NetEaseLyricsBundle(val lrc: String?, val tlyric: String?, val romalrc: String?)

    private fun fetchNetEaseWithTranslation(title: String, artist: String, durationMs: Long): NetEaseLyricsBundle? {
        return try {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val searchUrl = "https://music.163.com/api/search/get/web?csrf_token=&hlpretag=&hlposttag=&s=$query&type=1&offset=0&total=true&limit=10"
            val searchReq = Request.Builder().url(searchUrl).header("User-Agent", "Mozilla/5.0").header("Referer", "https://music.163.com/").build()
            val songs = okHttpClient.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) return null
                JSONObject(resp.body?.string() ?: return null).optJSONObject("result")?.optJSONArray("songs") ?: return null
            }
            var bestId = 0L; var bestScore = Int.MIN_VALUE
            for (i in 0 until songs.length()) {
                val s = songs.getJSONObject(i); val name = s.optString("name")
                val artists = s.optJSONArray("artists")
                val names = mutableListOf<String>(); for (j in 0 until (artists?.length() ?: 0)) names += artists!!.getJSONObject(j).optString("name")
                var score = 0
                if (LyricsMatchValidator.titleMatches(title, name)) score += 50
                else if (LyricsMatchValidator.titleSimilar(title, name)) score += 25
                if (names.any { LyricsMatchValidator.artistMatches(artist, it) }) score += 40
                if (score > bestScore) { bestScore = score; bestId = s.optLong("id") }
            }
            if (bestId == 0L || bestScore < 70) return null
            val lyricUrl = "https://music.163.com/api/song/lyric?os=pc&id=$bestId&lv=-1&kv=-1&tv=-1"
            val req = Request.Builder().url(lyricUrl).header("User-Agent", "Mozilla/5.0").header("Referer", "https://music.163.com/").build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val root = JSONObject(resp.body?.string() ?: return null)
                NetEaseLyricsBundle(root.optJSONObject("lrc")?.optString("lyric")?.ifBlank { null }, root.optJSONObject("tlyric")?.optString("lyric")?.ifBlank { null }, root.optJSONObject("romalrc")?.optString("lyric")?.ifBlank { null })
            }
        } catch (_: Exception) { null }
    }

    suspend fun fetchLrcmuxLyrics(title: String, artist: String): String? = withContext(Dispatchers.IO) {
        fetchSpotifyLyricsApi(title, artist) ?: fetchNetEaseWithTranslation(title, artist, 0L)?.lrc
    }

    fun parseLrcLyrics(lrcText: String, durationMs: Long = 0L, translationLrc: String? = null, romanizationLrc: String? = null): List<LyricLine> {
        val lines = mutableListOf<LyricLine>(); val pattern = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\](.*)"); val raw = lrcText.lines(); var hasTimestamps = false
        for (line in raw) {
            val t = line.trim(); if (t.isBlank() || t.startsWith("[ti:") || t.startsWith("[ar:") || t.startsWith("[al:") || t.startsWith("[by:") || t.startsWith("[offset:")) continue
            val m = pattern.matcher(t); if (m.find()) {
                hasTimestamps = true; val min = m.group(1)?.toIntOrNull() ?: 0; val sec = m.group(2)?.toIntOrNull() ?: 0; val msS = m.group(3) ?: "0"
                val ms = when (msS.length) { 1 -> msS.toInt() * 100; 2 -> msS.toInt() * 10; else -> msS.take(3).toInt() }; val text = m.group(4)?.trim() ?: ""; val time = min * 60 + sec + ms / 1000f
                if (lines.isNotEmpty() && kotlin.math.abs(lines.last().timeSec - time) < 0.28f && lines.last().translation == null && text.isNotBlank()) lines[lines.lastIndex] = lines.last().copy(translation = text) else if (text.isNotBlank()) lines.add(LyricLine(time, text))
            }
        }
        if (!translationLrc.isNullOrBlank()) {
            val trans = parseLrcLyrics(translationLrc, durationMs); for (i in lines.indices) { val best = trans.minByOrNull { kotlin.math.abs(it.timeSec - lines[i].timeSec) }; if (best != null && kotlin.math.abs(best.timeSec - lines[i].timeSec) < 0.8f && lines[i].translation == null) lines[i] = lines[i].copy(translation = best.text) }
        }
        if (!romanizationLrc.isNullOrBlank()) {
            val rom = parseLrcLyrics(romanizationLrc, durationMs); for (i in lines.indices) { val best = rom.minByOrNull { kotlin.math.abs(it.timeSec - lines[i].timeSec) }; if (best != null && kotlin.math.abs(best.timeSec - lines[i].timeSec) < 0.8f && lines[i].romanization == null) lines[i] = lines[i].copy(romanization = best.text) }
        }
        if (!hasTimestamps) { val valid = raw.map { it.trim() }.filter { it.isNotBlank() }; if (valid.isNotEmpty()) { val total = if (durationMs > 0) durationMs / 1000f else 180f; val step = total / (valid.size + 1); valid.forEachIndexed { i, s -> lines.add(LyricLine((i + 1) * step, s)) } } }
        return lines.sortedBy { it.timeSec }
    }

    fun parseJsonLyrics(jsonStr: String): List<LyricLine> = try { lyricAdapter.fromJson(jsonStr) ?: emptyList() } catch (_: Exception) { emptyList() }

    fun generateSmartFallback(songId: String, title: String, artist: String, durationMs: Long = 0L): CachedLyrics {
        val total = if (durationMs > 0) durationMs / 1000f else 200f
        val lines = listOf(LyricLine(0f, "♪ $title"), LyricLine(5f, artist), LyricLine(12f, "실시간 싱크 가사를 검색하고 있습니다"), LyricLine(total * .35f, "가사 탐색 중... 화면을 탭하여 수동 검색할 수 있습니다"), LyricLine(total * .75f, "♪ 즐거운 음악 감상 되세요"))
        return CachedLyrics(songId, title, artist, lyricAdapter.toJson(lines), getElegantAuraColors(title, artist))
    }

    suspend fun translateLyricsViaGemini(rawLrcText: String, context: Context): String = withContext(Dispatchers.IO) {
        val apiKey = com.example.BuildConfig.GEMINI_API_KEY; if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") return@withContext rawLrcText
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val prompt = "Translate the following synchronized LRC lyrics into natural, poetic Korean while strictly preserving the timestamps like [mm:ss.xx]. Return ONLY the translated LRC text, without explanations or markdown backticks.\n\n$rawLrcText"
            val payload = JSONObject().apply { put("contents", JSONArray().apply { put(JSONObject().apply { put("parts", JSONArray().apply { put(JSONObject().apply { put("text", prompt) }) }) }) }) }
            val req = Request.Builder().url(url).post(payload.toString().toRequestBody("application/json".toMediaType())).build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext rawLrcText
                val root = JSONObject(resp.body?.string() ?: return@withContext rawLrcText); val c = root.optJSONArray("candidates") ?: return@withContext rawLrcText; val p = c.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: return@withContext rawLrcText; val text = p.getJSONObject(0).optString("text")
                if (text.isNotBlank()) text.replace("```lrc", "").replace("```", "").trim() else rawLrcText
            }
        } catch (e: Exception) { Log.e(TAG, "Gemini translation error: ${e.message}"); rawLrcText }
    }

    fun getElegantAuraColors(title: String, artist: String): String {
        val palettes = listOf(listOf("#FF4A154B", "#FF1E1B4B", "#FF0F172A", "#FF6B21A8"), listOf("#FF0369A1", "#FF1E293B", "#FF0F766E", "#FF1D4ED8"), listOf("#FF831843", "#FF4C0519", "#FF1F2937", "#FFBE185D"), listOf("#FF134E4A", "#FF064E3B", "#FF0F172A", "#FF047857"), listOf("#FF3730A3", "#FF312E81", "#FF111827", "#FF4338CA"), listOf("#FF7C2D12", "#FF451A03", "#FF1C1917", "#FFB45309"))
        val selected = palettes[kotlin.math.abs((title + artist).hashCode()) % palettes.size]; return JSONArray().apply { selected.forEach { put(it) } }.toString()
    }
}
