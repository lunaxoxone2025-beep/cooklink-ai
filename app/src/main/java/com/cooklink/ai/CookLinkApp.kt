package com.cooklink.ai

import android.app.Application
import com.cooklink.ai.data.ImageStore
import com.cooklink.ai.data.RecipeRepository
import com.cooklink.ai.data.ShoppingRepository
import com.cooklink.ai.data.db.AppDatabase
import com.cooklink.ai.data.settings.SecureKeyStore
import com.cooklink.ai.data.settings.SettingsRepository
import com.cooklink.ai.data.importer.ImportController
import com.cooklink.ai.data.importer.MediaPrep
import com.cooklink.ai.data.importer.RecipeImporter
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container — small enough that a DI framework isn't worth it yet. */
class AppContainer(app: Application) {
    private val db = AppDatabase.create(app)
    val recipes = RecipeRepository(db)
    val shopping = ShoppingRepository(db)
    val settings = SettingsRepository(app)
    val keys = SecureKeyStore(app)
    val images = ImageStore(app)

    /** Long timeouts: watching a video can take the AI a minute or more. */
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .build()

    val importer = RecipeImporter(http, keys, settings, recipes, images, MediaPrep(app))
    val imports = ImportController(importer)
}

class CookLinkApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
