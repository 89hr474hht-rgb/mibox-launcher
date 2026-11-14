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
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import com.gathin.launchos.design.Motion;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A word written very large and nearly black behind the music hub, carved: a little depth, a faint bevel on the
 * upper edges, a soft shadow. It fills the view's width or 80 % of its height, right-aligned, and stays still; one band
 * of light crosses it when it first shows. Arabic is set in Android's Naskh face, other scripts in LaunchOS's own.
 *
 * <p>The word is carved once, off the main thread, into a picture of at most {@link #MAX_SIDE} pixels a side; drawing
 * it is then one bitmap. Nothing redraws once the light has passed.
 */
public final class ShineText extends View {
    private static final int MAX_SIDE = 2048, DEPTH = 8;
    private static final long SWEEP_MS = 2400;
    private static final String NASKH = "/system/fonts/NotoNaskhArabic-Bold.ttf";
    private static final ExecutorService CARVER = Executors.newSingleThreadExecutor();

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Paint show = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint light = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix place = new Matrix(), bandAt = new Matrix();
    private final Typeface latin, arabic;
    private Bitmap carved;
    /** The band of light and the word it lights, made once per carved picture; each frame only moves the band. */
    private LinearGradient band;
    private float bandWidth;
    private float scale = 1f;
    private String text = "";
    private int carvedFor;
    private long shownAt = -1;

    public ShineText(Context c) {
        this(c, null);
    }

    public ShineText(Context c, AttributeSet attrs) {
        super(c, attrs);
        latin = UiFonts.semibold(c);
        Typeface naskh = null;
        try {
            if (new File(NASKH).canRead()) naskh = Typeface.createFromFile(NASKH);
        } catch (RuntimeException e) {
            Logs.failed("ShineText.font", e);
        }
        arabic = naskh;
        light.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
    }

    void setText(String s) {
        String t = s == null ? "" : s.trim();
        if (t.equals(text)) return;
        text = t;
        carve();
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
        carve();
    }

    @Override
    protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (visibility == VISIBLE && isShown()) {
            shownAt = SystemClock.uptimeMillis();   // the light passes once each time the hub comes back
            invalidate();
        }
    }

    /** Carves the word in the background; the picture replaces the old one when it is ready. */
    private void carve() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || text.isEmpty()) return;
        int job = ++carvedFor;
        String word = text;
        boolean rtl = isArabic(word);
        Typeface tf = rtl && arabic != null ? arabic : latin;
        float s = Math.min(1f, MAX_SIDE / (float) Math.max(w, h));
        CARVER.execute(() -> {
            Bitmap b = draw(word, rtl, tf, w, h, s);
            main.post(() -> {
                if (job != carvedFor) return;
                carved = b;
                scale = s;
                prepareLight();
                if (shownAt < 0) shownAt = SystemClock.uptimeMillis();
                invalidate();
            });
        });
    }

    /** The carved word at the size it shows, scaled down to fit MAX_SIDE. Background thread. */
    private static Bitmap draw(String word, boolean rtl, Typeface tf, int w, int h, float scale) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        if (tf != null) p.setTypeface(tf);
        p.setLetterSpacing(rtl ? 0f : -0.03f);
        float size = h * 0.8f;
        p.setTextSize(size);
        float tw = p.measureText(word);
        if (tw > w * 0.98f) {
            size *= w * 0.98f / tw;
            p.setTextSize(size);
            tw = p.measureText(word);
        }
        float step = Math.max(1f, size / 170f);
        Bitmap b = Bitmap.createBitmap(Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(scale, scale);
        float x = w - tw - step * DEPTH, y = (h - p.descent() - p.ascent()) / 2f;
        float top = y + p.ascent(), bottom = y + p.descent();
        p.setMaskFilter(new BlurMaskFilter(Math.max(4f, size / 24f), BlurMaskFilter.Blur.NORMAL));
        p.setColor(0xCC000000);
        c.drawText(word, x + step * DEPTH * 0.8f, y + step * DEPTH * 1.2f, p);
        p.setMaskFilter(null);
        p.setShader(new LinearGradient(0, top, 0, bottom, 0xFF0D0D0E, 0xFF070708, Shader.TileMode.CLAMP));
        for (int i = DEPTH; i >= 1; i--) c.drawText(word, x + i * step * 0.55f, y + i * step, p);
        p.setShader(null);
        p.setColor(0xFF1F1F21);
        c.drawText(word, x - step * 0.6f, y - step * 0.6f, p);
        p.setShader(new LinearGradient(0, top, 0, bottom, new int[]{0xFF19191A, 0xFF121213, 0xFF0E0E0F},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawText(word, x, y, p);
        return b;
    }

    private void prepareLight() {
        int w = getWidth();
        bandWidth = w * 0.22f;
        band = new LinearGradient(0, 0, bandWidth, -bandWidth * 0.5f,
                new int[]{0x00FFFFFF, 0x0AFFFFFF, 0x26FFFFFF, 0x0AFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.35f, 0.5f, 0.65f, 1f}, Shader.TileMode.CLAMP);
        place.setScale(1 / scale, 1 / scale);
        BitmapShader word = new BitmapShader(carved, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        word.setLocalMatrix(place);
        light.setShader(new ComposeShader(word, band, PorterDuff.Mode.SRC_IN));
    }

    @Override
    protected void onDraw(Canvas c) {
        if (carved == null) return;
        place.setScale(1 / scale, 1 / scale);
        c.drawBitmap(carved, place, show);
        if (!Motion.on() || shownAt < 0 || band == null) return;
        long t = SystemClock.uptimeMillis() - shownAt;
        if (t >= SWEEP_MS) return;
        float q = t / (float) SWEEP_MS;
        q = q * q * (3 - 2 * q);
        int w = getWidth();
        float bw = bandWidth;
        boolean rtl = isArabic(text);
        float from = rtl ? w + bw : -bw * 1.6f, to = rtl ? -bw * 1.6f : w + bw;
        bandAt.setTranslate(from + (to - from) * q, getHeight() / 2f);
        band.setLocalMatrix(bandAt);
        c.drawRect(0, 0, w, getHeight(), light);
        postInvalidateOnAnimation();
    }
}
