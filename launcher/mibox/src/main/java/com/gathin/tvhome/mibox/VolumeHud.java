package com.gathin.tvhome.mibox;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;

/**
 * Puts the volume bar on screen in an accessibility overlay, the window type an accessibility service may add
 * without the "draw over other apps" permission. The window exists only while the bar shows: it is added on the
 * first key press and removed once the bar has folded away, so a hidden bar costs no surface memory.
 */
final class VolumeHud {
    static final long HIDE_AFTER_MS = 1800;
    /** A push past the end that is not followed by another within this time lets the bar spring back. */
    static final long RELEASE_AFTER_MS = 170;
    private static final int WINDOW_W_DP = 460, WINDOW_H_DP = 110;

    private final Context context;
    private final WindowManager windows;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private VolumeHudView view;
    private boolean attached;
    private int lastLevel = -1;
    private boolean lastMuted;

    private final Runnable hide = () -> {
        if (view != null) view.close(reducedMotion());
    };
    private final Runnable release = () -> {
        if (view != null) view.release(reducedMotion());
    };

    VolumeHud(Context serviceContext) {
        context = serviceContext;
        windows = serviceContext.getSystemService(WindowManager.class);
    }

    /** One volume step: what the level was, what it is now, and the speaker's name (empty for none). */
    void show(int before, boolean mutedBefore, int level, boolean muted, int max, String name, int push) {
        if (windows == null) return;
        if (view == null) {
            view = new VolumeHudView(context);
            view.setOnClosed(this::detach);
        }
        if (!attached) {
            view.prime(VolumeLevels.fraction(before, max, mutedBefore), VolumeLevels.waves(before, max, mutedBefore));
            try {
                windows.addView(view, params());
                attached = true;
            } catch (RuntimeException e) {
                return;   // the overlay was refused (the service is going away): the volume still changed
            }
        }
        boolean moved = lastLevel != level || lastMuted != muted;
        lastLevel = level;
        lastMuted = muted;
        view.update(VolumeLevels.fraction(level, max, muted), VolumeLevels.waves(level, max, muted), name, push,
                moved, reducedMotion());
        handler.removeCallbacks(release);
        if (push != 0) handler.postDelayed(release, RELEASE_AFTER_MS);
        handler.removeCallbacks(hide);
        handler.postDelayed(hide, HIDE_AFTER_MS);
    }

    void destroy() {
        handler.removeCallbacks(hide);
        handler.removeCallbacks(release);
        detach();
        view = null;
    }

    private void detach() {
        if (view != null) view.stop();
        if (!attached) return;
        attached = false;
        lastLevel = -1;
        try {
            windows.removeViewImmediate(view);
        } catch (RuntimeException ignored) {
            // Already gone with the service's window token.
        }
    }

    private WindowManager.LayoutParams params() {
        float dp = context.getResources().getDisplayMetrics().density;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                Math.round(WINDOW_W_DP * dp), Math.round(WINDOW_H_DP * dp),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.windowAnimations = 0;
        lp.setTitle("MiboxVolumeHud");
        return lp;
    }

    /** Animations off in Android's developer settings: the bar appears and goes without motion. */
    private boolean reducedMotion() {
        return Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }
}
