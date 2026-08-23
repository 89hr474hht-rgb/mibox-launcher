package com.razorbill.launcher

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.quranDataStore by preferencesDataStore(name = "razorbill_quran_prefs")
private val LAST_SURAH_KEY = intPreferencesKey("last_surah_number")

private fun positionKey(surahNumber: Int) = longPreferencesKey("position_ms_$surahNumber")

data class QuranProgress(val surahNumber: Int, val positionMs: Long)

class QuranStore(private val context: Context) {
    val lastProgress: Flow<QuranProgress?> = context.quranDataStore.data.map { prefs ->
        val surahNumber = prefs[LAST_SURAH_KEY] ?: return@map null
        val positionMs = prefs[positionKey(surahNumber)] ?: 0L
        QuranProgress(surahNumber, positionMs)
    }

    fun positionFor(surahNumber: Int): Flow<Long> = context.quranDataStore.data.map { prefs ->
        prefs[positionKey(surahNumber)] ?: 0L
    }

    suspend fun savePosition(surahNumber: Int, positionMs: Long) {
        context.quranDataStore.edit { prefs ->
            prefs[LAST_SURAH_KEY] = surahNumber
            prefs[positionKey(surahNumber)] = positionMs
        }
    }
}
