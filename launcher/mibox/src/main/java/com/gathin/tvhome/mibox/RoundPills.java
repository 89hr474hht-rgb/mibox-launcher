package com.gathin.tvhome.mibox;

import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import java.util.List;

/**
 * The top bar's pills as round buttons: only the icon shows, and the pill with focus widens to show its name, then
 * closes again when focus moves on, so the bar leaves room for the date. The text stays in each pill (the launcher
 * and the Bluetooth pill keep updating it); a closed pill is just too narrow to show it and draws it transparent.
 */
final class RoundPills {
    private static final int SIZE_DP = 42;
    private static final int DURATION_MS = 220;

    private RoundPills() {
    }

    static void attach(List<TextView> pills) {
        for (TextView p : pills) {
            if (p != null) new Pill(p);
        }
    }

    private static final class Pill {
        private final TextView v;
        private final ColorStateList colors;
        private final int padStart, padEnd, drawPad, size, iconPad;
        private ValueAnimator anim;
        /** 0 closed, 1 open. */
        private float open;

        Pill(TextView v) {
            this.v = v;
            colors = v.getTextColors();
            padStart = v.getPaddingStart();
            padEnd = v.getPaddingEnd();
            drawPad = v.getCompoundDrawablePadding();
            float d = v.getResources().getDisplayMetrics().density;
            size = Math.round(SIZE_DP * d);
            Drawable icon = v.getCompoundDrawablesRelative()[0];
            int iw = icon != null && icon.getBounds().width() > 0 ? icon.getBounds().width() : Math.round(22 * d);
            iconPad = Math.max(0, (size - iw) / 2);
            // On one line: a closed pill is too narrow for its name, which would otherwise wrap a letter per line
            // and pull the pill's text baseline (the bar aligns pills on it) out of line with the others.
            if (v.getMaxLines() != 1) v.setSingleLine(true);
            View.OnFocusChangeListener before = v.getOnFocusChangeListener();
            v.setOnFocusChangeListener((view, focused) -> {
                if (before != null) before.onFocusChange(view, focused);
                animateTo(focused ? 1f : 0f);
            });
            if (v.hasFocus()) apply(1f, ViewGroup.LayoutParams.WRAP_CONTENT);
            else apply(0f, size);
        }

        private void animateTo(float target) {
            if (anim != null) anim.cancel();
            int from = v.getWidth() > 0 ? v.getWidth() : size;
            int to = target > 0 ? natural() : size;
            float start = open;
            boolean reduced = Settings.Global.getFloat(v.getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(reduced ? 0 : DURATION_MS);
            anim.setInterpolator(new DecelerateInterpolator(1.5f));
            anim.addUpdateListener(a -> {
                float f = (float) a.getAnimatedValue();
                apply(start + (target - start) * f, Math.round(from + (to - from) * f));
            });
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                private boolean cancelled;

                @Override
                public void onAnimationCancel(android.animation.Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    // Open, the pill follows its text again (a Bluetooth speaker's name, the timer's minutes).
                    if (!cancelled) apply(target, target > 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : size);
                }
            });
            anim.start();
        }

        /** The pill's width with its name shown, as the launcher lays it out. */
        private int natural() {
            int ps = v.getPaddingStart(), pe = v.getPaddingEnd(), dp = v.getCompoundDrawablePadding();
            v.setPaddingRelative(padStart, v.getPaddingTop(), padEnd, v.getPaddingBottom());
            v.setCompoundDrawablePadding(drawPad);
            int h = v.getHeight() > 0 ? v.getHeight() : size;
            v.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            int w = Math.max(size, v.getMeasuredWidth());
            v.setPaddingRelative(ps, v.getPaddingTop(), pe, v.getPaddingBottom());
            v.setCompoundDrawablePadding(dp);
            return w;
        }

        private void apply(float o, int width) {
            open = o;
            v.setPaddingRelative(lerp(iconPad, padStart, o), v.getPaddingTop(), lerp(iconPad, padEnd, o),
                    v.getPaddingBottom());
            v.setCompoundDrawablePadding(lerp(0, drawPad, o));
            if (o >= 1f) {
                v.setTextColor(colors);
            } else {
                // Fades in over the second half of the opening, once there is room for it.
                float a = Math.max(0f, (o - 0.5f) * 2f);
                int c = colors.getColorForState(v.getDrawableState(), colors.getDefaultColor());
                v.setTextColor(Color.argb(Math.round(Color.alpha(c) * a), Color.red(c), Color.green(c), Color.blue(c)));
            }
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null && lp.width != width) {
                lp.width = width;
                v.setLayoutParams(lp);
            }
        }

        private static int lerp(int a, int b, float f) {
            return Math.round(a + (b - a) * f);
        }
    }
}
