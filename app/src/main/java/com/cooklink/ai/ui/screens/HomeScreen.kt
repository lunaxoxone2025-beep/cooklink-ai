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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooklink.ai.AppContainer
import com.cooklink.ai.ui.components.EmptyState
import com.cooklink.ai.ui.components.RecipeCard
import com.cooklink.ai.ui.components.SectionHeader
import java.util.Calendar

@Composable
fun HomeScreen(
    container: AppContainer,
    openRecipe: (Long) -> Unit,
    openImport: () -> Unit,
    openRecipes: () -> Unit,
) {
    val recipes by container.recipes.recipes.collectAsStateWithLifecycle(initialValue = emptyList())
    val recentlyOpened = remember(recipes) {
        recipes.filter { it.lastOpenedAt != null }.sortedByDescending { it.lastOpenedAt }.take(8)
    }
    val favorites = remember(recipes) { recipes.filter { it.isFavorite }.take(10) }
    val newest = remember(recipes) { recipes.sortedByDescending { it.createdAt }.take(10) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
                Text(greeting(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(2.dp))
                Text("What are we cooking?", style = MaterialTheme.typography.headlineLarge)
            }
        }
        item { ImportHero(openImport) }

        if (recipes.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.RamenDining,
                    title = "Your cookbook is empty",
                    message = "Import a recipe or write one yourself. Everything you save stays on this phone and works offline.",
                )
            }
        } else {
            if (recentlyOpened.isNotEmpty()) {
                item { SectionHeader("Pick up where you left off") }
                item { RecipeRow(recentlyOpened.map { it }, openRecipe) }
            }
            if (favorites.isNotEmpty()) {
                item { SectionHeader("Favorites", action = "See all", onAction = openRecipes) }
                item { RecipeRow(favorites, openRecipe) }
            }
            item { SectionHeader("Recently added", action = "Library", onAction = openRecipes) }
            item { RecipeRow(newest, openRecipe) }
        }
    }
}

@Composable
private fun RecipeRow(list: List<com.cooklink.ai.model.Recipe>, openRecipe: (Long) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(list, key = { it.id }) { r ->
            RecipeCard(r, onClick = { openRecipe(r.id) }, modifier = Modifier.width(180.dp))
        }
    }
}

@Composable
private fun ImportHero(openImport: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Card(
        onClick = openImport,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(primary, Color(0xFFE07A3F), Color(0xFFDDA15E))))
                .padding(22.dp),
        ) {
            Column {
                Text("Turn anything into a recipe", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Links, videos, photos, screenshots or your own notes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeroChip(Icons.Outlined.Link)
                    HeroChip(Icons.Outlined.Videocam)
                    HeroChip(Icons.Outlined.Image)
                    HeroChip(Icons.Outlined.Notes)
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.clip(MaterialTheme.shapes.small).background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Import", style = MaterialTheme.typography.labelLarge, color = primary)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroChip(icon: ImageVector) {
    Box(
        Modifier.padding(end = 8.dp).size(34.dp).clip(MaterialTheme.shapes.small).background(Color.White.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
