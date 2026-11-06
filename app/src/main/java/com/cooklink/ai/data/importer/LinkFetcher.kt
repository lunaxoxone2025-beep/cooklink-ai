package com.cooklink.ai.data.importer

import com.cooklink.ai.data.ai.AiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class Platform(val label: String) {
    YOUTUBE("YouTube"), TIKTOK("TikTok"), INSTAGRAM("Instagram"), FACEBOOK("Facebook"), WEB("Web page")
}

/** Everything we could legitimately read about a link, ready to hand to the AI. */
data class LinkContent(
    val platform: Platform,
    val url: String,
    /** Canonical public YouTube watch URL, if this is a YouTube video. */
    val youtubeUrl: String? = null,
    val title: String? = null,
    val author: String? = null,
    /** Caption, description, page text, etc. */
    val text: String = "",
    /** schema.org Recipe JSON published by the site itself, when present. */
    val structuredRecipe: String? = null,
    val imageUrl: String? = null,
)

/**
 * Reads public metadata only: official oEmbed endpoints (YouTube, TikTok), the page's
 * own Open Graph tags and schema.org data, and visible page text. It never downloads
 * protected video files or works around logins.
 */
class LinkFetcher(baseClient: OkHttpClient) {

    private val http = baseClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetch(rawUrl: String): LinkContent = withContext(Dispatchers.IO) {
        val url = normalize(rawUrl)
        val host = url.toHttpUrlOrNull()?.host?.lowercase() ?: throw AiException("That doesn't look like a valid link.")
        when {
            host.endsWith("youtube.com") || host == "youtu.be" -> youtube(url)
            host.endsWith("tiktok.com") -> tiktok(url)
            host.endsWith("instagram.com") -> socialPage(url, Platform.INSTAGRAM)
            host.endsWith("facebook.com") || host == "fb.watch" -> socialPage(url, Platform.FACEBOOK)
            else -> webPage(url)
        }
    }

    // ---------- YouTube ----------

    private fun youtube(url: String): LinkContent {
        val id = youtubeId(url) ?: throw AiException("Couldn't find the video in this YouTube link.")
        val watch = "https://www.youtube.com/watch?v=$id"
        val oembed = getJson("https://www.youtube.com/oembed?format=json&url=" + enc(watch))
        val page = getText(watch)
        val meta = page?.let { metaTags(it) }.orEmpty()
        return LinkContent(
            platform = Platform.YOUTUBE,
            url = watch,
            youtubeUrl = watch,
            title = oembed?.str("title") ?: meta["og:title"],
            author = oembed?.str("author_name"),
            text = meta["og:description"] ?: meta["description"] ?: "",
            imageUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
        )
    }

    private fun youtubeId(url: String): String? {
        val u = url.toHttpUrlOrNull() ?: return null
        val id = when {
            u.host.equals("youtu.be", true) -> u.pathSegments.firstOrNull()
            u.queryParameter("v") != null -> u.queryParameter("v")
            u.pathSegments.size >= 2 && u.pathSegments[0] in setOf("shorts", "embed", "live", "v") -> u.pathSegments[1]
            else -> null
        }
        return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,20}")) }
    }

    // ---------- TikTok ----------

    private fun tiktok(url: String): LinkContent {
        // Short links (vm.tiktok.com) redirect to the full video URL that oEmbed expects.
        val full = if (url.contains("/video/")) url else resolveRedirect(url) ?: url
        val oembed = getJson("https://www.tiktok.com/oembed?url=" + enc(full))
            ?: throw AiException("TikTok didn't share this video's details. It may be private or removed.")
        return LinkContent(
            platform = Platform.TIKTOK,
            url = full,
            title = oembed.str("title")?.take(120),
            author = oembed.str("author_name"),
            text = oembed.str("title").orEmpty(),
            imageUrl = oembed.str("thumbnail_url"),
        )
    }

    // ---------- Instagram / Facebook ----------

    private fun socialPage(url: String, platform: Platform): LinkContent {
        val html = getText(url)
        val meta = html?.let { metaTags(it) }.orEmpty()
        val caption = listOfNotNull(meta["og:title"], meta["og:description"], meta["description"])
            .distinct().joinToString("\n")
        return LinkContent(
            platform = platform,
            url = url,
            title = meta["og:title"],
            text = caption,
            imageUrl = meta["og:image"],
        )
    }

    // ---------- Recipe sites and everything else ----------

    private fun webPage(url: String): LinkContent {
        val html = getText(url) ?: throw AiException("Couldn't open this page. Check the link or your connection.")
        val meta = metaTags(html)
        val recipeLd = findRecipeJsonLd(html)
        val image = recipeLd?.let { imageFrom(it["image"]) } ?: meta["og:image"]
        return LinkContent(
            platform = Platform.WEB,
            url = url,
            title = recipeLd?.str("name") ?: meta["og:title"] ?: titleTag(html),
            author = meta["og:site_name"],
            // With structured data the page text is mostly noise, so send less of it.
            text = visibleText(html).take(if (recipeLd != null) 6_000 else 30_000),
            structuredRecipe = recipeLd?.toString()?.take(25_000),
            imageUrl = image,
        )
    }

    // ---------- helpers ----------

    private fun normalize(raw: String): String {
        val t = raw.trim()
        return if (t.startsWith("http://", true) || t.startsWith("https://", true)) t else "https://$t"
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun request(url: String) = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Accept-Language", "en,es;q=0.8,*;q=0.5")
        .build()

    /** Returns the body, or null on HTTP errors; caps size so huge pages can't exhaust memory. */
    private fun getText(url: String): String? = try {
        http.newCall(request(url)).execute().use { r ->
            if (!r.isSuccessful) return null
            val source = r.body?.source() ?: return null
            source.request(MAX_BYTES)
            val bytes = source.buffer.snapshot().let { if (it.size > MAX_BYTES) it.substring(0, MAX_BYTES.toInt()) else it }
            bytes.utf8()
        }
    } catch (e: IOException) {
        throw AiException("Couldn't reach the link. Check your internet connection.")
    }

    private fun getJson(url: String): JsonObject? =
        getText(url)?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }

    private fun resolveRedirect(url: String): String? = try {
        http.newCall(request(url)).execute().use { it.request.url.toString() }
    } catch (e: IOException) {
        null
    }

    private fun metaTags(html: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val tag = Regex("<meta\\s[^>]*>", RegexOption.IGNORE_CASE)
        val attr = Regex("""(property|name|content)\s*=\s*("([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
        tag.findAll(html).forEach { m ->
            var key: String? = null
            var content: String? = null
            attr.findAll(m.value).forEach { a ->
                val value = a.groupValues[3].ifEmpty { a.groupValues[4] }
                when (a.groupValues[1].lowercase()) {
                    "property", "name" -> key = value.lowercase()
                    "content" -> content = value
                }
            }
            val k = key
            val c = content
            if (k != null && c != null && c.isNotBlank() && k !in out) out[k] = decode(c)
        }
        return out
    }

    private fun titleTag(html: String): String? =
        Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.let { decode(it).trim() }?.takeIf { it.isNotEmpty() }

    private fun findRecipeJsonLd(html: String): JsonObject? {
        val scripts = Regex(
            """<script[^>]*type\s*=\s*["']application/ld\+json["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        for (m in scripts.findAll(html)) {
            val element = runCatching { json.parseToJsonElement(m.groupValues[1].trim()) }.getOrNull() ?: continue
            findRecipe(element)?.let { return it }
        }
        return null
    }

    private fun findRecipe(e: JsonElement): JsonObject? = when (e) {
        is JsonArray -> e.firstNotNullOfOrNull { findRecipe(it) }
        is JsonObject -> {
            val type = e["@type"]
            val isRecipe = when (type) {
                is JsonPrimitive -> type.contentOrNull.equals("Recipe", true)
                is JsonArray -> type.any { (it as? JsonPrimitive)?.contentOrNull.equals("Recipe", true) }
                else -> false
            }
            if (isRecipe) e else e["@graph"]?.let { findRecipe(it) }
        }
        else -> null
    }

    private fun imageFrom(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull
        is JsonArray -> e.firstNotNullOfOrNull { imageFrom(it) }
        is JsonObject -> e.str("url")
        else -> null
    }

    private fun visibleText(html: String): String {
        val noBlocks = html
            .replace(Regex("<(script|style|noscript|svg|head|nav|footer)[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("<(br|/p|/li|/h[1-6]|/div|/tr)[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), " ")
        return decode(noBlocks)
            .lines().map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    /** Decodes HTML entities without touching line breaks (Html.fromHtml would collapse them). */
    private fun decode(s: String): String =
        Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").replace(s) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) }
                e.startsWith("#") -> e.substring(1).toIntOrNull()?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) }
                else -> NAMED_ENTITIES[e.lowercase()]
            } ?: m.value
        }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private companion object {
        const val MAX_BYTES = 3L * 1024 * 1024
        val NAMED_ENTITIES = mapOf(
            "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
            "frac12" to "½", "frac14" to "¼", "frac34" to "¾", "deg" to "°", "ndash" to "–", "mdash" to "—",
            "hellip" to "…", "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“", "times" to "×",
            "eacute" to "é", "aacute" to "á", "iacute" to "í", "oacute" to "ó", "uacute" to "ú", "ntilde" to "ñ",
            "egrave" to "è", "agrave" to "à", "ograve" to "ò", "uuml" to "ü", "ouml" to "ö", "auml" to "ä", "ccedil" to "ç",
        )
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36 CookLinkAI"
    }
}
