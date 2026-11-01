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
        val existing = db.shopping().unchecked()
        val toInsert = mutableListOf<ShoppingItemEntity>()
        for (ing in recipe.ingredients) {
            if (ing.name.isBlank()) continue
            val qty = ing.quantity?.times(factor)
            val match = existing.firstOrNull {
                it.name.equals(ing.name.trim(), ignoreCase = true) && it.unit.equals(ing.unit.trim(), ignoreCase = true)
            }
            if (match != null) {
                val merged = when {
                    match.quantity == null -> qty
                    qty == null -> match.quantity
                    else -> match.quantity + qty
                }
                val titles = listOfNotNull(match.recipeTitle, recipe.title).distinct().joinToString(", ")
                db.shopping().update(match.copy(quantity = merged, recipeTitle = titles))
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
