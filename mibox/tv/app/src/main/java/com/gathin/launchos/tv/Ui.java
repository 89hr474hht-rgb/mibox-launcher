package com.gathin.launchos.tv;

import android.content.Context;
import android.view.View;

/** Small helpers for the views: dp, initials for a card without a picture, the meta line. Pure, any thread. */
final class Ui {
    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** An episode's title, or "Épisode 3" when the panel gave none. */
    static String episodeTitle(Context c, Item episode) {
        return episode.title.isEmpty() ? c.getString(R.string.episode_name, episode.num) : episode.title;
    }

    /** A season's name, or "Saison 2" when the panel gave none. */
    static String seasonName(Context c, Xtream.Season s) {
        return s.name.isEmpty() ? c.getString(R.string.season_name, s.number) : s.name;
    }

    /** One or two letters for a card without a picture. */
    static String initials(String name) {
        StringBuilder b = new StringBuilder();
        for (String w : name.trim().split("\\s+")) {
            if (!w.isEmpty() && Character.isLetterOrDigit(w.charAt(0))) b.appendCodePoint(w.codePointAt(0));
            if (b.length() >= 2) break;
        }
        return b.toString().toUpperCase(java.util.Locale.FRANCE);
    }

    /** "2019 · 1 h 52 · Action · ★ 7,4": the parts that are known, in that order. */
    static String meta(Object... parts) {
        StringBuilder b = new StringBuilder();
        for (Object p : parts) {
            if (p == null) continue;
            String s = String.valueOf(p).trim();
            if (s.isEmpty() || s.equals("0")) continue;
            if (b.length() > 0) b.append("  ·  ");
            b.append(s);
        }
        return b.toString();
    }

    static String rating(float r) {
        return r <= 0 ? "" : String.format(java.util.Locale.FRANCE, "★ %.1f", r);
    }

    /** Images are cut to their frame's rounded corners. */
    static void roundClip(View frame) {
        frame.setClipToOutline(true);
    }
}
