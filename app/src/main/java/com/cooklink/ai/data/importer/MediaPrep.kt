package com.cooklink.ai.data.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import com.cooklink.ai.data.ai.AiException
import com.cooklink.ai.data.ai.EncodedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Info about a picked video, shown in the import screen before sending. */
data class VideoInfo(val uri: Uri, val name: String, val sizeBytes: Long, val durationMs: Long?, val mimeType: String)

/**
 * Prepares user-picked media for the AI: downsizes photos, samples video frames,
 * and copies videos to a temporary file for upload. Enforces size limits.
 */
class MediaPrep(private val context: Context) {

    private val cache get() = File(context.cacheDir, "import").apply { mkdirs() }

    suspend fun describeVideo(uri: Uri): VideoInfo = withContext(Dispatchers.IO) {
        var name = "video"
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val mime = context.contentResolver.getType(uri) ?: "video/mp4"
        val duration = runCatching {
            MediaMetadataRetriever().useRetriever { r ->
                r.setDataSource(context, uri)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            }
        }.getOrNull()
        VideoInfo(uri, name, size, duration, mime)
    }

    /** Throws a user-readable error when the video breaks the app's limits. */
    fun checkVideo(info: VideoInfo) {
        if (!info.mimeType.startsWith("video/")) throw AiException("That file isn't a video.")
        if (info.sizeBytes > MAX_VIDEO_BYTES) {
            throw AiException("This video is ${info.sizeBytes / (1024 * 1024)} MB. The limit is ${MAX_VIDEO_BYTES / (1024 * 1024)} MB — trim it in your gallery first.")
        }
        val minutes = (info.durationMs ?: 0) / 60_000
        if (minutes > MAX_VIDEO_MINUTES) {
            throw AiException("This video is $minutes minutes long. The limit is $MAX_VIDEO_MINUTES minutes — trim it to the cooking part.")
        }
    }

    /** Copies the video into the app cache so it can be streamed to the uploader. Caller deletes it. */
    suspend fun copyVideo(info: VideoInfo): File = withContext(Dispatchers.IO) {
        val ext = info.mimeType.substringAfter('/', "mp4").substringBefore(';')
        val target = File(cache, "${UUID.randomUUID()}.$ext")
        val input = context.contentResolver.openInputStream(info.uri) ?: throw AiException("Couldn't open the video.")
        input.use { i -> target.outputStream().use { o -> i.copyTo(o) } }
        target
    }

    /** Loads a photo or screenshot, scales it to [maxSide] px and encodes it as JPEG. */
    suspend fun encodeImage(uri: Uri, maxSide: Int = IMAGE_MAX_SIDE): EncodedImage = withContext(Dispatchers.IO) {
        val bitmap = loadScaled(uri, maxSide) ?: throw AiException("Couldn't open one of the images.")
        encode(bitmap).also { bitmap.recycle() }
    }

    /**
     * Samples [count] evenly spaced frames from a video (used when the AI can't watch video files).
     * Returns frames with their timestamps in seconds.
     */
    suspend fun sampleFrames(uri: Uri, count: Int = FRAME_COUNT): List<Pair<Int, EncodedImage>> = withContext(Dispatchers.IO) {
        MediaMetadataRetriever().useRetriever { r ->
            r.setDataSource(context, uri)
            val durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: throw AiException("Couldn't read this video.")
            (0 until count).mapNotNull { i ->
                val tMs = durationMs * (2 * i + 1) / (2 * count)
                val frame = r.getFrameAtTime(tMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@mapNotNull null
                val scaled = scale(frame, FRAME_MAX_SIDE)
                val enc = encode(scaled)
                if (scaled !== frame) scaled.recycle()
                frame.recycle()
                (tMs / 1000).toInt() to enc
            }
        }.ifEmpty { throw AiException("Couldn't read any frames from this video.") }
    }

    /** Saves a frame from the middle of the video as a cover image file; null on failure. */
    suspend fun videoCover(uri: Uri, coversDir: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            MediaMetadataRetriever().useRetriever { r ->
                r.setDataSource(context, uri)
                val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                val frame = r.getFrameAtTime(d * 1000 * 2 / 3, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@useRetriever null
                val scaled = scale(frame, 1280)
                coversDir.mkdirs()
                val out = File(coversDir, "${UUID.randomUUID()}.jpg")
                out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                Uri.fromFile(out).toString()
            }
        }.getOrNull()
    }

    fun deleteTemp(file: File?) {
        file?.takeIf { it.parentFile?.canonicalPath == cache.canonicalPath }?.delete()
    }

    private fun loadScaled(uri: Uri, maxSide: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val ratio = maxSide.toFloat() / maxOf(w, h)
                if (ratio < 1f) decoder.setTargetSize((w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            decoded?.let { scale(it, maxSide).also { s -> if (s !== it) it.recycle() } }
        }
    }.getOrNull()

    private fun scale(b: Bitmap, maxSide: Int): Bitmap {
        val ratio = maxSide.toFloat() / maxOf(b.width, b.height)
        if (ratio >= 1f) return b
        return Bitmap.createScaledBitmap(b, (b.width * ratio).toInt().coerceAtLeast(1), (b.height * ratio).toInt().coerceAtLeast(1), true)
    }

    private fun encode(b: Bitmap): EncodedImage {
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return EncodedImage("image/jpeg", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
    }

    private inline fun <T> MediaMetadataRetriever.useRetriever(block: (MediaMetadataRetriever) -> T): T =
        try { block(this) } finally { runCatching { release() } }

    companion object {
        const val MAX_IMAGES = 10
        const val MAX_VIDEO_BYTES = 500L * 1024 * 1024
        const val MAX_VIDEO_MINUTES = 30
        const val IMAGE_MAX_SIDE = 1568
        const val FRAME_MAX_SIDE = 1024
        const val FRAME_COUNT = 16
    }
}
