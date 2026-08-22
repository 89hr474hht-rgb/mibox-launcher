package com.mibox.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
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
              Surface(modifier = Modifier.fillMaxSize()) {
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
}

private enum class Screen { HOME, SETTINGS }

@androidx.compose.runtime.Composable
private fun HomeScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val apps = remember { InstalledApps.query(context) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(apps) { app ->
                Card(
                    onClick = { context.startActivity(app.launchIntent) },
                    modifier = Modifier.aspectRatio(1f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = app.icon.toBitmap().asImageBitmap(),
                            contentDescription = app.label,
                            modifier = Modifier.aspectRatio(1f)
                        )
                        Text(text = app.label)
                    }
                }
            }
        }

        Button(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Text("⚙")
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
    var updateStatus by remember { mutableStateOf("(pas encore de résultat)") }
    var lastCheckedAt by remember { mutableStateOf("jamais") }
    var pendingUpdate by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var downloadPercent by remember { mutableStateOf<Int?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                    downloadPercent = null
                    scope.launch {
                        val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.FRANCE)
                            .format(java.util.Date())
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
                        lastCheckedAt = now
                        isBusy = false
                    }
                }
            ) {
                Text("Vérifier les mises à jour")
            }
            Text(text = "Dernière vérification : $lastCheckedAt")
            Text(text = "Résultat : $updateStatus")

            pendingUpdate?.let { info ->
                Button(
                    enabled = !isBusy,
                    onClick = {
                        isBusy = true
                        downloadPercent = 0
                        updateStatus = "Téléchargement de ${info.versionTag}…"
                        scope.launch {
                            try {
                                val file = UpdateChecker.downloadApk(context, info.apkDownloadUrl) { percent ->
                                    downloadPercent = percent
                                }
                                updateStatus = "Installation…"
                                UpdateChecker.installApk(context, file)
                            } catch (e: Exception) {
                                updateStatus = "Échec du téléchargement : ${e.message}"
                            }
                            isBusy = false
                        }
                    }
                ) {
                    Text("▶ Installer ${info.versionTag} maintenant")
                }
                Text(text = "Notes de version : " + info.changelog.ifBlank { "(aucune)" })
            }

            downloadPercent?.let { percent ->
                Column {
                    Text(text = "Téléchargement : $percent%")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color.DarkGray, RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = percent / 100f)
                                .height(8.dp)
                                .background(Color.White, RoundedCornerShape(4.dp))
                        )
                    }
                }
            }

            Button(onClick = onBack) {
                Text("Retour")
            }
        }
    }
}
