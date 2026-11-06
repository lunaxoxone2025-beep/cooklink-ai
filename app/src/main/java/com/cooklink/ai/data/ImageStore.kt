package com.cooklink.ai.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Copies picked photos into app storage so covers survive the gallery item being deleted, and work offline. */
class ImageStore(private val context: Context) {
    private val dir get() = File(context.filesDir, "covers").apply { mkdirs() }

    /** Folder where cover images live; other components may write new covers here. */
    val coversDir: File get() = dir

    suspend fun importCover(source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null
            Uri.fromFile(target).toString()
        }.getOrNull()
    }

    /** Downloads a remote cover image (og:image, video thumbnail) so it works offline. Null on any failure. */
    suspend fun downloadCover(http: okhttp3.OkHttpClient, url: String?): String? {
        if (url.isNullOrBlank() || !url.startsWith("http")) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = okhttp3.Request.Builder().url(url).build()
                http.newCall(request).execute().use { r ->
                    val type = r.header("Content-Type").orEmpty()
                    if (!r.isSuccessful || !type.startsWith("image/")) return@runCatching null
                    val body = r.body ?: return@runCatching null
                    if (body.contentLength() > 15L * 1024 * 1024) return@runCatching null
                    val target = File(dir, "${UUID.randomUUID()}.jpg")
                    body.byteStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                    Uri.fromFile(target).toString()
                }
            }.getOrNull()
        }
    }

    /** Deletes a cover previously created by [importCover]; ignores anything else. */
    suspend fun deleteCover(uri: String?) {
        val path = uri?.let { Uri.parse(it).path } ?: return
        withContext(Dispatchers.IO) {
            val file = File(path)
            if (file.parentFile?.canonicalPath == dir.canonicalPath) file.delete()
        }
    }
}
