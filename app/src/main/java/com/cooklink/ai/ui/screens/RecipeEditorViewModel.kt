package com.cooklink.ai.ui.screens

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooklink.ai.AppContainer
import com.cooklink.ai.model.Certainty
import com.cooklink.ai.model.Difficulty
import com.cooklink.ai.model.Ingredient
import com.cooklink.ai.model.Nutrition
import com.cooklink.ai.model.Origin
import com.cooklink.ai.model.Recipe
import com.cooklink.ai.model.SourceLink
import com.cooklink.ai.model.Step
import com.cooklink.ai.model.formatQuantity
import kotlinx.coroutines.launch

data class IngredientRow(
    val key: Long,
    val quantity: String = "",
    val unit: String = "",
    val name: String = "",
    val note: String = "",
    val category: String = "",
    val certainty: Certainty = Certainty.CONFIRMED,
)

data class StepRow(
    val key: Long,
    val text: String = "",
    val minutes: String = "",
    val temperature: String = "",
    val certainty: Certainty = Certainty.CONFIRMED,
)

data class SourceRow(val key: Long, val label: String = "", val url: String = "")

class RecipeEditorViewModel(private val container: AppContainer, private val recipeId: Long) : ViewModel() {

    private var keyCounter = 0L
    private fun nextKey() = ++keyCounter

    var loading by mutableStateOf(recipeId != 0L)
        private set
    var saving by mutableStateOf(false)
        private set
    private var original: Recipe? = null

    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var coverUri by mutableStateOf<String?>(null)
        private set
    var prep by mutableStateOf("")
    var cook by mutableStateOf("")
    var difficulty by mutableStateOf<Difficulty?>(null)
    var servings by mutableStateOf("2")
    var equipment by mutableStateOf("")
    var tags by mutableStateOf("")
    var notes by mutableStateOf("")
    var calories by mutableStateOf("")
    var protein by mutableStateOf("")
    var carbs by mutableStateOf("")
    var fat by mutableStateOf("")

    val ingredients = mutableStateListOf<IngredientRow>()
    val steps = mutableStateListOf<StepRow>()
    val sources = mutableStateListOf<SourceRow>()

    /** Covers imported during this session that must be cleaned up if the user discards. */
    private val pendingCovers = mutableListOf<String>()

    init {
        if (recipeId != 0L) {
            viewModelScope.launch {
                container.recipes.get(recipeId)?.let(::fill)
                loading = false
            }
        } else {
            ingredients += IngredientRow(nextKey())
            steps += StepRow(nextKey())
        }
    }

    private fun fill(r: Recipe) {
        original = r
        title = r.title
        description = r.description
        coverUri = r.coverUri
        prep = r.prepMinutes?.toString() ?: ""
        cook = r.cookMinutes?.toString() ?: ""
        difficulty = r.difficulty
        servings = r.servings.toString()
        equipment = r.equipment.joinToString(", ")
        tags = r.tags.joinToString(", ")
        notes = r.notes
        calories = r.nutrition.calories?.toString() ?: ""
        protein = r.nutrition.proteinG?.let { formatQuantity(it) } ?: ""
        carbs = r.nutrition.carbsG?.let { formatQuantity(it) } ?: ""
        fat = r.nutrition.fatG?.let { formatQuantity(it) } ?: ""
        ingredients.clear()
        r.ingredients.forEach {
            ingredients += IngredientRow(nextKey(), it.quantity?.let(::plainNumber) ?: "", it.unit, it.name, it.note, it.category, it.certainty)
        }
        if (ingredients.isEmpty()) ingredients += IngredientRow(nextKey())
        steps.clear()
        r.steps.forEach { steps += StepRow(nextKey(), it.text, it.durationMinutes?.toString() ?: "", it.temperature, it.certainty) }
        if (steps.isEmpty()) steps += StepRow(nextKey())
        sources.clear()
        r.sources.forEach { sources += SourceRow(nextKey(), it.label, it.url) }
    }

    // Editing a value the AI estimated means the user has now confirmed it.
    fun updateIngredient(index: Int, row: IngredientRow) { ingredients[index] = row.copy(certainty = Certainty.CONFIRMED) }
    fun addIngredient() { ingredients += IngredientRow(nextKey()) }
    fun removeIngredient(index: Int) { ingredients.removeAt(index) }

    fun updateStep(index: Int, row: StepRow) { steps[index] = row.copy(certainty = Certainty.CONFIRMED) }
    fun addStep() { steps += StepRow(nextKey()) }
    fun removeStep(index: Int) { steps.removeAt(index) }
    fun moveStep(from: Int, to: Int) {
        if (to !in steps.indices) return
        val item = steps.removeAt(from)
        steps.add(to, item)
    }

    fun updateSource(index: Int, row: SourceRow) { sources[index] = row }
    fun addSource() { sources += SourceRow(nextKey()) }
    fun removeSource(index: Int) { sources.removeAt(index) }

    fun setCover(uri: Uri) {
        viewModelScope.launch {
            container.images.importCover(uri)?.let {
                pendingCovers += it
                coverUri = it
            }
        }
    }

    fun removeCover() { coverUri = null }

    val canSave get() = title.isNotBlank() && !saving

    fun save(onSaved: (Long) -> Unit) {
        if (!canSave) return
        saving = true
        viewModelScope.launch {
            val base = original
            val recipe = Recipe(
                id = base?.id ?: 0L,
                title = title.trim(),
                description = description.trim(),
                coverUri = coverUri,
                prepMinutes = prep.toIntOrNull(),
                cookMinutes = cook.toIntOrNull(),
                difficulty = difficulty,
                servings = servings.toIntOrNull()?.coerceIn(1, 99) ?: 1,
                ingredients = ingredients.filter { it.name.isNotBlank() }.map {
                    Ingredient(it.name.trim(), parseNumber(it.quantity), it.unit.trim(), it.note.trim(), it.category, it.certainty)
                },
                equipment = splitList(equipment),
                steps = steps.filter { it.text.isNotBlank() }.map {
                    Step(it.text.trim(), it.minutes.toIntOrNull(), it.temperature.trim(), it.certainty)
                },
                nutrition = Nutrition(calories.toIntOrNull(), parseNumber(protein), parseNumber(carbs), parseNumber(fat)),
                sources = sources.filter { it.url.isNotBlank() }.map { SourceLink(it.label.trim(), it.url.trim()) },
                notes = notes.trim(),
                tags = splitList(tags),
                origin = base?.origin ?: Origin.MANUAL,
                isFavorite = base?.isFavorite ?: false,
                createdAt = base?.createdAt ?: System.currentTimeMillis(),
                lastOpenedAt = base?.lastOpenedAt,
            )
            val id = container.recipes.save(recipe)
            // Remove covers that were picked then replaced, and the old cover if it changed.
            pendingCovers.filter { it != coverUri }.forEach { container.images.deleteCover(it) }
            if (base?.coverUri != null && base.coverUri != coverUri) container.images.deleteCover(base.coverUri)
            pendingCovers.clear()
            saving = false
            onSaved(id)
        }
    }

    fun discard() {
        viewModelScope.launch {
            pendingCovers.forEach { container.images.deleteCover(it) }
            pendingCovers.clear()
        }
    }

    companion object {
        /** Accepts "1.5", "1,5", "1/2", "1 1/2" and "½". */
        fun parseNumber(s: String): Double? {
            val t = s.trim().replace(',', '.')
                .replace("½", " 1/2").replace("¼", " 1/4").replace("¾", " 3/4").replace("⅓", " 1/3").replace("⅔", " 2/3")
                .trim()
            if (t.isEmpty()) return null
            return runCatching {
                t.split(Regex("\\s+")).sumOf { part ->
                    if ('/' in part) {
                        val (a, b) = part.split('/')
                        a.toDouble() / b.toDouble()
                    } else part.toDouble()
                }
            }.getOrNull()?.takeIf { it.isFinite() && it >= 0 }
        }

        private fun plainNumber(d: Double): String =
            if (d % 1.0 == 0.0) d.toLong().toString() else "%.2f".format(java.util.Locale.US, d).trimEnd('0').trimEnd('.')

        private fun splitList(s: String) = s.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }
}
