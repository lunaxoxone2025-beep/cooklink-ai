package com.cooklink.ai.data

import com.cooklink.ai.data.db.AppDatabase
import com.cooklink.ai.data.db.ShoppingItemEntity
import com.cooklink.ai.model.Recipe
import kotlinx.coroutines.flow.Flow

class ShoppingRepository(private val db: AppDatabase) {

    val items: Flow<List<ShoppingItemEntity>> = db.shopping().observeAll()

    suspend fun addManual(name: String, quantity: Double?, unit: String) {
        db.shopping().insert(
            ShoppingItemEntity(
                name = name.trim(),
                quantity = quantity,
                unit = unit.trim(),
                category = GroceryCategories.guess(name),
                recipeTitle = null,
            )
        )
    }

    /**
     * Adds a recipe's ingredients scaled to [servings]. Ingredients already on the
     * list (same name + unit, not yet ticked) are merged by adding quantities.
     */
    suspend fun addRecipe(recipe: Recipe, servings: Int) {
        val factor = if (recipe.servings > 0) servings.toDouble() / recipe.servings else 1.0
        val existing = db.shopping().unchecked().toMutableList()
        val toInsert = mutableListOf<ShoppingItemEntity>()
        for (ing in recipe.ingredients) {
            if (ing.name.isBlank()) continue
            val qty = ing.quantity?.times(factor)
            // Same ingredient in a compatible unit (g/kg, ml/l, tsp/tbsp…) is merged into one line.
            val match = (existing + toInsert).firstOrNull {
                it.name.equals(ing.name.trim(), ignoreCase = true) && Units.compatible(it.unit, ing.unit)
            }
            if (match != null) {
                val (mergedQty, mergedUnit) = Units.add(match.quantity, match.unit, qty, ing.unit.trim())
                val titles = listOfNotNull(match.recipeTitle, recipe.title)
                    .flatMap { it.split(", ") }.distinct().joinToString(", ")
                val updated = match.copy(quantity = mergedQty, unit = mergedUnit, recipeTitle = titles)
                if (match.id == 0L) {
                    toInsert[toInsert.indexOf(match)] = updated
                } else {
                    db.shopping().update(updated)
                    existing[existing.indexOf(match)] = updated
                }
            } else {
                toInsert += ShoppingItemEntity(
                    name = ing.name.trim(),
                    quantity = qty,
                    unit = ing.unit.trim(),
                    category = ing.category.ifBlank { GroceryCategories.guess(ing.name) },
                    recipeTitle = recipe.title,
                )
            }
        }
        if (toInsert.isNotEmpty()) db.shopping().insertAll(toInsert)
    }

    suspend fun toggle(item: ShoppingItemEntity) = db.shopping().update(item.copy(checked = !item.checked))
    suspend fun delete(id: Long) = db.shopping().delete(id)
    suspend fun clearChecked() = db.shopping().clearChecked()
    suspend fun clearAll() = db.shopping().clearAll()
}

/** Converts between related units so amounts from different recipes can be added up. */
object Units {
    private val toBase = mapOf(
        "g" to ("g" to 1.0), "kg" to ("g" to 1000.0), "mg" to ("g" to 0.001),
        "ml" to ("ml" to 1.0), "l" to ("ml" to 1000.0), "dl" to ("ml" to 100.0), "cl" to ("ml" to 10.0),
        "tsp" to ("tsp" to 1.0), "tbsp" to ("tsp" to 3.0), "cup" to ("tsp" to 48.0), "cups" to ("tsp" to 48.0),
        "oz" to ("oz" to 1.0), "lb" to ("oz" to 16.0), "lbs" to ("oz" to 16.0),
    )

    private fun norm(u: String) = u.trim().lowercase().trimEnd('.')

    fun compatible(a: String, b: String): Boolean {
        val x = norm(a)
        val y = norm(b)
        if (x == y) return true
        val bx = toBase[x]?.first
        return bx != null && bx == toBase[y]?.first
    }

    /** Adds two amounts; the result uses the larger unit when it reads better (1200 g → 1.2 kg). */
    fun add(q1: Double?, u1: String, q2: Double?, u2: String): Pair<Double?, String> {
        if (q1 == null) return q2 to (if (q2 != null) u2 else u1)
        if (q2 == null) return q1 to u1
        val a = norm(u1)
        val b = norm(u2)
        if (a == b) return (q1 + q2) to u1
        val (base, f1) = toBase[a] ?: return (q1 + q2) to u1
        val f2 = toBase[b]?.second ?: return (q1 + q2) to u1
        val total = q1 * f1 + q2 * f2
        return when {
            base == "g" && total >= 1000 -> total / 1000 to "kg"
            base == "ml" && total >= 1000 -> total / 1000 to "l"
            base == "g" -> total to "g"
            base == "ml" -> total to "ml"
            base == "tsp" && total >= 3 -> total / 3 to "tbsp"
            else -> total to base
        }
    }
}

/** Simple keyword-based grocery aisle guess; the AI will supply categories in later phases. */
object GroceryCategories {
    const val OTHER = "Other"
    private val rules: List<Pair<String, List<String>>> = listOf(
        "Produce" to listOf("onion", "garlic", "tomato", "potato", "carrot", "pepper", "lettuce", "spinach", "lemon", "lime", "apple", "banana", "herb", "parsley", "cilantro", "basil", "ginger", "mushroom", "zucchini", "cucumber", "avocado", "celery", "broccoli", "cabbage", "chili", "berry", "orange", "leek", "scallion"),
        "Meat & Fish" to listOf("chicken", "beef", "pork", "lamb", "bacon", "sausage", "ham", "turkey", "fish", "salmon", "tuna", "shrimp", "prawn", "mince", "steak", "cod"),
        "Dairy & Eggs" to listOf("milk", "butter", "cheese", "cream", "yogurt", "yoghurt", "egg", "mozzarella", "parmesan", "ricotta"),
        "Bakery" to listOf("bread", "bun", "tortilla", "pita", "baguette", "roll"),
        "Pantry" to listOf("flour", "sugar", "rice", "pasta", "noodle", "oil", "vinegar", "bean", "lentil", "stock", "broth", "can", "sauce", "honey", "oat", "yeast", "baking", "cocoa", "chocolate", "nut", "almond"),
        "Spices" to listOf("salt", "pepper", "paprika", "cumin", "cinnamon", "oregano", "thyme", "rosemary", "curry", "turmeric", "nutmeg", "spice", "chili flakes"),
        "Frozen" to listOf("frozen", "ice"),
        "Drinks" to listOf("wine", "beer", "juice", "water", "soda", "coffee", "tea"),
    )

    fun guess(name: String): String {
        val n = name.lowercase()
        // Spices first for words like "black pepper" vs bell pepper handled crudely.
        if (n.contains("black pepper") || n.contains("ground ")) return "Spices"
        return rules.firstOrNull { (_, words) -> words.any { n.contains(it) } }?.first ?: OTHER
    }

    val all = rules.map { it.first } + OTHER
}
