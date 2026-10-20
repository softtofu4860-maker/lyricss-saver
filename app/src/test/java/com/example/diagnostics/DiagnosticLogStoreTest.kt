package com.example.diagnostics

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiagnosticLogStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun authenticated() = AdminSession { 0L }.also { it.completeDeviceAuthentication(true, true) }

    @Test fun `locked and expired sessions cannot read or clear existing logs`() = runTest {
        val store = DiagnosticLogStore(temporary.newFolder(), dispatcher = StandardTestDispatcher(testScheduler))
        try {
            store.record(DiagnosticStage.HTTP_RESPONSE, "http", "song", mapOf("status" to 403))
            val locked = AdminSession { 0L }
            try { store.read(locked); fail("locked read") } catch (_: SecurityException) {}
            try { store.clear(locked); fail("locked clear") } catch (_: SecurityException) {}
            assertTrue(store.read(authenticated()).contains("403"))
            var now = 0L
            val expires = AdminSession { now }.also { it.completeDeviceAuthentication(true, true) }
            now = AdminSession.SESSION_MS
            try { store.read(expires); fail("expired read") } catch (_: SecurityException) {}
            assertTrue(store.read(authenticated()).contains("403"))
        } finally { store.close() }
    }

    @Test fun `queued reads recheck authentication after the screen locks`() = runTest {
        val store = DiagnosticLogStore(temporary.newFolder(), dispatcher = StandardTestDispatcher(testScheduler))
        try {
            val session = authenticated()
            val request = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { store.read(session) }
            }
            // Revoke before the pending operation can expose the log.
            session.lock()
            runCurrent()
            assertTrue(request.await().exceptionOrNull() is SecurityException)
        } finally { store.close() }
    }

    @Test fun `logs persist as JSON lines but never serialize bodies keys or full URLs`() = runTest {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = DiagnosticLogStore(directory, dispatcher = dispatcher)
        val key = "AIza" + "x".repeat(35)
        store.record(DiagnosticStage.QUERY, "lrclib", "song", mapOf(
            "query" to "test $key https://example.com/?key=private-token",
            "title" to "track\nname", "lyrics" to "private lyrics", "apiKey" to "private-key",
            "body" to "private response"))
        store.close()
        val reopened = DiagnosticLogStore(directory, dispatcher = dispatcher)
        try {
            val raw = reopened.read(authenticated())
            assertFalse(raw.contains(key))
            assertFalse(raw.contains("private"))
            assertFalse(raw.contains("https://"))
            val json = JSONObject(raw)
            assertEquals("QUERY", json.getString("stage"))
            assertEquals("track name", json.getString("title"))
            assertFalse(json.has("lyrics"))
            assertFalse(json.has("apiKey"))
            assertFalse(json.has("body"))
        } finally { reopened.close() }
    }

    @Test fun `rotation bounds disk use keeps latest events and authenticated clear removes both files`() = runTest {
        val directory = temporary.newFolder()
        val store = DiagnosticLogStore(directory, maxBytes = 1024, dispatcher = StandardTestDispatcher(testScheduler))
        try {
            repeat(50) { store.record(DiagnosticStage.RETRY, "automatic", "song", mapOf("attempt" to it)) }
            val raw = store.read(authenticated())
            assertTrue(raw.contains("\"attempt\":49"))
            assertTrue(directory.listFiles()!!.size <= 2)
            assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 2048)
            raw.lines().filter(String::isNotBlank).forEach { JSONObject(it) }
            store.clear(authenticated())
            assertEquals("", store.read(authenticated()))
        } finally { store.close() }
    }

    @Test fun `unwritable log storage does not stop the writer or propagate into lyric requests`() = runTest {
        val store = DiagnosticLogStore(temporary.newFile(), dispatcher = StandardTestDispatcher(testScheduler))
        try {
            store.record(DiagnosticStage.SEARCH_START, "automatic", "song", emptyMap())
            assertEquals("", store.read(authenticated()))
        } finally { store.close() }
    }
}
