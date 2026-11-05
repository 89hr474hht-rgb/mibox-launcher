package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;

/**
 * The home screen's glow in the focused app's colour, brought to life: instead of one still patch of colour,
 * three soft lights in that colour and two neighbouring hues drift slowly behind the tiles, like the animated
 * backgrounds of Apple Music, and glide to the next app's colours when the focus moves.
 *
 * Built to cost almost nothing on the box's GPU: the lights are painted into a 160 x 90 bitmap, about 20 times a
 * second, and the screen shows it stretched, which is one texture, the same as upstream's still glow. It runs only
 * while the home screen is in front with its glow showing, and stands still when Android's animations are off.
 *
 * It takes the place of upstream's glow bitmap in the "ambient" view. Upstream tints that bitmap with the app's
 * colour (setTint); this one keeps the colour instead and paints its own lights with it.
 */
final class LivingGlow extends BitmapDrawable {
    private static final int W = 160, H = 90, BLOB = 64;
    private static final long FRAME_NS = 1_000_000_000L / 20;

    private final View ambient;
    private final Bitmap bitmap;
    private final Canvas canvas;
    private final Bitmap blob;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private final Choreographer choreographer = Choreographer.getInstance();
    private final long startNs = System.nanoTime();

    private int target = 0x5B6BFF;       // until upstream sets the first app's colour
    private final float[] shown = new float[3];   // the colour on screen, in RGB, gliding towards target
    private boolean hasShown;
    private boolean running;
    private long lastFrame;

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long now) {
            if (!running) return;
            if (now - lastFrame >= FRAME_NS) {
                lastFrame = now;
                if (ambient.isShown() && ambient.getAlpha() > 0.01f) {
                    paintFrame(now);
                    invalidateSelf();
                }
            }
            choreographer.postFrameCallback(this);
        }
    };

    private LivingGlow(Activity home, View ambient, Bitmap bitmap) {
        super(home.getResources(), bitmap);
        this.ambient = ambient;
        this.bitmap = bitmap;
        canvas = new Canvas(bitmap);
        setGravity(Gravity.FILL);
        setFilterBitmap(true);
        setDither(true);
        blob = Bitmap.createBitmap(BLOB, BLOB, Bitmap.Config.ALPHA_8);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(BLOB / 2f, BLOB / 2f, BLOB / 2f,
                new int[]{0xFF000000, 0xB0000000, 0x48000000, 0x00000000},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        new Canvas(blob).drawCircle(BLOB / 2f, BLOB / 2f, BLOB / 2f, p);
        paintFrame(System.nanoTime());
    }

    /** Puts the living glow in the home screen's "ambient" view; null when upstream's layout has no such view. */
    @SuppressWarnings("DiscouragedApi") // the app's R is not visible from this library
    static LivingGlow attach(Activity home) {
        if (!home.getResources().getBoolean(R.bool.mibox_living_glow)) return null;
        int id = home.getResources().getIdentifier("ambient", "id", home.getPackageName());
        View ambient = id == 0 ? null : home.findViewById(id);
        if (ambient == null || ambient.getVisibility() == View.GONE) return null;   // ambient_glow is off
        LivingGlow glow = new LivingGlow(home, ambient, Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888));
        ambient.setBackground(glow);
        return glow;
    }

    void resume() {
        if (ambient.getBackground() != this) ambient.setBackground(this);   // upstream rebuilt its own: take it back
        if (running) return;
        if (Settings.Global.getFloat(ambient.getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) {
            paintFrame(System.nanoTime());
            invalidateSelf();
            return;
        }
        running = true;
        lastFrame = 0;
        choreographer.postFrameCallback(frame);
    }

    void pause() {
        running = false;
        choreographer.removeFrameCallback(frame);
    }

    /** Upstream's setAmbient tints the glow with the app's colour: keep it, the lights glide to it. */
    @Override
    public void setTintList(ColorStateList tint) {
        if (tint != null) target = tint.getDefaultColor() & 0xFFFFFF;
        if (!running) {
            hasShown = false;   // not animating: show the new colour at once
            paintFrame(System.nanoTime());
            invalidateSelf();
        }
    }

    @Override
    public void setTintMode(PorterDuff.Mode mode) {
        // The colour is painted in, never applied as a tint.
    }

    private void paintFrame(long now) {
        float r = (target >> 16) & 0xFF, g = (target >> 8) & 0xFF, b = target & 0xFF;
        if (!hasShown) {
            shown[0] = r;
            shown[1] = g;
            shown[2] = b;
            hasShown = true;
        } else {
            // About 0.6 s to reach a new colour at 20 frames a second.
            shown[0] += (r - shown[0]) * 0.16f;
            shown[1] += (g - shown[1]) * 0.16f;
            shown[2] += (b - shown[2]) * 0.16f;
        }
        int base = Color.rgb(Math.round(shown[0]), Math.round(shown[1]), Math.round(shown[2]));
        float t = (now - startNs) / 1e9f;
        bitmap.eraseColor(0);
        // Three lights on slow, unrelated loops (37, 53 and 71 s), so the pattern never visibly repeats.
        light(GlowColors.shift(base, 0f, 1f), 0.30f + 0.14f * sin(t, 37, 0f), 0.40f + 0.16f * sin(t, 53, 1.3f), 1.15f, 0.40f);
        light(GlowColors.shift(base, 28f, 1.12f), 0.72f + 0.12f * sin(t, 53, 2.1f), 0.30f + 0.14f * sin(t, 71, 0.4f), 0.95f, 0.30f);
        light(GlowColors.shift(base, -34f, 0.9f), 0.52f + 0.18f * sin(t, 71, 4.0f), 0.82f + 0.10f * sin(t, 37, 2.7f), 1.05f, 0.24f);
    }

    private static float sin(float t, float period, float phase) {
        return (float) Math.sin(t * 2 * Math.PI / period + phase);
    }

    /** One light: the soft disc, centred at (fx, fy) of the frame, size times its height across, at that alpha. */
    private void light(int rgb, float fx, float fy, float size, float alpha) {
        float d = H * size;
        dst.set(fx * W - d, fy * H - d, fx * W + d, fy * H + d);
        // An ALPHA_8 bitmap is drawn as a mask in the paint's colour: no filter, nothing allocated per frame.
        paint.setColor(Math.round(255 * alpha) << 24 | rgb);
        canvas.drawBitmap(blob, null, dst, paint);
    }
}
