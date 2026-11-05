package com.gathin.tvhome.mibox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.junit.Test;

public class HomeExtrasTest {
    @Test
    public void androidVersionsReadPlainly() {
        assertEquals("Android 11 (API 30)", AppFacts.android(30));
        assertEquals("Android 12L (API 32)", AppFacts.android(32));
        assertEquals("Android 14 (API 34)", AppFacts.android(34));
        assertEquals("API 40", AppFacts.android(40));
    }

    @Test
    public void sizesInEachLanguage() {
        assertEquals("48,2 Mo", AppFacts.size(48_200_000L, Locale.FRANCE));
        assertEquals("48.2 MB", AppFacts.size(48_200_000L, Locale.US));
        assertEquals("612 Ko", AppFacts.size(612_400L, Locale.FRANCE));
        assertEquals("1,3 Go", AppFacts.size(1_300_000_000L, Locale.FRANCE));
        assertEquals("900 B", AppFacts.size(900L, Locale.US));
    }

    @Test
    public void whereAnAppCameFrom() {
        assertEquals(AppFacts.SOURCE_PLAY, AppFacts.source("com.android.vending", false, false));
        assertEquals(AppFacts.SOURCE_PLAY, AppFacts.source("com.android.vending", true, true));
        assertEquals(AppFacts.SOURCE_PREINSTALLED, AppFacts.source(null, true, false));
        assertEquals(AppFacts.SOURCE_MANUAL, AppFacts.source(null, false, false));
        assertEquals(AppFacts.SOURCE_MANUAL, AppFacts.source("com.google.android.packageinstaller", false, false));
        assertEquals(AppFacts.SOURCE_OTHER, AppFacts.source("com.example.store", false, false));
    }

    @Test
    public void versionsShowTheirCode() {
        assertEquals("1.4.2 (1402)", AppFacts.version("1.4.2", 1402));
        assertEquals("17", AppFacts.version(null, 17));
    }

    @Test
    public void glowColoursTurnAroundTheWheel() {
        assertEquals(0xFF0000, GlowColors.shift(0xFF0000, 0f, 1f));
        assertEquals(0x00FF00, GlowColors.shift(0xFF0000, 120f, 1f));
        assertEquals(0x0000FF, GlowColors.shift(0xFF0000, -120f, 1f));
        assertEquals(0x808080, GlowColors.shift(0x808080, 90f, 1f));   // a grey has no hue to turn
        assertEquals(0x800000, GlowColors.shift(0xFF0000, 0f, 0.5f) & 0xFF0000 | 0x000000);
        int brighter = GlowColors.shift(0x404040, 0f, 4f);
        assertTrue((brighter & 0xFF) <= 0xFF && (brighter & 0xFF) >= 0xFE);   // clamped at white
    }
}
