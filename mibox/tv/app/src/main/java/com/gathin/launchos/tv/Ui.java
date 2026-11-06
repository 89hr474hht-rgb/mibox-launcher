package com.gathin.launchos.tv;

import android.content.Context;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Small shared pieces of the look: the lift of a focused card, and dp. */
final class Ui {
    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** A focused card grows a little and rises above its neighbours. */
    static void lift(View v, float scale) {
        v.setOnFocusChangeListener((view, focused) -> {
            float s = focused ? scale : 1f;
            view.animate().scaleX(s).scaleY(s).translationZ(focused ? dp(view.getContext(), 10) : 0)
                    .setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
        });
    }

    /** Images are cut to their frame's rounded corners. */
    static void roundClip(View frame) {
        frame.setClipToOutline(true);
    }
}
