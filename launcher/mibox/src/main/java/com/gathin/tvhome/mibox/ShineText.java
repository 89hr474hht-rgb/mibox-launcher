package com.gathin.tvhome.mibox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

/**
 * A word written very large behind a page, as a quiet backdrop: carved a little out of the dark (a short extrusion
 * below and to the right), its face faint, and a soft band of light passing across it from left to right every few
 * seconds, all along the word. About thirty frames a second while it shows; nothing while it is hidden.
 */
public final class ShineText extends View {
    private static final long SWEEP_MS = 4600, REST_MS = 1600;
    private static final int DEPTH = 7;

    private final TextPaint face = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint side = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint light = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Matrix move = new Matrix();
    private final float density;
    private LinearGradient band;
    private String text = "";
    private int tint = 0xFFF3EEE4;
    private long born = SystemClock.uptimeMillis();

    public ShineText(Context c) {
        this(c, null);
    }

    public ShineText(Context c, AttributeSet attrs) {
        super(c, attrs);
        density = c.getResources().getDisplayMetrics().density;
        Typeface t = UiFonts.semibold(c);
        for (TextPaint p : new TextPaint[]{face, side, light}) {
            if (t != null) p.setTypeface(t);
            p.setLetterSpacing(-0.03f);
        }
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void setText(String s) {
        String t = s == null ? "" : s;
        if (t.equals(text)) return;
        text = t;
        fit();
        born = SystemClock.uptimeMillis();
        invalidate();
    }

    /** The colour of the face and of the light: cream, or a collection's tone. */
    void setTint(int color) {
        tint = color;
        band = null;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        fit();
    }

    /** As large as the height allows, and no wider than the view. */
    private void fit() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || text.isEmpty()) return;
        float size = h * 0.92f;
        face.setTextSize(size);
        float tw = face.measureText(text);
        if (tw > w * 0.98f) size *= w * 0.98f / tw;
        for (TextPaint p : new TextPaint[]{face, side, light}) p.setTextSize(size);
        band = null;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (text.isEmpty() || getWidth() == 0) return;
        float tw = face.measureText(text);
        float x = getWidth() - tw, y = (getHeight() - face.descent() - face.ascent()) / 2f;
        // The depth: the word repeated a little lower and to the right, darker as it goes back.
        float step = Math.max(1.5f, face.getTextSize() / 160f);
        for (int i = DEPTH; i >= 1; i--) {
            int a = Math.round(26 * (1f - i / (float) (DEPTH + 1)));
            side.setColor(a << 24);
            c.drawText(text, x + i * step * 0.6f, y + i * step, side);
        }
        side.setColor(0x40000000);
        c.drawText(text, x + step * 0.6f, y + step, side);
        // A rim of light on the upper left edges, as on something carved: the face covers all but its edge.
        side.setColor((tint & 0x00FFFFFF) | 0x30000000);
        c.drawText(text, x - step * 0.7f, y - step * 0.7f, side);
        // The face: faint, a touch brighter at the top.
        face.setShader(new LinearGradient(0, y + face.ascent(), 0, y + face.descent(),
                0xFF1C1B1A, 0xFF0D0D0D, Shader.TileMode.CLAMP));
        c.drawText(text, x, y, face);
        // The light: a slanted band crossing the word, then a rest before the next pass.
        long t = (SystemClock.uptimeMillis() - born) % (SWEEP_MS + REST_MS);
        if (t < SWEEP_MS) {
            float p = t / (float) SWEEP_MS;
            p = p * p * (3 - 2 * p);
            float bw = Math.max(tw * 0.22f, 120 * density);
            if (band == null) {
                int rgb = tint & 0x00FFFFFF;
                band = new LinearGradient(0, 0, bw, -bw * 0.35f,
                        new int[]{rgb, rgb | 0x30000000, rgb | 0x85000000, rgb | 0x30000000, rgb},
                        new float[]{0f, 0.35f, 0.5f, 0.65f, 1f}, Shader.TileMode.CLAMP);
            }
            move.setTranslate(x - bw + (tw + bw * 2) * p, y);
            band.setLocalMatrix(move);
            light.setShader(band);
            c.drawText(text, x, y, light);
        }
        if (isShown()) postInvalidateDelayed(t < SWEEP_MS ? 33 : Math.max(33, SWEEP_MS + REST_MS - t));
    }
}
