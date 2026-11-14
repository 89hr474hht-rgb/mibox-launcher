package com.gathin.launchos.tv;

import android.content.Context;

import java.util.Locale;

/** Small texts built from numbers for the screens: how long ago, how many bytes. Main thread or any, no state. */
final class Formats {
    private Formats() {
    }

    /** "à l'instant", "il y a 12 min", "il y a 3 h". */
    static String ago(Context c, long at) {
        long min = (System.currentTimeMillis() - at) / 60_000;
        if (min < 1) return c.getString(R.string.ago_now);
        if (min < 60) return c.getString(R.string.ago_minutes, (int) min);
        return c.getString(R.string.ago_hours, (int) (min / 60));
    }

    /** "512 Ko" or "12,4 Mo". */
    static String size(Context c, long bytes) {
        if (bytes < 1024 * 1024) return c.getString(R.string.size_kb, (int) (bytes / 1024));
        return c.getString(R.string.size_mb, String.format(Locale.FRANCE, "%.1f", bytes / (1024.0 * 1024)));
    }
}
