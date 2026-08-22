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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
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
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        pinnedAppsStore = PinnedAppsStore(applicationContext)
        setContent {
            MaterialTheme(colorScheme = razorbillDarkColorScheme()) {
              Surface(modifier = Modifier.fillMaxSize()) {
                var showSplash by remember { mutableStateOf(true) }
                var homeEverShown by remember { mutableStateOf(false) }
                var screen by remember { mutableStateOf(Screen.HOME) }
                if (showSplash) {
                    SplashScreen(onFinished = { showSplash = false })
                } else when (screen) {
                    Screen.HOME -> HomeScreen(
                        pinnedAppsStore = pinnedAppsStore,
                        playEntrance = !homeEverShown,
                        onEntranceShown = { homeEverShown = true },
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

private val RazorbillFont = FontFamily(Font(R.font.space_grotesk))

@androidx.compose.runtime.Composable
private fun razorbillDarkColorScheme() = androidx.tv.material3.darkColorScheme(
    background = Color(0xFF0A0B0D),
    surface = Color(0xFF0A0B0D),
    onBackground = Color.White,
    onSurface = Color.White
)

enum class Screen {
    HOME, SETTINGS_MENU, SETTINGS_UPDATE, SETTINGS_GENERAL,
    SETTINGS_APPEARANCE, SETTINGS_APPS, SETTINGS_SYSTEM, SETTINGS_DIAGNOSTICS, SETTINGS_ABOUT
}

@androidx.compose.runtime.Composable
private fun RazorbillMark(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val sx = size.width / 200f
        val sy = size.height / 200f

        val left = Path().apply {
            moveTo(40f * sx, 95.7f * sy)
            cubicTo(52.8f * sx, 96.3f * sy, 65.6f * sx, 104.3f * sy, 78.4f * sx, 123.5f * sy)
        }
        val right = Path().apply {
            moveTo(99.2f * sx, 120.3f * sy)
            cubicTo(113.6f * sx, 113.9f * sy, 129.6f * sx, 99.5f * sy, 160f * sx, 77.1f * sy)
        }

        val leftMeasure = PathMeasure().apply { setPath(left, false) }
        val rightMeasure = PathMeasure().apply { setPath(right, false) }
        val leftLen = leftMeasure.length
        val rightLen = rightMeasure.length
        val totalLen = leftLen + rightLen

        val leftEnd = (progress * totalLen).coerceIn(0f, leftLen)
        val rightEnd = (progress * totalLen - leftLen).coerceIn(0f, rightLen)

        val leftPartial = Path()
        leftMeasure.getSegment(0f, leftEnd, leftPartial, true)
        val rightPartial = Path()
        rightMeasure.getSegment(0f, rightEnd, rightPartial, true)

        val strokeWidth = 9f * sx
        for (i in 3 downTo 1) {
            val glowStroke = Stroke(width = strokeWidth * (1 + i * 0.7f), cap = StrokeCap.Round)
            drawPath(leftPartial, color = Color.White.copy(alpha = 0.12f * i), style = glowStroke)
            drawPath(rightPartial, color = Color.White.copy(alpha = 0.12f * i), style = glowStroke)
        }
        val mainStroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        drawPath(leftPartial, color = Color.White, style = mainStroke)
        drawPath(rightPartial, color = Color.White, style = mainStroke)
    }
}

@androidx.compose.runtime.Composable
private fun SplashScreen(onFinished: () -> Unit) {
    val pathProgress = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val screenAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        pathProgress.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        textAlpha.animateTo(1f, tween(500))
        delay(1000)
        screenAlpha.animateTo(0f, tween(500))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = screenAlpha.value }
            .background(Color(0xFF06070A)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RazorbillMark(progress = pathProgress.value, modifier = Modifier.size(140.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Razorbill",
                fontFamily = RazorbillFont,
                fontWeight = FontWeight.Light,
                fontSize = 26.sp,
                letterSpacing = 6.sp,
                color = Color.White.copy(alpha = textAlpha.value)
            )
        }
    }
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

private fun currentDateText(): String {
    val fmt = java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.FRANCE)
    return fmt.format(java.util.Date()).replaceFirstChar { it.uppercase() }
}

@androidx.compose.runtime.Composable
private fun LiveClock() {
    var time by remember { mutableStateOf(currentTimeText()) }
    var dateText by remember { mutableStateOf(currentDateText()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val now = currentTimeText()
            if (now != time) {
                time = now
                dateText = currentDateText()
            }
        }
    }
    Column {
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
                fontFamily = RazorbillFont,
                fontSize = 56.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.96f)
            )
        }
        Text(
            text = dateText,
            fontFamily = RazorbillFont,
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.55f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@androidx.compose.runtime.Composable
private fun AppCard(
    app: AppInfo,
    size: Dp,
    focusRequester: FocusRequester?,
    entranceDelayMs: Long = 0,
    onLaunch: () -> Unit,
    onTogglePin: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "cardScale")
    val iconBitmap = remember(app.packageName) { app.icon.toBitmap(width = 256, height = 256).asImageBitmap() }
    val shineAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "shineAlpha")
    val shineOffset by animateDpAsState(if (focused) 0.dp else -size, tween(550), label = "shineOffset")
    val accent = Color(0xFF6FD3E8)

    val entrance = remember { Animatable(if (entranceDelayMs > 0) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (entranceDelayMs > 0) {
            delay(entranceDelayMs)
            entrance.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(size + 24.dp)
            .graphicsLayer {
                alpha = entrance.value
                scaleX = 0.7f + 0.3f * entrance.value
                scaleY = 0.7f + 0.3f * entrance.value
            }
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .then(
                    if (focused) Modifier.border(2.dp, accent, RoundedCornerShape(18.dp)) else Modifier
                )
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onLaunch,
                    onLongClick = onTogglePin
                ),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                bitmap = iconBitmap,
                contentDescription = app.label,
                modifier = Modifier.size(size * 0.62f)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = shineOffset)
                    .alpha(shineAlpha)
                    .background(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent)
                        )
                    )
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = app.label,
            fontFamily = RazorbillFont,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center
        )
    }
}

@androidx.compose.runtime.Composable
private fun HomeScreen(
    pinnedAppsStore: PinnedAppsStore,
    playEntrance: Boolean,
    onEntranceShown: () -> Unit,
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
    LaunchedEffect(Unit) { onEntranceShown() }

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
                                entranceDelayMs = if (playEntrance) index * 40L else 0,
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
                        entranceDelayMs = if (playEntrance) (pinnedApps.size + index) * 40L else 0,
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
            Text(text = title, fontFamily = RazorbillFont, fontSize = 30.sp, fontWeight = FontWeight.Bold)
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
