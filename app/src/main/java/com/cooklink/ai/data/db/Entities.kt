package com.cooklink.ai.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val coverUri: String?,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val difficulty: String?,
    val servings: Int,
    val ingredientsJson: String,
    val equipmentJson: String,
    val stepsJson: String,
    val nutritionJson: String,
    val sourcesJson: String,
    val notes: String,
    val tagsJson: String,
    val origin: String,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long?,
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "recipe_collection",
    primaryKeys = ["recipeId", "collectionId"],
    foreignKeys = [
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CollectionEntity::class, parentColumns = ["id"], childColumns = ["collectionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("collectionId")],
)
data class RecipeCollectionRef(val recipeId: Long, val collectionId: Long)

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: Double?,
    val unit: String,
    val category: String,
    val recipeTitle: String?,
    val checked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Collection row with how many recipes it holds. */
data class CollectionWithCount(val id: Long, val name: String, val recipeCount: Int)
