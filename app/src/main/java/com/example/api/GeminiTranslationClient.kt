package com.example.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException

internal enum class TranslationProblem(val message: String) {
    MISSING_KEY("개인 Gemini API 키를 설정해 주세요."),
    AUTHENTICATION("API 키가 유효하지 않거나 요청 권한이 없습니다."),
    RATE_LIMIT("요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."),
    NETWORK("네트워크에 연결할 수 없습니다."),
    SERVER("번역 서버 오류입니다. 잠시 후 다시 시도해 주세요."),
    EMPTY("사용 가능한 번역 결과가 없습니다."),
    KEY_STORAGE("저장된 개인 키를 읽지 못했습니다. 키를 다시 설정해 주세요.")
}
internal class TranslationFailure(val problem: TranslationProblem) : Exception(problem.message)

internal class GeminiTranslationClient(
    private val key: () -> String?,
    private val client: OkHttpClient = OkHttpClient(),
    private val endpoint: String = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
) {
    suspend fun translate(raw: String): String = withContext(Dispatchers.IO) {
        val secret = try { key() } catch (_: Exception) { throw TranslationFailure(TranslationProblem.KEY_STORAGE) }
        if (secret.isNullOrBlank()) throw TranslationFailure(TranslationProblem.MISSING_KEY)
        if (secret.any { it <= ' ' || it >= '\u007f' }) throw TranslationFailure(TranslationProblem.AUTHENTICATION)
        val payload = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(
            JSONObject().put("text", "Translate each line into Korean. Preserve every [mm:ss.xx] marker exactly. Return only translated LRC, no markdown.\n$raw")))))
        val request = Request.Builder().url(endpoint).header("x-goog-api-key", secret)
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        try {
            val response = client.readResponse(request)
            when (response.code) {
                400, 401, 403 -> throw TranslationFailure(TranslationProblem.AUTHENTICATION)
                429 -> throw TranslationFailure(TranslationProblem.RATE_LIMIT)
            }
            if (response.code !in 200..299) throw TranslationFailure(TranslationProblem.SERVER)
            val parts = JSONObject(response.body.orEmpty()).optJSONArray("candidates")
                ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val text = parts?.let { array -> (0 until array.length()).mapNotNull {
                array.optJSONObject(it)?.optString("text")
            }.joinToString("\n") }.orEmpty().replace("```lrc", "").replace("```", "").trim()
            if (text.isBlank() || text == raw) throw TranslationFailure(TranslationProblem.EMPTY)
            text
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: TranslationFailure) { throw failure }
        catch (_: IOException) { throw TranslationFailure(TranslationProblem.NETWORK) }
        catch (_: Exception) { throw TranslationFailure(TranslationProblem.EMPTY) }
    }
}
