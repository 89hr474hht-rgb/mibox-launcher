package com.razorbill.launcher

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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private var onNotificationResult: ((Boolean) -> Unit)? = null
    private var onHomeRoleResult: ((Boolean) -> Unit)? = null
    private lateinit var pinnedAppsStore: PinnedAppsStore

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
        pinnedAppsStore = PinnedAppsStore(applicationContext)
        setContent {
            MaterialTheme {
              Surface(modifier = Modifier.fillMaxSize()) {
                var screen by remember { mutableStateOf(Screen.HOME) }
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        pinnedAppsStore = pinnedAppsStore,
                        onOpenSettings = { screen = Screen.SETTINGS_MENU },
                        onExitToSystem = { moveTaskToBack(true) }
                    )
                    else -> SettingsArea(
                        screen = screen,
                        isHomeRoleHeldInitially = isHomeRoleHeld(),
                        onNavigate = { screen = it },
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
                                callback(false)
                            }
                        },
                        onRequestNotificationPermission = { callback ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                onNotificationResult = callback
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                callback(true)
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

enum class Screen {
    HOME, SETTINGS_MENU, SETTINGS_UPDATE, SETTINGS_GENERAL,
    SETTINGS_APPEARANCE, SETTINGS_APPS, SETTINGS_SYSTEM, SETTINGS_DIAGNOSTICS, SETTINGS_ABOUT
}

private fun currentTimeText(): String {
    val cal = Calendar.getInstance()
    return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

@androidx.compose.runtime.Composable
private fun AnimatedBackground(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "bg")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "bgT"
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val angle = t * 2f * PI.toFloat()
        val dx = cos(angle) * w * 0.07f
        val dy = sin(angle) * h * 0.07f

        drawRect(color = Color(0xFF0A0B0D))
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF6FD3E8).copy(alpha = 0.55f), Color.Transparent),
                center = Offset(w * 0.18f + dx, h * 0.22f + dy),
                radius = w * 0.34f
            ),
            radius = w * 0.34f,
            center = Offset(w * 0.18f + dx, h * 0.22f + dy)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF3A6FF0).copy(alpha = 0.45f), Color.Transparent),
                center = Offset(w * 0.82f - dx, h * 0.72f - dy),
                radius = w * 0.3f
            ),
            radius = w * 0.3f,
            center = Offset(w * 0.82f - dx, h * 0.72f - dy)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF7A52D6).copy(alpha = 0.4f), Color.Transparent),
                center = Offset(w * 0.6f + dy, h * 0.1f + dx),
                radius = w * 0.26f
            ),
            radius = w * 0.26f,
            center = Offset(w * 0.6f + dy, h * 0.1f + dx)
        )
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF0A0B0D).copy(alpha = 0.25f),
                    Color(0xFF0A0B0D).copy(alpha = 0.55f),
                    Color(0xFF0A0B0D).copy(alpha = 0.92f)
                )
            )
        )
    }
}

@androidx.compose.runtime.Composable
private fun LiveClock() {
    var time by remember { mutableStateOf(currentTimeText()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val now = currentTimeText()
            if (now != time) time = now
        }
    }
    AnimatedContent(
        targetState = time,
        transitionSpec = {
            (slideInVertically(animationSpec = tween(450)) { h -> h } + fadeIn(tween(450))) togetherWith
                (slideOutVertically(animationSpec = tween(450)) { h -> -h } + fadeOut(tween(450)))
        },
        label = "clock"
    ) { t ->
        Text(
            text = t,
            fontFamily = FontFamily.Serif,
            fontSize = 44.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.94f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@androidx.compose.runtime.Composable
private fun AppCard(
    app: AppInfo,
    size: Dp,
    focusRequester: FocusRequester?,
    onLaunch: () -> Unit,
    onTogglePin: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "cardScale")
    val hue = remember(app.packageName) { (abs(app.packageName.hashCode()) % 360).toFloat() }
    val base = remember(hue) { Color.hsv(hue, 0.5f, 0.55f) }
    val lite = remember(hue) { Color.hsv(hue, 0.42f, 0.82f) }
    val bgBrush = if (focused) Brush.linearGradient(listOf(base, lite)) else Brush.linearGradient(listOf(base, base))
    val shineAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "shineAlpha")
    val shineOffset by animateDpAsState(if (focused) 0.dp else -size, tween(550), label = "shineOffset")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(size + 24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(18.dp))
                .background(bgBrush)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onLaunch,
                    onLongClick = onTogglePin
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = app.label.take(1).uppercase(),
                color = Color.White.copy(alpha = 0.92f),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                fontSize = (size.value / 3).sp
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = shineOffset)
                    .alpha(shineAlpha)
                    .background(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.4f), Color.Transparent)
                        )
                    )
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = app.label,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center
        )
    }
}

@androidx.compose.runtime.Composable
private fun HomeScreen(
    pinnedAppsStore: PinnedAppsStore,
    onOpenSettings: () -> Unit,
    onExitToSystem: () -> Unit
) {
    val context = LocalContext.current
    val allApps = remember { InstalledApps.query(context) }
    val pinnedPackages by pinnedAppsStore.pinnedPackages.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()

    val pinnedApps = allApps.filter { it.packageName in pinnedPackages }
    val unpinnedApps = allApps.filter { it.packageName !in pinnedPackages }

    val firstFocusRequester = remember { FocusRequester() }
    val firstIsInShelf = pinnedApps.isNotEmpty()

    LaunchedEffect(allApps.size) {
        if (allApps.isNotEmpty()) firstFocusRequester.requestFocus()
    }

    BackHandler(onBack = onExitToSystem)

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiveClock()
                Button(onClick = onOpenSettings) { Text("⚙") }
            }

            if (pinnedApps.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        pinnedApps.forEachIndexed { index, app ->
                            AppCard(
                                app = app,
                                size = 96.dp,
                                focusRequester = if (index == 0 && firstIsInShelf) firstFocusRequester else null,
                                onLaunch = { context.startActivity(app.launchIntent) },
                                onTogglePin = { scope.launch { pinnedAppsStore.togglePin(app.packageName) } }
                            )
                        }
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(unpinnedApps) { index, app ->
                    AppCard(
                        app = app,
                        size = 88.dp,
                        focusRequester = if (index == 0 && !firstIsInShelf) firstFocusRequester else null,
                        onLaunch = { context.startActivity(app.launchIntent) },
                        onTogglePin = { scope.launch { pinnedAppsStore.togglePin(app.packageName) } }
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SettingsArea(
    screen: Screen,
    isHomeRoleHeldInitially: Boolean,
    onNavigate: (Screen) -> Unit,
    onRequestHomeRole: ((Boolean) -> Unit) -> Unit,
    onRequestNotificationPermission: ((Boolean) -> Unit) -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onOpenDeveloperOptions: () -> Unit
) {
    BackHandler {
        onNavigate(if (screen == Screen.SETTINGS_MENU) Screen.HOME else Screen.SETTINGS_MENU)
    }

    val title = when (screen) {
        Screen.SETTINGS_MENU -> "Réglages"
        Screen.SETTINGS_UPDATE -> "Mises à jour"
        Screen.SETTINGS_GENERAL -> "Général"
        Screen.SETTINGS_APPEARANCE -> "Apparence"
        Screen.SETTINGS_APPS -> "Applications"
        Screen.SETTINGS_SYSTEM -> "Système"
        Screen.SETTINGS_DIAGNOSTICS -> "Diagnostics"
        Screen.SETTINGS_ABOUT -> "À propos"
        else -> "Réglages"
    }

    Box(modifier = Modifier.fillMaxSize().padding(56.dp, 44.dp)) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    onNavigate(if (screen == Screen.SETTINGS_MENU) Screen.HOME else Screen.SETTINGS_MENU)
                }) { Text("←") }
                Spacer(Modifier.width(16.dp))
                Text(text = title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(28.dp))

            when (screen) {
                Screen.SETTINGS_MENU -> SettingsMenuList(onNavigate)
                Screen.SETTINGS_UPDATE -> UpdateSection()
                Screen.SETTINGS_SYSTEM -> SystemSection(
                    isHomeRoleHeldInitially,
                    onRequestHomeRole,
                    onRequestNotificationPermission,
                    onOpenUnknownSourcesSettings,
                    onOpenDeveloperOptions
                )
                Screen.SETTINGS_ABOUT -> AboutSection()
                else -> Text("Contenu à définir.", color = Color.White.copy(alpha = 0.45f))
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SettingsMenuList(onNavigate: (Screen) -> Unit) {
    val items = listOf(
        Triple("Général", "Paramètres divers", Screen.SETTINGS_GENERAL),
        Triple("Apparence", "Couleur d'accent, fond d'écran", Screen.SETTINGS_APPEARANCE),
        Triple("Applications", "Réorganiser, masquer", Screen.SETTINGS_APPS),
        Triple("Mises à jour", "Vérifier, installer, historique", Screen.SETTINGS_UPDATE),
        Triple("Système", "Launcher par défaut, permissions", Screen.SETTINGS_SYSTEM),
        Triple("Diagnostics", "Logs, envoi de rapport de crash", Screen.SETTINGS_DIAGNOSTICS),
        Triple("À propos", "Version, dépôt GitHub", Screen.SETTINGS_ABOUT)
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (label, sub, target) ->
            Button(onClick = { onNavigate(target) }, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(label, fontSize = 18.sp)
                    Text(sub, fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SystemSection(
    isHomeRoleHeldInitially: Boolean,
    onRequestHomeRole: ((Boolean) -> Unit) -> Unit,
    onRequestNotificationPermission: ((Boolean) -> Unit) -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onOpenDeveloperOptions: () -> Unit
) {
    var notificationsStatus by remember { mutableStateOf("Non demandé") }
    var homeStatus by remember {
        mutableStateOf(if (isHomeRoleHeldInitially) "Actif" else "Pas encore actif")
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = { onRequestHomeRole { granted -> homeStatus = if (granted) "Actif" else "Refusé ou indisponible" } }) {
            Text("Devenir le launcher par défaut")
        }
        Text("Launcher par défaut : $homeStatus")

        Button(onClick = {
            onRequestNotificationPermission { granted -> notificationsStatus = if (granted) "Accordée" else "Refusée" }
            onOpenUnknownSourcesSettings()
        }) {
            Text("Tout configurer (notifications + sources inconnues)")
        }
        Text("Notifications : $notificationsStatus")

        Button(onClick = onOpenDeveloperOptions) { Text("Ouvrir les Options développeur (optimisation)") }
        Text(
            "Une fois là-bas : \"Limite de processus en arrière-plan\" → 1 processus, et réduire les " +
                "échelles d'animation. Impossible à appliquer automatiquement (protection système Android)."
        )
    }
}

@androidx.compose.runtime.Composable
private fun UpdateSection() {
    var updateStatus by remember { mutableStateOf("(pas encore de résultat)") }
    var lastCheckedAt by remember { mutableStateOf("jamais") }
    var pendingUpdate by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var downloadPercent by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Version actuelle : ${BuildConfig.VERSION_NAME}")
        Button(
            enabled = !isBusy,
            onClick = {
                isBusy = true
                updateStatus = "Vérification en cours…"
                pendingUpdate = null
                downloadPercent = null
                scope.launch {
                    val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.FRANCE).format(java.util.Date())
                    when (val result = UpdateChecker.checkForUpdate()) {
                        is UpdateChecker.CheckResult.UpToDate -> updateStatus = "À jour (v${BuildConfig.VERSION_NAME})"
                        is UpdateChecker.CheckResult.UpdateAvailable -> {
                            pendingUpdate = result.info
                            updateStatus = "Mise à jour disponible : ${result.info.versionTag}"
                        }
                        is UpdateChecker.CheckResult.Error -> updateStatus = "Erreur : ${result.message}"
                    }
                    lastCheckedAt = now
                    isBusy = false
                }
            }
        ) { Text("Vérifier les mises à jour") }
        Text("Dernière vérification : $lastCheckedAt")
        Text("Résultat : $updateStatus")

        pendingUpdate?.let { info ->
            Button(
                enabled = !isBusy,
                onClick = {
                    isBusy = true
                    downloadPercent = 0
                    updateStatus = "Téléchargement de ${info.versionTag}…"
                    scope.launch {
                        try {
                            val file = UpdateChecker.downloadApk(context, info.apkDownloadUrl) { percent -> downloadPercent = percent }
                            updateStatus = "Installation…"
                            UpdateChecker.installApk(context, file)
                        } catch (e: Exception) {
                            updateStatus = "Échec du téléchargement : ${e.message}"
                        }
                        isBusy = false
                    }
                }
            ) { Text("▶ Installer ${info.versionTag} maintenant") }
            Text("Notes de version : " + info.changelog.ifBlank { "(aucune)" })
        }

        downloadPercent?.let { percent ->
            Column {
                Text("Téléchargement : $percent%")
                Box(
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                        .background(Color.DarkGray, RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(fraction = percent / 100f).height(8.dp)
                            .background(Color.White, RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AboutSection() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Razorbill v${BuildConfig.VERSION_NAME}")
        Button(onClick = {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/89hr474hht-rgb/mibox-launcher"))
                )
            } catch (_: Exception) {
            }
        }) { Text("Voir sur GitHub") }
    }
}
