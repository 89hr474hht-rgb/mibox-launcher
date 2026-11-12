package com.gathin.tvhome.mibox;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import java.io.File;

/**
 * A word written very large behind a page, the page floating over it: carved in a dim white that barely shows, with
 * a little depth below and to the right, a faintly lit bevel on the upper edges and a soft halo behind. A band of light passes over it now and then,
 * and the whole word turns very slowly a few degrees in perspective, as if lit by a moving lamp. Words in Arabic script
 * are set in Android's Naskh face, a classic calligraphic style; others in LaunchOS's own. About thirty frames a second
 * while it shows; nothing while it is hidden.
 */
public final class ShineText extends View {
    private static final long SWEEP_MS = 4600, REST_MS = 2200, TURN_MS = 14_000;
    private static final int DEPTH = 12;
    private static final String NASKH = "/system/fonts/NotoNaskhArabic-Bold.ttf";

    private final TextPaint face = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint side = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint shadow = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint light = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix move = new Matrix();
    private final float density;
    private final Typeface latin, arabic;
    private LinearGradient band, gold, depth;
    private RadialGradient glow;
    private float laidSize = -1, laidX, laidY, laidW;
    private String text = "";
    private long born = SystemClock.uptimeMillis();

    public ShineText(Context c) {
        this(c, null);
    }

    public ShineText(Context c, AttributeSet attrs) {
        super(c, attrs);
        density = c.getResources().getDisplayMetrics().density;
        latin = UiFonts.semibold(c);
        Typeface naskh = null;
        try {
            if (new File(NASKH).canRead()) naskh = Typeface.createFromFile(NASKH);
        } catch (RuntimeException ignored) {
            // the system's own Arabic fallback is used
        }
        arabic = naskh;
        light.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        setLayerType(LAYER_TYPE_HARDWARE, null);
        setCameraDistance(9000 * density);
    }

    void setText(String s) {
        String t = s == null ? "" : s.trim();
        if (t.equals(text)) return;
        text = t;
        boolean rtl = isArabic(t);
        Typeface tf = rtl && arabic != null ? arabic : latin;
        for (TextPaint p : new TextPaint[]{face, side, shadow, light}) {
            if (tf != null) p.setTypeface(tf);
            p.setLetterSpacing(rtl ? 0f : -0.03f);
        }
        laidSize = -1;
        born = SystemClock.uptimeMillis();
        invalidate();
    }

    static boolean isArabic(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= 0x0600 && ch <= 0x06FF || ch >= 0x0750 && ch <= 0x077F || ch >= 0xFB50 && ch <= 0xFEFF) return true;
        }
        return false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        setPivotX(w * 0.6f);
        setPivotY(h / 2f);
        laidSize = -1;
    }

    /** Very large: about two thirds of the height or the whole width, a bit right of centre. */
    private void lay() {
        int w = getWidth(), h = getHeight();
        float size = h * 0.6f;
        face.setTextSize(size);
        float tw = face.measureText(text);
        float room = w * 0.96f;   // the whole word shows: a cut letter could change it
        if (tw > room) size *= room / tw;
        for (TextPaint p : new TextPaint[]{face, side, shadow, light}) p.setTextSize(size);
        laidW = face.measureText(text);
        laidX = Math.max(w * 0.02f, Math.min(w * 0.56f - laidW / 2f, w * 0.98f - laidW));
        laidY = (h - face.descent() - face.ascent()) / 2f + h * 0.03f;
        float top = laidY + face.ascent(), bottom = laidY + face.descent();
        // A dim white, a touch brighter at the top, so it reads as carved rather than printed.
        gold = new LinearGradient(0, top, 0, bottom, new int[]{0xFF2C2C2D, 0xFF1D1D1E, 0xFF151516},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        depth = new LinearGradient(0, top, 0, bottom, 0xFF111112, 0xFF0A0A0B, Shader.TileMode.CLAMP);
        // The halo: an ellipse around the word that has faded out well before the edges of the view.
        float cx = laidX + laidW / 2f, cy = h / 2f, r = h * 0.46f;
        glow = new RadialGradient(cx, cy, r, new int[]{0x12FFFFFF, 0x06FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        Matrix stretch = new Matrix();
        stretch.setScale(Math.min(w * 0.46f, laidW * 0.62f) / r, 1f, cx, cy);
        glow.setLocalMatrix(stretch);
        float blur = Math.max(4f, size / 18f);
        shadow.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));
        band = null;
        laidSize = size;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (text.isEmpty() || getWidth() == 0) return;
        if (laidSize < 0) lay();
        long now = SystemClock.uptimeMillis() - born;
        // The lamp moves: a slow turn of a few degrees, never still, never fast.
        double turn = now / (double) TURN_MS * 2 * Math.PI;
        setRotationY((float) (-3 * Math.sin(turn)));
        setRotationX((float) (1.5 * Math.sin(turn * 0.7 + 1)));
        float x = laidX, y = laidY, size = laidSize;
        float step = Math.max(1.2f, size / 160f);
        // A soft light behind, on the wall.
        halo.setShader(glow);
        c.drawRect(0, 0, getWidth(), getHeight(), halo);
        // The shadow it casts, blurred, further down and to the right.
        shadow.setColor(0x99000000);
        c.drawText(text, x + step * DEPTH * 0.9f, y + step * DEPTH * 1.3f, shadow);
        // Its depth: the word repeated a step lower and to the right each time, dark gold.
        side.setShader(depth);
        for (int i = DEPTH; i >= 1; i--) c.drawText(text, x + i * step * 0.55f, y + i * step, side);
        side.setShader(null);
        // The bevel: a pale rim on the upper edges, which the face covers but for its edge.
        side.setColor(0xFF424243);
        c.drawText(text, x - step * 0.6f, y - step * 0.6f, side);
        // The face, brushed gold.
        face.setShader(gold);
        c.drawText(text, x, y, face);
        // Now and then a band of light crosses it, from the start of the word to its end.
        long t = now % (SWEEP_MS + REST_MS);
        if (t < SWEEP_MS) {
            float p = t / (float) SWEEP_MS;
            p = p * p * (3 - 2 * p);
            float bw = Math.max(laidW * 0.18f, 110 * density);
            if (band == null) {
                band = new LinearGradient(0, 0, bw, -bw * 0.4f,
                        new int[]{0x00FFFFFF, 0x10FFFFFF, 0x48FFFFFF, 0x10FFFFFF, 0x00FFFFFF},
                        new float[]{0f, 0.38f, 0.5f, 0.62f, 1f}, Shader.TileMode.CLAMP);
            }
            boolean rtl = isArabic(text);
            float from = rtl ? x + laidW + bw : x - bw * 1.4f, to = rtl ? x - bw * 1.4f : x + laidW + bw;
            move.setTranslate(from + (to - from) * p, y);
            band.setLocalMatrix(move);
            light.setShader(band);
            c.drawText(text, x, y, light);
        }
        if (isShown()) postInvalidateDelayed(33);
    }
}
