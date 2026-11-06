package com.cooklink.ai.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** Accent colour of the neon interface. */
enum class NeonAccent(val label: String) { LIME("Lima"), ORANGE("Naranja"), PINK("Rosa"), CYAN("Cian") }

enum class AiProvider(val label: String, val keyHint: String, val keyUrl: String) {
    GEMINI("Google Gemini", "AIza…", "https://aistudio.google.com/apikey"),
    CLAUDE("Anthropic Claude", "sk-ant-…", "https://console.anthropic.com/settings/keys"),
}

/** Language the AI writes imported recipes in. promptName == null keeps the source language. */
enum class RecipeLanguage(val label: String, val promptName: String?) {
    ORIGINAL("Same as source", null),
    SPANISH("Español", "Spanish"),
    ENGLISH("English", "English"),
    ITALIAN("Italiano", "Italian"),
    SLOVENIAN("Slovenščina", "Slovenian"),
    GERMAN("Deutsch", "German"),
    FRENCH("Français", "French"),
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val providerKey = stringPreferencesKey("provider")
    private val languageKey = stringPreferencesKey("recipe_language")
    private val accentKey = stringPreferencesKey("accent")

    val accent: Flow<NeonAccent> = context.dataStore.data.map { p ->
        NeonAccent.entries.firstOrNull { it.name == p[accentKey] } ?: NeonAccent.LIME
    }

    suspend fun setAccent(a: NeonAccent) = context.dataStore.edit { it[accentKey] = a.name }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { p ->
        ThemeMode.entries.firstOrNull { it.name == p[themeKey] } ?: ThemeMode.SYSTEM
    }

    val provider: Flow<AiProvider> = context.dataStore.data.map { p ->
        AiProvider.entries.firstOrNull { it.name == p[providerKey] } ?: AiProvider.GEMINI
    }

    val recipeLanguage: Flow<RecipeLanguage> = context.dataStore.data.map { p ->
        RecipeLanguage.entries.firstOrNull { it.name == p[languageKey] } ?: RecipeLanguage.ORIGINAL
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[themeKey] = mode.name }
    suspend fun setProvider(p: AiProvider) = context.dataStore.edit { it[providerKey] = p.name }
    suspend fun setRecipeLanguage(l: RecipeLanguage) = context.dataStore.edit { it[languageKey] = l.name }
}
