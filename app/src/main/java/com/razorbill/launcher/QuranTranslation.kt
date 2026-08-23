package com.razorbill.launcher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Ayah(val numberInSurah: Int, val text: String)

object QuranTranslationRepository {
    private const val BASE_URL = "https://api.alquran.cloud/v1/surah"
    private const val EDITION = "fr.hamidullah"

    suspend fun getTranslation(context: Context, surahNumber: Int): List<Ayah>? =
        withContext(Dispatchers.IO) {
            getCached(context, surahNumber) ?: fetchAndCache(context, surahNumber)
        }

    private fun cacheDir(context: Context): File =
        File(context.filesDir, "quran_translations").apply { if (!exists()) mkdirs() }

    private fun getCached(context: Context, surahNumber: Int): List<Ayah>? {
        val file = File(cacheDir(context), "$surahNumber.json")
        if (!file.exists()) return null
        return try {
            parseAyahs(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchAndCache(context: Context, surahNumber: Int): List<Ayah>? {
        return try {
            val connection = URL("$BASE_URL/$surahNumber/$EDITION").openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"
            val body = if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else null
            connection.disconnect()
            body ?: return null
            val ayahs = parseAyahs(body)
            File(cacheDir(context), "$surahNumber.json").writeText(body)
            ayahs
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAyahs(json: String): List<Ayah> {
        val ayahsArray = JSONObject(json).getJSONObject("data").getJSONArray("ayahs")
        return (0 until ayahsArray.length()).map { i ->
            val o = ayahsArray.getJSONObject(i)
            Ayah(numberInSurah = o.getInt("numberInSurah"), text = o.getString("text"))
        }
    }
}
