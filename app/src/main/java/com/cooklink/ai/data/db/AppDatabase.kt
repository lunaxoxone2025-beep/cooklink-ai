package com.cooklink.ai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RecipeEntity::class, CollectionEntity::class, RecipeCollectionRef::class, ShoppingItemEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipes(): RecipeDao
    abstract fun collections(): CollectionDao
    abstract fun shopping(): ShoppingDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "cooklink.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2: keeps the AI's original version of imported recipes. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN originalJson TEXT DEFAULT NULL")
            }
        }
    }
}
