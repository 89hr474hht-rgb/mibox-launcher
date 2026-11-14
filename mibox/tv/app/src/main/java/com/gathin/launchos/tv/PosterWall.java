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
 * The login screen's background: still rows of posters, very dark (the view's alpha, 15 %). The posters are those
 * already kept on the box (the image cache), so nothing is downloaded and nothing is shown on a first start. Drawn
 * once, no animation: at most {@link #MAX} small bitmaps, decoded on {@link Bg#run} after reading only their size,
 * and dropped when the view leaves the window.
 */
public final class PosterWall extends View {
    private static final int MAX = 18;
    /** Files looked at, at most, to find {@link #MAX} posters (the cache also holds logos and wide pictures). */
    private static final int TRIES = 60;
    private static final float POSTER_DP = 120, GAP_DP = 14;

    private final List<Bitmap> posters = new ArrayList<>();
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float density;
    private int request;

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
        int r = ++request;
        int posterPx = Math.round(POSTER_DP * density);
        Bg.run(() -> load(app, posterPx), (list, error) -> {
            if (list == null || list.isEmpty() || r != request || !isAttachedToWindow()) return;
            posters.clear();
            posters.addAll(list);
            invalidate();
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        request++;
        posters.clear();
        super.onDetachedFromWindow();
    }

    /** Background thread: a few posters from the image cache, picked at random, decoded near the size they show at. */
    private static List<Bitmap> load(Context app, int widthPx) {
        List<Bitmap> out = new ArrayList<>();
        File[] files = new File(app.getCacheDir(), "img").listFiles();
        if (files == null) return out;
        List<File> list = new ArrayList<>();
        Collections.addAll(list, files);
        Collections.shuffle(list);
        int tries = 0;
        for (File f : list) {
            if (out.size() >= MAX || tries++ >= TRIES) break;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), bounds);
            // Posters only (taller than wide): logos and wide pictures look odd in the wall.
            if (bounds.outWidth <= 0 || bounds.outHeight <= bounds.outWidth * 1.2f) continue;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 1;
            while (bounds.outWidth / (o.inSampleSize * 2) >= widthPx) o.inSampleSize *= 2;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
            if (b != null) out.add(b);
        }
        return out;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (posters.isEmpty()) return;
        float w = POSTER_DP * density, h = w * 1.5f, gap = GAP_DP * density;
        float stepX = w + gap, stepY = h + gap;
        int rows = (int) Math.ceil(getHeight() / stepY) + 1;
        int cols = (int) Math.ceil(getWidth() / stepX) + 1;
        int n = 0;
        for (int r = 0; r < rows; r++) {
            // Every other row is offset by half a poster, like bricks.
            float offset = r % 2 == 0 ? -stepX / 2 : 0;
            for (int c = 0; c < cols; c++) {
                Bitmap b = posters.get((n++ + r * 3) % posters.size());
                float x = offset + c * stepX, y = r * stepY - h * 0.3f;
                box.set(x, y, x + w, y + h);
                canvas.drawBitmap(b, null, box, paint);
            }
        }
    }
}
