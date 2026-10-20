package com.example.api

import com.example.data.LyricLine
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AutomaticLyricsSearchTest {
    @Test fun `automatic search cleans metadata and reaches the title-only manual result`() = runTest {
        val queries = mutableListOf<String>()
        val search = AutomaticLyricsSearch(
            direct = { title, artist, _ -> assertEquals("봄", title); assertEquals("아이유", artist); null },
            lrclib = { query ->
                queries.add(query)
                when (query) {
                    "봄 아이유" -> listOf(LrclibResponse(trackName = "봄", artistName = "커버 가수", syncedLyrics = "[00:01] wrong"))
                    "봄 IU" -> throw IOException("temporary request failure")
                    else -> listOf(LrclibResponse(trackName = "봄", artistName = "IU", duration = 180.0, syncedLyrics = "[00:01] found"))
                }
            }, netEase = { error("successful LRCLIB lookup should not contact NetEase") }, loadNetEase = { emptyList() })
        val lines = search.find("봄 (Official Video)", "아이유 (IU)", 180000)
        assertEquals(listOf("봄 아이유", "봄 IU", "봄"), queries)
        assertEquals("found", lines.single().text)
    }

    @Test fun `automatic NetEase lookup tries next valid song when first has no lyrics`() = runTest {
        val loaded = mutableListOf<Long>()
        val search = AutomaticLyricsSearch(direct = { _, _, _ -> null }, lrclib = { emptyList() },
            netEase = { listOf(NetEaseSong(1, "song", listOf(NetEaseArtist(name = "artist"))),
                NetEaseSong(2, "song", listOf(NetEaseArtist(name = "artist")))) },
            loadNetEase = { id -> loaded.add(id); if (id == 2L) listOf(LyricLine(1f, "found")) else emptyList() })
        assertEquals("found", search.find("song", "artist", 180000).single().text)
        assertEquals(listOf(1L, 2L), loaded)
    }

    @Test fun `source timeout still falls back and changing songs cancels pending requests`() = runTest {
        var fallbackCalls = 0
        val search = AutomaticLyricsSearch(direct = { _, _, _ -> awaitCancellation() }, lrclib = { emptyList() },
            netEase = { fallbackCalls++; listOf(NetEaseSong(1, "song", listOf(NetEaseArtist(name = "artist")))) },
            loadNetEase = { listOf(LyricLine(1f, "fallback")) })
        assertEquals("fallback", search.find("song", "artist", 0).single().text)
        assertEquals(1, fallbackCalls)
        val started = CompletableDeferred<Unit>()
        val cancelledSearch = AutomaticLyricsSearch(direct = { _, _, _ -> started.complete(Unit); awaitCancellation() },
            lrclib = { emptyList() }, netEase = { error("cancelled song must not start a new source") }, loadNetEase = { emptyList() })
        val job = launch { cancelledSearch.find("old", "artist", 0) }
        started.await()
        job.cancelAndJoin()
    }

    @Test fun `missing artist does not stop searching but ambiguous results are rejected`() = runTest {
        val first = LrclibResponse(trackName = "song", artistName = "one", syncedLyrics = "[00:01] one")
        var results = listOf(first, first.copy(artistName = "two"))
        val search = AutomaticLyricsSearch(direct = { _, _, _ -> error("artist is missing") },
            lrclib = { results }, netEase = { emptyList() }, loadNetEase = { emptyList() })
        assertTrue(search.find("song", "", 0).isEmpty())
        results = listOf(first)
        assertEquals("one", search.find("song", "", 0).single().text)
    }
}
