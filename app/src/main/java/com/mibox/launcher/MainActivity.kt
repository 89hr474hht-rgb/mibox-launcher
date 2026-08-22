package com.mibox.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var onNotificationResult: ((Boolean) -> Unit)? = null
    private var onHomeRoleResult: ((Boolean) -> Unit)? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onNotificationResult?.invoke(granted) }

    private val homeRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> onHomeRoleResult?.invoke(isHomeRoleHeld()) }

    private fun isHomeRoleHeld(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var screen by remember { mutableStateOf(Screen.HOME) }
                when (screen) {
                    Screen.HOME -> HomeScreen(onOpenSettings = { screen = Screen.SETTINGS })
                    Screen.SETTINGS -> SettingsScreen(
                        isHomeRoleHeldInitially = isHomeRoleHeld(),
                        onBack = { screen = Screen.HOME },
                        onRequestHomeRole = { callback ->
                            val roleManager = getSystemService(RoleManager::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                roleManager != null &&
                                roleManager.isRoleAvailable(RoleManager.ROLE_HOME)
                            ) {
                                if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                                    callback(true)
                                } else {
                                    onHomeRoleResult = callback
                                    homeRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                                }
                            } else {
                                // Rôle non disponible sur cet appareil : pas de raccourci propre possible.
                                callback(false)
                            }
                        },
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
                        },
                        onOpenDeveloperOptions = {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
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
    isHomeRoleHeldInitially: Boolean,
    onBack: () -> Unit,
    onRequestHomeRole: ((Boolean) -> Unit) -> Unit,
    onRequestNotificationPermission: ((Boolean) -> Unit) -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onOpenDeveloperOptions: () -> Unit
) {
    var notificationsStatus by remember { mutableStateOf("Non demandé") }
    var homeStatus by remember {
        mutableStateOf(if (isHomeRoleHeldInitially) "Actif" else "Pas encore actif")
    }
    var updateStatus by remember { mutableStateOf("Jamais vérifié") }
    var pendingUpdate by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = "Réglages & diagnostics")

            Button(onClick = {
                onRequestHomeRole { granted ->
                    homeStatus = if (granted) "Actif" else "Refusé ou indisponible"
                }
            }) {
                Text("Devenir le launcher par défaut")
            }
            Text(text = "Launcher par défaut : $homeStatus")

            Button(onClick = {
                onRequestNotificationPermission { granted ->
                    notificationsStatus = if (granted) "Accordée" else "Refusée"
                }
                onOpenUnknownSourcesSettings()
            }) {
                Text("Tout configurer (notifications + sources inconnues)")
            }
            Text(text = "Notifications : $notificationsStatus")

            Button(onClick = onOpenDeveloperOptions) {
                Text("Ouvrir les Options développeur (optimisation)")
            }
            Text(
                text = "Une fois là-bas : \"Limite de processus en arrière-plan\" → 1 processus, " +
                    "et réduire les échelles d'animation. Ces réglages ne peuvent pas être appliqués " +
                    "automatiquement par l'app (protection système Android), juste les ouvrir pour toi."
            )

            Button(
                enabled = !isBusy,
                onClick = {
                    isBusy = true
                    updateStatus = "Vérification en cours…"
                    pendingUpdate = null
                    scope.launch {
                        when (val result = UpdateChecker.checkForUpdate()) {
                            is UpdateChecker.CheckResult.UpToDate -> {
                                updateStatus = "À jour (v${BuildConfig.VERSION_NAME})"
                            }
                            is UpdateChecker.CheckResult.UpdateAvailable -> {
                                pendingUpdate = result.info
                                updateStatus = "Mise à jour disponible : ${result.info.versionTag}"
                            }
                            is UpdateChecker.CheckResult.Error -> {
                                updateStatus = "Erreur : ${result.message}"
                            }
                        }
                        isBusy = false
                    }
                }
            ) {
                Text("Vérifier les mises à jour")
            }
            Text(text = "Mises à jour : $updateStatus")
            pendingUpdate?.let { info ->
                Text(text = info.changelog.ifBlank { "(pas de notes de version)" })
                Button(
                    enabled = !isBusy,
                    onClick = {
                        isBusy = true
                        updateStatus = "Téléchargement de ${info.versionTag}…"
                        scope.launch {
                            try {
                                val file = UpdateChecker.downloadApk(context, info.apkDownloadUrl)
                                updateStatus = "Installation…"
                                UpdateChecker.installApk(context, file)
                            } catch (e: Exception) {
                                updateStatus = "Échec du téléchargement : ${e.message}"
                            }
                            isBusy = false
                        }
                    }
                ) {
                    Text("Installer ${info.versionTag}")
                }
            }

            Button(onClick = onBack) {
                Text("Retour")
            }
        }
    }
}
