package com.cooklink.ai.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observe(id: Long): Flow<RecipeEntity?>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun get(id: Long): RecipeEntity?

    @Insert
    suspend fun insert(recipe: RecipeEntity): Long

    @Update
    suspend fun update(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE recipes SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE recipes SET lastOpenedAt = :time WHERE id = :id")
    suspend fun markOpened(id: Long, time: Long)

    @Query("SELECT recipeId FROM recipe_collection WHERE collectionId = :collectionId")
    fun observeRecipeIdsIn(collectionId: Long): Flow<List<Long>>
}

@Dao
interface CollectionDao {
    @Query(
        """SELECT c.id AS id, c.name AS name, COUNT(rc.recipeId) AS recipeCount
           FROM collections c LEFT JOIN recipe_collection rc ON rc.collectionId = c.id
           GROUP BY c.id ORDER BY c.name COLLATE NOCASE"""
    )
    fun observeAll(): Flow<List<CollectionWithCount>>

    @Query("SELECT collectionId FROM recipe_collection WHERE recipeId = :recipeId")
    fun observeCollectionIdsFor(recipeId: Long): Flow<List<Long>>

    @Insert
    suspend fun insert(collection: CollectionEntity): Long

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addRef(ref: RecipeCollectionRef)

    @Delete
    suspend fun removeRef(ref: RecipeCollectionRef)
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY checked ASC, category COLLATE NOCASE, name COLLATE NOCASE")
    fun observeAll(): Flow<List<ShoppingItemEntity>>

    @Insert
    suspend fun insert(item: ShoppingItemEntity): Long

    @Insert
    suspend fun insertAll(items: List<ShoppingItemEntity>)

    @Update
    suspend fun update(item: ShoppingItemEntity)

    @Query("SELECT * FROM shopping_items WHERE checked = 0")
    suspend fun unchecked(): List<ShoppingItemEntity>

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM shopping_items WHERE checked = 1")
    suspend fun clearChecked()

    @Query("DELETE FROM shopping_items")
    suspend fun clearAll()
}
