package com.cooklink.ai.data.ai

import com.cooklink.ai.data.GroceryCategories
import com.cooklink.ai.model.Certainty
import com.cooklink.ai.model.Difficulty
import com.cooklink.ai.model.Ingredient
import com.cooklink.ai.model.Nutrition
import com.cooklink.ai.model.Origin
import com.cooklink.ai.model.Recipe
import com.cooklink.ai.model.SourceLink
import com.cooklink.ai.model.Step
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** Shape the models are asked to return. Numbers are Double so "4" and "4.0" both parse. */
@Serializable
data class AiRecipe(
    val isRecipe: Boolean = true,
    val reason: String = "",
    val title: String = "",
    val description: String = "",
    val servings: Double? = null,
    val prepMinutes: Double? = null,
    val cookMinutes: Double? = null,
    val difficulty: String? = null,
    val ingredients: List<AiIngredient> = emptyList(),
    val equipment: List<String> = emptyList(),
    val steps: List<AiStep> = emptyList(),
    val nutrition: AiNutrition? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class AiIngredient(
    val name: String = "",
    val quantity: Double? = null,
    val unit: String? = null,
    val note: String? = null,
    val category: String? = null,
    val certainty: String? = null,
)

@Serializable
data class AiStep(
    val text: String = "",
    val durationMinutes: Double? = null,
    val temperature: String? = null,
    val certainty: String? = null,
)

@Serializable
data class AiNutrition(
    val calories: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
)

object RecipeExtraction {

    private val lenient = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    fun systemPrompt(language: String?): String = """
You turn cooking source material (a video, web page data, a social-media caption, or notes typed by a person) into ONE structured recipe, returned as a single JSON object and nothing else.

Honesty rules — these matter more than completeness:
- Use only what the sources contain. Never say something was stated, shown or spoken when it was not.
- Every ingredient and step has a "certainty":
  "confirmed" = explicitly written, spoken or clearly shown in the source.
  "estimated" = you inferred it (an amount judged from what is visible, a quantity implied by context, a step that obviously happens but is not shown).
  "missing"   = the source gives no basis. For a missing quantity set "quantity": null.
- If the ingredient is named but its amount is not given, only fill "quantity" when you can reasonably judge it from what you see or read, and mark it "estimated". Otherwise leave it null and mark it "missing".
- Times and temperatures follow the same rule: only put them in when stated, or clearly visible.
- Nutrition is always an estimate per serving; use null when you cannot estimate sensibly.
- If there is no recipe in the material, return {"isRecipe": false, "reason": "<short explanation>"}.

Formatting:
- Units: g, kg, ml, l, tsp, tbsp, cup, pinch, or the source's own unit. Countable items use an empty unit (2 eggs → quantity 2, unit "").
- "category" is one of: Produce, Meat & Fish, Dairy & Eggs, Bakery, Pantry, Spices, Frozen, Drinks, Other.
- Steps: one clear action each, in cooking order, with "durationMinutes" and "temperature" (e.g. "180 °C") when known.
- "servings": stated number, or your best estimate if not stated (needed to scale the recipe).
- "difficulty": "easy", "medium" or "hard".
- ${if (language == null) "Write all text in the same language as the source." else "Write all text in $language, translating if needed."}

JSON shape:
{
  "isRecipe": true,
  "title": "string",
  "description": "one or two sentences",
  "servings": number,
  "prepMinutes": number|null,
  "cookMinutes": number|null,
  "difficulty": "easy"|"medium"|"hard"|null,
  "ingredients": [{"name": "string", "quantity": number|null, "unit": "string", "note": "string", "category": "string", "certainty": "confirmed"|"estimated"|"missing"}],
  "equipment": ["string"],
  "steps": [{"text": "string", "durationMinutes": number|null, "temperature": "string", "certainty": "confirmed"|"estimated"|"missing"}],
  "nutrition": {"calories": number|null, "proteinG": number|null, "carbsG": number|null, "fatG": number|null},
  "tags": ["string"]
}
""".trimIndent()

    /** Parses the model reply, tolerating code fences or stray text around the JSON. */
    fun parse(reply: String): AiRecipe {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException("The AI answer didn't contain a recipe. Try again.")
        return runCatching { lenient.decodeFromString(AiRecipe.serializer(), reply.substring(start, end + 1)) }
            .getOrElse { throw AiException("The AI answer couldn't be read as a recipe. Try again.") }
    }

    fun toRecipe(ai: AiRecipe, origin: Origin, sources: List<SourceLink>, coverUri: String?): Recipe {
        if (!ai.isRecipe) {
            throw AiException(ai.reason.ifBlank { "No recipe was found in this content." })
        }
        val ingredients = ai.ingredients.filter { it.name.isNotBlank() }.map { i ->
            val certainty = certaintyOf(i.certainty)
            Ingredient(
                name = i.name.trim(),
                // A "missing" amount must not carry a number, whatever the model sent.
                quantity = if (certainty == Certainty.MISSING) null else i.quantity?.takeIf { it > 0 },
                unit = i.unit.orEmpty().trim(),
                note = i.note.orEmpty().trim(),
                category = i.category?.takeIf { it in GroceryCategories.all } ?: GroceryCategories.guess(i.name),
                certainty = certainty,
            )
        }
        val steps = ai.steps.filter { it.text.isNotBlank() }.map { s ->
            Step(
                text = s.text.trim(),
                durationMinutes = s.durationMinutes?.roundToInt()?.takeIf { it > 0 },
                temperature = s.temperature.orEmpty().trim(),
                certainty = certaintyOf(s.certainty),
            )
        }
        if (ingredients.isEmpty() && steps.isEmpty()) throw AiException("The AI couldn't find ingredients or steps in this content.")
        return Recipe(
            title = ai.title.trim().ifBlank { "Untitled recipe" },
            description = ai.description.trim(),
            coverUri = coverUri,
            prepMinutes = ai.prepMinutes?.roundToInt()?.takeIf { it > 0 },
            cookMinutes = ai.cookMinutes?.roundToInt()?.takeIf { it > 0 },
            difficulty = when (ai.difficulty?.lowercase()) {
                "easy" -> Difficulty.EASY
                "medium" -> Difficulty.MEDIUM
                "hard" -> Difficulty.HARD
                else -> null
            },
            servings = ai.servings?.roundToInt()?.coerceIn(1, 99) ?: 2,
            ingredients = ingredients,
            equipment = ai.equipment.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            steps = steps,
            nutrition = ai.nutrition?.let {
                Nutrition(it.calories?.roundToInt(), it.proteinG, it.carbsG, it.fatG)
            } ?: Nutrition(),
            sources = sources,
            tags = ai.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(8),
            origin = origin,
        )
    }

    private fun certaintyOf(s: String?) = when (s?.lowercase()?.trim()) {
        "confirmed" -> Certainty.CONFIRMED
        "missing" -> Certainty.MISSING
        // Anything unclear is treated as an estimate rather than silently confirmed.
        else -> Certainty.ESTIMATED
    }
}
