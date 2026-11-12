package com.gathin.tvhome.mibox;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * How the player shows lyrics, set in its lyrics options: the text size, the translation line shown or not, the
 * word-by-word highlight, and a shift for lyrics that come a little early or late (a Bluetooth speaker delays the
 * sound). Kept with the player's other settings.
 */
final class LyricsStyle {
    static final int SMALL = 0, MEDIUM = 1, LARGE = 2;
    /** The shift moves by this much, up to this far either way. */
    static final int OFFSET_STEP = 100, OFFSET_MAX = 3000;

    private static final String PREFS = "mibox_music";

    private LyricsStyle() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int size(Context c) {
        return Math.max(SMALL, Math.min(LARGE, prefs(c).getInt("lyrics_size", MEDIUM)));
    }

    static void setSize(Context c, int size) {
        prefs(c).edit().putInt("lyrics_size", size).apply();
    }

    static boolean translation(Context c) {
        return prefs(c).getBoolean("lyrics_translation", true);
    }

    static void setTranslation(Context c, boolean on) {
        prefs(c).edit().putBoolean("lyrics_translation", on).apply();
    }

    static boolean words(Context c) {
        return prefs(c).getBoolean("lyrics_words", true);
    }

    static void setWords(Context c, boolean on) {
        prefs(c).edit().putBoolean("lyrics_words", on).apply();
    }

    /** Positive shows the lyrics earlier. */
    static int offset(Context c) {
        return clampOffset(prefs(c).getInt("lyrics_offset", 0));
    }

    static void setOffset(Context c, int ms) {
        prefs(c).edit().putInt("lyrics_offset", clampOffset(ms)).apply();
    }

    static int clampOffset(int ms) {
        return Math.max(-OFFSET_MAX, Math.min(OFFSET_MAX, Math.round(ms / (float) OFFSET_STEP) * OFFSET_STEP));
    }

    /** "+0,3 s", "−1,2 s", or "0 s", in the box's language. */
    static String offsetText(int ms) {
        if (ms == 0) return "0 s";
        String n = String.format(java.util.Locale.getDefault(), "%.1f", Math.abs(ms) / 1000f);
        return (ms > 0 ? "+" : "−") + n + " s";
    }
}
