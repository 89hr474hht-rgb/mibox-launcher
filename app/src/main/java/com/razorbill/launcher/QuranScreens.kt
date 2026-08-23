package com.razorbill.launcher

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun hasAllFilesAccess(): Boolean =
    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R || Environment.isExternalStorageManager()

@Composable
private fun rememberAllFilesAccessGranted(): Boolean {
    var granted by remember { mutableStateOf(hasAllFilesAccess()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = hasAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted
}

@Composable
fun QuranListScreen(
    onSelectSurah: (Int) -> Unit,
    onBack: () -> Unit
) {
    val accent = LocalAccentColor.current
    val context = LocalContext.current
    val hasAccess = rememberAllFilesAccessGranted()
    val availableTracks = remember(hasAccess) {
        if (hasAccess) QuranLibrary.scanForTracks(context).keys else emptySet()
    }
    val firstFocusRequester = remember { FocusRequester() }
    val grantButtonFocusRequester = remember { FocusRequester() }
    BackHandler(onBack = onBack)

    if (!hasAccess) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedBackground()
            Column(
                modifier = Modifier.fillMaxSize().padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Autorisation nécessaire",
                    fontFamily = RazorbillFont,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Razorbill a besoin d'accéder au stockage (clé USB) pour trouver tes pistes du Coran.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    modifier = Modifier.focusRequester(grantButtonFocusRequester),
                    onClick = {
                        val intent = Intent(
                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                ) { Text("Autoriser l'accès aux fichiers") }
                Spacer(Modifier.height(20.dp))
                Button(onClick = onBack) { Text("Retour") }
            }
            LaunchedEffect(Unit) { grantButtonFocusRequester.requestFocus() }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground()
        Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
            Text(
                text = "Coran — 114 sourates",
                fontFamily = RazorbillFont,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = if (availableTracks.isEmpty())
                    "Aucune piste trouvée — branche une clé USB avec un dossier Coran."
                else "${availableTracks.size} piste(s) trouvée(s)",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(24.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                itemsIndexed(SURAHS) { index, surah ->
                    val interaction = remember { MutableInteractionSource() }
                    val focused by interaction.collectIsFocusedAsState()
                    val hasTrack = surah.number in availableTracks
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (focused) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                            .then(if (focused) Modifier.border(2.dp, accent, RoundedCornerShape(10.dp)) else Modifier)
                            .then(if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier)
                            .focusable(interactionSource = interaction)
                            .onKeyEvent { e ->
                                if ((e.key == Key.DirectionCenter || e.key == Key.Enter) && e.type == KeyEventType.KeyUp) {
                                    onSelectSurah(surah.number); true
                                } else false
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("${surah.number}".padStart(3, '0'), color = Color.White.copy(alpha = 0.4f))
                            Text(
                                surah.nameTransliterated,
                                color = if (hasTrack) Color.White else Color.White.copy(alpha = 0.35f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(surah.nameArabic, color = Color.White.copy(alpha = 0.6f))
                            Text("${surah.ayahCount} versets", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuranPlayerScreen(
    surahNumber: Int,
    quranStore: QuranStore,
    onBack: () -> Unit
) {
    val accent = LocalAccentColor.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val surah = remember(surahNumber) { SURAHS.first { it.number == surahNumber } }
    val trackFile = remember(surahNumber) { QuranLibrary.scanForTracks(context)[surahNumber] }

    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0) }
    var durationMs by remember { mutableStateOf(1) }
    var statusMessage by remember {
        mutableStateOf(
            if (trackFile != null) "Prêt à lire."
            else "Aucune piste trouvée — branche ta clé USB avec les récitations."
        )
    }

    var translation by remember(surahNumber) { mutableStateOf<List<Ayah>?>(null) }
    var translationLoading by remember(surahNumber) { mutableStateOf(true) }
    val translationListState = rememberLazyListState()

    val mediaPlayer = remember { MediaPlayer() }
    val backButtonFocusRequester = remember { FocusRequester() }
    BackHandler(onBack = onBack)

    LaunchedEffect(surahNumber) {
        translationLoading = true
        translation = QuranTranslationRepository.getTranslation(context, surahNumber)
        translationLoading = false
    }

    val ayahBoundaries = remember(translation) {
        translation?.let { ayahs ->
            val totalChars = ayahs.sumOf { it.text.length }.coerceAtLeast(1)
            var cumulative = 0
            ayahs.map { ayah ->
                cumulative += ayah.text.length
                cumulative.toFloat() / totalChars
            }
        }
    }
    val currentAyahIndex = remember(positionMs, durationMs, ayahBoundaries) {
        val boundaries = ayahBoundaries
        if (boundaries.isNullOrEmpty()) 0
        else {
            val progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            val idx = boundaries.indexOfFirst { it >= progress }
            if (idx == -1) boundaries.size - 1 else idx
        }
    }
    LaunchedEffect(currentAyahIndex) {
        if (!translation.isNullOrEmpty()) {
            translationListState.animateScrollToItem(maxOf(0, currentAyahIndex - 1))
        }
    }

    LaunchedEffect(surahNumber, trackFile) {
        if (trackFile == null) return@LaunchedEffect
        try {
            val savedPositionMs = quranStore.positionFor(surahNumber).first()
            withContext(Dispatchers.IO) {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(trackFile.absolutePath)
                mediaPlayer.prepare()
            }
            durationMs = mediaPlayer.duration.coerceAtLeast(1)
            if (savedPositionMs > 0 && savedPositionMs < durationMs) {
                mediaPlayer.seekTo(savedPositionMs.toInt())
                positionMs = savedPositionMs.toInt()
            }
            statusMessage = "Prêt à lire."
        } catch (e: Exception) {
            statusMessage = "Impossible de lire ce fichier : ${e.message}"
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = try { mediaPlayer.currentPosition } catch (_: Exception) { positionMs }
            if (!mediaPlayer.isPlaying) {
                isPlaying = false
            }
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            scope.launch {
                if (trackFile != null) {
                    quranStore.savePosition(surahNumber, positionMs.toLong())
                }
            }
            mediaPlayer.release()
        }
    }

    fun togglePlay() {
        if (trackFile == null) {
            statusMessage = "Aucune piste trouvée — branche ta clé USB avec les récitations."
            return
        }
        if (isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
            scope.launch { quranStore.savePosition(surahNumber, positionMs.toLong()) }
        } else {
            mediaPlayer.start()
            isPlaying = true
        }
    }

    fun formatMs(ms: Int): String {
        val totalSec = ms / 1000
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground()
        Column(modifier = Modifier.fillMaxSize().padding(40.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${surah.number}".padStart(3, '0') + " · " + surah.nameTransliterated,
                        fontFamily = RazorbillFont,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(surah.nameArabic, color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
                }
                Button(onClick = onBack) { Text("Retour") }
            }

            Spacer(Modifier.height(28.dp))

            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.weight(0.42f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(2.dp, accent, RoundedCornerShape(999.dp))
                            .focusRequester(backButtonFocusRequester)
                            .focusable()
                            .onKeyEvent { e ->
                                if ((e.key == Key.DirectionCenter || e.key == Key.Enter) && e.type == KeyEventType.KeyUp) {
                                    togglePlay(); true
                                } else false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (isPlaying) "❙❙" else "▶", fontSize = 28.sp, color = Color.White)
                    }

                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f))
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("${formatMs(positionMs)} / ${formatMs(durationMs)}", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(statusMessage, color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, textAlign = TextAlign.Center)
                }

                Spacer(Modifier.width(28.dp))

                Column(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Text(
                        "Traduction (français)",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Défilement approximatif — pas de minutage verset par verset pour cet enregistrement.",
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    when {
                        translationLoading -> Text(
                            "Chargement…",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 13.sp
                        )
                        translation.isNullOrEmpty() -> Text(
                            "Traduction indisponible — une connexion Internet est nécessaire au premier chargement.",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 13.sp
                        )
                        else -> LazyColumn(
                            state = translationListState,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            itemsIndexed(translation!!) { index, ayah ->
                                val active = index == currentAyahIndex
                                Text(
                                    "${ayah.numberInSurah}. ${ayah.text}",
                                    color = if (active) Color.White else Color.White.copy(alpha = 0.45f),
                                    fontSize = if (active) 15.sp else 14.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
