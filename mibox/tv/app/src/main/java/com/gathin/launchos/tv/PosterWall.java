package com.gathin.launchos.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The login screen's background: rows of posters drifting slowly sideways, very dark (the view's alpha, 15 %).
 * The posters are those already kept on the box (the image cache), so nothing is downloaded and nothing is shown
 * on a first start; then the page keeps its plain glows. One frame every 50 ms, at most 18 small bitmaps.
 */
public final class PosterWall extends View {
    private static final int MAX = 18;
    private static final float POSTER_DP = 120, GAP_DP = 14;

    private final List<Bitmap> posters = new ArrayList<>();
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float density;
    private float shift;
    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            shift += density * 0.4f;
            invalidate();
            postDelayed(this, 50);
        }
    };

    public PosterWall(Context c) {
        this(c, null);
    }

    public PosterWall(Context c, AttributeSet attrs) {
        super(c, attrs);
        density = c.getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Context app = getContext().getApplicationContext();
        Bg.run(() -> load(app), (list, error) -> {
            if (list == null || list.isEmpty() || !isAttachedToWindow()) return;
            posters.clear();
            posters.addAll(list);
            removeCallbacks(frame);
            post(frame);
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        super.onDetachedFromWindow();
    }

    /** Background thread: a few posters from the image cache, picked at random. */
    private static List<Bitmap> load(Context app) {
        List<Bitmap> out = new ArrayList<>();
        File[] files = new File(app.getCacheDir(), "img").listFiles();
        if (files == null) return out;
        List<File> list = new ArrayList<>();
        Collections.addAll(list, files);
        Collections.shuffle(list);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 2;
        for (File f : list) {
            if (out.size() >= MAX) break;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
            // Posters only (taller than wide): logos and wide pictures look odd in the wall.
            if (b != null && b.getHeight() > b.getWidth() * 1.2f) out.add(b);
        }
        return out;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (posters.isEmpty()) return;
        float w = POSTER_DP * density, h = w * 1.5f, gap = GAP_DP * density;
        float stepX = w + gap, stepY = h + gap;
        int rows = (int) Math.ceil(getHeight() / stepY) + 1;
        int cols = (int) Math.ceil(getWidth() / stepX) + 2;
        int n = 0;
        for (int r = 0; r < rows; r++) {
            // Rows drift in turn one way and the other.
            float offset = ((r % 2 == 0 ? shift : -shift) % stepX + stepX) % stepX - stepX;
            for (int c = 0; c < cols; c++) {
                Bitmap b = posters.get((n++ + r * 3) % posters.size());
                float x = offset + c * stepX, y = r * stepY - h * 0.3f;
                box.set(x, y, x + w, y + h);
                canvas.drawBitmap(b, null, box, paint);
            }
        }
    }
}
