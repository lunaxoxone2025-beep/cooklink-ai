package com.cooklink.ai.model

import kotlinx.serialization.Serializable

/**
 * How trustworthy a piece of recipe data is.
 * CONFIRMED: shown/said explicitly in the source (or typed by the user).
 * ESTIMATED: inferred by the AI; must be displayed as an estimate.
 * MISSING: the source did not contain it; the field is left empty on purpose.
 */
@Serializable
enum class Certainty { CONFIRMED, ESTIMATED, MISSING }

@Serializable
data class Ingredient(
    val name: String,
    val quantity: Double? = null,
    val unit: String = "",
    val note: String = "",
    val category: String = "",
    val certainty: Certainty = Certainty.CONFIRMED,
)

@Serializable
data class Step(
    val text: String,
    val durationMinutes: Int? = null,
    val temperature: String = "",
    val certainty: Certainty = Certainty.CONFIRMED,
)

@Serializable
data class Nutrition(
    val calories: Int? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
) {
    val isEmpty get() = calories == null && proteinG == null && carbsG == null && fatG == null
}

@Serializable
data class SourceLink(val label: String, val url: String)

/** Frozen copy of what the AI produced at import time, used for "compare with original". */
@Serializable
data class RecipeSnapshot(
    val title: String,
    val description: String = "",
    val servings: Int = 2,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<Step> = emptyList(),
    val equipment: List<String> = emptyList(),
)

@Serializable
enum class Difficulty(val label: String) { EASY("Easy"), MEDIUM("Medium"), HARD("Hard") }

/** How the recipe got into the library. */
@Serializable
enum class Origin(val label: String) { MANUAL("Created by you"), URL("Link"), TEXT("Text"), IMAGE("Images"), VIDEO("Video"), COMBINED("Combined") }

@Serializable
data class Recipe(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val coverUri: String? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val difficulty: Difficulty? = null,
    val servings: Int = 2,
    val ingredients: List<Ingredient> = emptyList(),
    val equipment: List<String> = emptyList(),
    val steps: List<Step> = emptyList(),
    val nutrition: Nutrition = Nutrition(),
    val sources: List<SourceLink> = emptyList(),
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val origin: Origin = Origin.MANUAL,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long? = null,
    /** The AI's version at import time; null for recipes written by hand. */
    val original: RecipeSnapshot? = null,
) {
    fun snapshot() = RecipeSnapshot(title, description, servings, prepMinutes, cookMinutes, ingredients, steps, equipment)

    val totalMinutes: Int?
        get() = if (prepMinutes == null && cookMinutes == null) null else (prepMinutes ?: 0) + (cookMinutes ?: 0)

    /** Number of fields the AI estimated rather than read from the source. */
    val estimatedCount: Int
        get() = ingredients.count { it.certainty == Certainty.ESTIMATED } + steps.count { it.certainty == Certainty.ESTIMATED }

    val missingCount: Int
        get() = ingredients.count { it.certainty == Certainty.MISSING } + steps.count { it.certainty == Certainty.MISSING }
}

fun formatMinutes(min: Int?): String = when {
    min == null -> "—"
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} h"
    else -> "${min / 60} h ${min % 60} min"
}

/** Pretty-prints a scaled quantity: 0.5 -> "½", 1.25 -> "1¼", 2.0 -> "2", 2.33 -> "2.3". */
fun formatQuantity(q: Double?): String {
    if (q == null) return ""
    val whole = q.toLong()
    val frac = q - whole
    val fracSymbol = when {
        frac < 0.06 -> ""
        kotlin.math.abs(frac - 0.25) < 0.04 -> "¼"
        kotlin.math.abs(frac - 1.0 / 3) < 0.04 -> "⅓"
        kotlin.math.abs(frac - 0.5) < 0.04 -> "½"
        kotlin.math.abs(frac - 2.0 / 3) < 0.04 -> "⅔"
        kotlin.math.abs(frac - 0.75) < 0.04 -> "¾"
        frac > 0.94 -> return (whole + 1).toString()
        else -> null
    }
    return when {
        fracSymbol == null -> if (q >= 10) "%.0f".format(q) else "%.1f".format(q).trimEnd('0').trimEnd('.', ',')
        whole == 0L && fracSymbol.isNotEmpty() -> fracSymbol
        else -> "$whole$fracSymbol"
    }
}
