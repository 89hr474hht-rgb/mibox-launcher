package com.gathin.tvhome.mibox;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
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
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * LaunchOS's start-up screen: once per boot, the first time the home screen comes up after the box starts (never when
 * coming back to Home). The logo springs in over slowly drifting colours, the name is written out from left to right,
 * and a bar fills while the launcher loads behind it. It stays at least {@link #MIN_MS}, then fades away while the
 * home screen settles into place. It is its own full-screen window, so the remote's keys do nothing until it is gone;
 * with Android's animations off it is shown still, for the same time.
 */
final class BootSplash extends Dialog {
    static final long MIN_MS = 4000;
    private static final String PREFS = "mibox_splash", KEY_BOOT = "boot";
    /** A first run of this version long after the box started (an update, say) is not a boot. */
    private static final long LATE_START_MS = 10 * 60_000L;

    private static boolean shownThisProcess;
    private static BootSplash showing;

    private final Activity home;
    private final boolean still;
    private long start;
    private boolean begun;
    private Backdrop backdrop;
    private ImageView logo, wordmark;
    private Bar bar;
    private TextView caption;
    private ValueAnimator clock;
    private boolean leaving;
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
        backdrop = new Backdrop(c);
        root.addView(backdrop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        logo = new ImageView(c);
        logo.setImageResource(R.drawable.mibox_logo);
        logo.setElevation(18 * dp);
        column.addView(logo, new LinearLayout.LayoutParams(Math.round(124 * dp), Math.round(124 * dp)));
        wordmark = new ImageView(c);
        wordmark.setImageResource(R.drawable.mibox_wordmark);
        wordmark.setAdjustViewBounds(true);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(40 * dp));
        wl.topMargin = Math.round(34 * dp);
        column.addView(wordmark, wl);
        bar = new Bar(c);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Math.round(240 * dp), Math.round(5 * dp));
        bl.topMargin = Math.round(44 * dp);
        column.addView(bar, bl);
        caption = new TextView(c);
        caption.setTextColor(0x99FFFFFF);
        caption.setTextSize(14);
        caption.setLetterSpacing(0.02f);
        if (UiFonts.medium(c) != null) caption.setTypeface(UiFonts.medium(c));
        caption.setText(R.string.mibox_splash_starting);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.topMargin = Math.round(16 * dp);
        column.addView(caption, cl);
        FrameLayout.LayoutParams col = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(column, col);
        setContentView(root);

        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0xFF07080C));
            w.setWindowAnimations(0);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        setCancelable(false);

        if (still) {
            bar.progress = 1f;
            home.getWindow().getDecorView().postDelayed(this::leave, MIN_MS);
            return;
        }
        logo.setAlpha(0f);
        logo.setScaleX(0.55f);
        logo.setScaleY(0.55f);
        logo.setRotation(-10f);
        wordmark.setAlpha(0f);
        wordmark.setTranslationY(10 * dp);
        bar.setAlpha(0f);
        caption.setAlpha(0f);
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
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f).setStartDelay(0).setDuration(820)
                .setInterpolator(new OvershootInterpolator(1.7f)).start();
        wordmark.animate().alpha(1f).translationY(0f).setStartDelay(620).setDuration(700)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        bar.animate().alpha(1f).setStartDelay(1050).setDuration(400).start();
        caption.animate().alpha(1f).setStartDelay(1150).setDuration(400).start();

        clock = ValueAnimator.ofFloat(0f, 1f);
        clock.setDuration(MIN_MS);
        clock.setInterpolator(null);
        clock.addUpdateListener(a -> tick(SystemClock.uptimeMillis() - start));
        clock.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!leaving) finale();
            }
        });
        clock.start();
    }

    private int captionStep;
    private final PathInterpolator load = new PathInterpolator(0.45f, 0.05f, 0.25f, 1f);

    /** One frame of the timeline, t ms after the start. */
    private void tick(long t) {
        backdrop.frame(t);
        // The name is written out from left to right between 0.62 and 1.5 s.
        float write = clamp((t - 620) / 880f);
        int ww = wordmark.getWidth();
        if (ww > 0) wordmark.setClipBounds(write >= 1f ? null : new Rect(0, 0, Math.round(ww * load.getInterpolation(write)),
                wordmark.getHeight()));
        // The bar fills from 1.1 to 3.7 s, with a short hold in the middle, like a real load.
        float p = clamp((t - 1100) / 2600f);
        float eased = p < 0.45f ? load.getInterpolation(p / 0.45f) * 0.62f
                : p < 0.58f ? 0.62f + (p - 0.45f) / 0.13f * 0.04f
                : 0.66f + load.getInterpolation((p - 0.58f) / 0.42f) * 0.34f;
        bar.progress = eased;
        bar.shimmer = (t % 1400) / 1400f;
        bar.invalidate();
        int step = t < 1900 ? 0 : t < 3000 ? 1 : 2;
        if (step != captionStep) {
            captionStep = step;
            int text = step == 1 ? R.string.mibox_splash_apps : R.string.mibox_splash_ready;
            caption.animate().alpha(0f).setStartDelay(0).setDuration(160).withEndAction(() -> {
                caption.setText(text);
                caption.animate().alpha(1f).setDuration(220).start();
            }).start();
        }
    }

    /** The logo gives one small beat, then the screen leaves. */
    private void finale() {
        bar.progress = 1f;
        bar.invalidate();
        logo.animate().scaleX(1.08f).scaleY(1.08f).setStartDelay(0).setDuration(170)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(() ->
                        logo.animate().scaleX(1f).scaleY(1f).setDuration(220).withEndAction(this::leave).start())
                .start();
    }

    /** Fades out while the home screen behind zooms gently into place. */
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
            content.setScaleX(1.045f);
            content.setScaleY(1.045f);
            content.animate().scaleX(1f).scaleY(1f).setDuration(700).setInterpolator(new DecelerateInterpolator(2f)).start();
        }
        logo.animate().scaleX(1.3f).scaleY(1.3f).alpha(0f).setDuration(420).setInterpolator(null).start();
        root.animate().alpha(0f).setStartDelay(80).setDuration(460).withEndAction(this::dismiss).start();
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

    /** Three soft lights in LaunchOS's colours drifting on a near-black field, painted small and shown stretched. */
    private static final class Backdrop extends View {
        private static final int W = 160, H = 90;
        private final Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        private final Canvas canvas = new Canvas(bitmap);
        private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint show = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final RectF dst = new RectF();
        private float fade;

        Backdrop(Context c) {
            super(c);
            frame(0);
        }

        void frame(long t) {
            float s = t / 1000f;
            fade = Math.min(1f, 0.35f + s / 0.6f);
            bitmap.eraseColor(0xFF07080C);
            blob(0x4F7BFF, 0.28f + 0.10f * (float) Math.sin(s * 0.9f), 0.36f + 0.08f * (float) Math.cos(s * 0.7f), 0.95f, 0.75f);
            blob(0x9A5CFF, 0.70f + 0.08f * (float) Math.cos(s * 0.8f), 0.30f + 0.10f * (float) Math.sin(s * 1.1f), 0.85f, 0.6f);
            blob(0xFF5C9A, 0.55f + 0.12f * (float) Math.sin(s * 0.6f + 2f), 0.82f + 0.06f * (float) Math.cos(s), 0.80f, 0.45f);
            invalidate();
        }

        private void blob(int rgb, float fx, float fy, float size, float alpha) {
            float r = H * size, cx = fx * W, cy = fy * H;
            int a = Math.round(255 * alpha * fade);
            light.setShader(new RadialGradient(cx, cy, r, new int[]{a << 24 | rgb, (a / 3) << 24 | rgb, rgb},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, light);
        }

        @Override
        protected void onDraw(Canvas c) {
            dst.set(0, 0, getWidth(), getHeight());
            c.drawBitmap(bitmap, null, dst, show);
        }
    }

    /** The loading bar: a rounded track, a blue-violet-pink fill and a light sweeping across it. */
    private static final class Bar extends View {
        float progress, shimmer;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
                gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Bar(Context c) {
            super(c);
            track.setColor(0x24FFFFFF);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            fill.setShader(new LinearGradient(0, 0, w, 0, new int[]{0xFF4F7BFF, 0xFF9A5CFF, 0xFFFF5C9A}, null,
                    Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = h / 2f;
            r.set(0, 0, w, h);
            c.drawRoundRect(r, rad, rad, track);
            float end = Math.max(h, w * progress);
            r.set(0, 0, end, h);
            c.drawRoundRect(r, rad, rad, fill);
            float x = -w * 0.3f + shimmer * w * 1.6f;
            gloss.setShader(new LinearGradient(x, 0, x + w * 0.3f, 0,
                    new int[]{Color.TRANSPARENT, 0x88FFFFFF, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
            c.save();
            c.clipRect(0, 0, end, h);
            c.drawRoundRect(r, rad, rad, gloss);
            c.restore();
        }
    }
}
