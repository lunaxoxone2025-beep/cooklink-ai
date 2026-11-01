package com.cooklink.ai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RecipeEntity::class, CollectionEntity::class, RecipeCollectionRef::class, ShoppingItemEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipes(): RecipeDao
    abstract fun collections(): CollectionDao
    abstract fun shopping(): ShoppingDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "cooklink.db").build()
    }
}
