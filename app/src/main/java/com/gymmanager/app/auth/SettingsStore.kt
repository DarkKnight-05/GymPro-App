package com.gymmanager.app.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "gym_manager_settings")
class SettingsStore(private val context: Context) {
    private object Keys { val THEME = stringPreferencesKey("theme") }
    val theme: Flow<ThemeMode> = context.dataStore.data.map { runCatching { ThemeMode.valueOf(it[Keys.THEME] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM) }
    suspend fun setTheme(mode: ThemeMode) { context.dataStore.edit { it[Keys.THEME] = mode.name } }
}
