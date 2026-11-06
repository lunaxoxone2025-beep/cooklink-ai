package com.cooklink.ai.data.ai

import com.cooklink.ai.data.settings.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException

/** An error whose message is safe and useful to show to the user. */
class AiException(message: String) : Exception(message)

/** A JPEG/PNG already downscaled and base64-encoded, ready to send. */
class EncodedImage(val mimeType: String, val base64: String)

/** A file uploaded to Gemini's Files API. */
class UploadedFile(val uri: String, val mimeType: String, val name: String)

/** Media attached to a request. Each client uses the parts it supports. */
data class AiMedia(
    val images: List<EncodedImage> = emptyList(),
    val youtubeUrl: String? = null,
    val geminiFile: UploadedFile? = null,
)

interface AiClient {
    val provider: AiProvider

    /** Whether the model can watch a public YouTube video by URL. */
    val canWatchYouTube: Boolean

    /** Whether the model can watch an uploaded video with its audio (otherwise we send frames). */
    val canWatchVideoFiles: Boolean

    /** Sends [system] + [user] with optional [media] and returns the model's raw text reply. */
    suspend fun generate(system: String, user: String, media: AiMedia = AiMedia()): String
}

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
private val json = Json { ignoreUnknownKeys = true }

/** Google Gemini via the generateContent REST endpoint and the Files API. */
class GeminiClient(private val http: OkHttpClient, private val apiKey: String) : AiClient {
    override val provider = AiProvider.GEMINI
    override val canWatchYouTube = true
    override val canWatchVideoFiles = true

    override suspend fun generate(system: String, user: String, media: AiMedia): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", system) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        media.youtubeUrl?.let { url ->
                            addJsonObject { putJsonObject("fileData") { put("fileUri", url) } }
                        }
                        media.geminiFile?.let { f ->
                            addJsonObject {
                                putJsonObject("fileData") {
                                    put("mimeType", f.mimeType)
                                    put("fileUri", f.uri)
                                }
                            }
                        }
                        media.images.forEach { img ->
                            addJsonObject {
                                putJsonObject("inlineData") {
                                    put("mimeType", img.mimeType)
                                    put("data", img.base64)
                                }
                            }
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
            .url("$BASE/v1beta/models/$MODEL:generateContent")
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

    /**
     * Uploads a video with the resumable Files API, reporting progress 0..1, then waits until
     * Gemini has finished processing it. Files are deleted by Google automatically after 48 h.
     */
    suspend fun uploadFile(file: File, mimeType: String, onProgress: (Float) -> Unit): UploadedFile = withContext(Dispatchers.IO) {
        val start = Request.Builder()
            .url("$BASE/upload/v1beta/files")
            .header("x-goog-api-key", apiKey)
            .header("X-Goog-Upload-Protocol", "resumable")
            .header("X-Goog-Upload-Command", "start")
            .header("X-Goog-Upload-Header-Content-Length", file.length().toString())
            .header("X-Goog-Upload-Header-Content-Type", mimeType)
            .post(buildJsonObject { putJsonObject("file") { put("display_name", "cooklink-video") } }.toString().toRequestBody(JSON_MEDIA))
            .build()
        val uploadUrl = try {
            http.newCall(start).execute().use { r ->
                if (!r.isSuccessful) {
                    val root = runCatching { json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject }.getOrNull() ?: JsonObject(emptyMap())
                    throw AiException(geminiError(r.code, root))
                }
                r.header("x-goog-upload-url")
            }
        } catch (e: IOException) {
            throw AiException("Couldn't start the video upload. Check your internet connection.")
        } ?: throw AiException("Gemini didn't accept the video upload. Try again.")

        val upload = Request.Builder()
            .url(uploadUrl)
            .header("X-Goog-Upload-Offset", "0")
            .header("X-Goog-Upload-Command", "upload, finalize")
            .post(ProgressFileBody(file, mimeType.toMediaType(), onProgress))
            .build()
        // Large uploads can take minutes on mobile data, so no overall call deadline here.
        val uploadClient = http.newBuilder().callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS).build()
        val (code, text) = execute(uploadClient, upload, "Gemini")
        val root = parseObject(text, "Gemini")
        if (code !in 200..299) throw AiException(geminiError(code, root))
        val f = root["file"]?.jsonObject ?: throw AiException("Gemini didn't confirm the upload. Try again.")
        val name = f["name"]?.jsonPrimitive?.contentOrNull ?: throw AiException("Gemini didn't confirm the upload. Try again.")
        val uri = f["uri"]?.jsonPrimitive?.contentOrNull ?: throw AiException("Gemini didn't confirm the upload. Try again.")
        var state = f["state"]?.jsonPrimitive?.contentOrNull

        // Videos are processed after upload; they can only be used once ACTIVE.
        var waited = 0
        while (state == "PROCESSING") {
            if (waited > 300) throw AiException("Gemini is taking too long to process the video. Try a shorter clip.")
            delay(3000)
            waited += 3
            val check = Request.Builder().url("$BASE/v1beta/$name").header("x-goog-api-key", apiKey).get().build()
            val (c, t) = execute(http, check, "Gemini")
            if (c !in 200..299) break
            state = parseObject(t, "Gemini")["state"]?.jsonPrimitive?.contentOrNull
        }
        if (state == "FAILED") throw AiException("Gemini couldn't process this video. Try another format (MP4 works best).")
        UploadedFile(uri, mimeType, name)
    }

    /** Best-effort cleanup; Google also deletes files after 48 h. */
    suspend fun deleteFile(file: UploadedFile) = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url("$BASE/v1beta/${file.name}").header("x-goog-api-key", apiKey).delete().build()).execute().close()
        }
        Unit
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
            code == 413 -> "This file is too large for Gemini."
            code == 404 -> "Gemini model not found ($MODEL). The app may need an update."
            code >= 500 -> "Gemini is having problems right now ($code). Try again in a moment."
            else -> "Gemini error $code: ${msg.ifBlank { "unknown error" }}"
        }
    }

    companion object {
        const val MODEL = "gemini-3.8-flash"
        private const val BASE = "https://generativelanguage.googleapis.com"
    }
}

/** Anthropic Claude via the Messages API. Sees images; videos are sent as sampled frames. */
class ClaudeClient(private val http: OkHttpClient, private val apiKey: String) : AiClient {
    override val provider = AiProvider.CLAUDE
    override val canWatchYouTube = false
    override val canWatchVideoFiles = false

    override suspend fun generate(system: String, user: String, media: AiMedia): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", MODEL)
            put("max_tokens", 8000)
            put("system", system)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        media.images.forEach { img ->
                            addJsonObject {
                                put("type", "image")
                                putJsonObject("source") {
                                    put("type", "base64")
                                    put("media_type", img.mimeType)
                                    put("data", img.base64)
                                }
                            }
                        }
                        addJsonObject {
                            put("type", "text")
                            put("text", user)
                        }
                    }
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
                    413 -> "Too much data for Claude in one go. Use fewer or smaller images."
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

/** Streams a file and reports upload progress. */
private class ProgressFileBody(
    private val file: File,
    private val type: MediaType,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    override fun contentType() = type
    override fun contentLength() = file.length()
    override fun writeTo(sink: BufferedSink) {
        val total = file.length().coerceAtLeast(1)
        var sent = 0L
        var lastReported = -1
        file.source().use { source ->
            while (true) {
                val read = source.read(sink.buffer, 64 * 1024L)
                if (read == -1L) break
                sink.flush()
                sent += read
                val pct = (sent * 100 / total).toInt()
                if (pct != lastReported) {
                    lastReported = pct
                    onProgress(sent.toFloat() / total)
                }
            }
        }
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
