package com.mibox.launcher

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

class MainActivity : ComponentActivity() {

    private var onNotificationResult: ((Boolean) -> Unit)? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onNotificationResult?.invoke(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var screen by remember { mutableStateOf(Screen.HOME) }
                when (screen) {
                    Screen.HOME -> HomeScreen(onOpenSettings = { screen = Screen.SETTINGS })
                    Screen.SETTINGS -> SettingsScreen(
                        onBack = { screen = Screen.HOME },
                        onRequestNotificationPermission = { callback ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                onNotificationResult = callback
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                callback(true) // permission n'existe pas avant Android 13, rien à demander
                            }
                        },
                        onOpenUnknownSourcesSettings = {
                            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

private enum class Screen { HOME, SETTINGS }

@androidx.compose.runtime.Composable
private fun HomeScreen(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "MiBox Launcher v${BuildConfig.VERSION_NAME}")
            Button(onClick = onOpenSettings) {
                Text("Réglages & diagnostics")
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    onRequestNotificationPermission: ((Boolean) -> Unit) -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit
) {
    var notificationsStatus by remember { mutableStateOf("Non demandé") }

    Box(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = "Réglages & diagnostics")

            Button(onClick = {
                onRequestNotificationPermission { granted ->
                    notificationsStatus = if (granted) "Accordée" else "Refusée"
                }
                onOpenUnknownSourcesSettings()
            }) {
                Text("Tout configurer")
            }

            Text(text = "Notifications : $notificationsStatus")
            Text(text = "Après avoir coché \"Autoriser cette source\" sur l'écran qui s'ouvre, reviens ici avec le bouton retour de la télécommande.")

            Button(onClick = onBack) {
                Text("Retour")
            }
        }
    }
}
