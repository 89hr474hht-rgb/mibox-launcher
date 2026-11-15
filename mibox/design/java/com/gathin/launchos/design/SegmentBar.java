package com.gathin.launchos.design;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;

/**
 * A segmented choice both apps use: its choices sit on one dark track, and the current one is marked by a pill that
 * glides to the new choice when it changes. The choices are the bar's children; the one marked is the child that
 * is selected ({@link View#isSelected}), so the code that owns the values only selects a child, as it did before.
 * The children draw only their focus (white), the bar draws the track and the pill under them.
 *
 * <p>Coming into the bar from above or below, the focus lands on the current choice, not the nearest one. With
 * Android's animations off the pill moves at once.
 */
public class SegmentBar extends LinearLayout {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF shown = new RectF();
    private final RectF from = new RectF();
    private final RectF to = new RectF();
    private final RectF outline = new RectF();
    private final float radius, inset;
    private View marked;
    private boolean placed;
    private ValueAnimator glide;
    private Listener listener;

    /** Told when the pill starts moving to another choice (for a preview that follows it). */
    public interface Listener {
        void moved(View choice);
    }

    public SegmentBar(Context c) {
        this(c, null);
    }

    public SegmentBar(Context c, AttributeSet attrs) {
        super(c, attrs);
        float d = c.getResources().getDisplayMetrics().density;
        radius = 12 * d;
        inset = 3 * d;
        track.setColor(0xFF17181B);   // lo_surface_2
        pill.setColor(0xFF2C2D32);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(2 * d);
        edge.setColor(0xFFF4F3F0);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
    }

    /** The pill's fill and its outline (the app's accent). */
    public void setPill(int fill, int outline) {
        pill.setColor(fill);
        edge.setColor(outline);
        invalidate();
    }

    public void setTrack(int color) {
        track.setColor(color);
        invalidate();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        View first = null, last = null, now = null;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() != VISIBLE) continue;
            if (first == null) first = c;
            last = c;
            if (c.isSelected()) now = c;
        }
        if (first != null) {
            trackRect.set(first.getLeft() - inset, first.getTop() - inset, last.getRight() + inset,
                    last.getBottom() + inset);
            canvas.drawRoundRect(trackRect, radius + inset, radius + inset, track);
        }
        if (now != marked) moveTo(now);
        else if (now != null && !isGliding()) shown.set(now.getLeft(), now.getTop(), now.getRight(), now.getBottom());
        if (marked != null) {
            canvas.drawRoundRect(shown, radius, radius, pill);
            float h = edge.getStrokeWidth() / 2;
            outline.set(shown.left + h, shown.top + h, shown.right - h, shown.bottom - h);
            canvas.drawRoundRect(outline, radius - h, radius - h, edge);
        }
        super.dispatchDraw(canvas);
    }

    private boolean isGliding() {
        return glide != null && glide.isRunning();
    }

    private void moveTo(View now) {
        View before = marked;
        marked = now;
        if (now == null) return;
        to.set(now.getLeft(), now.getTop(), now.getRight(), now.getBottom());
        if (listener != null && before != null) listener.moved(now);
        if (before == null || !placed || !Motion.on() || !isShown()) {
            if (glide != null) glide.cancel();
            shown.set(to);
            placed = to.width() > 0;
            return;
        }
        from.set(shown);
        if (glide != null) glide.cancel();
        glide = ValueAnimator.ofFloat(0f, 1f);
        glide.setDuration(Motion.MEDIUM);
        glide.setInterpolator(Motion.MOVE);
        glide.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            // The pill stretches a little towards where it goes, then settles: it reads as moving, not blinking.
            float lead = Math.min(1f, t * 1.35f);
            boolean right = to.left > from.left;
            float l = right ? lerp(from.left, to.left, t) : lerp(from.left, to.left, lead);
            float r = right ? lerp(from.right, to.right, lead) : lerp(from.right, to.right, t);
            shown.set(l, lerp(from.top, to.top, t), r, lerp(from.bottom, to.bottom, t));
            invalidate();
        });
        glide.start();
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * A choice was selected (or focused): its own redraw would not run this bar's dispatchDraw, so the pill would not
     * notice. Selecting a child refreshes its drawable state, which reaches here.
     */
    @Override
    public void childDrawableStateChanged(View child) {
        super.childDrawableStateChanged(child);
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (marked != null && !isGliding()) {
            shown.set(marked.getLeft(), marked.getTop(), marked.getRight(), marked.getBottom());
            placed = shown.width() > 0;
        }
    }

    /** From outside, only the current choice takes the focus; inside, every choice does, for Left and Right. */
    @Override
    public void addFocusables(ArrayList<View> views, int direction, int mode) {
        if (hasFocus() || direction == FOCUS_LEFT || direction == FOCUS_RIGHT) {
            super.addFocusables(views, direction, mode);
            return;
        }
        View current = null;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.isSelected() && c.getVisibility() == VISIBLE && c.isFocusable()) current = c;
        }
        if (current == null) super.addFocusables(views, direction, mode);
        else views.add(current);
    }
}
