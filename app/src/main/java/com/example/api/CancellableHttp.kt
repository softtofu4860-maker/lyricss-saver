package com.example.api

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.example.diagnostics.DiagnosticStage
import com.example.diagnostics.LyricsDiagnostics

internal data class HttpTextResponse(val code: Int, val body: String?)
internal suspend fun OkHttpClient.readText(request: Request): String? = readResponse(request).let {
    if (it.code in 200..299) it.body else null
}
internal suspend fun OkHttpClient.readResponse(request: Request): HttpTextResponse = suspendCancellableCoroutine { continuation ->
    val started = System.nanoTime()
    fun diagnostic(stage: DiagnosticStage, status: Int? = null, error: String? = null) {
        LyricsDiagnostics.record(stage, source = "http", fields = mapOf("host" to request.url.host,
            "status" to status, "error" to error, "elapsedMs" to (System.nanoTime() - started) / 1_000_000))
    }
    val call = newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            diagnostic(if (call.isCanceled()) DiagnosticStage.CANCELLED else DiagnosticStage.HTTP_FAILURE,
                error = e.javaClass.simpleName)
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            diagnostic(DiagnosticStage.HTTP_RESPONSE, status = response.code)
            try {
                // Read and close on OkHttp's dispatcher, keeping cancellation wired to the
                // call until the body is consumed. No response leaks on cancelled requests.
                val text = response.use { HttpTextResponse(it.code, if (it.isSuccessful) it.body?.string() else null) }
                continuation.resume(text)
            } catch (e: Exception) {
                diagnostic(DiagnosticStage.HTTP_FAILURE, error = e.javaClass.simpleName)
                continuation.resumeWithException(e)
            }
        }
    })
}
