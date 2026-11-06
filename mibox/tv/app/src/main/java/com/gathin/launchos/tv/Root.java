package com.gathin.launchos.tv;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/**
 * The main screen's root. Every move of the remote's arrows passes here, so the screen decides where the focus goes
 * instead of the nearest view on screen: into the rail at the section shown, out of it back where the page was, and
 * never from one column into another by accident.
 */
public final class Root extends FrameLayout {
    /** Decides a move: return the view to focus, or proposed to keep Android's choice. */
    interface Router {
        View route(View focused, int direction, View proposed);
    }

    private Router router;

    public Root(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setRouter(Router router) {
        this.router = router;
    }

    @Override
    public View focusSearch(View focused, int direction) {
        View proposed = super.focusSearch(focused, direction);
        return router == null ? proposed : router.route(focused, direction, proposed);
    }

    /** True when v is inside (or is) container. */
    static boolean inside(View v, View container) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == container) return true;
        }
        return false;
    }
}
