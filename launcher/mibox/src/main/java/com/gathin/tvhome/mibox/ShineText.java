package com.gathin.tvhome.mibox;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import java.io.File;

/**
 * A word written far larger than the screen behind a page, the page floating over it. It is carved in a white so
 * dark it is barely there (a little depth, a faint bevel on its upper edges, a soft shadow), and it glides slowly from
 * one end to the other and back, so the whole word passes by in its own time, starting where it is read from (the
 * right for Arabic). Now and then a band of light crosses the letters. Words in Arabic script are set in Android's
 * Naskh face, others in LaunchOS's own.
 *
 * <p>The carved word is drawn once into a picture at half size; each frame only moves that picture, so the glide
 * stays smooth on a small box.
 */
public final class ShineText extends View {
    /** One way across; the glide then comes back. */
    private static final long GLIDE_MS = 32_000;
    private static final long SWEEP_MS = 5200, REST_MS = 3800;
    private static final int DEPTH = 10;
    private static final float SCALE = 0.5f;
    private static final String NASKH = "/system/fonts/NotoNaskhArabic-Bold.ttf";

    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint show = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint light = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix place = new Matrix(), bandAt = new Matrix();
    private final float density;
    private final Typeface latin, arabic;
    private Bitmap carved;
    private BitmapShader carvedShader;
    private LinearGradient band;
    private float wordW;
    private boolean rtl;
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
    }

    void setText(String s) {
        String t = s == null ? "" : s.trim();
        if (t.equals(text)) return;
        text = t;
        rtl = isArabic(t);
        drop();
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
        drop();
    }

    @Override
    protected void onDetachedFromWindow() {
        drop();
        super.onDetachedFromWindow();
    }

    private void drop() {
        if (carved != null) carved.recycle();
        carved = null;
        carvedShader = null;
        band = null;
    }

    /** Carves the word once, at half size: shadow, depth, bevel and face, in near-black greys. */
    private void carve() {
        int h = getHeight();
        Typeface tf = rtl && arabic != null ? arabic : latin;
        if (tf != null) paint.setTypeface(tf);
        paint.setLetterSpacing(rtl ? 0f : -0.03f);
        float size = h * 0.92f;
        paint.setTextSize(size);
        float step = size / 150f, pad = size * 0.12f + DEPTH * step;
        wordW = paint.measureText(text) + pad * 2;
        int bw = Math.max(1, Math.round(wordW * SCALE)), bh = Math.max(1, Math.round(h * SCALE));
        carved = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(carved);
        c.scale(SCALE, SCALE);
        float x = pad, y = (h - paint.descent() - paint.ascent()) / 2f + h * 0.04f;
        float top = y + paint.ascent(), bottom = y + paint.descent();
        // The shadow it casts on the wall, soft.
        paint.setMaskFilter(new BlurMaskFilter(Math.max(4f, size / 22f), BlurMaskFilter.Blur.NORMAL));
        paint.setColor(0xCC000000);
        c.drawText(text, x + step * DEPTH * 0.8f, y + step * DEPTH * 1.2f, paint);
        paint.setMaskFilter(null);
        // Its depth.
        paint.setShader(new LinearGradient(0, top, 0, bottom, 0xFF0D0D0E, 0xFF070708, Shader.TileMode.CLAMP));
        for (int i = DEPTH; i >= 1; i--) c.drawText(text, x + i * step * 0.55f, y + i * step, paint);
        paint.setShader(null);
        // A faint rim of light on the upper edges, which the face covers but for its edge.
        paint.setColor(0xFF1F1F21);
        c.drawText(text, x - step * 0.6f, y - step * 0.6f, paint);
        // The face: a white so dark it is barely there, a touch lighter at the top.
        paint.setShader(new LinearGradient(0, top, 0, bottom, new int[]{0xFF19191A, 0xFF121213, 0xFF0E0E0F},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawText(text, x, y, paint);
        paint.setShader(null);
        carvedShader = new BitmapShader(carved, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (text.isEmpty() || getWidth() == 0 || getHeight() == 0) return;
        if (carved == null) carve();
        int w = getWidth();
        long now = SystemClock.uptimeMillis() - born;
        // Where the word is: the whole of it passes by, slowly, from where it is read from, and back.
        float x;
        if (wordW <= w) {
            x = (w - wordW) / 2f;
        } else {
            double phase = (now % (2 * GLIDE_MS)) / (double) GLIDE_MS;   // 0..2
            float p = (float) (phase <= 1 ? phase : 2 - phase);
            p = p * p * (3 - 2 * p);
            float start = rtl ? w - wordW : 0, end = rtl ? 0 : w - wordW;
            x = start + (end - start) * p;
        }
        place.setScale(1 / SCALE, 1 / SCALE);
        place.postTranslate(x, 0);
        c.drawBitmap(carved, place, show);
        // Now and then a band of light crosses the letters on screen.
        long t = now % (SWEEP_MS + REST_MS);
        if (t < SWEEP_MS) {
            float q = t / (float) SWEEP_MS;
            q = q * q * (3 - 2 * q);
            float bw = w * 0.22f;
            if (band == null) {
                band = new LinearGradient(0, 0, bw, -bw * 0.5f,
                        new int[]{0x00FFFFFF, 0x0AFFFFFF, 0x26FFFFFF, 0x0AFFFFFF, 0x00FFFFFF},
                        new float[]{0f, 0.35f, 0.5f, 0.65f, 1f}, Shader.TileMode.CLAMP);
            }
            float from = rtl ? w + bw : -bw * 1.6f, to = rtl ? -bw * 1.6f : w + bw;
            bandAt.setTranslate(from + (to - from) * q, getHeight() / 2f);
            band.setLocalMatrix(bandAt);
            carvedShader.setLocalMatrix(place);
            // The light where the letters are only.
            light.setShader(new ComposeShader(carvedShader, band, PorterDuff.Mode.SRC_IN));
            c.drawRect(0, 0, w, getHeight(), light);
        }
        if (isShown()) postInvalidateOnAnimation();
    }
}
