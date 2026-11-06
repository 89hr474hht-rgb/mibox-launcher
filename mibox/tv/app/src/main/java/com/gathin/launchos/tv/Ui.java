package com.gathin.launchos.tv;

import android.content.Context;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Small shared pieces of the look: the lift of a focused card, and dp. */
final class Ui {
    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** A focused card grows a little and rises above its neighbours. */
    static void lift(View v, float scale) {
        v.setOnFocusChangeListener((view, focused) -> {
            float s = focused ? scale : 1f;
            view.animate().scaleX(s).scaleY(s).translationZ(focused ? dp(view.getContext(), 10) : 0)
                    .setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
        });
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
