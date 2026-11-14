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
    }

    /** Moves to shelf target (step -1 up, 1 down), onto the card under focused; returns what keeps the focus now. */
    View move(View focused, int target, int step) {
        int[] at = new int[2];
        focused.getLocationOnScreen(at);
        int x = at[0] + focused.getWidth() / 2;
        layout.scrollToPositionWithOffset(target, 0);
        if (Motion.on()) {
            rows.setTranslationY(step * Ui.dp(rows.getContext(), SLIDE_DP));
            rows.animate().translationY(0).setDuration(Motion.MEDIUM).setInterpolator(Motion.ENTER).start();
        }
        focusWhenLaid(target, x);
        return focused;
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
