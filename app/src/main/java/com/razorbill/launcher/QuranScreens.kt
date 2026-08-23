package com.razorbill.launcher

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.tv.material3.Button
import androidx.tv.material3.Text

@Composable
fun QuranListScreen(
    onSelectSurah: (Int) -> Unit,
    onBack: () -> Unit
) {
    val accent = LocalAccentColor.current
    val firstFocusRequester = remember { FocusRequester() }
    BackHandler(onBack = onBack)

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
            Spacer(Modifier.height(24.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                itemsIndexed(SURAHS) { index, surah ->
                    val interaction = remember { MutableInteractionSource() }
                    val focused by interaction.collectIsFocusedAsState()
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
                            Text(surah.nameTransliterated, color = Color.White, fontWeight = FontWeight.SemiBold)
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
    onBack: () -> Unit
) {
    val accent = LocalAccentColor.current
    val surah = remember(surahNumber) { SURAHS.first { it.number == surahNumber } }
    var statusMessage by remember { mutableStateOf("Aucune piste trouvée — branche ta clé USB avec les récitations.") }
    val backButtonFocusRequester = remember { FocusRequester() }
    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground()
        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${surah.number}".padStart(3, '0') + " · " + surah.nameTransliterated,
                fontFamily = RazorbillFont,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(surah.nameArabic, color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
            Spacer(Modifier.height(28.dp))

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
                            statusMessage = "Aucune piste trouvée — branche ta clé USB avec les récitations."
                            true
                        } else false
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("▶", fontSize = 28.sp, color = Color.White)
            }

            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.12f))
            )
            Spacer(Modifier.height(16.dp))
            Text(statusMessage, color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)

            Spacer(Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Text(
                    "Traduction en direct : disponible une fois une piste réellement lue.",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(28.dp))
            Button(onClick = onBack) { Text("Retour") }
        }
    }
}
