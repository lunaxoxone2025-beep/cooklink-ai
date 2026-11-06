@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cooklink.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooklink.ai.AppContainer
import com.cooklink.ai.BuildConfig
import com.cooklink.ai.data.settings.AiProvider
import com.cooklink.ai.data.settings.RecipeLanguage
import com.cooklink.ai.data.settings.ThemeMode
import com.cooklink.ai.ui.theme.LocalCertaintyColors
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val recipes by container.recipes.recipes.collectAsStateWithLifecycle(initialValue = emptyList())
    val collections by container.recipes.collections.collectAsStateWithLifecycle(initialValue = emptyList())
    val theme by container.settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val provider by container.settings.provider.collectAsStateWithLifecycle(initialValue = AiProvider.GEMINI)
    val configured by container.keys.configured.collectAsStateWithLifecycle()
    val language by container.settings.recipeLanguage.collectAsStateWithLifecycle(initialValue = RecipeLanguage.ORIGINAL)

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Profile", style = MaterialTheme.typography.headlineLarge) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Recipes", recipes.size.toString(), Modifier.weight(1f))
                Stat("Favorites", recipes.count { it.isFavorite }.toString(), Modifier.weight(1f))
                Stat("Collections", collections.size.toString(), Modifier.weight(1f))
            }
        }

        item {
            Section("Appearance") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = theme == mode,
                            onClick = { scope.launch { container.settings.setTheme(mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(mode.label) }
                    }
                }
            }
        }

        item {
            Section("AI provider") {
                Text(
                    "Choose which AI reads your videos, photos and text. Keys are encrypted with the Android Keystore and never leave this phone except to call that provider.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                AiProvider.entries.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = provider == p, onClick = { scope.launch { container.settings.setProvider(p) } })
                        Column(Modifier.weight(1f)) {
                            Text(p.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                when (p) {
                                    AiProvider.GEMINI -> "Watches whole videos and listens to audio."
                                    AiProvider.CLAUDE -> "Strong at text and recipe pages. Cannot watch YouTube videos."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (configured[p] == true) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Key saved", tint = LocalCertaintyColors.current.confirmed, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        item {
            Section("Recipe language") {
                Text(
                    "Imported recipes are written in this language.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RecipeLanguage.entries.forEach { l ->
                        androidx.compose.material3.FilterChip(
                            selected = language == l,
                            onClick = { scope.launch { container.settings.setRecipeLanguage(l) } },
                            label = { Text(l.label) },
                        )
                    }
                }
            }
        }

        AiProvider.entries.forEach { p ->
            item(key = "key-${p.name}") { KeyEditor(container, p, configured[p] == true) }
        }

        item {
            Section("About") {
                Text("CookLink AI ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Built in phases. Ready now: library, collections, favourites, editor, shopping list, AI import from links and text. " +
                        "Next: photos and video (3), recipe generation (4), cooking mode (5), AI chef (6).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun KeyEditor(container: AppContainer, provider: AiProvider, saved: Boolean) {
    val uri = LocalUriHandler.current
    var editing by remember(saved) { mutableStateOf(!saved) }
    var value by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    Section("${provider.label} key") {
        if (!editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("•••• saved", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { editing = true }) { Text("Replace") }
                TextButton(onClick = { container.keys.clear(provider) }) { Text("Remove") }
            }
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.trim() },
                placeholder = { Text(provider.keyHint) },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (visible) "Hide" else "Show")
                    }
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { runCatching { uri.openUri(provider.keyUrl) } }) { Text("Get a key") }
                Spacer(Modifier.weight(1f))
                if (saved) TextButton(onClick = { editing = false; value = "" }) { Text("Cancel") }
                FilledTonalButton(enabled = value.length >= 20, onClick = {
                    container.keys.save(provider, value)
                    value = ""
                    editing = false
                }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
