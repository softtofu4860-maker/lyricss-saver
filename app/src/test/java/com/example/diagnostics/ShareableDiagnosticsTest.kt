package com.example.diagnostics

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShareableDiagnosticsTest {
    @Test fun `export omits song identity queries and unknown secret fields`() {
        val input = """{"time":"2026","stage":"FAILURE","title":"private song","artist":"private artist","query":"private query","song":"private id","apiKey":"private key","status":403}"""
        val output = JSONObject(ShareableDiagnostics.redact(input))
        assertEquals(403, output.getInt("status"))
        assertEquals("FAILURE", output.getString("stage"))
        listOf("title", "artist", "query", "song", "apiKey").forEach { assertFalse(output.has(it)) }
        assertEquals("", ShareableDiagnostics.redact("not JSON"))
    }
}
