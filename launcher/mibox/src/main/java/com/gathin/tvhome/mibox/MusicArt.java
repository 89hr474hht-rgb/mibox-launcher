package com.gathin.tvhome.mibox;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/**
 * The colours of a collection, since music files on a drive seldom carry a picture: each collection gets its own hue
 * from its name, always the same, for its cover (a gradient) and the player's glow. Favourites are rose.
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

    /** A rounded square from a light to a deep tone of the hue. */
    static GradientDrawable cover(float hue, float radiusPx) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                color(hue, 0.50f, 0.78f, 255), color((hue + 35) % 360, 0.62f, 0.36f, 255)});
        g.setCornerRadius(radiusPx);
        return g;
    }

    /** The player's glow: the hue at the top left, fading to the black page. */
    static GradientDrawable glow(float hue, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[]{color(hue, 0.65f, 0.42f, 150), color(hue, 0.65f, 0.25f, 50), 0x00000000});
        g.setGradientCenter(0.18f, 0f);
        g.setGradientRadius(Math.max(1f, radiusPx));
        return g;
    }
}
