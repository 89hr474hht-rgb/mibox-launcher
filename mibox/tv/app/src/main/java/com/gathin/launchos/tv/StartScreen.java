package com.gathin.launchos.tv;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * LaunchOS TV's start-up screen, when the app is opened (not when coming back to it): on true black, the "lo" monogram
 * writes itself in one line, its coral dot settles in, and "LaunchOS TV" comes in beside it. Then it fades away while
 * the app settles into place behind. About two and a half seconds; any key makes it leave at once. With Android's
 * animations off it is shown still, briefly.
 */
final class StartScreen {
    private static final long LENGTH_MS = 2500;
    private static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final PathInterpolator WRITE = new PathInterpolator(0.55f, 0f, 0.25f, 1f);

    private final Activity activity;
    private final FrameLayout screen;
    private final LinearLayout lockup;
    private final Monogram mark;
    private final ImageView name;
    private final boolean still;
    private ValueAnimator clock;
    private boolean leaving;

    private StartScreen(Activity a) {
        activity = a;
        still = Settings.Global.getFloat(a.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        float dp = a.getResources().getDisplayMetrics().density;
        screen = new FrameLayout(a);
        screen.setBackgroundColor(0xFF000000);
        screen.setClickable(true);
        lockup = new LinearLayout(a);
        lockup.setOrientation(LinearLayout.HORIZONTAL);
        lockup.setGravity(Gravity.CENTER_VERTICAL);
        mark = new Monogram(a);
        lockup.addView(mark, new LinearLayout.LayoutParams(Math.round(104 * dp), Math.round(104 * dp)));
        name = new ImageView(a);
        name.setImageResource(R.drawable.wordmark);
        name.setAdjustViewBounds(true);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(36 * dp));
        nl.setMarginStart(Math.round(-4 * dp));
        lockup.addView(name, nl);
        screen.addView(lockup, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    /** Covers the activity's window with the start-up screen and plays it; returns it so keys can end it. */
    static StartScreen show(Activity a) {
        StartScreen s = new StartScreen(a);
        ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
        decor.addView(s.screen, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        s.start();
        return s;
    }

    boolean showing() {
        return screen.getParent() != null && !leaving;
    }

    private void start() {
        if (still) {
            screen.postDelayed(this::leave, 900);
            return;
        }
        mark.written = 0f;
        mark.dot = 0f;
        name.setAlpha(0f);
        long begin = SystemClock.uptimeMillis();
        clock = ValueAnimator.ofFloat(0f, 1f);
        clock.setDuration(LENGTH_MS);
        clock.setInterpolator(null);
        clock.addUpdateListener(v -> tick(SystemClock.uptimeMillis() - begin));
        clock.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                leave();
            }
        });
        // The first frame of the app can take a moment: start once the screen is laid out.
        screen.post(clock::start);
    }

    private void tick(long t) {
        float dp = activity.getResources().getDisplayMetrics().density;
        mark.written = WRITE.getInterpolation(clamp((t - 80) / 900f));
        float d = clamp((t - 880) / 420f);
        mark.dot = d <= 0 ? 0 : (float) (1 + 0.2 * Math.sin(Math.PI * d) * (1 - d)) * EASE.getInterpolation(d);
        mark.invalidate();
        float s = EASE.getInterpolation(clamp((t - 1150) / 700f));
        LinearLayout.LayoutParams nl = (LinearLayout.LayoutParams) name.getLayoutParams();
        lockup.setTranslationX((name.getWidth() + nl.getMarginStart()) / 2f * (1 - s));
        name.setAlpha(s);
        name.setTranslationX(-12 * dp * (1 - s));
    }

    /** Fades out while the app behind settles into place; any key calls it early. */
    void leave() {
        if (leaving) return;
        leaving = true;
        if (clock != null) clock.cancel();
        View content = activity.findViewById(android.R.id.content);
        if (content != null && !still) {
            content.setScaleX(1.03f);
            content.setScaleY(1.03f);
            content.animate().scaleX(1f).scaleY(1f).setDuration(700).setInterpolator(EASE).start();
        }
        lockup.animate().scaleX(1.03f).scaleY(1.03f).alpha(0f).setDuration(380).setInterpolator(EASE).start();
        screen.animate().alpha(0f).setStartDelay(still ? 0 : 100).setDuration(still ? 200 : 460).setInterpolator(EASE)
                .withEndAction(() -> {
                    ViewGroup parent = (ViewGroup) screen.getParent();
                    if (parent != null) parent.removeView(screen);
                }).start();
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    /** The "lo" monogram as it is written: one cream line from the top of the L into the o, then the coral dot. */
    static final class Monogram extends View {
        private static final float GRID = 108f, CX = 64f, CY = 59.5f, R = 14.5f;
        float written = 1f, dot = 1f;
        private final Path line = new Path(), part = new Path();
        private final PathMeasure measure = new PathMeasure();
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float length;

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
            stroke.setColor(0xFFF3EEE4);
            fill.setColor(0xFFFF5A4A);
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
