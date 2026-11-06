@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cooklink.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooklink.ai.AppContainer
import com.cooklink.ai.ui.components.EmptyState
import com.cooklink.ai.ui.components.RecipeCard
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val FILTER_ALL = -1L
private const val FILTER_FAV = -2L

@Composable
fun RecipesScreen(container: AppContainer, openRecipe: (Long) -> Unit, createRecipe: () -> Unit) {
    val repo = container.recipes
    val scope = rememberCoroutineScope()
    val recipes by repo.recipes.collectAsStateWithLifecycle(initialValue = emptyList())
    val collections by repo.collections.collectAsStateWithLifecycle(initialValue = emptyList())

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var showNewCollection by remember { mutableStateOf(false) }
    var confirmDeleteCollection by remember { mutableStateOf<Long?>(null) }

    // If the selected collection is deleted, fall back to "All".
    LaunchedEffect(collections, filter) {
        if (filter >= 0 && collections.none { it.id == filter }) filter = FILTER_ALL
    }

    val idsInCollection by remember(filter) {
        if (filter >= 0) repo.recipeIdsIn(filter) else flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val visible = remember(recipes, query, filter, idsInCollection) {
        val q = query.trim().lowercase()
        recipes.filter { r ->
            val passFilter = when {
                filter == FILTER_FAV -> r.isFavorite
                filter >= 0 -> r.id in idsInCollection
                else -> true
            }
            passFilter && (q.isEmpty() ||
                r.title.lowercase().contains(q) ||
                r.description.lowercase().contains(q) ||
                r.tags.any { it.lowercase().contains(q) } ||
                r.ingredients.any { it.name.lowercase().contains(q) })
        }
    }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = createRecipe,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New recipe") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "RECIPES",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
            )
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search recipes or ingredients") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(selected = filter == FILTER_ALL, onClick = { filter = FILTER_ALL }, label = { Text("All · ${recipes.size}") })
                }
                item {
                    FilterChip(
                        selected = filter == FILTER_FAV,
                        onClick = { filter = FILTER_FAV },
                        label = { Text("Favorites") },
                        leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.padding(0.dp)) },
                    )
                }
                items(collections, key = { it.id }) { c ->
                    Box {
                        FilterChip(
                            selected = filter == c.id,
                            onClick = { if (filter == c.id) confirmDeleteCollection = c.id else filter = c.id },
                            label = { Text("${c.name} · ${c.recipeCount}") },
                        )
                    }
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = { showNewCollection = true },
                        label = { Text("Collection") },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                    )
                }
            }

            if (visible.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = if (recipes.isEmpty()) "No recipes yet" else "Nothing matches",
                    message = when {
                        recipes.isEmpty() -> "Create your first recipe, or import one from the Import tab."
                        filter >= 0 && query.isBlank() -> "This collection is empty. Open a recipe and add it from the menu."
                        else -> "Try another word or filter."
                    },
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visible, key = { it.id }) { r ->
                        RecipeCard(r, onClick = { openRecipe(r.id) }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }

    if (showNewCollection) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewCollection = false },
            title = { Text("New collection") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch { filter = repo.createCollection(name) }
                    showNewCollection = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewCollection = false }) { Text("Cancel") } },
        )
    }

    confirmDeleteCollection?.let { id ->
        val c = collections.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { confirmDeleteCollection = null },
            title = { Text("Delete “${c?.name ?: ""}”?") },
            text = { Text("The collection is removed. The recipes inside it stay in your library.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.deleteCollection(id) }
                    confirmDeleteCollection = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteCollection = null }) { Text("Keep") } },
        )
    }
}
