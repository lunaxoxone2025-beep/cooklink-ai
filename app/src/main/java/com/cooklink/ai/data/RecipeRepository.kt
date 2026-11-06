package com.cooklink.ai.data

import com.cooklink.ai.data.db.AppDatabase
import com.cooklink.ai.data.db.CollectionEntity
import com.cooklink.ai.data.db.CollectionWithCount
import com.cooklink.ai.data.db.RecipeCollectionRef
import com.cooklink.ai.data.db.RecipeEntity
import com.cooklink.ai.model.Difficulty
import com.cooklink.ai.model.Ingredient
import com.cooklink.ai.model.Nutrition
import com.cooklink.ai.model.Origin
import com.cooklink.ai.model.Recipe
import com.cooklink.ai.model.RecipeSnapshot
import com.cooklink.ai.model.SourceLink
import com.cooklink.ai.model.Step
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RecipeRepository(private val db: AppDatabase) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val recipes: Flow<List<Recipe>> = db.recipes().observeAll().map { list -> list.map { it.toModel() } }

    fun recipe(id: Long): Flow<Recipe?> = db.recipes().observe(id).map { it?.toModel() }

    suspend fun get(id: Long): Recipe? = db.recipes().get(id)?.toModel()

    /** Inserts when id == 0, otherwise updates. Returns the recipe id. */
    suspend fun save(recipe: Recipe): Long {
        val now = System.currentTimeMillis()
        return if (recipe.id == 0L) {
            db.recipes().insert(recipe.copy(updatedAt = now).toEntity())
        } else {
            db.recipes().update(recipe.copy(updatedAt = now).toEntity())
            recipe.id
        }
    }

    suspend fun delete(id: Long) = db.recipes().delete(id)
    suspend fun setFavorite(id: Long, favorite: Boolean) = db.recipes().setFavorite(id, favorite)
    suspend fun markOpened(id: Long) = db.recipes().markOpened(id, System.currentTimeMillis())

    // Collections
    val collections: Flow<List<CollectionWithCount>> = db.collections().observeAll()
    fun recipeIdsIn(collectionId: Long): Flow<List<Long>> = db.recipes().observeRecipeIdsIn(collectionId)
    fun collectionIdsFor(recipeId: Long): Flow<List<Long>> = db.collections().observeCollectionIdsFor(recipeId)
    suspend fun createCollection(name: String): Long = db.collections().insert(CollectionEntity(name = name.trim()))
    suspend fun deleteCollection(id: Long) = db.collections().delete(id)
    suspend fun setInCollection(recipeId: Long, collectionId: Long, inside: Boolean) {
        val ref = RecipeCollectionRef(recipeId, collectionId)
        if (inside) db.collections().addRef(ref) else db.collections().removeRef(ref)
    }

    private fun Recipe.toEntity() = RecipeEntity(
        id = id,
        title = title,
        description = description,
        coverUri = coverUri,
        prepMinutes = prepMinutes,
        cookMinutes = cookMinutes,
        difficulty = difficulty?.name,
        servings = servings,
        ingredientsJson = json.encodeToString(ingredients),
        equipmentJson = json.encodeToString(equipment),
        stepsJson = json.encodeToString(steps),
        nutritionJson = json.encodeToString(nutrition),
        sourcesJson = json.encodeToString(sources),
        notes = notes,
        tagsJson = json.encodeToString(tags),
        origin = origin.name,
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastOpenedAt = lastOpenedAt,
        originalJson = original?.let { json.encodeToString(it) },
    )

    private fun RecipeEntity.toModel() = Recipe(
        id = id,
        title = title,
        description = description,
        coverUri = coverUri,
        prepMinutes = prepMinutes,
        cookMinutes = cookMinutes,
        difficulty = difficulty?.let { d -> Difficulty.entries.firstOrNull { it.name == d } },
        servings = servings,
        ingredients = decode<List<Ingredient>>(ingredientsJson) ?: emptyList(),
        equipment = decode<List<String>>(equipmentJson) ?: emptyList(),
        steps = decode<List<Step>>(stepsJson) ?: emptyList(),
        nutrition = decode<Nutrition>(nutritionJson) ?: Nutrition(),
        sources = decode<List<SourceLink>>(sourcesJson) ?: emptyList(),
        notes = notes,
        tags = decode<List<String>>(tagsJson) ?: emptyList(),
        origin = Origin.entries.firstOrNull { it.name == origin } ?: Origin.MANUAL,
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastOpenedAt = lastOpenedAt,
        original = originalJson?.let { decode<RecipeSnapshot>(it) },
    )

    private inline fun <reified T> decode(s: String): T? = runCatching { json.decodeFromString<T>(s) }.getOrNull()
}
