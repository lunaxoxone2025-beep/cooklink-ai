@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cooklink.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooklink.ai.AppContainer
import com.cooklink.ai.data.db.ShoppingItemEntity
import com.cooklink.ai.model.formatQuantity
import com.cooklink.ai.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun ShoppingScreen(container: AppContainer) {
    val repo = container.shopping
    val scope = rememberCoroutineScope()
    val items by repo.items.collectAsStateWithLifecycle(initialValue = emptyList())
    var input by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var showPlanner by remember { mutableStateOf(false) }

    val open = remember(items) { items.filter { !it.checked }.groupBy { it.category }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }
    val done = remember(items) { items.filter { it.checked } }

    fun add() {
        val text = input.trim()
        if (text.isEmpty()) return
        val (qty, unit, name) = parseLine(text)
        scope.launch { repo.addManual(name, qty, unit) }
        input = ""
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Shopping", style = MaterialTheme.typography.headlineLarge)
                val left = items.count { !it.checked }
                Text(
                    if (items.isEmpty()) "Nothing on the list" else "$left to buy · ${done.size} in the basket",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { menu = true }, enabled = items.isNotEmpty()) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Remove ticked items") }, enabled = done.isNotEmpty(), onClick = {
                    menu = false; scope.launch { repo.clearChecked() }
                })
                DropdownMenuItem(text = { Text("Clear whole list") }, onClick = { menu = false; confirmClearAll = true })
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Add item, e.g. 2 kg potatoes") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { add() }, enabled = input.isNotBlank()) { Icon(Icons.Filled.Add, contentDescription = "Add item") }
        }
        androidx.compose.material3.OutlinedButton(
            onClick = { showPlanner = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        ) {
            Icon(Icons.Outlined.MenuBook, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add ingredients from recipes")
        }

        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.ShoppingBasket,
                title = "Your list is empty",
                message = "Add items here, or open a recipe and tap “Add to shopping list”. Matching ingredients are combined.",
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                open.forEach { (category, list) ->
                    stickyHeader(key = "h-$category") {
                        Text(
                            category.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
                        )
                    }
                    items(list, key = { it.id }) { item ->
                        ShoppingRow(item, onToggle = { scope.launch { repo.toggle(item) } }, onDelete = { scope.launch { repo.delete(item.id) } }, modifier = Modifier.animateItem())
                    }
                }
                if (done.isNotEmpty()) {
                    stickyHeader(key = "h-done") {
                        Text(
                            "IN THE BASKET",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(start = 20.dp, top = 18.dp, bottom = 4.dp),
                        )
                    }
                    items(done, key = { it.id }) { item ->
                        ShoppingRow(item, onToggle = { scope.launch { repo.toggle(item) } }, onDelete = { scope.launch { repo.delete(item.id) } }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }

    if (showPlanner) RecipePlannerSheet(container) { showPlanner = false }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear the whole list?") },
            confirmButton = { TextButton(onClick = { confirmClearAll = false; scope.launch { repo.clearAll() } }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ShoppingRow(item: ShoppingItemEntity, onToggle: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).alpha(if (item.checked) 0.5f else 1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            )
            if (!item.recipeTitle.isNullOrBlank()) {
                Text(item.recipeTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        val amount = listOf(formatQuantity(item.quantity), item.unit).filter { it.isNotBlank() }.joinToString(" ")
        if (amount.isNotBlank()) Text(amount, style = MaterialTheme.typography.titleSmall, modifier = Modifier.alpha(if (item.checked) 0.5f else 1f))
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Close, contentDescription = "Remove ${item.name}") }
    }
}

private val knownUnits = setOf(
    "g", "kg", "mg", "ml", "l", "dl", "cl", "tsp", "tbsp", "cup", "cups", "oz", "lb", "lbs",
    "pinch", "clove", "cloves", "can", "cans", "pack", "packs", "bunch", "slice", "slices", "piece", "pieces",
)

/** "2 kg potatoes" -> (2.0, "kg", "potatoes"); "milk" -> (null, "", "milk"). */
private fun parseLine(text: String): Triple<Double?, String, String> {
    val parts = text.split(Regex("\\s+"))
    val qty = parts.firstOrNull()?.let { RecipeEditorViewModel.parseNumber(it) }
    if (qty == null || parts.size < 2) return Triple(null, "", text)
    val maybeUnit = parts[1].lowercase().trimEnd('.')
    return if (maybeUnit in knownUnits && parts.size >= 3) {
        Triple(qty, maybeUnit, parts.drop(2).joinToString(" "))
    } else {
        Triple(qty, "", parts.drop(1).joinToString(" "))
    }
}

/** Pick several recipes with servings each and add all their ingredients at once (merged by unit). */
@Composable
private fun RecipePlannerSheet(container: AppContainer, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val recipes by container.recipes.recipes.collectAsStateWithLifecycle(initialValue = emptyList())
    val picked = remember { androidx.compose.runtime.mutableStateMapOf<Long, Int>() }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Plan your shopping", style = MaterialTheme.typography.titleLarge)
            Text(
                "Choose recipes and servings. Matching ingredients are combined.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (recipes.none { it.ingredients.isNotEmpty() }) {
                Text("No recipes with ingredients yet.", modifier = Modifier.padding(vertical = 16.dp))
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(recipes.filter { it.ingredients.isNotEmpty() }, key = { it.id }) { r ->
                    val servings = picked[r.id]
                    Row(
                        Modifier.fillMaxWidth().clickable { if (servings == null) picked[r.id] = r.servings else picked.remove(r.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = servings != null, onCheckedChange = { if (it) picked[r.id] = r.servings else picked.remove(r.id) })
                        Text(r.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2)
                        if (servings != null) {
                            IconButton(onClick = { if (servings > 1) picked[r.id] = servings - 1 }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                            Text("$servings", style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { if (servings < 99) picked[r.id] = servings + 1 }) { Text("+", style = MaterialTheme.typography.titleLarge) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Button(
                enabled = picked.isNotEmpty(),
                onClick = {
                    val chosen = picked.toMap()
                    scope.launch {
                        recipes.filter { it.id in chosen }.forEach { container.shopping.addRecipe(it, chosen.getValue(it.id)) }
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (picked.isEmpty()) "Pick recipes" else "Add ${picked.size} recipe(s)") }
        }
    }
}
