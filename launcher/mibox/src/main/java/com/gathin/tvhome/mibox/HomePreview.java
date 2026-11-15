package com.gathin.tvhome.mibox;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import com.gathin.launchos.design.Motion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A small live picture of the home screen for Réglages: the clock, the dock with the real apps' banners, the name
 * above it and the free places of the rows under it, on the home screen's glow. It is drawn with the home screen's
 * own proportions (a 960 x 540 dp screen, the same tile and dock sums as the launcher), so changing the columns, the
 * rows under the dock or the dock's opacity shows here what it will do, with the tiles growing, the dock rising and
 * the band fading into place. A value only looked at (the focus on a choice) is shown too, marked "Aperçu", until OK.
 *
 * <p>It only reads: the installed apps, the launcher's saved order (order.txt) and its dock_apps, never writes. Banners are loaded
 * off the main thread once; then it draws only while something moves.
 */
public final class HomePreview extends View {
    // The home screen in dp, and the launcher's tile sums (AppAdapter, MainActivity, DockDecoration).
    // HomePreviewTest checks the launcher's own sums against these, so a change there is seen here.
    static final float SCREEN_W = 960, SCREEN_H = 540;
    static final float INNER_X = 60, INNER_W = 840;
    static final float DESIGN_W = 180, DESIGN_H = 101, FOCUS_SCALE = 1.10f, MIN_HEIGHT_FIT = 0.85f;
    static final float ITEM_EXTRA = 45, DOCK_EXTRA = 18, BOTTOM_ROOM = 30, TITLE_ROOM = 104;
    private static final float GRID_TOP = 140, BAND_PAD = 14, BAND_RADIUS = 26;
    private static final int MAX_APPS = 21;

    /** The three values it shows; each animates on its own. */
    private float columns = 6, rows = 0, dock = 100;
    private int columnsTo = 6, rowsTo = 0, dockTo = 100;
    private boolean peeking;
    private ValueAnimator anim;
    private float fromColumns, fromRows, fromDock;
    /** A soft flash of the part that just changed: 0 = columns, 1 = rows, 2 = dock. */
    private int flashPart = -1;
    private float flash;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Date now = new Date();
    /** The glow's two lights, made again only when the size or the colour changes. */
    private Shader glowA, glowB;
    private int glowFor, glowW;
    private final RectF r = new RectF();
    private final Path clip = new Path();
    private final List<Bitmap> banners = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private int glow = 0x8A1A2A;
    private String peekLabel = "";
    /** The clock as the settings page chose it: 0 Android's own format, 1 on 12 hours, 2 on 24 hours. */
    private int clock;

    public HomePreview(Context c) {
        this(c, null);
    }

    public HomePreview(Context c, AttributeSet attrs) {
        super(c, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        loadApps();
    }

    /** The values in force (after OK, or when the page opens): shown as they are, no "Aperçu" mark. */
    public void applied(int columns, int rows, int dockOpacity, boolean animate) {
        peeking = false;
        go(columns, rows, dockOpacity, animate);
    }

    /** The clock's format, as chosen in Horloge (0 system, 1 12 h, 2 24 h). */
    public void clock(int format) {
        if (format == clock) return;
        clock = format;
        invalidate();
    }

    /** A value the focus is on but that is not chosen yet: shown, marked "Aperçu". */
    public void peek(int columns, int rows, int dockOpacity, String label) {
        peeking = true;
        peekLabel = label;
        go(columns, rows, dockOpacity, true);
    }

    private void go(int c, int rw, int d, boolean animate) {
        int part = c != columnsTo ? 0 : rw != rowsTo ? 1 : d != dockTo ? 2 : -1;
        columnsTo = c;
        rowsTo = rw;
        dockTo = d;
        if (anim != null) anim.cancel();
        if (!animate || !Motion.on() || !isShown()) {
            columns = c;
            rows = rw;
            dock = d;
            flash = 0;
            invalidate();
            return;
        }
        if (part >= 0) flashPart = part;
        fromColumns = columns;
        fromRows = rows;
        fromDock = dock;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(Motion.LONG);
        anim.setInterpolator(Motion.MOVE);
        anim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            columns = fromColumns + (columnsTo - fromColumns) * t;
            rows = fromRows + (rowsTo - fromRows) * t;
            dock = fromDock + (dockTo - fromDock) * t;
            flash = part >= 0 ? (float) Math.sin(Math.PI * t) : 0;
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, Math.round(width * SCREEN_H / SCREEN_W));
    }

    // ---- drawing ----

    @Override
    protected void onDraw(Canvas canvas) {
        float s = getWidth() / SCREEN_W;   // px per home-screen dp
        if (s <= 0) return;
        float d = getResources().getDisplayMetrics().density;
        clip.reset();
        r.set(0, 0, getWidth(), getHeight());
        clip.addRoundRect(r, 14 * d, 14 * d, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        background(canvas);

        // The header: the clock and the date at the left, the four round buttons at the right.
        text.setColor(0xFFF4F3F0);
        text.setTypeface(light);
        text.setTextSize(64 * s);
        boolean twelve = clock == 1 || (clock == 0 && !android.text.format.DateFormat.is24HourFormat(getContext()));
        now.setTime(System.currentTimeMillis());
        String time = android.text.format.DateFormat.format(twelve ? "h:mm" : "HH:mm", now).toString();
        canvas.drawText(time, 56 * s, 95 * s, text);
        if (twelve) {
            float after = 56 * s + text.measureText(time) + 8 * s;
            text.setTextSize(17 * s);
            text.setLetterSpacing(0.1f);
            canvas.drawText(android.text.format.DateFormat.format("a", now).toString().toUpperCase(java.util.Locale.ROOT),
                    after, 82 * s, text);
            text.setLetterSpacing(0f);
        }
        text.setTextSize(15 * s);
        text.setColor(0x9EF4F3F0);
        text.setLetterSpacing(0.2f);
        canvas.drawText("BONJOUR", 61 * s, 40 * s, text);
        text.setLetterSpacing(0f);
        paint.setShader(null);
        paint.setColor(0xCC16171A);
        for (int i = 0; i < 4; i++) canvas.drawCircle((883 - i * 52) * s, 79 * s, 21 * s, paint);

        // The tiles: the launcher's sums, with the columns and rows as they glide.
        float cols = Math.max(1f, columns);
        float cell = INNER_W / cols;
        float width = Math.min(DESIGN_W, (cell - 12) / FOCUS_SCALE);
        width = Math.max(80, width);
        if (rows > 0) {
            float h = SCREEN_H - GRID_TOP;
            float perItem = (h - (DOCK_EXTRA + BOTTOM_ROOM + TITLE_ROOM)) / (rows + 1);
            float byHeight = (perItem - ITEM_EXTRA) * DESIGN_W / DESIGN_H;
            width = Math.max(Math.max(80, width * MIN_HEIGHT_FIT), Math.min(width, byHeight));
        }
        float tileH = width * DESIGN_H / DESIGN_W;
        float itemH = tileH + ITEM_EXTRA;
        float dockTop = SCREEN_H - BOTTOM_ROOM - DOCK_EXTRA - itemH * (1 + rows) + 12;
        dockTop = Math.max(GRID_TOP + TITLE_ROOM, dockTop);
        float radius = Math.max(6, width * 0.06f);

        int inDock = Math.max(1, Math.min((int) Math.ceil(cols), Math.max(1, banners.size())));
        float bandRight = INNER_X + Math.min(inDock, cols) * cell - (cell - width) / 2 + BAND_PAD;
        r.set((INNER_X + (cell - width) / 2 - BAND_PAD) * s, (dockTop - BAND_PAD) * s, bandRight * s,
                (dockTop + tileH + BAND_PAD + 1) * s);
        paint.setColor(Color.argb(Math.round(dock * 2.55f), 0x14, 0x14, 0x16));
        canvas.drawRoundRect(r, BAND_RADIUS * s, BAND_RADIUS * s, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, s));
        paint.setColor(flashPart == 2 ? mix(0x1FFFFFFF, 0xCCF2C879, flash) : 0x1FFFFFFF);
        canvas.drawRoundRect(r, BAND_RADIUS * s, BAND_RADIUS * s, paint);
        paint.setStyle(Paint.Style.FILL);

        // The dock's title: the first app's name above the band.
        text.setTypeface(medium);
        text.setColor(0xFFF4F3F0);
        text.setTextSize(48 * s);
        canvas.drawText(names.isEmpty() ? "LaunchOS" : names.get(0), INNER_X * s + 8 * s, (dockTop - 40) * s, text);

        // The dock's apps, then the free places of the rows under it, the last column fading in or out as it glides.
        int whole = (int) Math.floor(cols);
        float part = cols - whole;
        int count = (int) Math.ceil(cols);
        for (int row = 0; row <= Math.ceil(rows); row++) {
            float rowAlpha = row == 0 ? 1f : Math.min(1f, rows - (row - 1));
            if (rowAlpha <= 0) continue;
            float top = dockTop + row * itemH + (row > 0 ? DOCK_EXTRA / 3 : 0);
            for (int col = 0; col < count; col++) {
                float a = rowAlpha * (col < whole ? 1f : part);
                if (a <= 0.01f) continue;
                float left = INNER_X + col * cell + (cell - width) / 2;
                r.set(left * s, top * s, (left + width) * s, (top + tileH) * s);
                int index = row * count + col;
                Bitmap b = row == 0 && col < banners.size() ? banners.get(col) : null;
                if (b != null) {
                    canvas.save();
                    clip.reset();
                    clip.addRoundRect(r, radius * s, radius * s, Path.Direction.CW);
                    canvas.clipPath(clip);
                    paint.setAlpha(Math.round(255 * a));
                    canvas.drawBitmap(b, null, r, paint);
                    paint.setAlpha(255);
                    canvas.restore();
                } else {
                    // A free place: a faint outline, so the size and the count read without any app.
                    paint.setColor(Color.argb(Math.round(18 * a), 255, 255, 255));
                    canvas.drawRoundRect(r, radius * s, radius * s, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    boolean lit = (flashPart == 0 && row == 0) || (flashPart == 1 && row > 0);
                    paint.setColor(lit ? mix(Color.argb(Math.round(40 * a), 255, 255, 255), 0xCCF2C879, flash)
                            : Color.argb(Math.round(40 * a), 255, 255, 255));
                    canvas.drawRoundRect(r, radius * s, radius * s, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                if (index > MAX_APPS * 2) break;
            }
        }
        canvas.restore();

        // The frame, and the mark while a value is only looked at.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(d);
        paint.setColor(peeking ? 0x99F2C879 : 0x38FFFFFF);
        r.set(d / 2, d / 2, getWidth() - d / 2, getHeight() - d / 2);
        canvas.drawRoundRect(r, 14 * d, 14 * d, paint);
        paint.setStyle(Paint.Style.FILL);
        if (peeking && !peekLabel.isEmpty()) {
            text.setTypeface(medium);
            text.setTextSize(11 * d);
            float tw = text.measureText(peekLabel);
            r.set(getWidth() - tw - 24 * d, 8 * d, getWidth() - 8 * d, 28 * d);
            paint.setColor(0xE6F2C879);
            canvas.drawRoundRect(r, 10 * d, 10 * d, paint);
            text.setColor(0xFF0E0F11);
            canvas.drawText(peekLabel, r.left + 8 * d, r.bottom - 6 * d, text);
        }
    }

    /** The home screen's glow in the first app's colour, kept dark as LivingGlow keeps it. */
    private void background(Canvas canvas) {
        canvas.drawColor(0xFF000000);
        int w = getWidth(), h = getHeight();
        if (glowA == null || glowFor != glow || glowW != w) {
            int a = GlowColors.shift(glow, 0, 0.7f), b = GlowColors.shift(glow, 28, 0.45f);
            glowA = new RadialGradient(w * 0.3f, h * 0.65f, w * 0.75f, 0xFF000000 | a, 0xFF000000,
                    Shader.TileMode.CLAMP);
            glowB = new RadialGradient(w * 0.9f, h * 0.1f, w * 0.5f, 0x99000000 | b, 0x00000000,
                    Shader.TileMode.CLAMP);
            glowFor = glow;
            glowW = w;
        }
        paint.setShader(glowA);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(glowB);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }

    private static int mix(int x, int y, float t) {
        t = Math.max(0, Math.min(1, t));
        int a = Math.round(Color.alpha(x) + (Color.alpha(y) - Color.alpha(x)) * t);
        int rr = Math.round(Color.red(x) + (Color.red(y) - Color.red(x)) * t);
        int g = Math.round(Color.green(x) + (Color.green(y) - Color.green(x)) * t);
        int bb = Math.round(Color.blue(x) + (Color.blue(y) - Color.blue(x)) * t);
        return Color.argb(a, rr, g, bb);
    }

    // ---- the apps ----

    /** The TV apps, in the owner's saved order first, read once off the main thread. */
    private void loadApps() {
        if (isInEditMode()) return;
        Context app = getContext().getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            List<Bitmap> pics = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            int colour = glow;
            try {
                PackageManager pm = app.getPackageManager();
                List<ResolveInfo> found = pm.queryIntentActivities(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
                List<String> order = savedOrder(app);
                List<ResolveInfo> sorted = new ArrayList<>();
                for (String key : order) {
                    for (ResolveInfo ri : found) {
                        if (key.startsWith(ri.activityInfo.packageName + "/") && !sorted.contains(ri)) sorted.add(ri);
                    }
                }
                // Then the dock apps of the launcher's config.xml, in their order, as the home screen fills its dock.
                for (String pkg : UpstreamViews.array(app, "dock_apps")) {
                    for (ResolveInfo ri : found) {
                        if (ri.activityInfo.packageName.equals(pkg) && !sorted.contains(ri)) sorted.add(ri);
                    }
                }
                List<ResolveInfo> rest = new ArrayList<>();
                for (ResolveInfo ri : found) if (!sorted.contains(ri)) rest.add(ri);
                rest.sort((x, y) -> String.valueOf(x.loadLabel(pm)).compareToIgnoreCase(String.valueOf(y.loadLabel(pm))));
                sorted.addAll(rest);
                float d = app.getResources().getDisplayMetrics().density;
                int bw = Math.round(90 * d), bh = Math.round(bw * DESIGN_H / DESIGN_W);
                for (ResolveInfo ri : sorted) {
                    if (ri.activityInfo.packageName.equals(app.getPackageName())) continue;
                    if (pics.size() >= MAX_APPS) break;
                    Drawable banner = ri.activityInfo.loadBanner(pm);
                    if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
                    Bitmap bm = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(bm);
                    if (banner != null) {
                        banner.setBounds(0, 0, bw, bh);
                        banner.draw(c);
                    } else {
                        c.drawColor(0xFF222327);
                        Drawable icon = ri.loadIcon(pm);
                        int size = Math.round(bh * 0.6f);
                        icon.setBounds((bw - size) / 2, (bh - size) / 2, (bw + size) / 2, (bh + size) / 2);
                        icon.draw(c);
                    }
                    if (pics.isEmpty()) colour = average(bm);
                    pics.add(bm);
                    labels.add(String.valueOf(ri.loadLabel(pm)));
                }
            } catch (RuntimeException ignored) {
                // No apps to show: the preview keeps its free places, which still show the sizes.
            }
            int c = colour;
            main.post(() -> {
                banners.clear();
                banners.addAll(pics);
                names.clear();
                names.addAll(labels);
                glow = c;
                invalidate();
            });
        }, "home-preview").start();
    }

    /** The keys of order.txt (the launcher's own file, read only), first one first; none when there is no file. */
    private static List<String> savedOrder(Context c) {
        List<String> out = new ArrayList<>();
        File f = new File(c.getFilesDir(), "order.txt");
        if (!f.isFile()) return out;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f),
                StandardCharsets.UTF_8))) {
            for (String line; (line = in.readLine()) != null; ) {
                if (line.contains("/")) out.add(line.trim());
            }
        } catch (java.io.IOException ignored) {
            // Unreadable: the apps come in A to Z.
        }
        return out;
    }

    /** The banner's most coloured average, for the glow behind (a grey banner gives a soft grey glow). */
    private static int average(Bitmap b) {
        long rr = 0, g = 0, bb = 0;
        int n = 0;
        for (int y = 0; y < b.getHeight(); y += 4) {
            for (int x = 0; x < b.getWidth(); x += 4) {
                int p = b.getPixel(x, y);
                int max = Math.max(Color.red(p), Math.max(Color.green(p), Color.blue(p)));
                int min = Math.min(Color.red(p), Math.min(Color.green(p), Color.blue(p)));
                if (max - min < 40) continue;   // whites, greys and blacks say nothing about the colour
                rr += Color.red(p);
                g += Color.green(p);
                bb += Color.blue(p);
                n++;
            }
        }
        if (n == 0) return 0x3A3D44;
        return (int) (rr / n) << 16 | (int) (g / n) << 8 | (int) (bb / n);
    }
}
