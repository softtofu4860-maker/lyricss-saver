package com.example.api

import android.content.Context
import android.util.Log
import com.example.data.CachedLyrics
import com.example.data.LyricLine
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.data.SongIdentity
import kotlinx.coroutines.CancellationException
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
import com.example.diagnostics.DiagnosticStage
import com.example.diagnostics.LyricsDiagnostics

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
        return SongIdentity.id(title, artist)
    }

    fun isFallbackLyrics(lyrics: CachedLyrics): Boolean {
        return lyrics.isFallback
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

        // An explicit correction must use only the user's query, not the old metadata
        // or a successful lookup for the incorrectly identified track.
        val query = customQuery?.trim()?.takeIf { it.isNotEmpty() }
        if (query != null) {
            val lrc = fetchLrclibFirstResult(query)
            val parsed = lrc?.let { parseLrcLyrics(it, durationMs) }.orEmpty()
            return@withContext if (parsed.isNotEmpty()) CachedLyrics(
                id = songId, title = title, artist = artist,
                lyricsJson = lyricAdapter.toJson(parsed),
                hexColorsJson = getElegantAuraColors(title, artist)
            ) else generateSmartFallback(songId, title, artist, durationMs)
        }

        // 1. 메타데이터에 가사가 이미 있는 경우 (LRC 형식 등)
        if (!metadataLyrics.isNullOrBlank()) {
            val parsed = parseLrcLyrics(metadataLyrics, durationMs)
            if (parsed.isNotEmpty()) {
                com.example.diagnostics.LyricsDiagnostics.record(com.example.diagnostics.DiagnosticStage.METADATA_USED,
                    "media", songId, mapOf("count" to parsed.size))
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

        val parsed = AutomaticLyricsSearch(
            direct = ::fetchLrclibDirect,
            lrclib = ::searchLrclib,
            netEase = ::searchNetEase,
            loadNetEase = { id -> fetchNetEaseLyrics(id)?.parse(durationMs).orEmpty() }
        ).find(title, artist, durationMs)
        if (parsed.isNotEmpty()) return@withContext CachedLyrics(
            id = songId, title = title, artist = artist,
            lyricsJson = lyricAdapter.toJson(parsed),
            hexColorsJson = getElegantAuraColors(title, artist)
        )

        // 5. Fallback: 스마트 가사 라인 생성 (가사 없음 표시 및 타임라인 안내)
        generateSmartFallback(songId, title, artist, durationMs)
    }

    suspend fun fetchSpotifyLyricsApi(title: String, artist: String): String? {
        return try {
            val url = "https://spotify-lyrics-api-pi.vercel.app/?format=lrc&name=${encode(title.trim())}&artist=${encode(artist.trim())}"
            val body = okHttpClient.readText(Request.Builder().url(url)
                .header("User-Agent", "SmartMusicScreensaver/1.0").build()) ?: return null
            if (!body.trimStart().startsWith("{")) return body.takeIf { it.contains("[") }
            val json = JSONObject(body)
            if (json.optBoolean("error", false)) return null
            json.optString("lrc").takeIf { it.isNotBlank() }?.let { return it }
            val lines = json.optJSONArray("lines") ?: return null
            buildString {
                for (i in 0 until lines.length()) {
                    val line = lines.getJSONObject(i)
                    val words = line.optString("words").trim()
                    val startMs = line.optLong("startTimeMs", -1L)
                    if (startMs >= 0 && words.isNotBlank()) append(String.format(java.util.Locale.US,
                        "[%02d:%02d.%02d]%s\n", startMs / 60000, startMs % 60000 / 1000,
                        startMs % 1000 / 10, words))
                }
            }.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "fetchSpotifyLyricsApi failed: ${e.message}")
            null
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun diagnosticFailure(source: String, error: Exception) {
        LyricsDiagnostics.record(DiagnosticStage.FAILURE, source, fields = mapOf("error" to error.javaClass.simpleName))
    }

    private suspend fun fetchLrclibDirect(title: String, artist: String, durationMs: Long): String? {
        return try {
            val duration = if (durationMs > 0) "&duration=${durationMs / 1000}" else ""
            val url = "https://lrclib.net/api/get?artist_name=${encode(artist)}&track_name=${encode(title)}$duration"
            val body = okHttpClient.readText(Request.Builder().url(url)
                .header("User-Agent", "SmartMusicScreensaver/1.0").build()) ?: return null
            val json = JSONObject(body)
            val matched = LyricsSearchRanking.isMatch(LrclibResponse(
                trackName = json.optString("trackName"), artistName = json.optString("artistName"),
                duration = json.optDouble("duration"), instrumental = json.optBoolean("instrumental")
            ), title, artist, durationMs)
            if (!matched) return null
            json.optString("syncedLyrics").takeUnless { it.isBlank() || it == "null" }
                ?: json.optString("plainLyrics").takeUnless { it.isBlank() || it == "null" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            diagnosticFailure("lrclib_exact", e)
            null
        }
    }

    private suspend fun fetchLrclibFirstResult(query: String): String? {
        val results = searchLrclib(query)
        return results
            .sortedByDescending { resultRelevance(resultQuery = query, title = it.trackName, artist = it.artistName) }
            .firstOrNull { !it.syncedLyrics.isNullOrBlank() }?.syncedLyrics
            ?: results
                .sortedByDescending { resultRelevance(query, it.trackName, it.artistName) }
                .firstOrNull { !it.plainLyrics.isNullOrBlank() }?.plainLyrics
    }

    /** Scores search results so a broad API response cannot silently show another song. */
    private fun resultRelevance(resultQuery: String, title: String?, artist: String?): Int {
        val queryTokens = searchTokens(resultQuery)
        val titleTokens = searchTokens(title.orEmpty())
        val artistTokens = searchTokens(artist.orEmpty())
        if (queryTokens.isEmpty()) return 0
        val titleHits = queryTokens.count { token -> titleTokens.any { it == token || it.contains(token) || token.contains(it) } }
        val artistHits = queryTokens.count { token -> artistTokens.any { it == token || it.contains(token) || token.contains(it) } }
        return titleHits * 3 + artistHits * 2
    }

    private fun searchTokens(value: String): Set<String> = value.lowercase(java.util.Locale.ROOT)
        .replace("&", " and ")
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 2 && it !in setOf("the", "and", "feat", "with", "version", "remix") }
        .toSet()

    suspend fun searchLrclib(query: String): List<LrclibResponse> {
        LyricsDiagnostics.record(DiagnosticStage.QUERY, "lrclib_api", fields = mapOf("query" to query))
        return try {
            val body = okHttpClient.readText(Request.Builder()
                .url("https://lrclib.net/api/search?q=${encode(query)}")
                .header("User-Agent", "SmartMusicScreensaver/1.0").build()) ?: return emptyList()
            val array = JSONArray(body)
            List(array.length()) { i ->
                val item = array.getJSONObject(i)
                LrclibResponse(
                    id = item.optLong("id"), trackName = item.optString("trackName"),
                    artistName = item.optString("artistName"), albumName = item.optString("albumName"),
                    duration = item.optDouble("duration"), instrumental = item.optBoolean("instrumental"),
                    plainLyrics = item.optString("plainLyrics").takeUnless { it.isBlank() || it == "null" },
                    syncedLyrics = item.optString("syncedLyrics").takeUnless { it.isBlank() || it == "null" }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "searchLrclib error: ${e.message}")
            diagnosticFailure("lrclib_api", e)
            emptyList()
        }
    }

    suspend fun searchNetEase(query: String): List<NetEaseSong> {
        LyricsDiagnostics.record(DiagnosticStage.QUERY, "netease_api", fields = mapOf("query" to query))
        return try {
            NetEaseSongReference.parse(query)?.let { id ->
                val body = okHttpClient.readText(netEaseRequest(
                    "https://music.163.com/api/song/detail?ids=${encode("[$id]")}"
                )) ?: return emptyList()
                val song = JSONObject(body).optJSONArray("songs")?.optJSONObject(0) ?: return emptyList()
                val artists = song.optJSONArray("artists") ?: song.optJSONArray("ar")
                return listOf(NetEaseSong(id, song.optString("name"),
                    artists?.let { array -> List(array.length()) { index ->
                        val artist = array.getJSONObject(index)
                        NetEaseArtist(artist.optLong("id"), artist.optString("name"))
                    } }.orEmpty()))
            }
            val url = "https://music.163.com/api/search/get/web?csrf_token=&s=${encode(query)}&type=1&offset=0&total=true&limit=10"
            val body = okHttpClient.readText(netEaseRequest(url)) ?: return emptyList()
            val songs = JSONObject(body).optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
            List(songs.length()) { i ->
                val song = songs.getJSONObject(i)
                val artists = song.optJSONArray("artists")
                NetEaseSong(song.optLong("id"), song.optString("name"),
                    if (artists == null) emptyList() else List(artists.length()) { j ->
                        val artist = artists.getJSONObject(j)
                        NetEaseArtist(artist.optLong("id"), artist.optString("name"))
                    })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "searchNetEase error: ${e.message}")
            diagnosticFailure("netease_api", e)
            emptyList()
        }
    }

    data class NetEaseLyricsBundle(val lrc: String?, val tlyric: String?, val romalrc: String?, val yrc: String? = null) {
        fun parse(durationMs: Long): List<LyricLine> {
            val wordLines = yrc?.let { parseLrcLyrics(it, durationMs, tlyric, romalrc) }.orEmpty()
            return wordLines.takeIf { it.isNotEmpty() } ?: parseLrcLyrics(lrc.orEmpty(), durationMs, tlyric, romalrc)
        }
    }

    private fun netEaseRequest(url: String) = Request.Builder().url(url)
        .header("User-Agent", "Mozilla/5.0").header("Referer", "https://music.163.com/").build()

    private suspend fun fetchNetEaseWithTranslation(title: String, artist: String): NetEaseLyricsBundle? {
        return try {
            val songId = searchNetEase("$title $artist")
                .filter { LyricsSearchRanking.isMatch(LrclibResponse(
                    trackName = it.name, artistName = it.artists.orEmpty().joinToString(" ") { a -> a.name.orEmpty() }
                ), title, artist, 0) }
                .maxByOrNull { resultRelevance("$title $artist", it.name, it.artists.orEmpty().joinToString(" ") { a -> a.name.orEmpty() }) }
                ?.takeIf { resultRelevance("$title $artist", it.name, it.artists.orEmpty().joinToString(" ") { a -> a.name.orEmpty() }) >= 3 }
                ?.id ?: return null
            fetchNetEaseLyrics(songId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Load exactly the chosen result rather than searching its title again. */
    suspend fun fetchNetEaseLyrics(songId: Long): NetEaseLyricsBundle? {
        return try {
            val body = okHttpClient.readText(netEaseRequest(
                "https://music.163.com/api/song/lyric?os=pc&id=$songId&lv=-1&kv=-1&tv=-1"
            )) ?: return null
            val root = JSONObject(body)
            NetEaseLyricsBundle(
                root.optJSONObject("lrc")?.optString("lyric")?.ifBlank { null },
                root.optJSONObject("tlyric")?.optString("lyric")?.ifBlank { null },
                root.optJSONObject("romalrc")?.optString("lyric")?.ifBlank { null },
                root.optJSONObject("yrc")?.optString("lyric")?.ifBlank { null }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            diagnosticFailure("netease_lyrics", e)
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
    ): List<LyricLine> = com.example.data.LyricsParser.parse(
        lrcText, durationMs, translationLrc, romanizationLrc)

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
            LyricLine(12f, "일치하는 가사를 찾지 못했습니다"),
            LyricLine(totalSec * 0.35f, "화면을 탭하여 제목·아티스트로 직접 검색해 주세요"),
            LyricLine(totalSec * 0.75f, "♪ 즐거운 음악 감상 되세요")
        )
        val json = lyricAdapter.toJson(lines)
        return CachedLyrics(
            id = songId,
            title = title,
            artist = artist,
            lyricsJson = json,
            isFallback = true,
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

            val body = okHttpClient.readText(request) ?: return@withContext rawLrcText
            val respJson = JSONObject(body)
            val candidates = respJson.optJSONArray("candidates") ?: return@withContext rawLrcText
            val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text")
            if (!text.isNullOrBlank()) text.replace("```lrc", "").replace("```", "").trim() else rawLrcText
        } catch (e: CancellationException) {
            throw e
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
        val selected = palettes[(hash and Int.MAX_VALUE) % palettes.size]
        val jsonArray = JSONArray()
        selected.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }
}
