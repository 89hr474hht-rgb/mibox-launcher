package com.gathin.launchos.tv;

/**
 * The guide's arithmetic, kept apart so it can be tested on the JVM: where the window of hours starts, and where a
 * programme falls in it, in minutes from the window's start. Times are seconds since 1970.
 */
final class Timeline {
    /** The window starts on the half hour. */
    static final int STEP_MIN = 30;

    private Timeline() {
    }

    /** The half hour at or before now. */
    static long windowStart(long nowSec) {
        long step = STEP_MIN * 60L;
        return nowSec - Math.floorMod(nowSec, step);
    }

    /**
     * Where [start, end) falls in the window [from, from + minutes): {first minute, last minute}, cut to the
     * window, or null when it is outside or empty.
     */
    static float[] span(long start, long end, long from, int minutes) {
        long to = from + minutes * 60L;
        if (end <= start || end <= from || start >= to) return null;
        long a = Math.max(start, from), b = Math.min(end, to);
        return new float[]{(a - from) / 60f, (b - from) / 60f};
    }

    /** Minutes from the window's start to now, or -1 when now is outside the window. */
    static float nowAt(long nowSec, long from, int minutes) {
        float m = (nowSec - from) / 60f;
        return m < 0 || m > minutes ? -1f : m;
    }
}
