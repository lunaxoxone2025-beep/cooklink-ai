@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cooklink.ai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cooklink.ai.AppContainer
import com.cooklink.ai.data.ai.AiException
import com.cooklink.ai.data.importer.MediaPrep
import com.cooklink.ai.data.importer.VideoInfo
import com.cooklink.ai.data.settings.AiProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Form state for the photos/video/notes import. Lives at screen level so it survives the
 * card scrolling out of the LazyColumn.
 */
class MediaDraft(private val prep: MediaPrep, private val scope: CoroutineScope) {
    var images by mutableStateOf(listOf<Uri>())
    var video by mutableStateOf<VideoInfo?>(null)
    var notes by mutableStateOf("")
    var useLink by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun addImages(uris: List<Uri>) {
        val room = MediaPrep.MAX_IMAGES - images.size
        if (uris.size > room) error = "Up to ${MediaPrep.MAX_IMAGES} images per recipe; extra ones were skipped."
        images = (images + uris.take(room.coerceAtLeast(0))).distinct()
    }

    fun setVideo(uri: Uri) {
        scope.launch {
            try {
                val info = prep.describeVideo(uri)
                prep.checkVideo(info)
                video = info
                error = null
            } catch (e: AiException) {
                error = e.message
            } catch (e: Exception) {
                error = "Couldn't open that video."
            }
        }
    }

    /** Adds media shared from another app, sorting photos from videos. */
    fun addShared(uris: List<Uri>, resolver: android.content.ContentResolver) {
        val (videos, pics) = uris.partition { resolver.getType(it)?.startsWith("video/") == true }
        addImages(pics)
        videos.firstOrNull()?.let { setVideo(it) }
    }

    fun clear() {
        images = emptyList(); video = null; notes = ""; useLink = false; error = null
    }
}

@Composable
fun rememberMediaDraft(): MediaDraft {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { MediaDraft(MediaPrep(context.applicationContext), scope) }
}

/** Photos, screenshots, a phone video and notes — alone or combined — turned into one recipe. */
@Composable
fun MediaImportCard(
    container: AppContainer,
    draft: MediaDraft,
    busy: Boolean,
    hasAnyKey: Boolean,
    linkUrl: String?,
) {
    val provider by container.settings.provider.collectAsState(initial = AiProvider.GEMINI)
    val images = draft.images
    val video = draft.video
    val notes = draft.notes
    val useLink = draft.useLink
    val error = draft.error

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MediaPrep.MAX_IMAGES)) { uris ->
        if (uris.isNotEmpty()) draft.addImages(uris)
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) draft.setVideo(uri)
    }

    val includeLink = useLink && linkUrl != null
    val hasMaterial = images.isNotEmpty() || video != null || notes.isNotBlank()

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Photos, video & notes", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Screenshots, dish photos or a video from your phone — on their own or combined.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy && images.size < MediaPrep.MAX_IMAGES,
                    onClick = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Photos")
                }
                OutlinedButton(
                    enabled = !busy,
                    onClick = { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (video == null) "Video" else "Change video")
                }
            }

            if (images.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(images, key = { it.toString() }) { uri ->
                        Box {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(84.dp).clip(MaterialTheme.shapes.small),
                            )
                            RemoveBadge(Modifier.align(Alignment.TopEnd), enabled = !busy) { draft.images = draft.images - uri }
                        }
                    }
                }
                Text(
                    "${images.size}/${MediaPrep.MAX_IMAGES} images",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            video?.let { v ->
                Spacer(Modifier.height(12.dp))
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    v.durationMs?.let { formatDuration(it) },
                                    v.sizeBytes.takeIf { it > 0 }?.let { "%.1f MB".format(it / 1048576.0) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RemoveBadge(enabled = !busy) { draft.video = null }
                    }
                }
                Text(
                    if (provider == AiProvider.GEMINI) "Gemini watches the video and listens to the audio and subtitles."
                    else "Claude reads ${MediaPrep.FRAME_COUNT} still frames (no audio). Choose Gemini in Profile for spoken recipes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { draft.notes = it },
                placeholder = { Text("Optional notes: servings, changes, what you heard…") },
                minLines = 2,
                maxLines = 8,
                enabled = !busy,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            if (linkUrl != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useLink, onCheckedChange = { draft.useLink = it }, enabled = !busy)
                    Text("Also use the link above", style = MaterialTheme.typography.bodyMedium)
                }
            }

            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            }

            Spacer(Modifier.height(10.dp))
            Button(
                enabled = hasAnyKey && !busy && (hasMaterial || includeLink) && (images.isNotEmpty() || video != null || notes.trim().length >= 20 || includeLink),
                onClick = {
                    draft.error = null
                    container.imports.importMedia(images, video, notes, if (includeLink) linkUrl else null)
                },
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Create recipe")
            }
        }
    }
}

@Composable
private fun RemoveBadge(modifier: Modifier = Modifier, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.55f),
        modifier = modifier.padding(4.dp).size(24.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
