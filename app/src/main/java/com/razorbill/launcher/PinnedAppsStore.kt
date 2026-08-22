package com.razorbill.launcher

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "razorbill_prefs")
private val PINNED_APPS_KEY = stringSetPreferencesKey("pinned_apps")
private val HIDDEN_APPS_KEY = stringSetPreferencesKey("hidden_apps")
private val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color_hex")

val ACCENT_COLOR_CHOICES = listOf(
    "#6FD3E8", // teal (default)
    "#7A93F0", // periwinkle
    "#D98AD9", // orchid
    "#F0A25A", // amber
    "#8AD98F"  // sage
)
const val DEFAULT_ACCENT_COLOR = "#6FD3E8"

class PinnedAppsStore(private val context: Context) {
    val pinnedPackages: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[PINNED_APPS_KEY] ?: emptySet()
    }

    val hiddenPackages: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[HIDDEN_APPS_KEY] ?: emptySet()
    }

    val accentColorHex: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ACCENT_COLOR_KEY] ?: DEFAULT_ACCENT_COLOR
    }

    suspend fun togglePin(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[PINNED_APPS_KEY] ?: emptySet()
            prefs[PINNED_APPS_KEY] = if (packageName in current) current - packageName else current + packageName
        }
    }

    suspend fun toggleHidden(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[HIDDEN_APPS_KEY] ?: emptySet()
            prefs[HIDDEN_APPS_KEY] = if (packageName in current) current - packageName else current + packageName
        }
    }

    suspend fun setAccentColor(hex: String) {
        context.dataStore.edit { prefs -> prefs[ACCENT_COLOR_KEY] = hex }
    }

    suspend fun resetFavorites() {
        context.dataStore.edit { prefs -> prefs[PINNED_APPS_KEY] = emptySet() }
    }
}
