package com.gathin.tvhome.mibox;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * Synced lyrics, as music apps show them: every line of the track, the one being sung large and bright, the ones
 * around it dimmed, its smaller second line (a translation) right under it. The view scrolls smoothly so the line
 * being sung stays a third of the way down. Lines in a right-to-left script are laid out right to left. Everything is
 * centred, so a line and its translation in two scripts still sit together.
 */
public final class LyricsView extends View {
    private static final float NOW_ALPHA = 1f, NEAR_ALPHA = 0.42f, FAR_ALPHA = 0.24f;

    private final TextPaint mainPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final TextPaint subPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final TextPaint emptyPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final float density;
    private Lrc lrc;
    private String empty = "";
    private final List<StaticLayout[]> layouts = new ArrayList<>();
    private final List<Float> tops = new ArrayList<>();
    private int laidOutFor = -1;
    private int current = -1;
    private float scroll;
    private ValueAnimator scroller;

    public LyricsView(Context c) {
        this(c, null);
    }

    public LyricsView(Context c, AttributeSet attrs) {
        super(c, attrs);
        density = c.getResources().getDisplayMetrics().scaledDensity;
        Typeface bold = UiFonts.bold(c), regular = UiFonts.regular(c);
        mainPaint.setColor(0xFFFFFFFF);
        mainPaint.setTextSize(30 * density);
        if (bold != null) mainPaint.setTypeface(bold);
        subPaint.setColor(0xFFFFFFFF);
        subPaint.setTextSize(17 * density);
        if (regular != null) subPaint.setTypeface(regular);
        emptyPaint.setColor(0x80FFFFFF);
        emptyPaint.setTextSize(18 * density);
        if (regular != null) emptyPaint.setTypeface(regular);
    }

    /** New lyrics (null for none: then the empty text is shown). */
    void setLyrics(Lrc l, String emptyText) {
        empty = emptyText == null ? "" : emptyText;
        if (l == lrc) {
            invalidate();
            return;
        }
        lrc = l;
        current = -1;
        laidOutFor = -1;
        scroll = 0;
        if (scroller != null) scroller.cancel();
        invalidate();
    }

    /** Where the track is: the line being sung follows, scrolling there smoothly. */
    void setPosition(long ms) {
        if (lrc == null) return;
        int i = lrc.indexAt(ms);
        if (i == current) return;
        current = i;
        layout();
        float target = targetScroll();
        if (scroller != null) scroller.cancel();
        if (Math.abs(target - scroll) > getHeight() * 1.5f) {
            scroll = target;   // a seek: jump there
            invalidate();
            return;
        }
        scroller = ValueAnimator.ofFloat(scroll, target);
        scroller.setDuration(520);
        scroller.setInterpolator(new DecelerateInterpolator(1.6f));
        scroller.addUpdateListener(a -> {
            scroll = (float) a.getAnimatedValue();
            invalidate();
        });
        scroller.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        laidOutFor = -1;
        layout();
        scroll = targetScroll();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (scroller != null) scroller.cancel();
        super.onDetachedFromWindow();
    }

    private void layout() {
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        if (lrc == null || width <= 0 || laidOutFor == width) return;
        laidOutFor = width;
        layouts.clear();
        tops.clear();
        float y = 0, gap = 26 * density, between = 6 * density;
        for (Lrc.Line line : lrc.lines) {
            StaticLayout main = line.text.isEmpty() ? null : make(line.text, mainPaint, width);
            StaticLayout sub = line.sub.isEmpty() ? null : make(line.sub, subPaint, width);
            layouts.add(new StaticLayout[]{main, sub});
            tops.add(y);
            if (main == null && sub == null) {
                y += gap;   // a pause between lines
                continue;
            }
            if (main != null) y += main.getHeight();
            if (main != null && sub != null) y += between;
            if (sub != null) y += sub.getHeight();
            y += gap;
        }
    }

    private static StaticLayout make(String text, TextPaint paint, int width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setLineSpacing(0, 1.12f)
                .build();
    }

    /** The scroll that puts the current line a third of the way down. */
    private float targetScroll() {
        if (current < 0 || current >= tops.size()) return -getHeight() / 3f;
        return tops.get(current) - getHeight() / 3f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        if (lrc == null) {
            if (empty.isEmpty() || width <= 0) return;
            StaticLayout l = make(empty, emptyPaint, width);
            canvas.save();
            canvas.translate(getPaddingLeft(), (getHeight() - l.getHeight()) / 2f);
            l.draw(canvas);
            canvas.restore();
            return;
        }
        layout();
        float between = 6 * density;
        for (int i = 0; i < layouts.size(); i++) {
            float top = tops.get(i) - scroll;
            StaticLayout[] pair = layouts.get(i);
            float h = (pair[0] == null ? 0 : pair[0].getHeight()) + (pair[1] == null ? 0 : pair[1].getHeight() + between);
            if (top + h < 0 || top > getHeight()) continue;
            int d = Math.abs(i - current);
            float alpha = i == current ? NOW_ALPHA : d == 1 ? NEAR_ALPHA : FAR_ALPHA;
            canvas.save();
            canvas.translate(getPaddingLeft(), top);
            if (pair[0] != null) {
                mainPaint.setAlpha(Math.round(255 * alpha));
                pair[0].draw(canvas);
                canvas.translate(0, pair[0].getHeight() + between);
            }
            if (pair[1] != null) {
                subPaint.setAlpha(Math.round(255 * alpha * (i == current ? 0.78f : 0.9f)));
                pair[1].draw(canvas);
            }
            canvas.restore();
        }
    }
}
