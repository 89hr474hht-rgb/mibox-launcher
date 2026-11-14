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
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;

import com.gathin.launchos.design.Motion;

/**
 * The home screen's glow in the focused app's colour: three soft lights in that colour and two neighbouring hues,
 * kept dark so the tiles stay in front. When the focus moves to another app the colour fades to the new one in about a
 * third of a second, then nothing redraws until the next change.
 *
 * The lights are painted into a 160 x 90 bitmap that the screen shows stretched, one texture like upstream's still
 * glow. It takes the place of upstream's glow bitmap in the "ambient" view; upstream tints that bitmap with the app's
 * colour (setTint), and this one paints its lights with that colour instead.
 */
final class LivingGlow extends BitmapDrawable {
    private static final int W = 160, H = 90, BLOB = 64;
    /** About thirty frames a second while the colour fades; none otherwise. */
    private static final long FRAME_NS = 1_000_000_000L / 30;
    /** How dark the lights are kept, so the glow stays behind the tiles. */
    private static final float DIM = 0.30f;

    private final View ambient;
    private final Bitmap bitmap;
    private final Canvas canvas;
    private final Bitmap blob;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private final Choreographer choreographer = Choreographer.getInstance();

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
            if (settled()) {
                running = false;   // the colour has arrived: stop until the next change
                return;
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
    static LivingGlow attach(Activity home) {
        if (!home.getResources().getBoolean(R.bool.mibox_living_glow)) return null;
        int id = UpstreamViews.id(home, "ambient");
        View ambient = id == 0 ? null : home.findViewById(id);
        if (ambient == null || ambient.getVisibility() == View.GONE) return null;   // ambient_glow is off
        LivingGlow glow = new LivingGlow(home, ambient, Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888));
        ambient.setBackground(glow);
        return glow;
    }

    void resume() {
        if (ambient.getBackground() != this) ambient.setBackground(this);   // upstream rebuilt its own: take it back
        fade();
    }

    /** Starts the fade to the target colour, or shows it at once when animations are off. */
    private void fade() {
        if (!Motion.on()) {
            hasShown = false;
            paintFrame(System.nanoTime());
            invalidateSelf();
            return;
        }
        if (running || settled()) return;
        running = true;
        lastFrame = 0;
        choreographer.postFrameCallback(frame);
    }

    private boolean settled() {
        return hasShown && Math.abs(shown[0] - ((target >> 16) & 0xFF)) < 1 && Math.abs(shown[1] - ((target >> 8) & 0xFF)) < 1
                && Math.abs(shown[2] - (target & 0xFF)) < 1;
    }

    void pause() {
        running = false;
        choreographer.removeFrameCallback(frame);
    }

    /** Upstream's setAmbient tints the glow with the app's colour: keep it, the lights glide to it. */
    @Override
    public void setTintList(ColorStateList tint) {
        if (tint != null) target = tint.getDefaultColor() & 0xFFFFFF;
        fade();
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
            // About a third of a second to reach a new colour at thirty frames a second.
            shown[0] += (r - shown[0]) * 0.22f;
            shown[1] += (g - shown[1]) * 0.22f;
            shown[2] += (b - shown[2]) * 0.22f;
        }
        int base = Color.rgb(Math.round(shown[0]), Math.round(shown[1]), Math.round(shown[2]));
        bitmap.eraseColor(0);
        // Three still lights: the app's colour at the top left, its neighbours further off; all kept dark.
        light(GlowColors.shift(base, 0f, 1.0f), 0.24f, 0.30f, 1.30f, 0.86f * DIM);
        light(GlowColors.shift(base, 32f, 1.0f), 0.78f, 0.24f, 1.10f, 0.62f * DIM);
        light(GlowColors.shift(base, -38f, 0.95f), 0.54f, 0.92f, 1.20f, 0.50f * DIM);
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
