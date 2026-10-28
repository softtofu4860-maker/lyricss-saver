package com.example.diagnostics

import org.json.JSONObject

/** Export a strict allowlist; omit song identity, titles, artists and queries. */
internal object ShareableDiagnostics {
    private val fields = setOf("time", "stage", "source", "status", "count", "accepted", "error", "elapsedMs", "attempt", "delayMs", "reason", "durationMs", "mode", "hasLyrics", "sdk")
    fun redact(log: String): String = log.lineSequence().mapNotNull { line ->
        runCatching {
            val input = JSONObject(line)
            JSONObject().apply {
                fields.forEach { key -> if (input.has(key)) put(key, input.get(key)) }
            }.toString()
        }.getOrNull()
    }.joinToString("\n")
}
