package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tubepulse_prefs")

class PreferencesDataStore(context: Context) {
    private val appContext = context.applicationContext

    val themePreference: Flow<ThemePreference> = appContext.settingsDataStore.data.map { prefs ->
        val raw = prefs[KEY_THEME] ?: ThemePreference.DARK.name
        enumValues<ThemePreference>().firstOrNull { it.name == raw } ?: ThemePreference.DARK
    }

    suspend fun setThemePreference(preference: ThemePreference) {
        appContext.settingsDataStore.edit { prefs ->
            prefs[KEY_THEME] = preference.name
        }
    }

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme_preference")
    }
}
