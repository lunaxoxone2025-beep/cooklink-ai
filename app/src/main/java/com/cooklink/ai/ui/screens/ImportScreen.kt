@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cooklink.ai.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooklink.ai.AppContainer
import com.cooklink.ai.model.Origin
import com.cooklink.ai.model.Recipe
import com.cooklink.ai.model.SourceLink
import kotlinx.coroutines.launch

@Composable
fun ImportScreen(
    container: AppContainer,
    sharedText: String?,
    onSharedTextConsumed: () -> Unit,
    writeManually: () -> Unit,
    openRecipe: (Long) -> Unit,
    openProfile: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val configured by container.keys.configured.collectAsStateWithLifecycle()
    val hasAnyKey = configured.values.any { it }
    var link by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            link = extractUrl(sharedText) ?: sharedText.trim()
            onSharedTextConsumed()
        }
    }

    val url = extractUrl(link)

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Import", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Bring in a recipe from anywhere. Tip: in YouTube, TikTok or Instagram tap Share → CookLink AI.",
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
                            "Add a Gemini or Claude API key in Profile so AI import is ready when it switches on.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }

        // Link: the most common path, so it sits on top with its own input.
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
                        shape = MaterialTheme.shapes.small,
                        trailingIcon = {
                            IconButton(onClick = { clipboard.getText()?.text?.let { link = it.trim() } }) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = "Paste")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    PhaseNote("AI extraction from links arrives in Phase 2. For now you can keep the link as a draft and fill it in yourself.")
                    Spacer(Modifier.height(10.dp))
                    Button(
                        enabled = url != null,
                        onClick = {
                            val u = url ?: return@Button
                            scope.launch {
                                val id = container.recipes.save(
                                    Recipe(
                                        title = "Recipe from ${hostLabel(u)}",
                                        origin = Origin.URL,
                                        sources = listOf(SourceLink(hostLabel(u), u)),
                                    )
                                )
                                link = ""
                                openRecipe(id)
                            }
                        },
                    ) { Text("Save link as draft") }
                }
            }
        }

        item {
            MethodCard(Icons.Outlined.EditNote, "Write it yourself", "Type a recipe with ingredients, steps and a photo.", enabled = true, onClick = writeManually)
        }
        item { MethodCard(Icons.Outlined.Notes, "Text or transcript", "Paste notes, a caption or a transcript.", phase = 2) }
        item { MethodCard(Icons.Outlined.Image, "Photos & screenshots", "Dish photos or screenshots of a recipe.", phase = 3) }
        item { MethodCard(Icons.Outlined.Videocam, "Video from your phone", "Frames, speech and subtitles are analysed.", phase = 3) }
        item { MethodCard(Icons.Outlined.Layers, "Combine sources", "Mix a video, screenshots and notes into one recipe.", phase = 3) }
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
            Box(Modifier.weight(1f).alpha(if (enabled) 1f else 0.55f)) { MethodTitle(icon, title, subtitle) }
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

@Composable
private fun PhaseNote(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
