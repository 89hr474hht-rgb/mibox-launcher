package com.gathin.tvhome.mibox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;

/**
 * Synced lyrics, as music apps show them. The line being sung sits a little above the middle: its words light up one
 * after the other as they are sung, from grey to white with a soft edge, and its smaller second line (a translation)
 * sits under it. The lines around it are dimmed and fade out towards the edges; a pause between lines shows three
 * dots. When the next line comes, everything glides up.
 *
 * <p>Only the lines on screen are laid out, when they first show, so a track with hundreds of long lines opens at
 * once. Lines in a right-to-left script are laid out and lit right to left. The position comes from a {@link Clock}
 * read on every frame while the music plays, so the highlight moves smoothly.
 */
public final class LyricsView extends View {
    /** Where the lyrics are, read on every frame. */
    interface Clock {
        long now();
    }

    private static final float DIM = 0.30f, NEAR = 0.40f, FAR = 0.24f;
    private static final long GLIDE_MS = 560;
    /** About 25 frames a second while words light up: smooth enough, and light on a small box. */
    private static final long FRAME_MS = 40;
    private static final float[][] SIZES = {{24, 15}, {28, 17}, {34, 20}};

    /** Smooth text: anti-aliased, placed to the sub-pixel, so it glides without stepping. */
    private static final int SMOOTH = TextPaint.ANTI_ALIAS_FLAG | TextPaint.SUBPIXEL_TEXT_FLAG;
    private final TextPaint mainPaint = new TextPaint(SMOOTH);
    private final TextPaint subPaint = new TextPaint(SMOOTH);
    private final TextPaint emptyPaint = new TextPaint(SMOOTH);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private Lrc lrc;
    private String empty = "";
    private StaticLayout emptyLayout;
    private Clock clock;
    private boolean playing, translation = true, words = true;

    /** A line laid out: its two layouts (either may be null) and its height. */
    private static final class Laid {
        StaticLayout main, sub;
        float height;
    }

    private final SparseArray<Laid> laid = new SparseArray<>();
    private int laidWidth = -1;
    private int current = -1;
    /** The glide: how far the lines start from their place, easing to zero. */
    private float glideFrom;
    private long glideStart;
    /** The last highlight edge measured, kept while it stays on the same character. */
    private int edgeChar = -1, edgeLine = -1;
    private float edgeX0, edgeX1;

    public LyricsView(Context c) {
        this(c, null);
    }

    public LyricsView(Context c, AttributeSet attrs) {
        super(c, attrs);
        density = c.getResources().getDisplayMetrics().scaledDensity;
        Typeface strong = UiFonts.semibold(c), regular = UiFonts.regular(c);
        mainPaint.setColor(0xFFFFFFFF);
        if (strong != null) mainPaint.setTypeface(strong);
        subPaint.setColor(0xFFFFFFFF);
        if (regular != null) subPaint.setTypeface(regular);
        emptyPaint.setColor(0x8CFFFFFF);
        emptyPaint.setTextSize(19 * density);
        if (regular != null) emptyPaint.setTypeface(regular);
        dot.setColor(0xFFFFFFFF);
        setSize(LyricsStyle.MEDIUM);
    }

    /** Text size (LyricsStyle.SMALL to LARGE), the translation line, and the word-by-word highlight. */
    void setStyle(int size, boolean translation, boolean words) {
        this.translation = translation;
        this.words = words;
        setSize(size);
        relayout();
    }

    private void setSize(int size) {
        float[] s = SIZES[Math.max(0, Math.min(SIZES.length - 1, size))];
        mainPaint.setTextSize(s[0] * density);
        subPaint.setTextSize(s[1] * density);
    }

    void setClock(Clock c) {
        clock = c;
        invalidate();
    }

    /** While the music plays the view redraws every frame, for the highlight to move. */
    void setPlaying(boolean on) {
        playing = on;
        invalidate();
    }

    /** New lyrics (null for none: then the text given is shown in their place). */
    void setLyrics(Lrc l, String emptyText) {
        String e = emptyText == null ? "" : emptyText;
        if (!e.equals(empty)) {
            empty = e;
            emptyLayout = null;
        }
        if (l != lrc) {
            lrc = l;
            current = -1;
            glideFrom = 0;
            relayout();
        }
        invalidate();
    }

    private void relayout() {
        laid.clear();
        laidWidth = -1;
        emptyLayout = null;
        edgeLine = -1;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        relayout();
    }

    // ---- layout, one line at a time ----

    private int width() {
        return getWidth() - getPaddingLeft() - getPaddingRight();
    }

    private Laid line(int i) {
        int w = width();
        if (w != laidWidth) {
            laid.clear();
            laidWidth = w;
        }
        Laid l = laid.get(i);
        if (l != null) return l;
        l = new Laid();
        Lrc.Line ln = lrc.lines.get(i);
        if (!ln.text.isEmpty()) l.main = make(ln.text, mainPaint, w);
        if (translation && !ln.sub.isEmpty()) l.sub = make(ln.sub, subPaint, w);
        float h = 0;
        if (l.main != null) h += l.main.getHeight();
        if (l.main != null && l.sub != null) h += 8 * density;
        if (l.sub != null) h += l.sub.getHeight();
        // A pause between lines takes the room of its three dots.
        l.height = h == 0 ? 18 * density : h;
        laid.put(i, l);
        return l;
    }

    private float gap() {
        return mainPaint.getTextSize() * 0.95f;
    }

    private static StaticLayout make(String text, TextPaint paint, int width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, Math.max(1, width))
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setLineSpacing(0, 1.1f)
                .build();
    }

    /** From the top of line a to the top of line b, laying out the lines between. */
    private float distance(int a, int b) {
        float d = 0;
        if (b > a) for (int i = a; i < b; i++) d += line(i).height + gap();
        else for (int i = b; i < a; i++) d -= line(i).height + gap();
        return d;
    }

    // ---- following the sound ----

    private void follow(long ms) {
        int i = lrc.indexAt(ms);
        if (i == current) return;
        int from = Math.max(0, current), to = Math.max(0, i);
        float glide = glideNow();
        current = i;
        edgeLine = -1;
        if (Math.abs(to - from) > 12) {
            glideFrom = 0;   // a seek: no glide
            return;
        }
        float d = distance(from, to) + glide;
        glideFrom = Math.abs(d) > getHeight() ? 0 : d;
        glideStart = SystemClock.uptimeMillis();
    }

    private float glideNow() {
        if (glideFrom == 0) return 0;
        float t = (SystemClock.uptimeMillis() - glideStart) / (float) GLIDE_MS;
        if (t >= 1) {
            glideFrom = 0;
            return 0;
        }
        float ease = 1 - (1 - t) * (1 - t) * (1 - t);
        return glideFrom * (1 - ease);
    }

    // ---- drawing ----

    @Override
    protected void onDraw(Canvas canvas) {
        int w = width();
        if (w <= 0) return;
        if (lrc == null || lrc.lines.isEmpty()) {
            drawEmpty(canvas, w);
            return;
        }
        long ms = clock == null ? 0 : clock.now();
        follow(ms);
        float glide = glideNow();
        int h = getHeight();
        float anchor = h * 0.38f;
        int c = Math.max(0, current);
        // A long line (a verse and its translation over several lines) moves up so it fits whole.
        float fit = Math.max(h * 0.08f, Math.min(anchor, h * 0.94f - line(c).height));
        float top = fit + glide;
        drawLine(canvas, c, top, ms);
        float y = top + line(c).height + gap();
        for (int i = c + 1; i < lrc.lines.size() && y < h; i++) {
            drawLine(canvas, i, y, ms);
            y += line(i).height + gap();
        }
        y = top;
        for (int i = c - 1; i >= 0 && y > 0; i--) {
            y -= line(i).height + gap();
            drawLine(canvas, i, y, ms);
        }
        if (glideFrom != 0) {
            postInvalidateOnAnimation();
        } else if (playing) {
            postInvalidateDelayed(nextFrame(ms));
        }
    }

    /**
     * When to draw again while the music plays: often while a line is being lit or the pause dots breathe, else only
     * when the next line is due. A box draws little; the sound is never starved.
     */
    private long nextFrame(long ms) {
        if (current >= 0 && current < lrc.lines.size()) {
            Lrc.Line ln = lrc.lines.get(current);
            if (ln.text.isEmpty() && ln.sub.isEmpty()) return FRAME_MS;
            if (words && !ln.text.isEmpty() && ms < ln.endMs()) return FRAME_MS;
        }
        int next = current + 1;
        if (next < lrc.lines.size()) {
            return Math.max(FRAME_MS, Math.min(500, lrc.lines.get(next).atMs - ms));
        }
        return 500;
    }

    private void drawEmpty(Canvas canvas, int w) {
        if (empty.isEmpty()) return;
        if (emptyLayout == null) emptyLayout = make(empty, emptyPaint, Math.min(w, Math.round(520 * density)));
        canvas.save();
        canvas.translate(getPaddingLeft() + (w - emptyLayout.getWidth()) / 2f, (getHeight() - emptyLayout.getHeight()) * 0.4f);
        emptyLayout.draw(canvas);
        canvas.restore();
    }

    /** Lines fade out near the top and the bottom. */
    private float edgeFade(float top, float height) {
        float mid = top + height / 2f, h = getHeight();
        float in = Math.min((mid - h * 0.02f) / (h * 0.22f), (h - mid) / (h * 0.2f));
        return Math.max(0f, Math.min(1f, in));
    }

    private void drawLine(Canvas canvas, int i, float top, long ms) {
        Laid l = line(i);
        float fade = edgeFade(top, l.height);
        if (fade <= 0.01f) return;
        boolean now = i == current;
        int d = Math.abs(i - Math.max(0, current));
        float base = now ? DIM : d == 1 ? NEAR : FAR;
        canvas.save();
        canvas.translate(getPaddingLeft(), top);
        Lrc.Line ln = lrc.lines.get(i);
        if (l.main == null && l.sub == null) {
            if (now) drawDots(canvas, l.height, ms, ln, fade);
            canvas.restore();
            return;
        }
        if (l.main != null) {
            if (now && words) {
                mainPaint.setAlpha(Math.round(255 * DIM * fade));
                l.main.draw(canvas);
                light(canvas, l.main, i, ln.sung(ms), fade);
            } else {
                mainPaint.setAlpha(Math.round(255 * (now ? 1f : base) * fade));
                l.main.draw(canvas);
            }
            canvas.translate(0, l.main.getHeight() + 8 * density);
        }
        if (l.sub != null) {
            subPaint.setAlpha(Math.round(255 * (now ? 0.74f : base * 0.85f) * fade));
            l.sub.draw(canvas);
        }
        canvas.restore();
    }

    /** Draws the part of the line already sung in full white, its edge soft, line by line of the layout. */
    private void light(Canvas canvas, StaticLayout layout, int index, float sung, float fade) {
        if (sung <= 0) return;
        mainPaint.setAlpha(Math.round(255 * fade));
        float soft = mainPaint.getTextSize() * 0.9f, pad = mainPaint.getTextSize();
        for (int L = 0; L < layout.getLineCount(); L++) {
            int start = layout.getLineStart(L), end = layout.getLineEnd(L);
            CharSequence text = layout.getText();
            while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;
            if (sung <= start) break;
            float left = layout.getLineLeft(L), right = layout.getLineRight(L);
            int top = layout.getLineTop(L), bottom = layout.getLineBottom(L);
            canvas.save();
            canvas.clipRect(left - pad, top, right + pad, bottom);
            if (sung >= end) {
                layout.draw(canvas);
            } else {
                boolean rtl = layout.getParagraphDirection(L) == Layout.DIR_RIGHT_TO_LEFT;
                float x = edge(layout, index, L, sung, end, rtl, left, right);
                int on = 0xFFFFFFFF, off = 0x00FFFFFF;
                mainPaint.setShader(rtl
                        ? new LinearGradient(x - soft / 2, 0, x + soft / 2, 0, off, on, Shader.TileMode.CLAMP)
                        : new LinearGradient(x - soft / 2, 0, x + soft / 2, 0, on, off, Shader.TileMode.CLAMP));
                layout.draw(canvas);
                mainPaint.setShader(null);
            }
            canvas.restore();
        }
    }

    /** Where the highlight's edge is on line L of the layout, between two characters as the word goes on. */
    private float edge(StaticLayout layout, int index, int L, float sung, int end, boolean rtl, float left, float right) {
        int ch = (int) sung;
        if (index != edgeLine || ch != edgeChar) {
            edgeLine = index;
            edgeChar = ch;
            edgeX0 = layout.getPrimaryHorizontal(ch);
            edgeX1 = ch + 1 >= end ? (rtl ? left : right) : layout.getPrimaryHorizontal(ch + 1);
        }
        return edgeX0 + (edgeX1 - edgeX0) * (sung - ch);
    }

    /** Three dots breathing in turn during a pause between lines. */
    private void drawDots(Canvas canvas, float height, long ms, Lrc.Line ln, float fade) {
        float r = 4.5f * density, gap = 16 * density, cx = width() / 2f, cy = height / 2f;
        long since = Math.max(0, ms - ln.atMs);
        for (int k = 0; k < 3; k++) {
            double phase = (since / 600.0) - k * 0.35;
            float a = 0.35f + 0.55f * (float) (0.5 + 0.5 * Math.sin(phase * Math.PI));
            dot.setAlpha(Math.round(255 * a * fade));
            canvas.drawCircle(cx + (k - 1) * gap, cy, r, dot);
        }
    }
}
