package com.gathin.tvhome.mibox;

/** The volume bar's rules, apart from Android so they are tested on the JVM. */
final class VolumeLevels {
    /** How far the bar stretches at most when pushed past its end (8 % of its width). */
    static final float MAX_STRETCH = 0.08f;

    private VolumeLevels() {
    }

    /** The filled part of the bar, 0 to 1; a muted stream reads as empty. */
    static float fraction(int level, int max, boolean muted) {
        if (muted || max <= 0 || level <= 0) return 0f;
        return Math.min(1f, level / (float) max);
    }

    /** Waves on the speaker icon: 0 is the crossed-out speaker (muted or silent), then 1 or 2 waves. */
    static int waves(int level, int max, boolean muted) {
        float f = fraction(level, max, muted);
        if (f <= 0f) return 0;
        return f <= 0.5f ? 1 : 2;
    }

    /**
     * The bar's stretch while a key keeps pushing past the end: 1 at rest, then more with each press, less and
     * less, towards 1 + MAX_STRETCH. It resists like a rubber band instead of growing without end.
     */
    static float stretch(int pushes) {
        if (pushes <= 0) return 1f;
        return 1f + MAX_STRETCH * (1f - (float) Math.exp(-pushes / 1.8));
    }

    /**
     * +1 when a raise left the level at the top, -1 when a lower left it at the bottom: the press had nowhere
     * to go, so the bar stretches that way. 0 for a press that moved the level, or for mute.
     */
    static int push(boolean raise, int before, int after, int max) {
        if (before != after) return 0;
        if (raise && after >= max) return 1;
        if (!raise && after <= 0) return -1;
        return 0;
    }
}
