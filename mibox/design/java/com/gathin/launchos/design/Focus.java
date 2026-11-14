package com.gathin.launchos.design;

import android.view.View;

/** The focus scale both apps use, added next to whatever focus listener a view already has. */
public final class Focus {
    public static final float SCALE = 1.05f;
    /** For large things (a wide card), where 1.05 would push into the neighbours. */
    public static final float SCALE_LARGE = 1.025f;

    private Focus() {
    }

    public static void lift(View v) {
        lift(v, SCALE);
    }

    public static void lift(View v, float scale) {
        View.OnFocusChangeListener before = v.getOnFocusChangeListener();
        v.setOnFocusChangeListener((view, focused) -> {
            if (before != null) before.onFocusChange(view, focused);
            float s = focused ? scale : 1f;
            if (!Motion.on()) {
                view.setScaleX(s);
                view.setScaleY(s);
                return;
            }
            view.animate().scaleX(s).scaleY(s).setDuration(Motion.SHORT).setInterpolator(Motion.ENTER).start();
        });
    }
}
