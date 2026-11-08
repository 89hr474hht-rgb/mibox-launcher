package com.gathin.launchos.tv;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.ScrollView;

/**
 * A scrolling column that keeps whatever has the focus out of the mini-player's corner: the bottom right of the
 * screen, where the picture-in-picture sits, plus a 24 dp margin ({@code ds_pip_zone_*}). It works out how much of
 * its own bottom lies in that corner, pads its content by that much so the last row can rise above it, and scrolls
 * a focused row until it clears it. Nothing that matters is ever left under the mini-player.
 */
public final class SafeScrollView extends ScrollView {
    private final int zoneWidth, zoneHeight;
    private final int[] where = new int[2];
    private int safe;

    public SafeScrollView(Context c) {
        this(c, null);
    }

    public SafeScrollView(Context c, AttributeSet attrs) {
        super(c, attrs);
        zoneWidth = c.getResources().getDimensionPixelSize(R.dimen.ds_pip_zone_width);
        zoneHeight = c.getResources().getDimensionPixelSize(R.dimen.ds_pip_zone_height);
        setClipToPadding(false);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int next = overlap();
        if (next != safe) {
            safe = next;
            setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), safe);
        }
        super.onLayout(changed, l, t, r, b);
    }

    /** How much of this view's bottom lies in the mini-player's corner, in pixels (0 when it does not reach it). */
    private int overlap() {
        getLocationOnScreen(where);
        int screenW = getRootView().getWidth(), screenH = getRootView().getHeight();
        if (screenW == 0 || screenH == 0) return safe;
        boolean reaches = where[0] + getWidth() > screenW - zoneWidth;
        int bottom = where[1] + getHeight();
        return reaches ? Math.max(0, bottom - (screenH - zoneHeight)) : 0;
    }

    /**
     * A focused row is scrolled until its bottom clears the corner, not just the screen's edge. ScrollView's own
     * version stops at the content's last pixel and so never scrolls into the padding; this one does.
     */
    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        if (safe == 0 || getChildCount() == 0) return super.computeScrollDeltaToGetChildRectOnScreen(rect);
        int top = getScrollY(), bottom = top + getHeight() - safe;
        int room = bottom - top, delta = 0;
        if (rect.bottom > bottom && rect.top > top) {
            delta = rect.height() > room ? rect.top - top : rect.bottom - bottom;
        } else if (rect.top < top && rect.bottom < bottom) {
            delta = rect.height() > room ? rect.bottom - bottom : rect.top - top;
        }
        int range = Math.max(0, getChildAt(0).getHeight() - (getHeight() - getPaddingTop() - getPaddingBottom()));
        return Math.max(-top, Math.min(delta, range - top));
    }
}
