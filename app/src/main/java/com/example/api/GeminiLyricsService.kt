package com.example.api

import android.content.Context
import android.util.Log
import com.example.data.CachedLyrics
import com.example.data.LyricLine
import com.example.data.LyricsIdentity
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class LrclibResponse(
    val id: Long? = null, val trackName: String? = null, val artistName: String? = null,
    val albumName: String? = null, val duration: Double? = null, val instrumental: Boolean? = false,
    val plainLyrics: String? = null, val syncedLyrics: String? = null
)
data class NetEaseArtist(val id: Long? = null, val name: String? = null)
data class NetEaseSong(
    val id: Long? = null, val name: String? = null, val artists: List<NetEaseArtist>? = null,
    val durationMs: Long? = null, val album: String? = null
)

object GeminiLyricsService {
    private const val TAG = "GeminiLyricsService"
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val lyricAdapter: JsonAdapter<List<LyricLine>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, LyricLine::class.java))

    fun generateSongId(title: String, artist: String): String = LyricsIdentity.songId(title, artist)
    fun isFallbackLyrics(lyrics: CachedLyrics): Boolean =
        lyrics.genre == "Unavailable" || lyrics.lyricsJson.contains("실시간 싱크 가사를 검색하고 있습니다") ||
            lyrics.lyricsJson.contains("가사 탐색 중")

    private fun encoded(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun JSONObject.textOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val call = okHttpClient.newCall(Request.Builder().url(url)
            .header("User-Agent", "SmartMusicScreensaver/1.0")
            .header("Referer", "https://music.163.com/").build())
        try {
            // Cancel the socket as soon as the coroutine is cancelled.
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                    }
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        response.use {
                            val result = runCatching { if (it.isSuccessful) it.body?.string() else null }
                            if (continuation.isActive) continuation.resumeWith(result)
                        }
                    }
                })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Lyrics provider request failed")
            null
        }
    }

    suspend fun getLyricsForSong(
        context: Context, title: String, artist: String, metadataLyrics: String? = null,
        durationMs: Long = 0L, customQuery: String? = null, album: String? = null, trackId: String? = null
    ): CachedLyrics {
        val query = SongQuery(title, artist, album, durationMs, LyricsIdentity.spotifyTrackId(trackId))
        val metadata = metadataLyrics?.takeIf { customQuery.isNullOrBlank() && it.isNotBlank() }?.let {
            LyricsCandidate("metadata", query.trackId, title, listOf(artist), album, durationMs.takeIf { d -> d > 0 },
                rawLyrics = it, synced = LrcParser.hasValidSync(it, durationMs))
        }
        val candidates = if (metadata?.synced == true) listOf(metadata)
            else searchCandidates(query, customQuery) + listOfNotNull(metadata)
        val chosen = LyricsMatching.choose(query, candidates)
        val songId = LyricsIdentity.recordingId(title, artist, query.trackId, album, durationMs)
        if (chosen == null) return generateSmartFallback(songId, title, artist, durationMs)
        val candidate = chosen.candidate
        val lines = LrcParser.parse(candidate.rawLyrics, candidate.translationLrc, candidate.romanizationLrc)
        return CachedLyrics(
            id = songId, title = title, artist = artist, lyricsJson = lyricAdapter.toJson(lines),
            hexColorsJson = getElegantAuraColors(title, artist), trackId = query.trackId, album = album,
            durationMs = durationMs, provider = candidate.provider, providerId = candidate.providerId,
            synced = candidate.synced, matchScore = chosen.score
        )
    }

    /** Collect provider candidates before choosing; an early plain result cannot hide synced lyrics. */
    suspend fun searchCandidates(query: SongQuery, customQuery: String? = null): List<LyricsCandidate> = coroutineScope {
        val search = customQuery?.takeIf { it.isNotBlank() } ?: "${query.title} ${query.artist}"
        val spotify = async {
            if (customQuery.isNullOrBlank()) spotifyCandidate(query)?.let(::listOf).orEmpty() else emptyList()
        }
        val lrclib = async {
            val direct = if (customQuery.isNullOrBlank()) fetchLrclibDirect(query) else null
            val results = listOfNotNull(direct) + searchLrclib(search)
            results.distinctBy { it.id }.mapNotNull { candidate ->
                val synced = candidate.syncedLyrics?.takeIf { LrcParser.hasValidSync(it, query.durationMs) }
                val raw = synced ?: candidate.plainLyrics?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                LyricsCandidate("lrclib", candidate.id?.toString(), candidate.trackName.orEmpty(),
                    listOf(candidate.artistName.orEmpty()), candidate.albumName,
                    candidate.duration?.takeIf { it.isFinite() && it > 0 }?.let { (it * 1000).toLong() },
                    raw, synced = synced != null)
            }
        }
        val netEase = async {
            // Validate metadata before making per-song lyric requests.
            val shortlist = searchNetEase(search).mapNotNull { song ->
                val score = LyricsMatching.score(query, LyricsCandidate("netease", song.id?.toString(),
                    song.name.orEmpty(), song.artists.orEmpty().map { artist -> artist.name.orEmpty() },
                    song.album, song.durationMs, "", synced = false)) ?: return@mapNotNull null
                (score to song).takeIf { score >= LyricsMatching.AUTO_SELECT_SCORE }
            }.sortedByDescending { it.first }.take(3).map { it.second }
            shortlist.mapNotNull { song ->
                val bundle = song.id?.let { fetchNetEaseLyrics(it) } ?: return@mapNotNull null
                val raw = bundle.lrc?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val synced = LrcParser.hasValidSync(raw, query.durationMs)
                if (!synced && LrcParser.parseSynced(raw).isNotEmpty()) return@mapNotNull null
                LyricsCandidate("netease", song.id.toString(), song.name.orEmpty(),
                    song.artists.orEmpty().map { it.name.orEmpty() }, song.album, song.durationMs, raw,
                    bundle.tlyric, bundle.romalrc, synced)
            }
        }
        spotify.await() + lrclib.await() + netEase.await()
    }

    private suspend fun spotifyCandidate(query: SongQuery): LyricsCandidate? {
        val id = LyricsIdentity.spotifyTrackId(query.trackId) ?: return null
        val raw = fetchSpotifyLyricsApi(query.title, query.artist, id) ?: return null
        val synced = LrcParser.hasValidSync(raw, query.durationMs)
        if (!synced && LrcParser.parseSynced(raw).isNotEmpty()) return null
        return LyricsCandidate("spotify", id, query.title, listOf(query.artist), query.album,
            query.durationMs.takeIf { it > 0 }, raw, synced = synced, boundTrackId = id)
    }

    /** Documented ID-based endpoint: https://github.com/Paxsenix0/Spotify-Lyrics-API */
    suspend fun fetchSpotifyLyricsApi(title: String, artist: String, trackId: String? = null): String? {
        val id = LyricsIdentity.spotifyTrackId(trackId) ?: return null
        val url = "https://spotify-lyrics-api-pi.vercel.app/api/lyrics?url=${encoded("https://open.spotify.com/track/$id")}&format=lrc"
        val body = get(url) ?: return null
        return try {
            if (!body.trimStart().startsWith("{")) {
                return body.takeIf { LrcParser.hasValidSync(it) }
            }
            val root = JSONObject(body)
            if (root.optBoolean("error", false)) return null
            val payload = root.optJSONObject("lyrics") ?: root
            payload.textOrNull("lrc")?.let { return it }
            val lines = payload.optJSONArray("lines") ?: return null
            (0 until lines.length()).mapNotNull { index ->
                val line = lines.getJSONObject(index)
                val words = line.textOrNull("words") ?: return@mapNotNull null
                val start = line.optLong("startTimeMs", -1)
                val timeTag = line.textOrNull("timeTag")
                when {
                    payload.optString("syncType") == "UNSYNCED" -> words
                    start >= 0 -> String.format(java.util.Locale.US, "[%02d:%02d.%03d]%s",
                        start / 60000, (start / 1000) % 60, start % 1000, words)
                    timeTag != null -> "[$timeTag]$words"
                    else -> words
                }
            }.joinToString("\n").takeIf { it.isNotBlank() }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { null }
    }

    private fun lrclibResponse(item: JSONObject): LrclibResponse = LrclibResponse(
        id = item.optLong("id").takeIf { it > 0 }, trackName = item.textOrNull("trackName"),
        artistName = item.textOrNull("artistName"), albumName = item.textOrNull("albumName"),
        duration = item.optDouble("duration").takeIf { it.isFinite() && it > 0 },
        instrumental = item.optBoolean("instrumental"), plainLyrics = item.textOrNull("plainLyrics"),
        syncedLyrics = item.textOrNull("syncedLyrics")
    )

    private suspend fun fetchLrclibDirect(query: SongQuery): LrclibResponse? {
        var url = "https://lrclib.net/api/get?track_name=${encoded(query.title)}&artist_name=${encoded(query.artist)}"
        if (!query.album.isNullOrBlank()) url += "&album_name=${encoded(query.album)}"
        if (query.durationMs > 0) url += "&duration=${query.durationMs / 1000}"
        return get(url)?.let { runCatching { lrclibResponse(JSONObject(it)) }.getOrNull() }
    }

    suspend fun searchLrclib(query: String): List<LrclibResponse> {
        val raw = get("https://lrclib.net/api/search?q=${encoded(query)}") ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { lrclibResponse(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    suspend fun searchNetEase(query: String): List<NetEaseSong> {
        val raw = get("https://music.163.com/api/search/get/web?csrf_token=&s=${encoded(query)}&type=1&offset=0&total=true&limit=10")
            ?: return emptyList()
        return runCatching {
            val songs = JSONObject(raw).optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
            (0 until songs.length()).map { index ->
                val song = songs.getJSONObject(index)
                val rawArtists = song.optJSONArray("artists") ?: song.optJSONArray("ar") ?: JSONArray()
                NetEaseSong(
                    song.optLong("id").takeIf { it > 0 }, song.textOrNull("name"),
                    (0 until rawArtists.length()).map {
                        val a = rawArtists.getJSONObject(it)
                        NetEaseArtist(a.optLong("id"), a.textOrNull("name"))
                    },
                    song.optLong("duration").takeIf { it > 0 } ?: song.optLong("dt").takeIf { it > 0 },
                    (song.optJSONObject("album") ?: song.optJSONObject("al"))?.textOrNull("name")
                )
            }
        }.getOrDefault(emptyList())
    }

    data class NetEaseLyricsBundle(val lrc: String?, val tlyric: String?, val romalrc: String?)
    suspend fun fetchNetEaseLyrics(songId: Long): NetEaseLyricsBundle? {
        if (songId <= 0) return null
        val raw = get("https://music.163.com/api/song/lyric?os=pc&id=$songId&lv=-1&kv=-1&tv=-1") ?: return null
        return runCatching {
            val root = JSONObject(raw)
            NetEaseLyricsBundle(root.optJSONObject("lrc")?.textOrNull("lyric"),
                root.optJSONObject("tlyric")?.textOrNull("lyric"), root.optJSONObject("romalrc")?.textOrNull("lyric"))
        }.getOrNull()
    }

    suspend fun fetchLrcmuxLyrics(title: String, artist: String): String? =
        LyricsMatching.choose(SongQuery(title, artist), searchCandidates(SongQuery(title, artist)))?.candidate?.rawLyrics

    fun parseLrcLyrics(lrcText: String, durationMs: Long = 0L,
        translationLrc: String? = null, romanizationLrc: String? = null): List<LyricLine> =
        LrcParser.parse(lrcText, translationLrc, romanizationLrc)

    fun parseJsonLyrics(jsonStr: String): List<LyricLine> =
        runCatching { lyricAdapter.fromJson(jsonStr) ?: emptyList() }.getOrDefault(emptyList())

    fun generateSmartFallback(songId: String, title: String, artist: String, durationMs: Long = 0L): CachedLyrics =
        CachedLyrics(id = songId, title = title, artist = artist,
            lyricsJson = lyricAdapter.toJson(listOf(LyricLine(0f, "가사를 찾지 못했습니다. 화면을 탭하여 직접 검색해 주세요."))),
            hexColorsJson = getElegantAuraColors(title, artist), genre = "Unavailable", durationMs = durationMs)

    suspend fun translateLyricsViaGemini(rawLrcText: String, context: Context): String = withContext(Dispatchers.IO) {
        val apiKey = context.getSharedPreferences("screensaver_prefs", Context.MODE_PRIVATE)
            .getString("gemini_api_key", "")?.trim().orEmpty()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            error("설정에서 개인 Gemini API 키를 입력해 주세요.")
        }
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
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
                .header("x-goog-api-key", apiKey)
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("번역 요청에 실패했습니다 (HTTP ${resp.code}).")
                val body = resp.body?.string() ?: error("번역 응답이 비어 있습니다.")
                val respJson = JSONObject(body)
                val candidates = respJson.optJSONArray("candidates") ?: error("번역 결과가 없습니다.")
                if (candidates.length() > 0) {
                    val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                    val text = parts?.getJSONObject(0)?.optString("text")
                    if (!text.isNullOrBlank()) {
                        return@withContext text.replace("```lrc", "").replace("```", "").trim()
                    }
                }
            }
            error("번역 결과가 비어 있습니다.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Do not log request URLs or credentials.
            throw IllegalStateException("번역에 실패했습니다. API 키와 네트워크를 확인해 주세요.", e)
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
        val selected = palettes[Math.floorMod(hash, palettes.size)]
        val jsonArray = JSONArray()
        selected.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }
}
