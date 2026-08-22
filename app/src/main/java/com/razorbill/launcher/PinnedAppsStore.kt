package com.razorbill.launcher

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "razorbill_prefs")
private val PINNED_APPS_KEY = stringSetPreferencesKey("pinned_apps")

class PinnedAppsStore(private val context: Context) {
    val pinnedPackages: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[PINNED_APPS_KEY] ?: emptySet()
    }

    suspend fun togglePin(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[PINNED_APPS_KEY] ?: emptySet()
            prefs[PINNED_APPS_KEY] = if (packageName in current) current - packageName else current + packageName
        }
    }
}
