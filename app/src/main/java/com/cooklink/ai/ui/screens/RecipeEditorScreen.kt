@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cooklink.ai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooklink.ai.AppContainer
import com.cooklink.ai.model.Difficulty
import com.cooklink.ai.ui.components.CertaintyTag
import com.cooklink.ai.ui.components.CoverImage

@Composable
fun RecipeEditorScreen(container: AppContainer, recipeId: Long, onClose: () -> Unit, onSaved: (Long) -> Unit) {
    val vm: RecipeEditorViewModel = viewModel(
        key = "editor-$recipeId",
        factory = viewModelFactory { initializer { RecipeEditorViewModel(container, recipeId) } },
    )
    var confirmDiscard by remember { mutableStateOf(false) }
    var aiDialog by remember { mutableStateOf<AiDialog?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.aiMessage) {
        vm.aiMessage?.let {
            snackbar.showSnackbar(it, withDismissAction = true, duration = SnackbarDuration.Long)
            vm.aiMessage = null
        }
    }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setCover(uri)
    }

    BackHandler { confirmDiscard = true }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (recipeId == 0L) "New recipe" else "Edit recipe") },
                navigationIcon = {
                    IconButton(onClick = { confirmDiscard = true }) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                },
                actions = {
                    TextButton(enabled = vm.canSave, onClick = { vm.save(onSaved) }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        if (vm.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    onClick = { pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (vm.coverUri != null) {
                            CoverImage(vm.coverUri, vm.title, Modifier.fillMaxSize())
                            IconButton(onClick = vm::removeCover, modifier = Modifier.align(Alignment.TopEnd)) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(6.dp))
                                Text("Add a cover photo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                Field(vm.title, { vm.title = it }, "Title *", capitalize = true)
            }
            item {
                Field(vm.description, { vm.description = it }, "Short description", singleLine = false, capitalize = true)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(vm.prep, { vm.prep = it.filter(Char::isDigit) }, "Prep min", Modifier.weight(1f), number = true)
                    Field(vm.cook, { vm.cook = it.filter(Char::isDigit) }, "Cook min", Modifier.weight(1f), number = true)
                    Field(vm.servings, { vm.servings = it.filter(Char::isDigit).take(2) }, "Servings", Modifier.weight(1f), number = true)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Difficulty", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(76.dp))
                    Difficulty.entries.forEach { d ->
                        FilterChip(
                            selected = vm.difficulty == d,
                            onClick = { vm.difficulty = if (vm.difficulty == d) null else d },
                            label = { Text(d.label) },
                        )
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Scale all amounts", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    val current = vm.servings.toIntOrNull() ?: 0
                    OutlinedButton(onClick = { vm.scaleTo(current / 2) }, enabled = current >= 2) { Text("½×") }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick = { vm.scaleTo(current * 2) }, enabled = current in 1..49) { Text("2×") }
                }
            }
            item { EditorHeader("Ingredients", "Amount, unit, then the ingredient. Fractions like 1/2 work. ⇄ asks the AI for a substitute.") }
            itemsIndexed(vm.ingredients, key = { _, r -> r.key }) { index, row ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Field(row.quantity, { vm.updateIngredient(index, row.copy(quantity = it)) }, "Qty", Modifier.width(72.dp))
                        Field(row.unit, { vm.updateIngredient(index, row.copy(unit = it)) }, "Unit", Modifier.width(76.dp))
                        Field(row.name, { vm.updateIngredient(index, row.copy(name = it)) }, "Ingredient", Modifier.weight(1f))
                        if (vm.aiBusyIngredient == index) {
                            CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(
                                onClick = { aiDialog = AiDialog.Swap(index, row.name) },
                                enabled = row.name.isNotBlank() && !vm.aiBusy,
                                modifier = Modifier.size(36.dp),
                            ) { Icon(Icons.Outlined.SwapHoriz, contentDescription = "Replace with AI") }
                        }
                        IconButton(onClick = { vm.removeIngredient(index) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove ingredient")
                        }
                    }
                    CertaintyTag(row.certainty)
                }
            }
            item { AddRowButton("Add ingredient", vm::addIngredient) }

            item { EditorHeader("Steps", "One action per step. ✨ lets the AI rewrite a step.") }
            itemsIndexed(vm.steps, key = { _, r -> r.key }) { index, row ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Step ${index + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            CertaintyTag(row.certainty)
                            if (vm.aiBusyStep == index) {
                                CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = { aiDialog = AiDialog.Rewrite(index) }, enabled = !vm.aiBusy && vm.title.isNotBlank()) {
                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = "Rewrite with AI")
                                }
                            }
                            IconButton(onClick = { vm.moveStep(index, index - 1) }, enabled = index > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                            }
                            IconButton(onClick = { vm.moveStep(index, index + 1) }, enabled = index < vm.steps.lastIndex) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                            }
                            IconButton(onClick = { vm.removeStep(index) }) { Icon(Icons.Outlined.Delete, contentDescription = "Remove step") }
                        }
                        Field(row.text, { vm.updateStep(index, row.copy(text = it)) }, "What to do", singleLine = false, capitalize = true)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field(row.minutes, { vm.updateStep(index, row.copy(minutes = it.filter(Char::isDigit))) }, "Minutes", Modifier.weight(1f), number = true)
                            Field(row.temperature, { vm.updateStep(index, row.copy(temperature = it)) }, "Temp (e.g. 180 °C)", Modifier.weight(1.4f))
                        }
                    }
                }
            }
            item { AddRowButton("Add step", vm::addStep) }

            item { EditorHeader("Equipment & tags", "Separate with commas.") }
            item { Field(vm.equipment, { vm.equipment = it }, "Equipment (pan, oven, blender…)", singleLine = false) }
            item { Field(vm.tags, { vm.tags = it }, "Tags (dinner, vegetarian…)") }

            item { EditorHeader("Nutrition per serving", "Optional.") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Field(vm.calories, { vm.calories = it.filter(Char::isDigit) }, "kcal", Modifier.weight(1f), number = true)
                    Field(vm.protein, { vm.protein = it }, "Protein g", Modifier.weight(1f), number = true)
                    Field(vm.carbs, { vm.carbs = it }, "Carbs g", Modifier.weight(1f), number = true)
                    Field(vm.fat, { vm.fat = it }, "Fat g", Modifier.weight(1f), number = true)
                }
            }

            item { EditorHeader("Sources", "Links to the original video or page.") }
            itemsIndexed(vm.sources, key = { _, r -> r.key }) { index, row ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Field(row.label, { vm.updateSource(index, row.copy(label = it)) }, "Label")
                        Field(row.url, { vm.updateSource(index, row.copy(url = it)) }, "URL")
                    }
                    IconButton(onClick = { vm.removeSource(index) }) { Icon(Icons.Outlined.Delete, contentDescription = "Remove source") }
                }
            }
            item { AddRowButton("Add source link", vm::addSource) }

            item { EditorHeader("My notes", null) }
            item { Field(vm.notes, { vm.notes = it }, "Tips, changes, what worked…", singleLine = false, capitalize = true) }

            item {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.save(onSaved) }, enabled = vm.canSave, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(if (vm.saving) "Saving…" else "Save recipe")
                }
            }
        }
    }

    aiDialog?.let { d ->
        var wish by remember(d) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { aiDialog = null },
            title = { Text(if (d is AiDialog.Swap) "Replace “${d.name}”" else "Rewrite step ${(d as AiDialog.Rewrite).index + 1}") },
            text = {
                Column {
                    Text(
                        if (d is AiDialog.Swap) "What should it become? Leave empty for the usual substitute."
                        else "Anything specific? Leave empty to just make it clearer.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = wish,
                        onValueChange = { wish = it },
                        placeholder = { Text(if (d is AiDialog.Swap) "e.g. olive oil, vegan, cheaper" else "e.g. use the oven, more detail") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (d) {
                        is AiDialog.Swap -> vm.substituteIngredient(d.index, wish)
                        is AiDialog.Rewrite -> vm.regenerateStep(d.index, wish)
                    }
                    aiDialog = null
                }) { Text("Ask AI") }
            },
            dismissButton = { TextButton(onClick = { aiDialog = null }) { Text("Cancel") } },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Anything you changed on this screen will be lost.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; vm.discard(); onClose() }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    number: Boolean = false,
    capitalize: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text,
            capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
        ),
        modifier = modifier,
    )
}

@Composable
private fun EditorHeader(title: String, hint: String?) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddRowButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.clip(MaterialTheme.shapes.small)) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

private sealed interface AiDialog {
    data class Swap(val index: Int, val name: String) : AiDialog
    data class Rewrite(val index: Int) : AiDialog
}
