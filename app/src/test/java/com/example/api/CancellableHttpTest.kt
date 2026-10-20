package com.example.api

import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class CancellableHttpTest {
    @Test fun `returns body for success and null for HTTP error`() = runBlocking {
        MockWebServer().use { server ->
            val client = OkHttpClient()
            try {
                server.enqueue(MockResponse().setBody("lyrics"))
                server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
                val request = Request.Builder().url(server.url("/lyrics")).build()
                assertEquals("lyrics", client.readText(request))
                assertNull(client.readText(request))
            } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
        }
    }

    @Test fun `coroutine cancellation cancels underlying HTTP call`() = runBlocking {
        MockWebServer().use { server ->
            val failed = CompletableDeferred<Unit>()
            val client = OkHttpClient.Builder().eventListener(object : EventListener() {
                override fun callFailed(call: Call, ioe: IOException) { failed.complete(Unit) }
            }).build()
            try {
                server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
                val job = launch(Dispatchers.IO) {
                    client.readText(Request.Builder().url(server.url("/slow")).build())
                }
                assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                job.cancelAndJoin()
                withTimeout(5000) { failed.await() }
                assertTrue(job.isCancelled)
            } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
        }
    }
}
