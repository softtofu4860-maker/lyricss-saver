package com.example.diagnostics

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal enum class DiagnosticStage {
    APP_START, MEDIA_DETECTED, SEARCH_START, CACHE_HIT, METADATA_USED, QUERY,
    CANDIDATES, MATCH, NO_MATCH, SOURCE_TIMEOUT, RETRY, SUCCESS, CANCELLED, FAILURE,
    HTTP_RESPONSE, HTTP_FAILURE
}

internal object LyricsDiagnostics {
    @Volatile private var store: DiagnosticLogStore? = null

    @Synchronized fun initialize(context: Context) {
        if (store == null) store = DiagnosticLogStore(File(context.applicationContext.noBackupFilesDir, "lyrics-diagnostics"))
    }

    fun record(stage: DiagnosticStage, source: String = "", songId: String = "", fields: Map<String, Any?> = emptyMap()) {
        store?.record(stage, source, songId, fields)
    }

    suspend fun read(session: AdminSession): String {
        session.requireUnlocked()
        return checkNotNull(store) { "진단 로그 저장소가 준비되지 않았습니다" }.read(session)
    }

    suspend fun clear(session: AdminSession) {
        session.requireUnlocked()
        checkNotNull(store).clear(session)
    }
}

/** Single IO writer; bounded queue and two rotating 64 KiB files outside Android backup. */
internal class DiagnosticLogStore(
    private val directory: File,
    private val maxBytes: Long = 64 * 1024L,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private sealed interface Command {
        data class Write(val line: String) : Command
        data class Read(val session: AdminSession, val result: CompletableDeferred<String>) : Command
        data class Clear(val session: AdminSession, val result: CompletableDeferred<Unit>) : Command
    }
    private val commands = Channel<Command>(128)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val current get() = File(directory, "search.jsonl")
    private val previous get() = File(directory, "search.previous.jsonl")
    private val worker = scope.launch {
        for (command in commands) when (command) {
            is Command.Write -> runCatching { write(command.line) }
            is Command.Read -> command.result.completeWith(runCatching {
                command.session.requireUnlocked()
                val text = listOf(previous, current).filter(File::isFile)
                    .flatMap { it.readLines(Charsets.UTF_8) }.takeLast(200).joinToString("\n")
                command.session.requireUnlocked()
                text
            })
            is Command.Clear -> command.result.completeWith(runCatching {
                command.session.requireUnlocked()
                listOf(previous, current).forEach { file -> check(!file.exists() || file.delete()) }
            })
        }
    }

    fun record(stage: DiagnosticStage, source: String, songId: String, fields: Map<String, Any?>) {
        // Only known fields can reach disk. Never serialize requests, responses or exceptions.
        val event = JSONObject().apply {
            put("time", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date()))
            put("stage", stage.name)
            if (source.isNotBlank()) put("source", sanitize(source))
            if (songId.isNotBlank()) put("song", sanitize(songId.take(16)))
            fields.filterKeys { it in ALLOWED_FIELDS }.forEach { (name, value) ->
                if (value != null) put(name, if (value is Number || value is Boolean) value else sanitize(value.toString()))
            }
        }.toString()
        // Dropping diagnostic events under pressure must never slow or fail lyric fetching.
        commands.trySend(Command.Write(event))
    }

    suspend fun read(session: AdminSession): String {
        session.requireUnlocked()
        val result = CompletableDeferred<String>()
        commands.send(Command.Read(session, result))
        return result.await()
    }

    suspend fun clear(session: AdminSession) {
        session.requireUnlocked()
        val result = CompletableDeferred<Unit>()
        commands.send(Command.Clear(session, result))
        result.await()
    }

    private fun write(line: String) {
        check(directory.isDirectory || directory.mkdirs())
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
        if (bytes.size > maxBytes) return
        if (current.length() + bytes.size > maxBytes) {
            check(!previous.exists() || previous.delete())
            check(!current.exists() || current.renameTo(previous))
        }
        current.appendBytes(bytes)
    }

    internal suspend fun close() { commands.close(); worker.join(); scope.cancel() }

    companion object {
        private val ALLOWED_FIELDS = setOf("title", "artist", "query", "durationMs", "attempt", "delayMs",
            "count", "accepted", "status", "host", "error", "elapsedMs", "reason", "hasLyrics", "sdk", "mode")
        private val SECRET = Regex("(?i)(?:api[_-]?key|key|token|authorization|password)\\s*[:=]\\s*[^\\s,;]+")
        private val GOOGLE_KEY = Regex("AIza[\\w-]{20,}")
        private val URL = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
        private val CONTROL = Regex("[\\p{Cc}\\p{Cf}]")
        internal fun sanitize(value: String): String = value.replace(URL, "[url]")
            .replace(SECRET, "[secret]").replace(GOOGLE_KEY, "[secret]")
            .replace(CONTROL, " ").take(160)
    }
}
