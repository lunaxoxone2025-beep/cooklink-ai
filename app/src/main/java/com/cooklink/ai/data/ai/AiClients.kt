package com.cooklink.ai.data.ai

import com.cooklink.ai.data.settings.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** An error whose message is safe and useful to show to the user. */
class AiException(message: String) : Exception(message)

interface AiClient {
    val provider: AiProvider

    /** Whether the model can watch a public YouTube video by URL. */
    val canWatchYouTube: Boolean

    /** Sends [system] + [user] (and optionally a YouTube video) and returns the model's raw text reply. */
    suspend fun generate(system: String, user: String, youtubeUrl: String? = null): String
}

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
private val json = Json { ignoreUnknownKeys = true }

/** Google Gemini via the generateContent REST endpoint. */
class GeminiClient(private val http: OkHttpClient, private val apiKey: String) : AiClient {
    override val provider = AiProvider.GEMINI
    override val canWatchYouTube = true

    override suspend fun generate(system: String, user: String, youtubeUrl: String?): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", system) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        if (youtubeUrl != null) {
                            addJsonObject { putJsonObject("fileData") { put("fileUri", youtubeUrl) } }
                        }
                        addJsonObject { put("text", user) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
            }
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val (code, text) = execute(http, request, "Gemini")
        val root = parseObject(text, "Gemini")
        if (code !in 200..299) throw AiException(geminiError(code, root))

        root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull?.let {
            throw AiException("Gemini refused to read this content ($it).")
        }
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw AiException("Gemini returned no answer. Try again.")
        val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray.orEmpty()
        val answer = parts.mapNotNull { p ->
            val o = p.jsonObject
            if (o["thought"]?.jsonPrimitive?.booleanOrNull == true) null else o["text"]?.jsonPrimitive?.contentOrNull
        }.joinToString("")
        if (answer.isBlank()) {
            val reason = candidate["finishReason"]?.jsonPrimitive?.contentOrNull ?: "unknown"
            throw AiException("Gemini stopped without a recipe (reason: $reason).")
        }
        answer
    }

    private fun geminiError(code: Int, root: JsonObject): String {
        val err = root["error"]?.jsonObject
        val msg = err?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty()
        return when {
            msg.contains("API key not valid", true) || msg.contains("API_KEY_INVALID", true) ->
                "Gemini rejected your API key. Check it in Profile."
            code == 429 -> "Gemini quota reached. Wait a minute, or check your plan limits in Google AI Studio."
            code == 403 -> "Gemini denied access: $msg"
            msg.contains("video", true) && msg.contains("private", true) ->
                "This YouTube video is private or unlisted. Gemini can only watch public videos."
            code == 404 -> "Gemini model not found ($MODEL). The app may need an update."
            code >= 500 -> "Gemini is having problems right now ($code). Try again in a moment."
            else -> "Gemini error $code: ${msg.ifBlank { "unknown error" }}"
        }
    }

    companion object {
        const val MODEL = "gemini-3.8-flash"
    }
}

/** Anthropic Claude via the Messages API. */
class ClaudeClient(private val http: OkHttpClient, private val apiKey: String) : AiClient {
    override val provider = AiProvider.CLAUDE
    override val canWatchYouTube = false

    override suspend fun generate(system: String, user: String, youtubeUrl: String?): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", MODEL)
            put("max_tokens", 8000)
            put("system", system)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", user)
                }
            }
        }
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val (code, text) = execute(http, request, "Claude")
        val root = parseObject(text, "Claude")
        if (code !in 200..299) {
            val msg = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty()
            throw AiException(
                when (code) {
                    401 -> "Claude rejected your API key. Check it in Profile."
                    402, 403 -> "Claude denied the request: ${msg.ifBlank { "check your Anthropic account credits" }}"
                    404 -> "Claude model not found ($MODEL). The app may need an update."
                    429 -> "Claude rate limit reached. Wait a minute and try again."
                    529, 503 -> "Claude is overloaded right now. Try again in a moment."
                    else -> "Claude error $code: ${msg.ifBlank { "unknown error" }}"
                }
            )
        }
        val answer = root["content"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("")
        if (answer.isBlank()) throw AiException("Claude returned an empty answer. Try again.")
        answer
    }

    companion object {
        const val MODEL = "claude-sonnet-5-5"
    }
}

private fun execute(http: OkHttpClient, request: Request, name: String): Pair<Int, String> =
    try {
        http.newCall(request).execute().use { it.code to (it.body?.string() ?: "") }
    } catch (e: java.net.SocketTimeoutException) {
        throw AiException("$name took too long to answer. Long videos can time out — try again or use a shorter one.")
    } catch (e: IOException) {
        throw AiException("No connection to $name. Check your internet and try again.")
    }

private fun parseObject(text: String, name: String): JsonObject =
    runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
        throw AiException("$name sent an unreadable response. Try again.")
    }
