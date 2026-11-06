@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cooklink.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooklink.ai.AppContainer
import com.cooklink.ai.model.Certainty
import com.cooklink.ai.model.Recipe
import com.cooklink.ai.model.formatMinutes
import com.cooklink.ai.model.formatQuantity
import com.cooklink.ai.ui.components.CertaintyDot
import com.cooklink.ai.ui.components.CertaintyTag
import com.cooklink.ai.ui.components.CoverImage
import com.cooklink.ai.ui.theme.LocalCertaintyColors
import kotlinx.coroutines.launch

@Composable
fun RecipeDetailScreen(
    container: AppContainer,
    recipeId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onCook: (servings: Int) -> Unit,
    onAskChef: () -> Unit,
    onCompare: () -> Unit,
) {
    val repo = container.recipes
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val recipeState by remember(recipeId) { repo.recipe(recipeId) }.collectAsStateWithLifecycle(initialValue = null)
    var seen by remember { mutableStateOf(false) }

    LaunchedEffect(recipeId) { repo.markOpened(recipeId) }

    val recipe = recipeState
    if (recipe == null) {
        // Null before the first load, or after the recipe was deleted: only leave in the second case.
        if (seen) LaunchedEffect(Unit) { onDeleted() }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    SideEffect { seen = true }

    var servings by rememberSaveable(recipe.id) { mutableIntStateOf(recipe.servings.coerceAtLeast(1)) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showCollections by remember { mutableStateOf(false) }
    val factor = if (recipe.servings > 0) servings.toDouble() / recipe.servings else 1.0

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item {
                Box {
                    CoverImage(recipe.coverUri, recipe.title, Modifier.fillMaxWidth().aspectRatio(4f / 3.2f))
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.3f to Color.Transparent)
                        )
                    )
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OverlayButton(Icons.Filled.ArrowBack, "Back", onBack)
                        Spacer(Modifier.weight(1f))
                        OverlayButton(
                            if (recipe.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            if (recipe.isFavorite) "Remove from favorites" else "Add to favorites",
                        ) { scope.launch { repo.setFavorite(recipe.id, !recipe.isFavorite) } }
                        Spacer(Modifier.width(8.dp))
                        Box {
                            OverlayButton(Icons.Filled.MoreVert, "More") { menuOpen = true }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                    onClick = { menuOpen = false; onEdit() },
                                )
                                if (recipe.original != null) {
                                    DropdownMenuItem(
                                        text = { Text("Compare with original") },
                                        leadingIcon = { Icon(Icons.Outlined.CompareArrows, null) },
                                        onClick = { menuOpen = false; onCompare() },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Collections") },
                                    leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                                    onClick = { menuOpen = false; showCollections = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                    onClick = { menuOpen = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Text(recipe.origin.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(recipe.title, style = MaterialTheme.typography.headlineMedium)
                    if (recipe.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(recipe.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(16.dp))
                    MetaGrid(recipe)
                    if (recipe.estimatedCount + recipe.missingCount > 0) {
                        Spacer(Modifier.height(14.dp))
                        ConfidenceBanner(recipe)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.Button(
                            onClick = { onCook(servings) },
                            enabled = recipe.steps.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Icon(Icons.Outlined.Restaurant, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start cooking")
                        }
                        androidx.compose.material3.OutlinedButton(onClick = onAskChef, modifier = Modifier.height(52.dp)) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Ask chef")
                        }
                    }
                }
            }

            // Ingredients with servings scaler
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ingredients", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    ServingsStepper(servings, onChange = { servings = it })
                }
            }
            if (recipe.ingredients.isEmpty()) {
                item { Placeholder("No ingredients yet.") }
            }
            itemsIndexed(recipe.ingredients) { _, ing ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CertaintyDot(ing.certainty)
                    Spacer(Modifier.width(12.dp))
                    val qty = formatQuantity(ing.quantity?.times(factor))
                    val amount = listOf(qty, ing.unit).filter { it.isNotBlank() }.joinToString(" ")
                    Column(Modifier.weight(1f)) {
                        Text(ing.name, style = MaterialTheme.typography.bodyLarge)
                        if (ing.note.isNotBlank()) {
                            Text(ing.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (amount.isNotBlank()) {
                        Text(amount, style = MaterialTheme.typography.titleSmall)
                    }
                    if (ing.certainty != Certainty.CONFIRMED) {
                        Spacer(Modifier.width(8.dp))
                        CertaintyTag(ing.certainty)
                    }
                }
            }
            if (recipe.ingredients.isNotEmpty()) {
                item {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                container.shopping.addRecipe(recipe, servings)
                                snackbar.showSnackbar("Added ${recipe.ingredients.size} ingredients for $servings servings")
                            }
                        },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Outlined.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add to shopping list")
                    }
                }
            }

            if (recipe.equipment.isNotEmpty()) {
                item { SectionTitle("Equipment") }
                item {
                    FlowRow(
                        Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        recipe.equipment.forEach {
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Method") }
            if (recipe.steps.isEmpty()) item { Placeholder("No steps yet.") }
            itemsIndexed(recipe.steps) { index, step ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(30.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(step.text, style = MaterialTheme.typography.bodyLarge)
                        if (step.durationMinutes != null || step.temperature.isNotBlank() || step.certainty != Certainty.CONFIRMED) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                step.durationMinutes?.let { StepMeta(Icons.Outlined.Timer, formatMinutes(it)) }
                                if (step.temperature.isNotBlank()) StepMeta(Icons.Outlined.Thermostat, step.temperature)
                                CertaintyTag(step.certainty)
                            }
                        }
                    }
                }
            }

            if (!recipe.nutrition.isEmpty) {
                item { SectionTitle("Nutrition per serving (estimate)") }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val n = recipe.nutrition
                        NutritionCell("kcal", n.calories?.toString(), Modifier.weight(1f))
                        NutritionCell("protein", n.proteinG?.let { "${formatQuantity(it)} g" }, Modifier.weight(1f))
                        NutritionCell("carbs", n.carbsG?.let { "${formatQuantity(it)} g" }, Modifier.weight(1f))
                        NutritionCell("fat", n.fatG?.let { "${formatQuantity(it)} g" }, Modifier.weight(1f))
                    }
                }
            }

            if (recipe.notes.isNotBlank()) {
                item { SectionTitle("My notes") }
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    ) {
                        Text(recipe.notes, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(16.dp))
                    }
                }
            }

            if (recipe.sources.isNotEmpty()) {
                item { SectionTitle("Original source") }
                item { Sources(recipe) }
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete recipe?") },
            text = { Text("“${recipe.title}” will be removed from your library. This can’t be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        // The screen closes itself when the recipe flow turns null.
                        container.images.deleteCover(recipe.coverUri)
                        repo.delete(recipe.id)
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (showCollections) CollectionsSheet(container, recipe.id) { showCollections = false }
}

@Composable
private fun OverlayButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f), contentColor = Color.White),
    ) { Icon(icon, contentDescription = description) }
}

@Composable
private fun MetaGrid(recipe: Recipe) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetaCell("Prep", formatMinutes(recipe.prepMinutes), Modifier.weight(1f))
        MetaCell("Cook", formatMinutes(recipe.cookMinutes), Modifier.weight(1f))
        MetaCell("Total", formatMinutes(recipe.totalMinutes), Modifier.weight(1f))
        MetaCell("Level", recipe.difficulty?.label ?: "—", Modifier.weight(1f))
    }
}

@Composable
private fun MetaCell(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NutritionCell(label: String, value: String?, modifier: Modifier) = MetaCell(label, value ?: "—", modifier)

@Composable
private fun ConfidenceBanner(recipe: Recipe) {
    val c = LocalCertaintyColors.current
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(c.estimated.copy(alpha = 0.12f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Check before cooking", style = MaterialTheme.typography.titleSmall, color = c.estimated)
            val parts = buildList {
                if (recipe.estimatedCount > 0) add("${recipe.estimatedCount} AI estimate${if (recipe.estimatedCount > 1) "s" else ""}")
                if (recipe.missingCount > 0) add("${recipe.missingCount} missing value${if (recipe.missingCount > 1) "s" else ""}")
            }
            Text(
                parts.joinToString(" · ") + ". Marked items were not stated in the source.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServingsStepper(value: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { if (value > 1) onChange(value - 1) }, enabled = value > 1) {
            Icon(Icons.Filled.Remove, contentDescription = "Fewer servings")
        }
        Text("$value serving${if (value == 1) "" else "s"}", style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = { if (value < 99) onChange(value + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = "More servings")
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Column {
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp))
    }
}

@Composable
private fun Placeholder(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
private fun StepMeta(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Sources(recipe: Recipe) {
    val uri = LocalUriHandler.current
    Column(Modifier.padding(horizontal = 12.dp)) {
        recipe.sources.forEach { s ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                    .clickable { runCatching { uri.openUri(s.url) } }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(s.label.ifBlank { "Source" }, style = MaterialTheme.typography.titleSmall)
                    Text(s.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun CollectionsSheet(container: AppContainer, recipeId: Long, onDismiss: () -> Unit) {
    val repo = container.recipes
    val scope = rememberCoroutineScope()
    val collections by repo.collections.collectAsStateWithLifecycle(initialValue = emptyList())
    val memberOf by remember(recipeId) { repo.collectionIdsFor(recipeId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Collections", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            if (collections.isEmpty()) {
                Text("No collections yet. Create one below.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            collections.forEach { c ->
                val inside = c.id in memberOf
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                        .clickable { scope.launch { repo.setInCollection(recipeId, c.id, !inside) } }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = inside, onCheckedChange = { scope.launch { repo.setInCollection(recipeId, c.id, it) } })
                    Text(c.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("New collection") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(enabled = newName.isNotBlank(), onClick = {
                    val name = newName
                    newName = ""
                    scope.launch {
                        val id = repo.createCollection(name)
                        repo.setInCollection(recipeId, id, true)
                    }
                }) { Text("Add") }
            }
        }
    }
}
