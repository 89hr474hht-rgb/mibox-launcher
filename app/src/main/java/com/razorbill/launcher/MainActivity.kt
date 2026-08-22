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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.withFrameNanos
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
                var settingsSection by remember { mutableStateOf(SettingsSection.UPDATE) }
                if (showSplash) {
                    SplashScreen(onFinished = { showSplash = false })
                } else when (screen) {
                    Screen.HOME -> HomeScreen(
                        pinnedAppsStore = pinnedAppsStore,
                        playEntrance = !homeEverShown,
                        onEntranceShown = { homeEverShown = true },
                        onOpenSettings = { screen = Screen.SETTINGS },
                        onExitToSystem = { moveTaskToBack(true) }
                    )
                    else -> SettingsArea(
                        section = settingsSection,
                        isHomeRoleHeldInitially = isHomeRoleHeld(),
                        onSelectSection = { settingsSection = it },
                        onBackToHome = { screen = Screen.HOME },
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

enum class Screen { HOME, SETTINGS }

enum class SettingsSection(val label: String) {
    GENERAL("Général"), APPEARANCE("Apparence"), APPS("Applications"),
    UPDATE("Mises à jour"), SYSTEM("Système"), DIAGNOSTICS("Diagnostics"), ABOUT("À propos")
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

private data class Star(val x: Float, val y: Float, val radius: Float, val speed: Float, val phase: Float)
private data class Orb(val baseX: Float, val baseY: Float, val size: Float, val color: Color, val alpha: Float, val period: Float, val rangeX: Float, val rangeY: Float, val seed: Float)
private data class ShootingStar(val startX: Float, val startY: Float, val angleDeg: Float, val length: Float, val period: Float, val phaseOffset: Float)

private fun generateStars(n: Int): List<Star> {
    var seed = 42
    fun rand(): Float { seed = (seed * 9301 + 49297) % 233280; return seed / 233280f }
    return List(n) {
        Star(
            x = rand(), y = rand(),
            radius = 1f + rand() * 2.2f,
            speed = 1.2f + rand() * 2.2f,
            phase = rand() * 6.28f
        )
    }
}

private fun generateOrbs(n: Int): List<Orb> {
    var seed = 7
    fun rand(): Float { seed = (seed * 9301 + 49297) % 233280; return seed / 233280f }
    val colors = listOf(Color(0xFF6FD3E8), Color(0xFF3A6FF0), Color(0xFF7A52D6))
    return List(n) { i ->
        Orb(
            baseX = rand(), baseY = rand() * 0.85f,
            size = 60f + rand() * 90f,
            color = colors[i % colors.size],
            alpha = 0.12f + rand() * 0.12f,
            period = 26f + rand() * 26f,
            rangeX = 60f + rand() * 120f,
            rangeY = 40f + rand() * 90f,
            seed = rand() * 6.28f
        )
    }
}

private fun generateShootingStars(): List<ShootingStar> = listOf(
    ShootingStar(0.08f, 0.16f, 25f, 220f, 4.6f, 0f),
    ShootingStar(0.72f, 0.6f, 20f, 260f, 5.4f, 2.4f),
    ShootingStar(0.45f, 0.3f, 30f, 200f, 6.1f, 3.7f)
)

@androidx.compose.runtime.Composable
private fun AnimatedBackground(modifier: Modifier = Modifier) {
    val stars = remember { generateStars(55) }
    val orbs = remember { generateOrbs(7) }
    val shootingStars = remember { generateShootingStars() }
    var timeSec by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        var start = -1L
        while (true) {
            withFrameNanos { now ->
                if (start < 0) start = now
                timeSec = (now - start) / 1_000_000_000f
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(color = Color(0xFF06070A))

        // Nebula: slow, more pronounced drift.
        val nebulaAngle = timeSec * 0.24f
        val ndx = cos(nebulaAngle) * w * 0.08f
        val ndy = sin(nebulaAngle) * h * 0.06f
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFF6FD3E8).copy(alpha = 0.5f), Color.Transparent), center = Offset(w * 0.2f + ndx, h * 0.2f + ndy), radius = w * 0.38f),
            radius = w * 0.38f, center = Offset(w * 0.2f + ndx, h * 0.2f + ndy)
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFF3A6FF0).copy(alpha = 0.4f), Color.Transparent), center = Offset(w * 0.8f - ndx, h * 0.75f - ndy), radius = w * 0.32f),
            radius = w * 0.32f, center = Offset(w * 0.8f - ndx, h * 0.75f - ndy)
        )

        // Orbs: soft particles slowly floating along their own loop.
        orbs.forEach { orb ->
            val angle = (timeSec / orb.period) * 2f * PI.toFloat() + orb.seed
            val ox = orb.baseX * w + cos(angle) * orb.rangeX
            val oy = orb.baseY * h + sin(angle) * orb.rangeY
            drawCircle(
                brush = Brush.radialGradient(listOf(orb.color.copy(alpha = orb.alpha), Color.Transparent), center = Offset(ox, oy), radius = orb.size),
                radius = orb.size, center = Offset(ox, oy)
            )
        }

        // Stars: twinkle + slow overall drift.
        val driftX = sin(timeSec * 0.05f) * 26f
        val driftY = cos(timeSec * 0.04f) * 18f
        stars.forEach { star ->
            val tw = (sin(timeSec * star.speed + star.phase) * 0.5f + 0.5f)
            val a = 0.2f + tw * 0.8f
            val r = star.radius * (0.7f + tw)
            drawCircle(color = Color.White.copy(alpha = a), radius = r, center = Offset(star.x * w + driftX, star.y * h + driftY))
        }

        // Shooting stars: periodic streak with a fading tail.
        shootingStars.forEach { s ->
            val cycle = (timeSec + s.phaseOffset) % s.period
            val active = cycle < 0.9f
            if (active) {
                val progress = cycle / 0.9f
                val envelope = if (progress < 0.15f) progress / 0.15f else (1f - (progress - 0.15f) / 0.85f)
                val rad = s.angleDeg * PI.toFloat() / 180f
                val sx = s.startX * w + cos(rad) * s.length * progress
                val sy = s.startY * h + sin(rad) * s.length * progress
                val tx = sx - cos(rad) * s.length * 0.25f
                val ty = sy - sin(rad) * s.length * 0.25f
                drawLine(
                    brush = Brush.linearGradient(listOf(Color.White.copy(alpha = envelope.coerceIn(0f, 1f)), Color.Transparent), start = Offset(sx, sy), end = Offset(tx, ty)),
                    start = Offset(sx, sy),
                    end = Offset(tx, ty),
                    strokeWidth = 2f
                )
                drawCircle(color = Color.White.copy(alpha = envelope.coerceIn(0f, 1f)), radius = 2.4f, center = Offset(sx, sy))
            }
        }

        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF06070A).copy(alpha = 0.15f),
                    Color(0xFF06070A).copy(alpha = 0.45f),
                    Color(0xFF06070A).copy(alpha = 0.88f)
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
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onLaunch: () -> Unit,
    onTogglePin: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { onFocusChanged?.invoke(focused) }
    val iconBitmap = remember(app.packageName) { app.icon.toBitmap(width = 256, height = 256).asImageBitmap() }
    val shineAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "shineAlpha")
    val shineOffset by animateDpAsState(if (focused) 0.dp else -size, tween(550), label = "shineOffset")
    val accent = Color(0xFF6FD3E8)

    val holdProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var justCompletedHold by remember { mutableStateOf(false) }

    val focusScale by animateFloatAsState(if (focused) 1.1f else 1f, label = "cardFocusScale")
    val scale = focusScale * (1f + holdProgress.value * 0.12f)

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
      Box(modifier = Modifier.size(size + 16.dp), contentAlignment = Alignment.Center) {
        if (holdProgress.value > 0f) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val stroke = 4.dp.toPx()
                val ringRadius = (this.size.minDimension - stroke) / 2f
                drawArc(
                    color = Color.White.copy(alpha = 0.15f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    topLeft = Offset((this.size.width - ringRadius * 2) / 2f, (this.size.height - ringRadius * 2) / 2f),
                    size = androidx.compose.ui.geometry.Size(ringRadius * 2, ringRadius * 2)
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * holdProgress.value,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    topLeft = Offset((this.size.width - ringRadius * 2) / 2f, (this.size.height - ringRadius * 2) / 2f),
                    size = androidx.compose.ui.geometry.Size(ringRadius * 2, ringRadius * 2)
                )
            }
        }
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
                .focusable(interactionSource = interaction)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.key != Key.DirectionCenter && keyEvent.key != Key.Enter) return@onKeyEvent false
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            if (holdJob?.isActive != true) {
                                holdJob = scope.launch {
                                    val remainingMs = ((1f - holdProgress.value) * 900).toInt().coerceAtLeast(1)
                                    holdProgress.animateTo(1f, tween(remainingMs, easing = LinearEasing))
                                    if (holdProgress.value >= 0.999f) {
                                        justCompletedHold = true
                                        onTogglePin()
                                        delay(280)
                                        holdProgress.snapTo(0f)
                                    }
                                }
                            }
                            true
                        }
                        KeyEventType.KeyUp -> {
                            val wasCompleted = justCompletedHold
                            justCompletedHold = false
                            if (!wasCompleted) {
                                holdJob?.cancel()
                                if (holdProgress.value < 0.05f) {
                                    onLaunch()
                                } else {
                                    val v = holdProgress.value
                                    scope.launch {
                                        holdProgress.animateTo(0f, tween((v * 500).toInt().coerceAtLeast(80)))
                                    }
                                }
                            }
                            true
                        }
                        else -> false
                    }
                },
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

    val shelfFirstFocusRequester = remember { FocusRequester() }
    val gridFirstFocusRequester = remember { FocusRequester() }
    var contentVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableStateOf(0) }

    LaunchedEffect(allApps.size) {
        if (allApps.isNotEmpty()) {
            if (pinnedApps.isNotEmpty()) shelfFirstFocusRequester.requestFocus()
            else gridFirstFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(Unit) { onEntranceShown() }
    LaunchedEffect(interactionTick) {
        contentVisible = true
        delay(10_000)
        contentVisible = false
    }

    BackHandler(onBack = onExitToSystem)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { interactionTick++; false }
    ) {
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
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(380)),
                    exit = slideOutVertically(tween(380, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(280))
                ) {
                    Button(onClick = onOpenSettings) { Text("⚙") }
                }
            }

            AnimatedVisibility(
                visible = contentVisible,
                enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(380)),
                exit = slideOutVertically(tween(380, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(280))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    if (pinnedApps.isNotEmpty()) {
                        val accent = Color(0xFF6FD3E8)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(20.dp, RoundedCornerShape(28.dp), ambientColor = accent, spotColor = accent)
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF17323A).copy(alpha = 0.6f), Color(0xFF0D1517).copy(alpha = 0.45f))
                                    )
                                )
                                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color.White.copy(alpha = 0.25f))
                                )
                                Row(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    pinnedApps.forEachIndexed { index, app ->
                                        AppCard(
                                            app = app,
                                            size = 104.dp,
                                            focusRequester = if (index == 0) shelfFirstFocusRequester else null,
                                            entranceDelayMs = if (playEntrance) index * 40L else 0,
                                            onLaunch = { context.startActivity(app.launchIntent) },
                                            onTogglePin = { scope.launch { pinnedAppsStore.togglePin(app.packageName) } }
                                        )
                                    }
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
                                focusRequester = if (index == 0) gridFirstFocusRequester else null,
                                entranceDelayMs = if (playEntrance) (pinnedApps.size + index) * 40L else 0,
                                onLaunch = { context.startActivity(app.launchIntent) },
                                onTogglePin = { scope.launch { pinnedAppsStore.togglePin(app.packageName) } }
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SettingsArea(
    section: SettingsSection,
    isHomeRoleHeldInitially: Boolean,
    onSelectSection: (SettingsSection) -> Unit,
    onBackToHome: () -> Unit,
    onRequestHomeRole: ((Boolean) -> Unit) -> Unit,
    onRequestNotificationPermission: ((Boolean) -> Unit) -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onOpenDeveloperOptions: () -> Unit
) {
    BackHandler(onBack = onBackToHome)
    val accent = Color(0xFF6FD3E8)
    val activeRowFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { activeRowFocusRequester.requestFocus() }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxSize()
                .padding(start = 48.dp, top = 48.dp, bottom = 48.dp)
        ) {
            val logoInteraction = remember { MutableInteractionSource() }
            val logoFocused by logoInteraction.collectIsFocusedAsState()
            Text(
                text = "← Razorbill",
                fontFamily = RazorbillFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = Color.White.copy(alpha = if (logoFocused) 0.9f else 0.4f),
                modifier = Modifier
                    .padding(bottom = 18.dp, start = 18.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (logoFocused) Modifier.border(2.dp, accent, RoundedCornerShape(10.dp)) else Modifier)
                    .focusable(interactionSource = logoInteraction)
                    .onKeyEvent { e ->
                        if ((e.key == Key.DirectionCenter || e.key == Key.Enter) && e.type == KeyEventType.KeyUp) {
                            onBackToHome(); true
                        } else false
                    }
                    .padding(6.dp)
            )
            SettingsSection.entries.forEach { s ->
                val active = s == section
                val rowInteraction = remember(s) { MutableInteractionSource() }
                val rowFocused by rowInteraction.collectIsFocusedAsState()
                Text(
                    text = s.label,
                    fontFamily = RazorbillFont,
                    fontSize = 16.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active || rowFocused) Color.White else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                        .then(if (rowFocused) Modifier.border(2.dp, accent, RoundedCornerShape(12.dp)) else Modifier)
                        .then(if (active) Modifier.focusRequester(activeRowFocusRequester) else Modifier)
                        .focusable(interactionSource = rowInteraction)
                        .onKeyEvent { e ->
                            if ((e.key == Key.DirectionCenter || e.key == Key.Enter) && e.type == KeyEventType.KeyUp) {
                                onSelectSection(s); true
                            } else false
                        }
                        .padding(horizontal = 18.dp, vertical = 13.dp)
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(56.dp)) {
            AnimatedContent(targetState = section, label = "settingsDetail") { s ->
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text = s.label, fontFamily = RazorbillFont, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(26.dp))
                    when (s) {
                        SettingsSection.UPDATE -> UpdateSection()
                        SettingsSection.SYSTEM -> SystemSection(
                            isHomeRoleHeldInitially,
                            onRequestHomeRole,
                            onRequestNotificationPermission,
                            onOpenUnknownSourcesSettings,
                            onOpenDeveloperOptions
                        )
                        SettingsSection.ABOUT -> AboutSection()
                        else -> Text("Contenu à définir.", color = Color.White.copy(alpha = 0.45f))
                    }
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
