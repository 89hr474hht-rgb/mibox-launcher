package com.gathin.launchos.design;

import android.animation.ValueAnimator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/** The only curves and durations the two apps animate with. */
public final class Motion {
    /** Things arriving or settling: fast start, long soft end. */
    public static final Interpolator ENTER = new PathInterpolator(0.2f, 0f, 0f, 1f);
    /** Things moving from one place to another on screen. */
    public static final Interpolator MOVE = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    /** Focus, presses, small state changes. */
    public static final long SHORT = 120;
    /** Panels, rows and pages appearing. */
    public static final long MEDIUM = 220;
    /** A whole screen changing, the start-up mark settling. */
    public static final long LONG = 360;

    private Motion() {
    }

    /** False when the user switched Android's animations off: then things change at once. */
    public static boolean on() {
        return ValueAnimator.areAnimatorsEnabled();
    }
}
