@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cooklink.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooklink.ai.AppContainer
import com.cooklink.ai.model.Ingredient
import com.cooklink.ai.model.Step
import com.cooklink.ai.model.formatMinutes
import com.cooklink.ai.model.formatQuantity
import com.cooklink.ai.ui.theme.LocalCertaintyColors
import kotlinx.coroutines.launch

private enum class Change { SAME, ADDED, REMOVED }

/** Shows the AI's original import next to the user's current version, line by line. */
@Composable
fun CompareScreen(container: AppContainer, recipeId: Long, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val recipe by remember(recipeId) { container.recipes.recipe(recipeId) }.collectAsStateWithLifecycle(initialValue = null)
    var confirmRestore by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Compare with original") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        val r = recipe
        val original = r?.original
        if (r == null || original == null) {
            Text(
                "This recipe has no AI original to compare with.",
                modifier = Modifier.padding(padding).padding(20.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            return@Scaffold
        }
        val ingDiff = diff(original.ingredients.map(::ingredientLine), r.ingredients.map(::ingredientLine))
        val stepDiff = diff(original.steps.map(::stepLine), r.steps.map(::stepLine))
        val changes = ingDiff.count { it.second != Change.SAME } + stepDiff.count { it.second != Change.SAME }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
        ) {
            item {
                Text(r.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (changes == 0) "No changes: your version matches what the AI imported."
                    else "$changes line(s) differ. Green = in your version only, crossed out = only in the AI original.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    Meta("Servings", "${original.servings} → ${r.servings}", Modifier.weight(1f))
                    Meta("Prep", "${formatMinutes(original.prepMinutes)} → ${formatMinutes(r.prepMinutes)}", Modifier.weight(1f))
                    Meta("Cook", "${formatMinutes(original.cookMinutes)} → ${formatMinutes(r.cookMinutes)}", Modifier.weight(1f))
                }
                if (original.title != r.title) {
                    Spacer(Modifier.height(8.dp))
                    Text("AI title: ${original.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Section("Ingredients")
            }
            items(ingDiff) { (text, change) -> DiffLine(text, change) }
            item { Section("Steps") }
            items(stepDiff) { (text, change) -> DiffLine(text, change) }
            if (changes > 0) {
                item {
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Restore the AI original")
                    }
                }
            }
        }

        if (confirmRestore) {
            AlertDialog(
                onDismissRequest = { confirmRestore = false },
                title = { Text("Restore original?") },
                text = { Text("Ingredients, steps, servings and times go back to the AI version. Your notes, photo and favourites stay.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmRestore = false
                        scope.launch {
                            container.recipes.save(
                                r.copy(
                                    title = original.title,
                                    description = original.description,
                                    servings = original.servings,
                                    prepMinutes = original.prepMinutes,
                                    cookMinutes = original.cookMinutes,
                                    ingredients = original.ingredients,
                                    steps = original.steps,
                                    equipment = original.equipment,
                                )
                            )
                            onBack()
                        }
                    }) { Text("Restore") }
                },
                dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
}

@Composable
private fun Meta(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DiffLine(text: String, change: Change) {
    val green = LocalCertaintyColors.current.confirmed
    val (bg, fg, mark) = when (change) {
        Change.SAME -> Triple(Color.Transparent, MaterialTheme.colorScheme.onSurface, " ")
        Change.ADDED -> Triple(green.copy(alpha = 0.14f), MaterialTheme.colorScheme.onSurface, "+")
        Change.REMOVED -> Triple(MaterialTheme.colorScheme.error.copy(alpha = 0.10f), MaterialTheme.colorScheme.onSurfaceVariant, "−")
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(MaterialTheme.shapes.extraSmall).background(bg).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(mark, style = MaterialTheme.typography.titleSmall, color = if (change == Change.ADDED) green else fg)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            textDecoration = if (change == Change.REMOVED) TextDecoration.LineThrough else null,
        )
    }
}

private fun ingredientLine(i: Ingredient) =
    listOf(formatQuantity(i.quantity), i.unit, i.name).filter { it.isNotBlank() }.joinToString(" ") +
        (if (i.note.isNotBlank()) " (${i.note})" else "")

private fun stepLine(s: Step) = s.text +
    listOfNotNull(s.durationMinutes?.let { formatMinutes(it) }, s.temperature.takeIf { it.isNotBlank() })
        .joinToString(", ").let { if (it.isEmpty()) "" else " · $it" }

/** Order-preserving line diff (LCS), good enough for short recipe lists. */
private fun diff(old: List<String>, new: List<String>): List<Pair<String, Change>> {
    val n = old.size
    val m = new.size
    val lcs = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
        lcs[i][j] = if (old[i] == new[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
    }
    val out = mutableListOf<Pair<String, Change>>()
    var i = 0
    var j = 0
    while (i < n && j < m) {
        when {
            old[i] == new[j] -> { out += old[i] to Change.SAME; i++; j++ }
            lcs[i + 1][j] >= lcs[i][j + 1] -> { out += old[i] to Change.REMOVED; i++ }
            else -> { out += new[j] to Change.ADDED; j++ }
        }
    }
    while (i < n) out += old[i++] to Change.REMOVED
    while (j < m) out += new[j++] to Change.ADDED
    return out
}
