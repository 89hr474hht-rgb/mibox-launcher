package com.gathin.launchos.tv;

import android.view.View;
import android.view.ViewTreeObserver;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.launchos.design.Motion;

import java.util.Map;

/**
 * Up and Down between shelves of cards (item_row: a title over a horizontal list), the same on Accueil and on Films
 * and Séries. The shelf moved to goes to the top of the shelves' area with a short slide, and the focus lands on its
 * card under the one that had it (or where that shelf was left). When that shelf's cards are not attached yet, the
 * focus waits on the shelf's list and moves to the card after the next layout passes, so it is never lost.
 * Main thread only.
 */
final class ShelfNavigator {
    /** The key under which a shelf remembers its last card (rows of the same kind share it across rebuilds). */
    interface Keys {
        String key(RecyclerView.ViewHolder shelf);
    }

    /** Layout passes to wait for a shelf's cards before giving up (the focus then stays on the shelf's list). */
    private static final int MAX_PASSES = 8;
    private static final float SLIDE_DP = 32;

    private final RecyclerView rows;
    private final LinearLayoutManager layout;
    private final Map<String, Integer> positions;
    private final Keys keys;
    private ViewTreeObserver.OnGlobalLayoutListener waiting;

    ShelfNavigator(RecyclerView rows, LinearLayoutManager layout, Map<String, Integer> positions, Keys keys) {
        this.rows = rows;
        this.layout = layout;
        this.positions = positions;
        this.keys = keys;
        // Room after the last shelf, so it can reach the top of the area like the others and the shelf above it
        // leaves the screen whole instead of showing the bottom of its titles.
        rows.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect out, View view, RecyclerView parent, RecyclerView.State state) {
                if (parent.getChildAdapterPosition(view) != state.getItemCount() - 1) return;
                int h = view.getHeight() > 0 ? view.getHeight() : view.getMeasuredHeight();
                out.bottom = Math.max(0, parent.getHeight() - h);
            }
        });
    }

    /**
     * The shelves' vertical layout. Up and Down are handed to the screen's router. Android scrolls a list to show a
     * newly focused child; here it only does so when the top of the shelf holding the focus is off screen (focus given
     * back after another screen, for instance), since the navigator places every shelf it moves to itself.
     */
    static LinearLayoutManager layout(android.content.Context c) {
        return new LinearLayoutManager(c) {
            /**
             * Up and Down go to the screen's router (and so to {@link #move}) instead of to whatever card the list
             * finds next on its own, which it would then scroll to by just enough to show.
             */
            @Override
            public View onInterceptFocusSearch(View focused, int direction) {
                if (direction != View.FOCUS_UP && direction != View.FOCUS_DOWN) return null;
                for (View v = focused; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
                    if (v instanceof RecyclerView && ((RecyclerView) v).getLayoutManager() == this) {
                        android.view.ViewParent up = v.getParent();
                        return up == null ? null : up.focusSearch(focused, direction);
                    }
                }
                return null;
            }

            @Override
            public boolean requestChildRectangleOnScreen(RecyclerView parent, View child, android.graphics.Rect rect,
                                                         boolean immediate, boolean focusedChildVisible) {
                if (child.getTop() >= 0 && child.getTop() < parent.getHeight()) return false;
                return super.requestChildRectangleOnScreen(parent, child, rect, immediate, focusedChildVisible);
            }
        };
    }

    /** Moves to shelf target (step -1 up, 1 down), onto the card under focused; returns the view that has the focus now. */
    View move(View focused, int target, int step) {
        int[] at = new int[2];
        focused.getLocationOnScreen(at);
        int x = at[0] + focused.getWidth() / 2;
        rows.invalidateItemDecorations();   // the room after the last shelf is measured once the list has its size
        layout.scrollToPositionWithOffset(target, 0);
        if (Motion.on()) {
            rows.setTranslationY(step * Ui.dp(rows.getContext(), SLIDE_DP));
            rows.animate().translationY(0).setDuration(Motion.MEDIUM).setInterpolator(Motion.ENTER).start();
        }
        focusWhenLaid(target, x);
        // The card (or the shelf's list) that took the focus; handing back the old card would take it away again.
        View now = rows.getRootView().findFocus();
        return now != null && now.isAttachedToWindow() ? now : focused;
    }

    /** Focuses shelf row's card nearest to screen x as soon as it is laid out. */
    private void focusWhenLaid(int row, int x) {
        stopWaiting();
        if (focus(row, x)) return;
        waiting = new ViewTreeObserver.OnGlobalLayoutListener() {
            private int passes;

            @Override
            public void onGlobalLayout() {
                if (focus(row, x) || ++passes > MAX_PASSES) stopWaiting();
            }
        };
        rows.getViewTreeObserver().addOnGlobalLayoutListener(waiting);
    }

    private void stopWaiting() {
        if (waiting == null) return;
        rows.getViewTreeObserver().removeOnGlobalLayoutListener(waiting);
        waiting = null;
    }

    /** True when the card got the focus; else, if the old card left the screen, the shelf's list holds it. */
    private boolean focus(int row, int x) {
        View card = cardIn(row, x);
        if (card != null) {
            card.requestFocus();
            return true;
        }
        View rowView = layout.findViewByPosition(row);
        View f = rows.getRootView().findFocus();
        if (rowView != null && (f == null || !f.isAttachedToWindow() || !Root.inside(f, rows))) {
            View list = rowView.findViewById(R.id.row_list);
            if (list != null) list.requestFocus();
        }
        return false;
    }

    /** The card in a shelf nearest to screen x (or where the shelf was left when x is -1); null when not laid out. */
    View cardIn(int row, int x) {
        View rowView = layout.findViewByPosition(row);
        if (rowView == null) return null;
        RecyclerView list = rowView.findViewById(R.id.row_list);
        if (list == null || list.getChildCount() == 0) return null;
        RecyclerView.ViewHolder holder = rows.getChildViewHolder(rowView);
        String key = holder == null ? null : keys.key(holder);
        Integer left = key == null ? null : positions.get(key);
        if (x < 0 && left != null && list.getLayoutManager() != null) {
            View v = list.getLayoutManager().findViewByPosition(left);
            if (v != null) return v;
        }
        View best = null;
        int bestDistance = Integer.MAX_VALUE;
        int[] at = new int[2];
        for (int i = 0; i < list.getChildCount(); i++) {
            View c = list.getChildAt(i);
            if (!c.isFocusable()) continue;
            c.getLocationOnScreen(at);
            if (at[0] < 0) continue;
            int d = x < 0 ? at[0] : Math.abs(at[0] + c.getWidth() / 2 - x);
            if (d < bestDistance) {
                bestDistance = d;
                best = c;
            }
        }
        return best;
    }
}
