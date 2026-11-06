package com.cooklink.ai.data.importer

import com.cooklink.ai.data.ImageStore
import com.cooklink.ai.data.RecipeRepository
import com.cooklink.ai.data.ai.AiClient
import com.cooklink.ai.data.ai.AiException
import com.cooklink.ai.data.ai.AiMedia
import com.cooklink.ai.data.ai.ClaudeClient
import com.cooklink.ai.data.ai.GeminiClient
import com.cooklink.ai.data.ai.RecipeExtraction
import com.cooklink.ai.data.settings.AiProvider
import com.cooklink.ai.data.settings.SecureKeyStore
import com.cooklink.ai.data.settings.SettingsRepository
import com.cooklink.ai.model.Origin
import com.cooklink.ai.model.SourceLink
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

/** Progress stages shown on the import screen. */
enum class ImportStage(val label: String) {
    READING_LINK("Reading the link…"),
    PREPARING_MEDIA("Preparing photos and video…"),
    UPLOADING_VIDEO("Uploading the video…"),
    ANALYZING_MEDIA("Analysing photos and video… (can take a minute)"),
    WATCHING_VIDEO("Watching the video… (can take a minute)"),
    READING_TEXT("Reading the content…"),
    SAVING("Saving the recipe…"),
}

data class ImportResult(val recipeId: Long, val notice: String?)

/**
 * Turns links, text, photos and videos into saved recipes with the configured AI provider.
 * Everything that reaches the AI is public metadata or material the user chose to share.
 */
class RecipeImporter(
    private val http: OkHttpClient,
    private val keys: SecureKeyStore,
    private val settings: SettingsRepository,
    private val recipes: RecipeRepository,
    private val imagesStore: ImageStore,
    private val media: MediaPrep,
) {
    private val fetcher = LinkFetcher(http)

    suspend fun importLink(rawUrl: String, onStage: (ImportStage) -> Unit): ImportResult {
        onStage(ImportStage.READING_LINK)
        val link = fetcher.fetch(rawUrl)
        var notice: String? = null

        // YouTube is the one platform where an AI can officially watch the video itself.
        val client = if (link.youtubeUrl != null) {
            gemini() ?: preferredClient().also {
                notice = "Claude can't watch YouTube videos, so this recipe was built from the title and description only. " +
                    "Add a Gemini key in Profile to have the video watched."
            }
        } else {
            preferredClient()
        }

        val watchVideo = link.youtubeUrl != null && client.canWatchYouTube
        val material = buildLinkPrompt(link, watchVideo)
        if (!watchVideo && link.structuredRecipe == null && link.text.trim().length < 40) {
            throw AiException(
                when (link.platform) {
                    Platform.INSTAGRAM, Platform.FACEBOOK ->
                        "${link.platform.label} doesn't let apps read this post. Copy the caption and paste it in “Text”, " +
                            "or use screenshots / video upload when they arrive in Phase 3."
                    Platform.TIKTOK ->
                        "This TikTok has almost no caption to read. Paste the recipe text in “Text”, " +
                            "or use screenshots / video upload when they arrive in Phase 3."
                    else -> "There's not enough text on this page to find a recipe."
                }
            )
        }
        if (link.platform == Platform.TIKTOK || link.platform == Platform.INSTAGRAM || link.platform == Platform.FACEBOOK) {
            notice = notice ?: "Built from the post's caption only; the video itself can't be read from ${link.platform.label}. Check amounts marked as estimates."
        }

        onStage(if (watchVideo) ImportStage.WATCHING_VIDEO else ImportStage.READING_TEXT)
        val reply = client.generate(systemPrompt(), material, AiMedia(youtubeUrl = if (watchVideo) link.youtubeUrl else null))
        val ai = RecipeExtraction.parse(reply)

        onStage(ImportStage.SAVING)
        val cover = imagesStore.downloadCover(http, link.imageUrl)
        val sourceLabel = listOfNotNull(link.platform.label, link.author).joinToString(" · ")
        val recipe = try {
            RecipeExtraction.toRecipe(ai, Origin.URL, listOf(SourceLink(sourceLabel, link.url)), cover)
        } catch (e: AiException) {
            imagesStore.deleteCover(cover)
            throw e
        }
        return ImportResult(recipes.save(recipe), notice)
    }

    suspend fun importText(text: String, onStage: (ImportStage) -> Unit): ImportResult {
        val trimmed = text.trim()
        if (trimmed.length < 20) throw AiException("Write or paste a bit more — at least a few ingredients or steps.")
        val client = preferredClient()
        onStage(ImportStage.READING_TEXT)
        val material = buildString {
            appendLine("Source type: text written or pasted by the user (notes, a caption, a description or a transcript).")
            appendLine("Turn it into a complete, well-structured recipe. Keep the user's amounts exactly; mark anything you add as estimated.")
            appendLine()
            appendLine("<source_text>")
            appendLine(trimmed.take(MAX_TEXT))
            appendLine("</source_text>")
        }
        val reply = client.generate(systemPrompt(), material)
        val ai = RecipeExtraction.parse(reply)
        onStage(ImportStage.SAVING)
        val recipe = RecipeExtraction.toRecipe(ai, Origin.TEXT, emptyList(), null)
        return ImportResult(recipes.save(recipe), null)
    }

    /**
     * Photos, screenshots, a video from the phone, notes and/or a link, combined into one recipe.
     * Gemini watches the video with its audio; Claude gets evenly sampled frames instead.
     */
    suspend fun importMedia(
        images: List<android.net.Uri>,
        video: VideoInfo?,
        notes: String,
        linkUrl: String?,
        onStage: (ImportStage) -> Unit,
        onProgress: (Float) -> Unit,
    ): ImportResult {
        if (images.isEmpty() && video == null && notes.isBlank() && linkUrl == null) {
            throw AiException("Add at least one photo, a video, a link or some notes.")
        }
        if (images.size > MediaPrep.MAX_IMAGES) throw AiException("Use at most ${MediaPrep.MAX_IMAGES} images per recipe.")
        video?.let { media.checkVideo(it) }

        val client = preferredClient()
        var notice: String? = null
        var tempVideo: java.io.File? = null
        var uploaded: com.cooklink.ai.data.ai.UploadedFile? = null
        var cover: String? = null

        try {
            // Optional link: read its public text first so it can be cross-referenced.
            val link = linkUrl?.let {
                onStage(ImportStage.READING_LINK)
                fetcher.fetch(it)
            }

            onStage(ImportStage.PREPARING_MEDIA)
            val encoded = images.map { media.encodeImage(it) }
            var frames: List<Pair<Int, com.cooklink.ai.data.ai.EncodedImage>> = emptyList()

            if (video != null) {
                if (client is GeminiClient) {
                    val copy = media.copyVideo(video)
                    tempVideo = copy
                    onStage(ImportStage.UPLOADING_VIDEO)
                    uploaded = client.uploadFile(copy, video.mimeType.substringBefore(';'), onProgress)
                    media.deleteTemp(copy)
                    tempVideo = null
                } else {
                    frames = media.sampleFrames(video.uri)
                    notice = "Claude can't hear audio, so the video was read from ${frames.size} still frames. " +
                        "Spoken amounts were not captured — check items marked as estimates, or use Gemini for videos."
                }
            }

            // Cover: first photo, else a frame of the video, else the link's image.
            cover = images.firstOrNull()?.let { imagesStore.importCover(it) }
                ?: video?.let { media.videoCover(it.uri, imagesStore.coversDir) }
                ?: link?.imageUrl?.let { imagesStore.downloadCover(http, it) }

            val watchYouTube = link?.youtubeUrl != null && client.canWatchYouTube
            val material = buildString {
                appendLine("Source type: material collected by the user for ONE recipe. Cross-reference all of it.")
                appendLine("Prefer amounts that are written or spoken; when sources disagree, use the most explicit one and mention the other in the ingredient note.")
                appendLine()
                if (encoded.isNotEmpty()) {
                    appendLine("${encoded.size} photo(s)/screenshot(s) are attached first, in the order the user picked them.")
                    appendLine("Text visible in screenshots counts as confirmed. Amounts judged from how food looks are estimated.")
                }
                if (uploaded != null) {
                    appendLine("A video from the user's phone is attached. Watch it and listen to the audio and any on-screen text or subtitles: spoken or written amounts are confirmed.")
                }
                if (frames.isNotEmpty()) {
                    appendLine("The user's video is represented by ${frames.size} still frames taken at these seconds: ${frames.joinToString { "${it.first}s" }}.")
                    appendLine("There is NO audio. Use on-screen text and visible actions; anything inferred between frames is estimated.")
                }
                if (link != null) {
                    appendLine()
                    append(buildLinkPrompt(link, watchYouTube))
                }
                if (notes.isNotBlank()) {
                    appendLine()
                    appendLine("Notes typed by the user (treat as confirmed):")
                    appendLine("<user_notes>")
                    appendLine(notes.trim().take(MAX_TEXT))
                    appendLine("</user_notes>")
                }
            }

            onStage(ImportStage.ANALYZING_MEDIA)
            val reply = client.generate(
                systemPrompt(),
                material,
                AiMedia(
                    images = encoded + frames.map { it.second },
                    youtubeUrl = if (watchYouTube) link?.youtubeUrl else null,
                    geminiFile = uploaded,
                ),
            )
            val ai = RecipeExtraction.parse(reply)

            onStage(ImportStage.SAVING)
            val kinds = listOf(images.isNotEmpty(), video != null, notes.isNotBlank() || link != null).count { it }
            val origin = when {
                kinds > 1 -> Origin.COMBINED
                video != null -> Origin.VIDEO
                images.isNotEmpty() -> Origin.IMAGE
                link != null -> Origin.URL
                else -> Origin.TEXT
            }
            val sources = link?.let {
                listOf(SourceLink(listOfNotNull(it.platform.label, it.author).joinToString(" · "), it.url))
            }.orEmpty()
            val recipe = RecipeExtraction.toRecipe(ai, origin, sources, cover)
            val id = recipes.save(recipe)
            cover = null // now owned by the recipe
            return ImportResult(id, notice)
        } finally {
            media.deleteTemp(tempVideo)
            uploaded?.let { (client as? GeminiClient)?.deleteFile(it) }
            cover?.let { imagesStore.deleteCover(it) }
        }
    }

    private fun buildLinkPrompt(link: LinkContent, watchVideo: Boolean) = buildString {
        appendLine("Source type: ${link.platform.label}")
        appendLine("URL: ${link.url}")
        link.title?.let { appendLine("Title: $it") }
        link.author?.let { appendLine("Author / channel: $it") }
        appendLine()
        when {
            watchVideo -> appendLine(
                "The video is attached. Watch it and listen to the audio: ingredients, amounts and steps spoken or shown " +
                    "count as confirmed. Use the description below too."
            )
            link.youtubeUrl != null -> appendLine(
                "Only the title and description are available; the video itself was NOT watched. " +
                    "Anything not written below is estimated or missing."
            )
            link.platform != Platform.WEB -> appendLine(
                "Only the post caption is available; the video itself could not be read. " +
                    "Anything not written in the caption is estimated or missing."
            )
        }
        link.structuredRecipe?.let {
            appendLine()
            appendLine("The site publishes this structured recipe data (schema.org). Treat its fields as confirmed:")
            appendLine("<structured_recipe>")
            appendLine(it)
            appendLine("</structured_recipe>")
        }
        if (link.text.isNotBlank()) {
            appendLine()
            appendLine(if (link.platform == Platform.WEB) "<page_text>" else "<caption_or_description>")
            appendLine(link.text.take(MAX_TEXT))
            appendLine(if (link.platform == Platform.WEB) "</page_text>" else "</caption_or_description>")
        }
    }

    private suspend fun systemPrompt() = RecipeExtraction.systemPrompt(settings.recipeLanguage.first().promptName)

    private fun gemini(): AiClient? = keys.get(AiProvider.GEMINI)?.let { GeminiClient(http, it) }
    private fun claude(): AiClient? = keys.get(AiProvider.CLAUDE)?.let { ClaudeClient(http, it) }

    /** The provider chosen in Profile, falling back to whichever one has a key. */
    private suspend fun preferredClient(): AiClient {
        val preferred = settings.provider.first()
        val first = if (preferred == AiProvider.GEMINI) gemini() else claude()
        return first ?: gemini() ?: claude()
            ?: throw AiException("Add a Gemini or Claude API key in Profile first.")
    }

    private companion object {
        const val MAX_TEXT = 30_000
    }
}
