package com.razorbill.launcher

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the GitHub Releases API for this project's own repo. Public repo,
 * so no auth token needed to check for or download the latest release.
 */
object UpdateChecker {
    private const val REPO = "89hr474hht-rgb/mibox-launcher"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"

    data class UpdateInfo(
        val versionTag: String,
        val changelog: String,
        val apkDownloadUrl: String
    )

    sealed class CheckResult {
        data class UpdateAvailable(val info: UpdateInfo) : CheckResult()
        object UpToDate : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    suspend fun checkForUpdate(): CheckResult = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return@withContext CheckResult.Error("Réponse GitHub inattendue (HTTP $code)")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.getString("tag_name")
            val changelog = json.optString("body", "")
            val assets = json.getJSONArray("assets")

            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }

            if (apkUrl == null) {
                return@withContext CheckResult.Error("Dernière release sans APK attaché")
            }

            val currentTag = "v${BuildConfig.VERSION_NAME}"
            if (tag == currentTag) {
                CheckResult.UpToDate
            } else {
                CheckResult.UpdateAvailable(UpdateInfo(tag, changelog, apkUrl))
            }
        } catch (e: Exception) {
            CheckResult.Error(e.message ?: e.toString())
        }
    }

    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (percent: Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        val totalSize = connection.contentLength
        val file = File(context.getExternalFilesDir(null), "update.apk")
        connection.inputStream.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesCopied = 0L
                var lastReportedPercent = -1
                var bytesRead = input.read(buffer)
                while (bytesRead >= 0) {
                    output.write(buffer, 0, bytesRead)
                    bytesCopied += bytesRead
                    if (totalSize > 0) {
                        val percent = ((bytesCopied * 100) / totalSize).toInt()
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            onProgress(percent)
                        }
                    }
                    bytesRead = input.read(buffer)
                }
            }
        }
        file
    }

    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
