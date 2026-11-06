@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cooklink.ai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooklink.ai.AppContainer
import com.cooklink.ai.data.importer.ImportState
import com.cooklink.ai.model.Origin
import com.cooklink.ai.model.Recipe
import com.cooklink.ai.model.SourceLink
import com.cooklink.ai.ui.theme.LocalCertaintyColors
import kotlinx.coroutines.launch

@Composable
fun ImportScreen(
    container: AppContainer,
    sharedText: String?,
    onSharedTextConsumed: () -> Unit,
    sharedMedia: List<android.net.Uri>?,
    onSharedMediaConsumed: () -> Unit,
    writeManually: () -> Unit,
    openRecipe: (Long) -> Unit,
    openProfile: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val imports = container.imports
    val state by imports.state.collectAsStateWithLifecycle()
    val configured by container.keys.configured.collectAsStateWithLifecycle()
    val hasAnyKey = configured.values.any { it }
    val busy = state is ImportState.Running

    var link by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var textOpen by rememberSaveable { mutableStateOf(false) }

    // The new recipe is opened app-wide by CookLinkRoot; here we clear the inputs once per import.
    // Declared before the share handler so a link shared right after an import isn't wiped.
    val draft = rememberMediaDraft()
    val context = androidx.compose.ui.platform.LocalContext.current
    var lastHandled by rememberSaveable { mutableStateOf(-1L) }
    LaunchedEffect(state) {
        val s = state
        if (s is ImportState.Done && s.recipeId != lastHandled) {
            lastHandled = s.recipeId
            link = ""
            text = ""
            textOpen = false
            draft.clear()
        }
    }

    // Photos/videos shared from the gallery or another app.
    LaunchedEffect(sharedMedia) {
        sharedMedia?.let {
            draft.addShared(it, context.contentResolver)
            onSharedMediaConsumed()
        }
    }

    // Shared links go in the link box; shared captions without a link go in the text box.
    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            val url = extractUrl(sharedText)
            if (url != null) {
                link = url
            } else {
                text = sharedText.trim()
                textOpen = true
            }
            onSharedTextConsumed()
        }
    }


    val url = extractUrl(link)

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Import", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Tip: tap Share → CookLink AI in YouTube, TikTok, Instagram or your gallery.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!hasAnyKey) {
            item {
                Card(
                    onClick = openProfile,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Add a Gemini or Claude API key in Profile to import with AI.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }

        item { StatusCard(state, onRetryDismiss = { imports.dismiss() }, onCancel = { imports.cancel() }, onOpen = openRecipe) }

        // Link
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) {
                    MethodTitle(Icons.Outlined.Link, "Link", "YouTube, Shorts, TikTok, Reels, Facebook or a recipe site")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        placeholder = { Text("Paste a link") },
                        singleLine = true,
                        enabled = !busy,
                        shape = MaterialTheme.shapes.small,
                        trailingIcon = {
                            IconButton(onClick = { clipboard.getText()?.text?.let { link = extractUrl(it) ?: it.trim() } }, enabled = !busy) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = "Paste")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(enabled = url != null && hasAnyKey && !busy, onClick = { url?.let { imports.importLink(it) } }) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Import with AI")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            enabled = url != null && !busy,
                            onClick = {
                                val u = url ?: return@TextButton
                                scope.launch {
                                    val id = container.recipes.save(
                                        Recipe(title = "Recipe from ${hostLabel(u)}", origin = Origin.URL, sources = listOf(SourceLink(hostLabel(u), u)))
                                    )
                                    link = ""
                                    openRecipe(id)
                                }
                            },
                        ) { Text("Save as draft") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "YouTube videos are watched by Gemini. TikTok, Instagram and Facebook only share the caption — for those, save the video or take screenshots and add them below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            MediaImportCard(container = container, draft = draft, busy = busy, hasAnyKey = hasAnyKey, linkUrl = url)
        }

        // Text
        item {
            Card(
                onClick = { if (!textOpen) textOpen = true },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) {
                    MethodTitle(Icons.Outlined.Notes, "Text or transcript", "Paste notes, a caption, a description or a transcript")
                    AnimatedVisibility(visible = textOpen) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                placeholder = { Text("e.g. 2 eggs, 200 g flour, a splash of milk… whisk, rest 10 min, fry in butter") },
                                minLines = 5,
                                maxLines = 14,
                                enabled = !busy,
                                shape = MaterialTheme.shapes.small,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(enabled = text.trim().length >= 20 && hasAnyKey && !busy, onClick = { imports.importText(text) }) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Create recipe")
                                }
                                Spacer(Modifier.weight(1f))
                                TextButton(enabled = !busy, onClick = { clipboard.getText()?.text?.let { text = it } }) { Text("Paste") }
                            }
                        }
                    }
                }
            }
        }

        item { MethodCard(Icons.Outlined.EditNote, "Write it yourself", "Type a recipe with ingredients, steps and a photo.", enabled = !busy, onClick = writeManually) }

    }
}

@Composable
private fun StatusCard(state: ImportState, onRetryDismiss: () -> Unit, onCancel: () -> Unit, onOpen: (Long) -> Unit) {
    when (state) {
        ImportState.Idle -> Unit
        is ImportState.Running -> Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.stage.label + (state.progress?.let { " ${(it * 100).toInt()}%" } ?: ""),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    state.progress?.let {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { it },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text(
                        "You can leave this screen; the recipe opens when it's ready.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
        is ImportState.Failed -> Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Import didn't work", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                }
                TextButton(onClick = onRetryDismiss) { Text("OK") }
            }
        }
        is ImportState.Done -> if (state.notice != null) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LocalCertaintyColors.current.confirmed)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Recipe imported", style = MaterialTheme.typography.titleSmall)
                        Text(state.notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            TextButton(onClick = { onOpen(state.recipeId) }) { Text("Open") }
                            TextButton(onClick = onRetryDismiss) { Text("OK") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MethodTitle(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MethodCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = false,
    phase: Int? = null,
    onClick: () -> Unit = {},
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).alpha(if (enabled || phase == null) 1f else 0.55f)) { MethodTitle(icon, title, subtitle) }
            if (phase != null) {
                Text(
                    "Phase $phase",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private val urlRegex = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

/** Shared text often wraps the link in a caption ("Check this out! https://…"), so pull out the URL. */
fun extractUrl(text: String): String? = urlRegex.find(text)?.value?.trimEnd('.', ',', ')', '!', '?')

fun hostLabel(url: String): String {
    val host = runCatching { java.net.URI(url).host }.getOrNull()?.removePrefix("www.")?.removePrefix("m.") ?: return "the web"
    return when {
        "youtu" in host -> "YouTube"
        "tiktok" in host -> "TikTok"
        "instagram" in host -> "Instagram"
        "facebook" in host || "fb.watch" in host -> "Facebook"
        else -> host
    }
}
