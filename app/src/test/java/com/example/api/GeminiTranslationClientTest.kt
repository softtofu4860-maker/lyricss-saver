package com.example.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PersonalApiKeyStore
import com.example.data.TestAesCipher
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class GeminiTranslationClientTest {
    @Test fun `saved personal key is actually sent in a header and never in request URL`() = runBlocking {
        val store = PersonalApiKeyStore(ApplicationProvider.getApplicationContext<Context>(), TestAesCipher())
        store.delete(); store.set("personal-test-key")
        MockWebServer().use { server ->
            val http = OkHttpClient()
            try {
                server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"[00:01] 안녕"}]}}]}"""))
                val client = GeminiTranslationClient(store::get, http, server.url("/translate").toString())
                assertEquals("[00:01] 안녕", client.translate("[00:01] hello"))
                val request = server.takeRequest()
                assertEquals("personal-test-key", request.getHeader("x-goog-api-key"))
                assertFalse(request.path!!.contains("personal-test-key"))
                assertTrue(request.body.readUtf8().contains("hello"))
            } finally { http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll(); store.delete() }
        }
    }
    @Test fun `missing invalid rate limited server and empty results remain distinct failures`() = runBlocking {
        MockWebServer().use { server ->
            val http = OkHttpClient()
            try {
                for ((status, expected) in listOf(400 to TranslationProblem.AUTHENTICATION, 401 to TranslationProblem.AUTHENTICATION, 403 to TranslationProblem.AUTHENTICATION,
                    429 to TranslationProblem.RATE_LIMIT, 503 to TranslationProblem.SERVER, 200 to TranslationProblem.EMPTY)) {
                    server.enqueue(MockResponse().setResponseCode(status).setBody("{}"))
                    try { GeminiTranslationClient({ "bad-key" }, http, server.url("/").toString()).translate("[00:01] hello"); fail("expected error") }
                    catch (failure: TranslationFailure) { assertEquals(expected, failure.problem) }
                }
                val before = server.requestCount
                try { GeminiTranslationClient({ null }, http, server.url("/").toString()).translate("hello"); fail("missing key") }
                catch (failure: TranslationFailure) { assertEquals(TranslationProblem.MISSING_KEY, failure.problem) }
                assertEquals(before, server.requestCount)
            } finally { http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll() }
        }
    }
    @Test fun `disconnected network is reported without exposing keys in exception text`() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { throw java.io.IOException("secret must not escape") }.build()
        try {
            try { GeminiTranslationClient({ "private-key" }, http).translate("hello"); fail("expected network error") }
            catch (failure: TranslationFailure) {
                assertEquals(TranslationProblem.NETWORK, failure.problem)
                assertFalse(failure.toString().contains("private-key"))
                assertFalse(failure.toString().contains("secret"))
            }
        } finally { http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll() }
    }
}
