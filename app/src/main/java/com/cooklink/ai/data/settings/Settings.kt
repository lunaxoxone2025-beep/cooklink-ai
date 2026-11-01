package com.cooklink.ai.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class AiProvider(val label: String, val keyHint: String, val keyUrl: String) {
    GEMINI("Google Gemini", "AIza…", "https://aistudio.google.com/apikey"),
    CLAUDE("Anthropic Claude", "sk-ant-…", "https://console.anthropic.com/settings/keys"),
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val providerKey = stringPreferencesKey("provider")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { p ->
        ThemeMode.entries.firstOrNull { it.name == p[themeKey] } ?: ThemeMode.SYSTEM
    }

    val provider: Flow<AiProvider> = context.dataStore.data.map { p ->
        AiProvider.entries.firstOrNull { it.name == p[providerKey] } ?: AiProvider.GEMINI
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[themeKey] = mode.name }
    suspend fun setProvider(p: AiProvider) = context.dataStore.edit { it[providerKey] = p.name }
}
