package com.offlineai.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "axion_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class AppPreferences(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val SELECTED_MODEL_PATH = stringPreferencesKey("selected_model_path")
        val SELECTED_MODEL_NAME = stringPreferencesKey("selected_model_name")
        val SELECTED_MODEL_SIZE = stringPreferencesKey("selected_model_size")
        val THINKING_ENABLED = booleanPreferencesKey("thinking_enabled")
        val BACKEND = stringPreferencesKey("compute_backend")
        val SWAP_PATH = stringPreferencesKey("swap_path")
        val SWAP_SIZE_MB = stringPreferencesKey("swap_size_mb")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.THEME]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val selectedModelPath: Flow<String?> = context.dataStore.data.map { it[Keys.SELECTED_MODEL_PATH] }
    val selectedModelName: Flow<String?> = context.dataStore.data.map { it[Keys.SELECTED_MODEL_NAME] }
    val selectedModelSize: Flow<Long?> = context.dataStore.data.map {
        it[Keys.SELECTED_MODEL_SIZE]?.toLongOrNull()
    }

    val thinkingEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.THINKING_ENABLED] ?: false
    }

    val backend: Flow<String> = context.dataStore.data.map {
        it[Keys.BACKEND] ?: "AUTO"
    }

    val swapPath: Flow<String?> = context.dataStore.data.map { it[Keys.SWAP_PATH] }
    val swapSizeMb: Flow<Long> = context.dataStore.data.map {
        it[Keys.SWAP_SIZE_MB]?.toLongOrNull() ?: 0L
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setSelectedModel(path: String?, name: String?, sizeBytes: Long?) {
        context.dataStore.edit { prefs ->
            if (path == null) {
                prefs.remove(Keys.SELECTED_MODEL_PATH)
                prefs.remove(Keys.SELECTED_MODEL_NAME)
                prefs.remove(Keys.SELECTED_MODEL_SIZE)
            } else {
                prefs[Keys.SELECTED_MODEL_PATH] = path
                prefs[Keys.SELECTED_MODEL_NAME] = name ?: path.substringAfterLast('/')
                prefs[Keys.SELECTED_MODEL_SIZE] = (sizeBytes ?: 0L).toString()
            }
        }
    }

    suspend fun setThinkingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.THINKING_ENABLED] = enabled }
    }

    suspend fun setBackend(backend: String) {
        context.dataStore.edit { it[Keys.BACKEND] = backend }
    }

    suspend fun setSwap(path: String?, sizeMb: Long) {
        context.dataStore.edit { prefs ->
            if (path == null) {
                prefs.remove(Keys.SWAP_PATH)
                prefs.remove(Keys.SWAP_SIZE_MB)
            } else {
                prefs[Keys.SWAP_PATH] = path
                prefs[Keys.SWAP_SIZE_MB] = sizeMb.toString()
            }
        }
    }
}
