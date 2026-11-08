package com.gathin.launchos.tv;

import android.app.Activity;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * A short message in a glass pill at the top of the screen, never in the mini-player's corner. It never takes the
 * focus, so the remote stays where it was, and it is read aloud. A new message replaces the one on screen.
 */
final class GlassToast {
    private static final long SHOWN_MS = 2600;
    private static final Object TAG = new Object();

    private GlassToast() {
    }

    static void show(Activity a, CharSequence text, boolean ok) {
        ViewGroup root = a.findViewById(android.R.id.content);
        if (root == null) return;
        View old = root.findViewWithTag(TAG);
        if (old != null) root.removeView(old);

        View v = LayoutInflater.from(a).inflate(R.layout.view_glass_toast, root, false);
        v.setTag(TAG);
        ImageView icon = v.findViewById(R.id.toast_icon);
        icon.setImageResource(ok ? R.drawable.ic_check_circle : R.drawable.ic_alert);
        icon.setImageTintList(a.getColorStateList(ok ? R.color.ds_ok : R.color.ds_danger));
        ((TextView) v.findViewById(R.id.toast_text)).setText(text);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = Ui.dp(a, 28);
        root.addView(v, lp);

        v.setAlpha(0f);
        v.setTranslationY(-Ui.dp(a, 16));
        v.animate().alpha(1f).translationY(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        v.announceForAccessibility(text);
        v.postDelayed(() -> v.animate().alpha(0f).translationY(-Ui.dp(a, 12)).setDuration(260)
                .withEndAction(() -> root.removeView(v)).start(), SHOWN_MS);
    }
}
