@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cooklink.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooklink.ai.AppContainer
import com.cooklink.ai.model.Recipe
import com.cooklink.ai.ui.components.EmptyState
import com.cooklink.ai.ui.components.NeonPanel
import com.cooklink.ai.ui.components.RecipeCard
import com.cooklink.ai.ui.components.SectionHeader
import com.cooklink.ai.ui.theme.LocalNeon
import com.cooklink.ai.ui.theme.Neon
import com.cooklink.ai.ui.theme.neonGlow
import java.util.Calendar

@Composable
fun HomeScreen(
    container: AppContainer,
    openRecipe: (Long) -> Unit,
    openImport: () -> Unit,
    openRecipes: () -> Unit,
    openChef: () -> Unit,
) {
    val recipes by container.recipes.recipes.collectAsStateWithLifecycle(initialValue = emptyList())
    val recentlyOpened = remember(recipes) {
        recipes.filter { it.lastOpenedAt != null }.sortedByDescending { it.lastOpenedAt }.take(8)
    }
    val favorites = remember(recipes) { recipes.filter { it.isFavorite }.take(10) }
    val newest = remember(recipes) { recipes.sortedByDescending { it.createdAt }.take(10) }
    // A different highlighted recipe each day.
    val pick = remember(recipes) {
        val pool = favorites.ifEmpty { recipes }
        if (pool.isEmpty()) null else pool[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % pool.size]
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { Header(recipes.size) }
        item { ImportHero(openImport) }
        item { ChefStrip(openChef) }

        if (recipes.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.RamenDining,
                    title = "Empty cookbook",
                    message = "Import a recipe or write one yourself. Everything you save stays on this phone and works offline.",
                )
            }
        } else {
            pick?.let { p ->
                item { SectionHeader("Tonight's pick") }
                item {
                    RecipeCard(p, onClick = { openRecipe(p.id) }, wide = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                }
            }
            if (recentlyOpened.isNotEmpty()) {
                item { SectionHeader("Keep cooking") }
                item { RecipeRow(recentlyOpened, openRecipe) }
            }
            if (favorites.isNotEmpty()) {
                item { SectionHeader("Favorites", action = "All", onAction = openRecipes) }
                item { RecipeRow(favorites, openRecipe) }
            }
            item { SectionHeader("Fresh in", action = "Library", onAction = openRecipes) }
            item { RecipeRow(newest, openRecipe) }
        }
    }
}

@Composable
private fun Header(count: Int) {
    val accent = LocalNeon.current.accent
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("COOK", style = MaterialTheme.typography.headlineMedium, color = Neon.Text)
            Box(
                Modifier.padding(start = 4.dp).clip(MaterialTheme.shapes.extraSmall).background(accent).padding(horizontal = 6.dp, vertical = 1.dp),
            ) { Text("LINK", style = MaterialTheme.typography.headlineMedium, color = Neon.Ink) }
            Spacer(Modifier.weight(1f))
            Text(
                "$count ${if (count == 1) "RECIPE" else "RECIPES"}",
                style = MaterialTheme.typography.labelMedium,
                color = Neon.Muted,
                modifier = Modifier.clip(CircleShape).border(1.dp, Neon.Line, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(greeting().uppercase(), style = MaterialTheme.typography.labelMedium, color = accent)
        Spacer(Modifier.height(4.dp))
        Text("WHAT ARE WE\nCOOKING?", style = MaterialTheme.typography.displayMedium)
    }
}

@Composable
private fun ImportHero(openImport: () -> Unit) {
    val accent = LocalNeon.current.accent
    NeonPanel(glow = accent, onClick = openImport, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("DROP ANYTHING IN.", style = MaterialTheme.typography.headlineSmall, color = accent)
            Text("GET A RECIPE OUT.", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Links, videos, photos, screenshots or your own notes — AI turns them into a recipe.",
                style = MaterialTheme.typography.bodyMedium,
                color = Neon.Muted,
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceDot(Icons.Outlined.Link)
                SourceDot(Icons.Outlined.Videocam)
                SourceDot(Icons.Outlined.Image)
                SourceDot(Icons.Outlined.Notes)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.neonGlow(accent, radius = 12.dp, corner = 24.dp, strength = 0.5f)
                        .clip(CircleShape).background(accent).padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("IMPORT", style = MaterialTheme.typography.labelLarge, color = Neon.Ink)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Neon.Ink, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SourceDot(icon: ImageVector) {
    Box(
        Modifier.padding(end = 8.dp).size(36.dp).clip(CircleShape).border(1.dp, Neon.Line, CircleShape).background(Neon.PanelHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Neon.Text, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ChefStrip(openChef: () -> Unit) {
    val partner = LocalNeon.current.partner
    NeonPanel(onClick = openChef, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(partner.copy(alpha = 0.15f)).border(1.dp, partner, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = partner) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("AI CHEF", style = MaterialTheme.typography.titleLarge, color = partner)
                Text("“What can I cook with what I have?”", style = MaterialTheme.typography.bodySmall, color = Neon.Muted)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun RecipeRow(list: List<Recipe>, openRecipe: (Long) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(list, key = { it.id }) { r ->
            RecipeCard(r, onClick = { openRecipe(r.id) }, modifier = Modifier.width(168.dp))
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
