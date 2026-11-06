package com.cooklink.ai.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.cooklink.ai.model.Recipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
private data class BackupEntry(val recipe: Recipe, val coverJpeg: String? = null)

@Serializable
private data class BackupFile(val app: String = "CookLink AI", val version: Int = 1, val exportedAt: Long, val recipes: List<BackupEntry>)

/**
 * Exports the whole library (with cover photos) to one JSON file the user can keep in
 * Google Drive, email or a PC, and restores it on any phone. This is the optional,
 * account-free way to sync or move recipes; API keys are never included.
 */
class BackupManager(private val context: Context, private val recipes: RecipeRepository, private val images: ImageStore) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    suspend fun export(target: Uri): Int = withContext(Dispatchers.IO) {
        val all = recipes.recipes.first()
        val entries = all.map { r ->
            val cover = r.coverUri?.let { Uri.parse(it).path }?.let(::File)?.takeIf { it.isFile && it.length() < 8L * 1024 * 1024 }
            BackupEntry(r, cover?.readBytes()?.let { Base64.encodeToString(it, Base64.NO_WRAP) })
        }
        val text = json.encodeToString(BackupFile.serializer(), BackupFile(exportedAt = System.currentTimeMillis(), recipes = entries))
        context.contentResolver.openOutputStream(target, "wt")?.use { it.write(text.toByteArray()) }
            ?: throw IllegalStateException("Couldn't write the backup file.")
        all.size
    }

    /** Adds recipes from a backup; ones already in the library (same title and creation time) are skipped. */
    suspend fun restore(source: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(source)?.use { it.readBytes().decodeToString() }
            ?: throw IllegalStateException("Couldn't open the file.")
        val backup = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { throw IllegalStateException("This isn't a CookLink AI backup file.") }
        val existing = recipes.recipes.first().map { it.title to it.createdAt }.toSet()
        var added = 0
        for (entry in backup.recipes) {
            val r = entry.recipe
            if ((r.title to r.createdAt) in existing) continue
            val cover = entry.coverJpeg?.let { b64 ->
                runCatching {
                    val f = File(images.coversDir, "${UUID.randomUUID()}.jpg")
                    f.writeBytes(Base64.decode(b64, Base64.NO_WRAP))
                    Uri.fromFile(f).toString()
                }.getOrNull()
            }
            recipes.save(r.copy(id = 0, coverUri = cover))
            added++
        }
        added
    }
}
