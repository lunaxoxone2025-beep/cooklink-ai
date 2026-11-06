package com.cooklink.ai.data.ai

import com.cooklink.ai.data.settings.AiProvider
import com.cooklink.ai.data.settings.SecureKeyStore
import com.cooklink.ai.data.settings.SettingsRepository
import com.cooklink.ai.model.Certainty
import com.cooklink.ai.model.Ingredient
import com.cooklink.ai.model.Recipe
import com.cooklink.ai.model.Step
import com.cooklink.ai.model.formatQuantity
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import kotlin.math.roundToInt

/** Picks the AI client from the user's settings and saved keys. */
class AiProviders(private val http: OkHttpClient, private val keys: SecureKeyStore, private val settings: SettingsRepository) {
    fun gemini(): GeminiClient? = keys.get(AiProvider.GEMINI)?.let { GeminiClient(http, it) }
    fun claude(): ClaudeClient? = keys.get(AiProvider.CLAUDE)?.let { ClaudeClient(http, it) }

    suspend fun preferred(): AiClient {
        val first = if (settings.provider.first() == AiProvider.GEMINI) gemini() else claude()
        return first ?: gemini() ?: claude() ?: throw AiException("Add a Gemini or Claude API key in Profile first.")
    }

    /** Instruction appended to prompts so answers follow the language chosen in Profile. */
    suspend fun languageRule(): String =
        settings.recipeLanguage.first().promptName?.let { "Write in $it." } ?: "Write in the same language as the recipe."
}

@Serializable
private data class StepReply(val text: String = "", val durationMinutes: Double? = null, val temperature: String? = null)

@Serializable
private data class SwapReply(
    val ingredient: AiIngredient = AiIngredient(),
    val steps: List<StepEdit> = emptyList(),
    val explanation: String = "",
)

@Serializable
private data class StepEdit(val index: Int = -1, val text: String = "")

/** Result of swapping one ingredient: the new ingredient plus any step texts that mention it. */
data class Substitution(val ingredient: Ingredient, val stepChanges: Map<Int, String>, val explanation: String)

/** Small AI edits inside the recipe editor, plus the chef chat. */
class RecipeAssistant(private val providers: AiProviders) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /** Rewrites step [index], optionally following [wish] ("make it clearer", "use the oven"). */
    suspend fun regenerateStep(recipe: Recipe, index: Int, wish: String): Step {
        val client = providers.preferred()
        val system = """
You improve ONE step of a recipe. Keep it a single clear action, consistent with the other steps and ingredients.
Do not invent quantities, times or temperatures that are not supported by the recipe; leave them null/empty if unknown.
${providers.languageRule()}
Return JSON only: {"text": "string", "durationMinutes": number|null, "temperature": "string"}
""".trimIndent()
        val user = buildString {
            appendLine(describe(recipe))
            appendLine()
            appendLine("Rewrite step ${index + 1}: \"${recipe.steps.getOrNull(index)?.text.orEmpty()}\"")
            if (wish.isNotBlank()) appendLine("The cook asks: $wish")
        }
        val reply = parse<StepReply>(client.generate(system, user))
        if (reply.text.isBlank()) throw AiException("The AI didn't return a new step. Try again.")
        return Step(
            text = reply.text.trim(),
            durationMinutes = reply.durationMinutes?.roundToInt()?.takeIf { it > 0 },
            temperature = reply.temperature.orEmpty().trim(),
            certainty = Certainty.ESTIMATED,
        )
    }

    /** Replaces ingredient [index] with something matching [wish] ("olive oil", "vegetarian", "cheaper"). */
    suspend fun substitute(recipe: Recipe, index: Int, wish: String): Substitution {
        val client = providers.preferred()
        val target = recipe.ingredients.getOrNull(index) ?: throw AiException("Pick an ingredient first.")
        val system = """
You replace ONE ingredient in a recipe with a sensible substitute, adjusting the amount for an equivalent result.
Also rewrite any steps whose text names the old ingredient or must change technique because of the swap (use the step's 0-based index).
Be honest: if the swap changes taste or texture, say so briefly in "explanation".
${providers.languageRule()}
Return JSON only:
{"ingredient": {"name": "string", "quantity": number|null, "unit": "string", "note": "string", "category": "string"},
 "steps": [{"index": number, "text": "string"}],
 "explanation": "one or two sentences"}
""".trimIndent()
        val user = buildString {
            appendLine(describe(recipe))
            appendLine()
            appendLine("Replace ingredient: ${line(target)}")
            appendLine(if (wish.isBlank()) "Suggest the most common substitute." else "The cook wants: $wish")
        }
        val reply = parse<SwapReply>(client.generate(system, user))
        if (reply.ingredient.name.isBlank()) throw AiException("The AI didn't suggest a substitute. Try again.")
        val ing = Ingredient(
            name = reply.ingredient.name.trim(),
            quantity = reply.ingredient.quantity?.takeIf { it > 0 },
            unit = reply.ingredient.unit.orEmpty().trim(),
            note = reply.ingredient.note.orEmpty().trim().ifBlank { "instead of ${target.name}" },
            category = reply.ingredient.category.orEmpty(),
            certainty = Certainty.ESTIMATED,
        )
        val changes = reply.steps.filter { it.index in recipe.steps.indices && it.text.isNotBlank() }.associate { it.index to it.text.trim() }
        return Substitution(ing, changes, reply.explanation.trim())
    }

    /** Free-form chat with the AI chef; [recipe] gives context when the chat was opened from one. */
    suspend fun chat(recipe: Recipe?, library: List<String>, turns: List<ChatTurn>): String {
        val client = providers.preferred()
        val system = buildString {
            appendLine("You are a friendly, practical personal chef inside the CookLink AI app.")
            appendLine("Give concrete, safe cooking advice: exact amounts, times and temperatures when you suggest them, and food-safety temperatures when relevant (e.g. chicken 74 °C / 165 °F inside).")
            appendLine("Keep answers short and easy to read on a phone. Use simple lists for ingredients or steps.")
            appendLine("If you suggest a full new or adapted recipe, list ingredients with amounts and numbered steps so it can be saved.")
            appendLine("Answer in the language the user writes in.")
            if (recipe != null) {
                appendLine()
                appendLine("The user is looking at this recipe:")
                appendLine(describe(recipe))
            }
            if (library.isNotEmpty()) {
                appendLine()
                appendLine("Recipes saved in the user's library: ${library.take(100).joinToString("; ")}")
            }
        }
        return client.chat(system, turns.takeLast(20)).trim()
    }

    private fun describe(r: Recipe) = buildString {
        appendLine("Recipe: ${r.title} (serves ${r.servings})")
        if (r.description.isNotBlank()) appendLine(r.description)
        appendLine("Ingredients:")
        r.ingredients.forEach { appendLine("- ${line(it)}") }
        appendLine("Steps:")
        r.steps.forEachIndexed { i, s ->
            val extra = listOfNotNull(s.durationMinutes?.let { "$it min" }, s.temperature.takeIf { it.isNotBlank() }).joinToString(", ")
            appendLine("${i}. ${s.text}${if (extra.isNotEmpty()) " ($extra)" else ""}")
        }
        if (r.equipment.isNotEmpty()) appendLine("Equipment: ${r.equipment.joinToString()}")
    }

    private fun line(i: Ingredient) =
        listOf(formatQuantity(i.quantity), i.unit, i.name).filter { it.isNotBlank() }.joinToString(" ") +
            (if (i.note.isNotBlank()) " (${i.note})" else "")

    private inline fun <reified T> parse(reply: String): T {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException("The AI answer couldn't be read. Try again.")
        return runCatching { json.decodeFromString<T>(reply.substring(start, end + 1)) }
            .getOrElse { throw AiException("The AI answer couldn't be read. Try again.") }
    }
}
