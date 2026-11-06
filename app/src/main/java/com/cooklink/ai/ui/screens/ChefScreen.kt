@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cooklink.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooklink.ai.AppContainer
import com.cooklink.ai.data.ai.AiException
import com.cooklink.ai.data.ai.ChatTurn
import com.cooklink.ai.data.importer.ImportState
import com.cooklink.ai.model.Recipe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChefViewModel(private val container: AppContainer, private val recipeId: Long) : ViewModel() {
    val turns = mutableStateListOf<ChatTurn>()
    var thinking by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var recipe by mutableStateOf<Recipe?>(null)
        private set

    init {
        if (recipeId > 0) viewModelScope.launch { recipe = container.recipes.get(recipeId) }
    }

    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty() || thinking) return
        turns += ChatTurn(fromUser = true, text = q)
        thinking = true
        error = null
        viewModelScope.launch {
            try {
                val library = container.recipes.recipes.first().map { it.title }
                val answer = container.assistant.chat(recipe, library, turns.toList())
                turns += ChatTurn(fromUser = false, text = answer)
            } catch (e: AiException) {
                error = e.message
                turns.removeAt(turns.lastIndex)
            } catch (e: Exception) {
                error = "The chef couldn't answer: ${e.message ?: e.javaClass.simpleName}"
                turns.removeAt(turns.lastIndex)
            } finally {
                thinking = false
            }
        }
    }

    fun clear() {
        turns.clear()
        error = null
    }
}

/** Chat with the AI chef, optionally about one recipe. Any answer can be saved as a new recipe. */
@Composable
fun ChefScreen(container: AppContainer, recipeId: Long, onBack: () -> Unit) {
    val vm: ChefViewModel = viewModel(
        key = "chef-$recipeId",
        factory = viewModelFactory { initializer { ChefViewModel(container, recipeId) } },
    )
    var input by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    val importState by container.imports.state.collectAsStateWithLifecycle()
    val saving = importState is ImportState.Running

    LaunchedEffect(vm.turns.size, vm.thinking) {
        val count = vm.turns.size + (if (vm.thinking) 1 else 0)
        if (count > 0) list.animateScrollToItem(count)
    }

    val suggestions = if (recipeId > 0) listOf(
        "Make this recipe cheaper",
        "Adapt it for 4 people",
        "Give me a vegetarian version",
        "Can I replace the butter with olive oil?",
        "How do I know when it's done?",
    ) else listOf(
        "What can I cook with eggs, rice and spinach?",
        "How do I know when chicken is cooked?",
        "Explain how to make a roux",
        "A quick dinner in 20 minutes",
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("AI chef")
                        vm.recipe?.let {
                            Text(it.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = vm::clear, enabled = vm.turns.isNotEmpty() && !vm.thinking) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "New conversation")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        if (vm.recipe != null) "Ask anything about this recipe: swaps, portions, techniques, timing."
                        else "Ask about ingredients you have, techniques, substitutions or doneness.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(vm.turns) { t ->
                    Bubble(t, saving = saving, onSave = {
                        val context = vm.recipe?.let { "Based on the recipe \"${it.title}\".\n\n" }.orEmpty()
                        container.imports.importText(context + t.text)
                    })
                }
                if (vm.thinking) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text("The chef is thinking…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                vm.error?.let { e ->
                    item { Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                }
            }

            if (vm.turns.isEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { s -> AssistChip(onClick = { vm.send(s) }, label = { Text(s) }) }
                }
            }

            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Ask the chef…") },
                    maxLines = 4,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                FilledIconButton(
                    onClick = { vm.send(input); input = "" },
                    enabled = input.isNotBlank() && !vm.thinking,
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
            }
        }
    }
}

@Composable
private fun Bubble(t: ChatTurn, saving: Boolean, onSave: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (t.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = if (t.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                SelectionContainer {
                    Text(
                        t.text.replace("**", ""),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (t.fromUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (!t.fromUser && t.text.length > 200) {
                    TextButton(onClick = onSave, enabled = !saving, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (saving) "Saving…" else "Save as recipe")
                    }
                }
            }
        }
    }
}
