package com.gathin.tvhome.mibox;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;

/**
 * The Mi Box S (2nd gen) remote, drawn after Xiaomi's own picture of it (a 200 x 580 grid scaled to the view): power,
 * the Assistant button, the round D-pad, TV+, Back and Home, the four white app buttons and the volume rocker.
 *
 * <p>The still parts are drawn once into a bitmap; each frame adds the moving ones. The remote rises and tilts up
 * into place, its app buttons pop in one by one, then it floats and sways slightly while a light runs down its body.
 * The button being set lifts, glows in its colour behind a soft spotlight that glides from button to button, with a
 * light running round its edge and ripples; choosing an app presses it and sends waves out of the remote's top.
 * Nothing moves when Android's animations are off.
 */
final class RemoteView extends View {
    static final float VW = 200, VH = 580;
    private static final float BL = 30, BT = 12, BR = 170, BB = 566, BRAD = 24, DPAD_Y = 172;

    private static final int[] CODES = {RemoteRemap.NETFLIX, RemoteRemap.PRIME, RemoteRemap.YOUTUBE, 0};
    private static final float[] KX = {69, 131, 69, 131}, KY = {290, 290, 324, 324};
    private static final int[] GLOW = {0xFFE50914, 0xFF1EA8E5, 0xFFFF2D3C, 0xFF9A9DFF};

    private final Pill pill;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
            image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint vignette = new Paint(Paint.ANTI_ALIAS_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG),
            halo = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
            shadow = new Paint(Paint.ANTI_ALIAS_FLAG), rings = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF(), body = new RectF(BL, BT, BR, BB), grid = new RectF(0, 0, VW, VH);
    private final Matrix local = new Matrix(), tilt = new Matrix();
    private final Camera camera = new Camera();
    private final boolean still;
    private final long born = SystemClock.uptimeMillis();
    private final float[] lift = new float[4];
    private Bitmap still2d;
    private float scale, spotX = 100, spotY = 300, spotOn;
    private int selected;
    private long pressedAt = -1, lastFrame;

    RemoteView(Context context) {
        super(context);
        still = Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        pill = new Pill(context);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        ring.setStyle(Paint.Style.STROKE);
        rings.setStyle(Paint.Style.STROKE);
        // Unit shaders, placed with a local matrix each frame.
        vignette.setShader(new RadialGradient(0, 0, 1, new int[]{0, 0, 0x8C000000}, new float[]{0, 0.3f, 1},
                Shader.TileMode.CLAMP));
        sheen.setShader(new LinearGradient(0, -1, 0, 1, new int[]{0, 0x1CFFFFFF, 0x30FFFFFF, 0x1CFFFFFF, 0},
                new float[]{0, 0.35f, 0.5f, 0.65f, 1}, Shader.TileMode.CLAMP));
        shadow.setShader(new RadialGradient(0, 0, 1, 0x66000000, 0, Shader.TileMode.CLAMP));
        camera.setLocation(0, 0, -24);
    }

    /** Highlights the button for this key code (0 for none). */
    void select(int code) {
        if (code == selected) return;
        selected = code;
        int i = index(code);
        if (i >= 0) {
            halo.setShader(new RadialGradient(0, 0, 1, new int[]{withAlpha(GLOW[i], 0.75f), withAlpha(GLOW[i], 0.22f), 0},
                    new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP));
            ring.setShader(new SweepGradient(0, 0, new int[]{0, 0, withAlpha(GLOW[i], 0.9f), 0xFFFFFFFF, 0},
                    new float[]{0, 0.45f, 0.82f, 0.92f, 1}));
            if (still) {
                spotX = KX[i];
                spotY = KY[i];
            }
        }
        invalidate();
    }

    /** The selected button gives one press, after a new app was chosen for it. */
    void press() {
        pressedAt = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        scale = Math.min(w / VW, h / VH) * 0.97f;
        if (still2d != null) still2d.recycle();
        still2d = null;
        if (w <= 0 || h <= 0) return;
        still2d = Bitmap.createBitmap(Math.round(VW * scale), Math.round(VH * scale), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(still2d);
        c.scale(scale, scale);
        drawStillParts(c);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (still2d != null) still2d.recycle();
        still2d = null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (still2d == null && getWidth() > 0) onSizeChanged(getWidth(), getHeight(), 0, 0);
    }

    // The parts that never move, drawn once.

    private void drawStillParts(Canvas c) {
        // Body: matt black, a lighter top, bevelled sides and a thin rim.
        paint.setShader(new LinearGradient(0, BT, 0, BB, new int[]{0xFF34373E, 0xFF23252B, 0xFF16171B},
                new float[]{0, 0.35f, 1}, Shader.TileMode.CLAMP));
        c.drawRoundRect(body, BRAD, BRAD, paint);
        paint.setShader(new LinearGradient(BL, 0, BR, 0,
                new int[]{0x34FFFFFF, 0x0AFFFFFF, 0, 0, 0x08FFFFFF, 0x28FFFFFF},
                new float[]{0, 0.06f, 0.2f, 0.8f, 0.94f, 1}, Shader.TileMode.CLAMP));
        c.drawRoundRect(body, BRAD, BRAD, paint);
        stroke.setStrokeWidth(1.1f);
        stroke.setShader(new LinearGradient(0, BT, 0, BB, 0x80FFFFFF, 0x10FFFFFF, Shader.TileMode.CLAMP));
        tmp.set(body);
        tmp.inset(0.55f, 0.55f);
        c.drawRoundRect(tmp, BRAD, BRAD, stroke);
        stroke.setShader(null);
        paint.setShader(null);
        // The infrared window.
        paint.setColor(0xFF0C0D10);
        tmp.set(90, 17, 110, 20.5f);
        c.drawRoundRect(tmp, 2, 2, paint);

        // Power, in orange.
        roundKey(c, 100, 44, 12.5f);
        line(0xFFFF6A13, 1.7f);
        c.drawArc(94.5f, 38.5f, 105.5f, 49.5f, -55, 290, false, stroke);
        c.drawLine(100, 36.6f, 100, 43.5f, stroke);

        // Assistant: the four dots.
        roundKey(c, 100, 88, 14);
        dot(c, 96.2f, 86.4f, 4.2f, 0xFF4285F4);
        dot(c, 103.6f, 82.8f, 2.1f, 0xFFEA4335);
        dot(c, 104.4f, 89.6f, 2.5f, 0xFFFBBC05);
        dot(c, 101.2f, 93.2f, 2.0f, 0xFF34A853);

        // The D-pad ring and OK, with the groove between them.
        paint.setShader(new LinearGradient(0, DPAD_Y - 58, 0, DPAD_Y + 58, 0xFF33363D, 0xFF1D1F24,
                Shader.TileMode.CLAMP));
        shadowCircle(c, 100, DPAD_Y, 58);
        c.drawCircle(100, DPAD_Y, 58, paint);
        rim(c, 100, DPAD_Y, 58);
        paint.setShader(null);
        paint.setColor(0xFF121316);
        c.drawCircle(100, DPAD_Y, 27, paint);
        paint.setShader(new LinearGradient(0, DPAD_Y - 25, 0, DPAD_Y + 25, 0xFF30333A, 0xFF212328,
                Shader.TileMode.CLAMP));
        c.drawCircle(100, DPAD_Y, 24.5f, paint);
        rim(c, 100, DPAD_Y, 24.5f);
        paint.setShader(null);

        // Xiaomi TV+, Back, Home.
        roundKey(c, 58, 252, 15);
        Typeface medium = pill.medium, bold = pill.bold;
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(0xCCFFFFFF);
        paint.setTypeface(medium);
        paint.setTextSize(3.5f);
        paint.setLetterSpacing(0.04f);
        c.drawText("xiaomi", 58, 247.6f, paint);
        paint.setColor(0xFFF2F3F5);
        paint.setTypeface(bold);
        paint.setTextSize(7.6f);
        paint.setLetterSpacing(0);
        c.drawText("TV+", 58.4f, 257, paint);
        roundKey(c, 100, 252, 15);
        line(0xFFE8EAEE, 1.6f);
        c.drawLine(94.2f, 252, 105.8f, 252, stroke);
        c.drawLine(94.2f, 252, 98.8f, 247.4f, stroke);
        c.drawLine(94.2f, 252, 98.8f, 256.6f, stroke);
        roundKey(c, 142, 252, 15);
        Path house = new Path();
        house.moveTo(142, 245.4f);
        house.lineTo(148.6f, 251.2f);
        house.lineTo(147, 251.2f);
        house.lineTo(147, 258);
        house.lineTo(137, 258);
        house.lineTo(137, 251.2f);
        house.lineTo(135.4f, 251.2f);
        house.close();
        paint.setColor(0xFFE8EAEE);
        stroke.setStrokeWidth(0.8f);
        c.drawPath(house, paint);
        c.drawPath(house, stroke);

        // Volume rocker.
        tmp.set(84.5f, 348, 115.5f, 428);
        paint.setColor(0x70000000);
        tmp.offset(0, 1.4f);
        c.drawRoundRect(tmp, 15.5f, 15.5f, paint);
        tmp.offset(0, -1.4f);
        paint.setShader(new LinearGradient(0, 348, 0, 428, 0xFF31343B, 0xFF202227, Shader.TileMode.CLAMP));
        c.drawRoundRect(tmp, 15.5f, 15.5f, paint);
        paint.setShader(null);
        stroke.setColor(0x26FFFFFF);
        stroke.setStrokeWidth(0.8f);
        c.drawRoundRect(tmp, 15.5f, 15.5f, stroke);
        line(0xFFE8EAEE, 1.6f);
        c.drawLine(94.5f, 366, 105.5f, 366, stroke);
        c.drawLine(100, 360.5f, 100, 371.5f, stroke);
        c.drawLine(94.5f, 410, 105.5f, 410, stroke);
    }

    private void roundKey(Canvas c, float cx, float cy, float r) {
        shadowCircle(c, cx, cy, r);
        paint.setShader(new LinearGradient(0, cy - r, 0, cy + r, 0xFF33363D, 0xFF202227, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, paint);
        paint.setShader(null);
        rim(c, cx, cy, r);
    }

    private void shadowCircle(Canvas c, float cx, float cy, float r) {
        Shader s = paint.getShader();
        paint.setShader(null);
        paint.setColor(0x80000000);
        c.drawCircle(cx, cy + 1.4f, r, paint);
        paint.setShader(s);
    }

    private void rim(Canvas c, float cx, float cy, float r) {
        stroke.setStrokeWidth(0.8f);
        stroke.setShader(new LinearGradient(0, cy - r, 0, cy + r, 0x40FFFFFF, 0x08FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r - 0.4f, stroke);
        stroke.setShader(null);
    }

    private void line(int color, float width) {
        stroke.setColor(color);
        stroke.setStrokeWidth(width);
    }

    private void dot(Canvas c, float x, float y, float r, int color) {
        paint.setColor(color);
        c.drawCircle(x, y, r, paint);
    }

    // Each frame.

    @Override
    protected void onDraw(Canvas c) {
        if (still2d == null) return;
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float t = still ? 0 : (now - born) / 1000f;
        float enter = still ? 1f : Math.min(1f, (now - born) / 900f);
        float e = 1 - (float) Math.pow(1 - enter, 3);
        int sel = index(selected);

        // Waves behind the remote, like Xiaomi's picture of it.
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        for (int i = 0; i < 4 && !still; i++) {
            float f = ((t / 9f) + i / 4f) % 1f;
            rings.setStrokeWidth(1.5f * scale);
            rings.setColor(withAlpha(0xFF5A86C8, 0.16f * e * (float) Math.sin(Math.PI * f)));
            c.drawCircle(cx, cy - 60 * scale, (60 + 190 * f) * scale, rings);
        }

        float floatY = still ? 0 : (float) Math.sin(t * 1.1f) * 4f;
        // The shadow on the "floor", smaller as the remote floats up.
        local.setScale(70 * scale * (1 - floatY / 60f), 9 * scale);
        local.postTranslate(cx, cy + (VH / 2 - 6) * scale);
        shadow.getShader().setLocalMatrix(local);
        shadow.setAlpha(Math.round(255 * e));
        c.drawCircle(cx, cy + (VH / 2 - 6) * scale, 80 * scale, shadow);

        c.save();
        c.translate(cx, cy + (1 - e) * 70 * scale);
        camera.save();
        camera.rotateX((1 - e) * 24f + (still ? 0 : (float) Math.sin(t * 0.7f) * 1.2f));
        camera.rotateY(still ? 0 : (float) Math.sin(t * 0.45f) * 3.5f);
        camera.getMatrix(tilt);
        camera.restore();
        c.concat(tilt);
        c.scale(scale, scale);
        c.translate(-VW / 2, -VH / 2 + floatY);
        int alpha = Math.round(255 * e);
        image.setAlpha(alpha);
        c.drawBitmap(still2d, null, grid, image);

        // A light running down the body every seven seconds.
        float sweep = still ? -1 : (t % 7f) / 1.8f;
        if (sweep >= 0 && sweep <= 1) {
            local.setScale(1, 70);
            local.postRotate(-18);
            local.postTranslate(100, BT - 80 + sweep * (BB - BT + 160));
            sheen.getShader().setLocalMatrix(local);
            sheen.setAlpha(alpha);
            c.drawRoundRect(body, BRAD, BRAD, sheen);
        }

        // The Assistant's voice arcs, breathing.
        float voice = still ? 1 : 0.55f + 0.45f * (float) Math.sin(t * 2.2f);
        stroke.setShader(null);
        stroke.setStrokeWidth(1.4f);
        for (int i = 0; i < 2; i++) {
            float r = 17.5f + i * 3.6f;
            stroke.setColor(withAlpha(0xFF4C83FF, e * (i == 0 ? 0.95f : 0.55f) * voice));
            tmp.set(100 - r, 88 - r, 100 + r, 88 + r);
            c.drawArc(tmp, -40, 80, false, stroke);
            c.drawArc(tmp, 140, 80, false, stroke);
        }

        // The spotlight glides to the selected button and darkens the rest of the remote.
        float k = still ? 1 : Math.min(1f, dt * 9f);
        if (sel >= 0) {
            spotX += (KX[sel] - spotX) * k;
            spotY += (KY[sel] - spotY) * k;
        }
        spotOn += ((sel >= 0 ? 1f : 0f) - spotOn) * (still ? 1 : Math.min(1f, dt * 6f));
        if (spotOn > 0.01f) {
            local.setScale(185, 185);
            local.postTranslate(spotX, spotY);
            vignette.getShader().setLocalMatrix(local);
            vignette.setAlpha(Math.round(alpha * spotOn));
            c.drawRoundRect(body, BRAD, BRAD, vignette);
            if (halo.getShader() != null) {
                float pulse = still ? 1 : 0.8f + 0.2f * (float) Math.sin(t * 3.6f);
                local.setScale(52 * pulse, 40 * pulse);
                local.postTranslate(spotX, spotY);
                halo.getShader().setLocalMatrix(local);
                halo.setAlpha(Math.round(alpha * spotOn));
                tmp.set(spotX - 60, spotY - 46, spotX + 60, spotY + 46);
                c.drawOval(tmp, halo);
            }
        }

        // The four app buttons: popping in, the selected one lifted, ringed and rippling.
        for (int i = 0; i < 4; i++) {
            float pop = still ? 1f : clamp((now - born - 450 - i * 90) / 420f);
            if (pop <= 0) continue;
            float s = 0.6f + 0.4f * back(pop);
            lift[i] += ((i == sel ? 1f : 0f) - lift[i]) * (still ? 1 : Math.min(1f, dt * 10f));
            s *= 1 + 0.14f * lift[i];
            if (i == sel && pressedAt > 0) s *= pressScale((now - pressedAt) / 520f);
            c.save();
            c.translate(KX[i], KY[i]);
            if (lift[i] > 0.02f) drawSelection(c, i, t, lift[i] * spotOn, alpha);
            c.scale(s, s);
            pill.draw(c, CODES[i], Math.round(alpha * pop), lift[i]);
            if (sel >= 0 && i != sel) {   // the other buttons step back into the shade
                paint.setShader(null);
                paint.setColor(withAlpha(0xFF0A0B0E, 0.42f * spotOn * pop * alpha / 255f));
                tmp.set(-Pill.W / 2, -Pill.H / 2, Pill.W / 2, Pill.H / 2);
                c.drawRoundRect(tmp, Pill.H / 2, Pill.H / 2, paint);
            }
            if (i == sel && pressedAt > 0) {   // a white flash as it is pressed
                float f = (now - pressedAt) / 380f;
                if (f < 1) {
                    paint.setShader(null);
                    paint.setColor(withAlpha(0xFFFFFFFF, 0.55f * (1 - f)));
                    tmp.set(-Pill.W / 2, -Pill.H / 2, Pill.W / 2, Pill.H / 2);
                    c.drawRoundRect(tmp, Pill.H / 2, Pill.H / 2, paint);
                }
            }
            c.restore();
        }

        // After a choice, signal waves leave the top of the remote.
        if (pressedAt > 0 && !still) {
            float f0 = (now - pressedAt) / 1100f;
            for (int i = 0; i < 3; i++) {
                float f = f0 - i * 0.16f;
                if (f <= 0 || f >= 1) continue;
                float r = 10 + 46 * f;
                stroke.setStrokeWidth(2f);
                stroke.setColor(withAlpha(sel >= 0 ? GLOW[sel] : 0xFF8FB6FF, 0.85f * (1 - f)));
                tmp.set(100 - r, BT - r + 6, 100 + r, BT + r + 6);
                c.drawArc(tmp, 215, 110, false, stroke);
            }
            if (f0 > 1.5f) pressedAt = -1;
        }
        c.restore();
        if (!still && isShown()) postInvalidateOnAnimation();
    }

    private void drawSelection(Canvas c, int i, float t, float on, int alpha) {
        // A light running round the button's edge.
        local.setRotate((t * 160f) % 360f);
        ring.getShader().setLocalMatrix(local);
        ring.setStrokeWidth(1.8f);
        ring.setAlpha(Math.round(alpha * on));
        tmp.set(-Pill.W / 2 - 4.5f, -Pill.H / 2 - 4.5f, Pill.W / 2 + 4.5f, Pill.H / 2 + 4.5f);
        c.drawRoundRect(tmp, Pill.H / 2 + 4.5f, Pill.H / 2 + 4.5f, ring);
        stroke.setShader(null);
        stroke.setStrokeWidth(0.9f);
        stroke.setColor(withAlpha(GLOW[i], 0.45f * on * alpha / 255f));
        c.drawRoundRect(tmp, Pill.H / 2 + 4.5f, Pill.H / 2 + 4.5f, stroke);
        // Ripples, one every 0.9 s.
        for (int j = 0; j < 2 && !still; j++) {
            float f = ((t / 1.8f) + j / 2f) % 1f;
            float g = 5 + 13 * f;
            stroke.setStrokeWidth(1.2f);
            stroke.setColor(withAlpha(GLOW[i], 0.5f * (1 - f) * on * alpha / 255f));
            tmp.set(-Pill.W / 2 - g, -Pill.H / 2 - g, Pill.W / 2 + g, Pill.H / 2 + g);
            c.drawRoundRect(tmp, Pill.H / 2 + g, Pill.H / 2 + g, stroke);
        }
    }

    private static float pressScale(float p) {
        if (p >= 1) return 1;
        if (p < 0.22f) return 1 - 0.16f * (p / 0.22f);
        float q = (p - 0.22f) / 0.78f;
        return 0.84f + 0.16f * back(q);
    }

    /** Ease out with a small overshoot. */
    private static float back(float x) {
        float s = 2.2f, y = x - 1;
        return 1 + y * y * ((s + 1) * y + s);
    }

    private static float clamp(float x) {
        return Math.max(0f, Math.min(1f, x));
    }

    private static int index(int code) {
        if (code == 0) return -1;
        for (int i = 0; i < CODES.length; i++) if (CODES[i] == code) return i;
        return -1;
    }

    static int withAlpha(int color, float a) {
        return Math.round(255 * clamp(a)) << 24 | (color & 0xFFFFFF);
    }

    /**
     * One of the remote's white app buttons (Netflix, Prime Video, YouTube, Apps), centred on the origin in a
     * 54 x 21 box; the rows of RemoteKeysActivity use it too, so the list shows the same buttons as the remote.
     */
    static final class Pill {
        static final float W = 54, H = 21;
        final Typeface bold, medium;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG),
                mark = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path path = new Path();
        private final LinearGradient face = new LinearGradient(0, -H / 2, 0, H / 2,
                new int[]{0xFFFFFFFF, 0xFFF1F2F4, 0xFFD5D7DC}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP);

        Pill(Context context) {
            Typeface b = UiFonts.bold(context), m = UiFonts.medium(context);
            bold = b != null ? b : Typeface.DEFAULT_BOLD;
            medium = m != null ? m : Typeface.DEFAULT;
            mark.setStyle(Paint.Style.STROKE);
            mark.setStrokeCap(Paint.Cap.ROUND);
        }

        void draw(Canvas c, int code, int alpha, float lifted) {
            float a = alpha / 255f;
            r.set(-W / 2, -H / 2, W / 2, H / 2);
            // Its shadow on the remote, longer when the button is lifted.
            fill.setShader(null);
            fill.setColor(withAlpha(0xFF000000, a * (0.55f + 0.2f * lifted)));
            r.offset(0, 1.2f + 2.2f * lifted);
            c.drawRoundRect(r, H / 2, H / 2, fill);
            r.offset(0, -1.2f - 2.2f * lifted);
            fill.setShader(face);
            fill.setAlpha(alpha);
            c.drawRoundRect(r, H / 2, H / 2, fill);
            mark.setStrokeWidth(0.6f);
            mark.setColor(withAlpha(0xFF000000, 0.18f * a));
            r.inset(0.3f, 0.3f);
            c.drawRoundRect(r, H / 2, H / 2, mark);

            text.setTextAlign(Paint.Align.CENTER);
            text.setTextScaleX(1f);
            text.setLetterSpacing(0);
            if (code == RemoteRemap.NETFLIX) {
                text.setTypeface(bold);
                text.setTextSize(9.6f);
                text.setTextScaleX(0.84f);
                text.setLetterSpacing(0.03f);
                text.setColor(withAlpha(0xFFE50914, a));
                c.drawText("NETFLIX", 0, baseline(0), text);
            } else if (code == RemoteRemap.PRIME) {
                text.setTypeface(medium);
                text.setTextSize(7.4f);
                text.setColor(withAlpha(0xFF232A33, a));
                c.drawText("prime video", 0, baseline(-1.6f), text);
                // The smile under "video", ending in an arrow.
                mark.setStrokeWidth(0.9f);
                mark.setColor(withAlpha(0xFF232A33, a));
                path.reset();
                path.moveTo(-1.5f, 3.4f);
                path.quadTo(7.5f, 7.2f, 16.5f, 3.2f);
                c.drawPath(path, mark);
                path.reset();
                path.moveTo(14.4f, 2.3f);
                path.lineTo(16.8f, 3.1f);
                path.lineTo(16.1f, 5.5f);
                c.drawPath(path, mark);
            } else if (code == RemoteRemap.YOUTUBE) {
                text.setTypeface(bold);
                text.setTextSize(7.8f);
                text.setTextScaleX(0.92f);
                text.setLetterSpacing(-0.02f);
                text.setTextAlign(Paint.Align.LEFT);
                float tw = text.measureText("YouTube"), x = -(10 + 1.4f + tw) / 2;
                fill.setShader(null);
                fill.setColor(withAlpha(0xFFFF0000, a));
                r.set(x, -3.6f, x + 10, 3.6f);
                c.drawRoundRect(r, 2.2f, 2.2f, fill);
                fill.setColor(withAlpha(0xFFFFFFFF, a));
                path.reset();
                path.moveTo(x + 4f, -1.9f);
                path.lineTo(x + 7f, 0);
                path.lineTo(x + 4f, 1.9f);
                path.close();
                c.drawPath(path, fill);
                text.setColor(withAlpha(0xFF161616, a));
                c.drawText("YouTube", x + 11.4f, baseline(0), text);
            } else {
                text.setTypeface(medium);
                text.setTextSize(6.2f);
                text.setLetterSpacing(0.14f);
                text.setTextAlign(Paint.Align.LEFT);
                float tw = text.measureText("APPS"), x = -(tw + 2.2f + 6.4f) / 2;
                text.setColor(withAlpha(0xFF7A7E86, a));
                c.drawText("APPS", x, baseline(0), text);
                fill.setShader(null);
                fill.setColor(withAlpha(0xFF7A7E86, a));
                float gx = x + tw + 2.2f + 0.8f;
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) c.drawCircle(gx + i * 2.4f, -2.4f + j * 2.4f, 0.8f, fill);
                }
            }
        }

        private float baseline(float centreY) {
            return centreY - (text.descent() + text.ascent()) / 2f;
        }
    }

    /** A remote button's white face, for the rows of the remote buttons page. */
    static final class Chip extends View {
        private final Pill pill;
        private final int code;

        Chip(Context context, int code) {
            super(context);
            this.code = code;
            pill = new Pill(context);
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = Math.min(getWidth() / (Pill.W + 2), getHeight() / (Pill.H + 5));
            c.translate(getWidth() / 2f, getHeight() / 2f - s);
            c.scale(s, s);
            pill.draw(c, code, 255, 0);
        }
    }
}
