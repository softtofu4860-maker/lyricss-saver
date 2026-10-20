package com.example.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.api.AutomaticLyricsSearch
import com.example.api.LrclibResponse
import com.example.api.readText
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SearchDiagnosticsIntegrationTest {
    private fun session(): AdminSession {
        LyricsDiagnostics.initialize(ApplicationProvider.getApplicationContext<Context>())
        return AdminSession { 0L }.also { it.completeDeviceAuthentication(true, true) }
    }

    @Test fun `HTTP errors record status and host without request secrets or response text`() = runBlocking {
        val session = session()
        LyricsDiagnostics.clear(session)
        MockWebServer().use { server ->
            val client = OkHttpClient()
            try {
                server.enqueue(MockResponse().setResponseCode(503).setBody("private response lyrics"))
                assertNull(client.readText(Request.Builder().url(server.url("/lyrics?key=private-api-key"))
                    .header("Authorization", "Bearer private-token").build()))
                val log = LyricsDiagnostics.read(session)
                assertTrue(log.contains("HTTP_RESPONSE"))
                assertTrue(log.contains("\"status\":503"))
                assertTrue(log.contains("\"host\""))
                assertFalse(log.contains("private"))
                assertFalse(log.contains("/lyrics"))
            } finally {
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }
    }

    @Test fun `automatic candidate rejection records its reason without lyric payload`() = runBlocking {
        val session = session()
        LyricsDiagnostics.clear(session)
        val search = AutomaticLyricsSearch(direct = { _, _, _ -> null },
            lrclib = { listOf(LrclibResponse(trackName = "song", artistName = "artist", duration = 300.0,
                syncedLyrics = "[00:01] private lyric payload")) },
            netEase = { emptyList() }, loadNetEase = { emptyList() })
        assertTrue(search.find("song", "artist", 180000).isEmpty())
        val log = LyricsDiagnostics.read(session)
        assertTrue(log.contains("CANDIDATES"))
        assertTrue(log.contains("duration_mismatch"))
        assertTrue(log.contains("NO_MATCH"))
        assertFalse(log.contains("private lyric payload"))
    }
}
