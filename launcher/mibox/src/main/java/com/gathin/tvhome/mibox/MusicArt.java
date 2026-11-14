package com.gathin.tvhome.mibox;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/**
 * The colours of a collection, since music files on a drive seldom carry a picture: each collection gets its own hue
 * from its name, always the same, for its cover. Favourites are rose.
 */
final class MusicArt {
    private MusicArt() {
    }

    static float hue(String key, String name) {
        if (MusicLibrary.FAVORITES.equals(key)) return 345f;
        String s = name == null ? "" : name;
        int h = 0;
        for (int i = 0; i < s.length(); i++) h = h * 31 + s.charAt(i);
        return (h & 0x7fffffff) % 360;
    }

    static int color(float hue, float sat, float value, int alpha) {
        return Color.HSVToColor(alpha, new float[]{hue, sat, value});
    }

    /** A rounded square in a muted, dark tone of the hue: it tells collections apart without pulling the eye. */
    static GradientDrawable cover(float hue, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color(hue, 0.42f, 0.40f, 255));
        g.setCornerRadius(radiusPx);
        return g;
    }
}
