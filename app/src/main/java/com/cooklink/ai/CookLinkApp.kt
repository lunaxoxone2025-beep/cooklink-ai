package com.cooklink.ai

import android.app.Application
import com.cooklink.ai.data.ImageStore
import com.cooklink.ai.data.RecipeRepository
import com.cooklink.ai.data.ShoppingRepository
import com.cooklink.ai.data.db.AppDatabase
import com.cooklink.ai.data.settings.SecureKeyStore
import com.cooklink.ai.data.settings.SettingsRepository

/** Manual dependency container — small enough that a DI framework isn't worth it yet. */
class AppContainer(app: Application) {
    private val db = AppDatabase.create(app)
    val recipes = RecipeRepository(db)
    val shopping = ShoppingRepository(db)
    val settings = SettingsRepository(app)
    val keys = SecureKeyStore(app)
    val images = ImageStore(app)
}

class CookLinkApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
