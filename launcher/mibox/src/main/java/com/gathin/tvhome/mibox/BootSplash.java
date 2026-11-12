package com.gathin.tvhome.mibox;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * LaunchOS's start-up screen: once per boot, the first time the home screen comes up after the box starts (never when
 * coming back to Home). On true black, the "lo" monogram writes itself in one line, its gold dot settles in, then the
 * monogram steps aside for the name. While the launcher loads behind, only the dot breathes. It stays at least
 * {@link #MIN_MS}, then fades away while the home screen settles into place. It is its own full-screen window, so the
 * remote's keys do nothing until it is gone; with Android's animations off it is shown still, for the same time.
 */
final class BootSplash extends Dialog {
    static final long MIN_MS = 3200;
    private static final String PREFS = "mibox_splash", KEY_BOOT = "boot";
    /** A first run of this version long after the box started (an update, say) is not a boot. */
    private static final long LATE_START_MS = 10 * 60_000L;
    /** Ease out, the way things come to rest. */
    private static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    /** Ease in and out, for the line being written. */
    private static final PathInterpolator WRITE = new PathInterpolator(0.55f, 0f, 0.25f, 1f);

    private static boolean shownThisProcess;
    private static BootSplash showing;

    private final Activity home;
    private final boolean still;
    private long start;
    private boolean begun, leaving;
    private Monogram mark;
    private ImageView name;
    private LinearLayout lockup;
    private ValueAnimator clock;
    private Runnable onGone;

    private BootSplash(Activity home) {
        super(home, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        this.home = home;
        still = Settings.Global.getFloat(home.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }

    /** True for the first home screen of this boot; remembers the boot, so it is true once. */
    static boolean firstHomeOfBoot(Context context) {
        if (shownThisProcess) return false;
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean known = p.contains(KEY_BOOT);
        int seen = p.getInt(KEY_BOOT, Integer.MIN_VALUE);
        if (boot != -1 && boot == seen) return false;
        p.edit().putInt(KEY_BOOT, boot).apply();
        long up = SystemClock.elapsedRealtime();
        if (boot == -1 || !known) return up < LATE_START_MS;   // no boot counter, or first run of this version
        return true;
    }

    /** Shows the start-up screen over the home screen; then runs onGone once it has faded out. */
    static void show(Activity home, Runnable onGone) {
        shownThisProcess = true;
        BootSplash s = new BootSplash(home);
        s.onGone = onGone;
        showing = s;
        s.show();
    }

    static boolean isShowing(Activity home) {
        BootSplash s = showing;
        return s != null && s.home == home && s.isShowing();
    }

    /** The home screen is going away: close the start-up screen with it, at once. */
    static void dismissFor(Activity home) {
        BootSplash s = showing;
        if (s == null || s.home != home) return;
        s.onGone = null;
        if (s.clock != null) s.clock.cancel();
        if (s.isShowing()) {
            try {
                s.dismiss();
            } catch (IllegalArgumentException ignored) {
                // The window was already gone with its activity.
            }
        }
        showing = null;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Context c = getContext();
        float dp = c.getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(c);
        lockup = new LinearLayout(c);
        lockup.setOrientation(LinearLayout.HORIZONTAL);
        lockup.setGravity(Gravity.CENTER_VERTICAL);
        mark = new Monogram(c);
        lockup.addView(mark, new LinearLayout.LayoutParams(Math.round(112 * dp), Math.round(112 * dp)));
        name = new ImageView(c);
        name.setImageResource(R.drawable.mibox_wordmark);
        name.setColorFilter(Monogram.CREAM, PorterDuff.Mode.SRC_IN);
        name.setAdjustViewBounds(true);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(40 * dp));
        nl.setMarginStart(Math.round(-4 * dp));
        lockup.addView(name, nl);
        root.addView(lockup, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setContentView(root);

        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0xFF000000));
            w.setWindowAnimations(0);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        setCancelable(false);

        if (still) {
            mark.written = 1f;
            mark.dot = 1f;
            home.getWindow().getDecorView().postDelayed(this::leave, MIN_MS);
            return;
        }
        name.setAlpha(0f);
        // The name is laid out but hidden: the monogram starts centred on the screen, then steps aside for it.
        lockup.setVisibility(View.INVISIBLE);
        // The timeline starts once the screen is really shown: at boot the home screen can take seconds to draw
        // its first frame, and the start-up screen appears with it.
        home.getWindow().getDecorView().postDelayed(this::begin, 6000);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) begin();
    }

    private void begin() {
        if (begun || leaving || still || !isShowing()) return;
        begun = true;
        start = SystemClock.uptimeMillis();
        lockup.setVisibility(View.VISIBLE);
        clock = ValueAnimator.ofFloat(0f, 1f);
        clock.setDuration(MIN_MS);
        clock.setInterpolator(null);
        clock.addUpdateListener(a -> tick(SystemClock.uptimeMillis() - start));
        clock.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (!leaving) leave();
            }
        });
        clock.start();
    }

    /** One frame of the timeline, t ms after the start. */
    private void tick(long t) {
        float dp = getContext().getResources().getDisplayMetrics().density;
        // 0.15 to 1.25 s: the line writes itself, down the L, along, and once round the o.
        mark.written = WRITE.getInterpolation(clamp((t - 150) / 1100f));
        // 1.1 to 1.6 s: the dot settles in, a touch past full size and back.
        float d = clamp((t - 1100) / 500f);
        mark.dot = d <= 0 ? 0 : (float) (1 + 0.18 * Math.sin(Math.PI * d) * (1 - d)) * EASE.getInterpolation(d);
        // From 2.1 s, while it waits, the dot breathes.
        if (t > 2100) mark.dot = 1f + 0.06f * (float) Math.sin((t - 2100) / 520.0 * Math.PI);
        mark.invalidate();
        // 1.45 to 2.25 s: the monogram steps aside and the name comes in beside it.
        float s = EASE.getInterpolation(clamp((t - 1450) / 800f));
        float shift = (name.getWidth() + nameMargin()) / 2f;
        lockup.setTranslationX(shift * (1 - s));
        name.setAlpha(s);
        name.setTranslationX(-14 * dp * (1 - s));
    }

    private int nameMargin() {
        return ((LinearLayout.LayoutParams) name.getLayoutParams()).getMarginStart();
    }

    /** Fades out while the home screen behind settles into place. */
    private void leave() {
        if (leaving) return;
        leaving = true;
        if (clock != null) clock.cancel();
        View content = home.findViewById(android.R.id.content);
        View root = getWindow() != null ? getWindow().getDecorView() : null;
        if (still || root == null) {
            dismiss();
            return;
        }
        if (content != null) {
            content.setScaleX(1.04f);
            content.setScaleY(1.04f);
            content.animate().scaleX(1f).scaleY(1f).setDuration(800).setInterpolator(EASE).start();
        }
        lockup.animate().scaleX(1.03f).scaleY(1.03f).alpha(0f).setDuration(420).setInterpolator(EASE).start();
        root.animate().alpha(0f).setStartDelay(120).setDuration(520).setInterpolator(EASE)
                .withEndAction(this::dismiss).start();
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (showing == this) showing = null;
        Runnable r = onGone;
        onGone = null;
        if (r != null) r.run();
    }

    /** Every key waits until the launcher is ready. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return true;
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    /**
     * The "lo" monogram drawn as it is written: the same line as the app's icon (one cream stroke from the top of the
     * L, round its foot and once round the o), up to {@link #written}, and the gold dot at {@link #dot} of its size.
     */
    static final class Monogram extends View {
        static final int CREAM = 0xFFF3EEE4, GOLD = 0xFFF2C879;
        private static final float GRID = 108f, CX = 64f, CY = 59.5f, R = 14.5f;
        float written = 1f, dot = 1f;
        private final Path line = new Path(), part = new Path();
        private final PathMeasure measure = new PathMeasure();
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float length;

        Monogram(Context c) {
            super(c);
            line.moveTo(30f, 33.5f);
            line.lineTo(30f, 67f);
            line.quadTo(30f, 74f, 37f, 74f);
            line.lineTo(CX, 74f);
            line.arcTo(new RectF(CX - R, CY - R, CX + R, CY + R), 90f, -359.9f, false);
            measure.setPath(line, false);
            length = measure.getLength();
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setStrokeWidth(7f);
            stroke.setColor(CREAM);
            fill.setColor(GOLD);
        }

        @Override
        protected void onDraw(Canvas c) {
            float scale = Math.min(getWidth(), getHeight()) / GRID;
            c.save();
            c.scale(scale, scale);
            if (written > 0) {
                part.reset();
                measure.getSegment(0, length * written, part, true);
                c.drawPath(part, stroke);
            }
            if (dot > 0) c.drawCircle(CX, CY, 4.8f * dot, fill);
            c.restore();
        }
    }
}
