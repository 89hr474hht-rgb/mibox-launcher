package com.gathin.launchos.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.gathin.launchos.design.Motion;

/**
 * The on/off switch of a settings row: activated is on. Turning it slides the knob across and fills the track with
 * the accent as it goes, in the time of a small state change; with Android's animations off it changes at once.
 * A switch shown for the first time is drawn in place, without sliding.
 */
public final class ToggleView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int on, off, knobOn, knobOff;
    private float t;
    private boolean drawn;
    private ValueAnimator anim;

    public ToggleView(Context c) {
        this(c, null);
    }

    public ToggleView(Context c, AttributeSet attrs) {
        super(c, attrs);
        on = c.getColor(R.color.lo_accent);
        off = c.getColor(R.color.lo_surface_3);
        knobOn = c.getColor(R.color.lo_text);
        knobOff = c.getColor(R.color.lo_text_2);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(c.getResources().getDisplayMetrics().density);
        edge.setColor(c.getColor(R.color.lo_line_strong));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    public void setActivated(boolean activated) {
        boolean was = isActivated();
        super.setActivated(activated);
        float target = activated ? 1f : 0f;
        if (anim != null) anim.cancel();
        if (was == activated || !drawn || !Motion.on() || !isShown()) {
            t = target;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(t, target);
        anim.setDuration(Motion.MEDIUM);
        anim.setInterpolator(Motion.MOVE);
        anim.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawn = true;
        float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
        r.set(0, 0, w, h);
        track.setColor(blend(off, on, t));
        canvas.drawRoundRect(r, h / 2, h / 2, track);
        edge.setAlpha(Math.round(255 * (1 - t)));
        r.inset(edge.getStrokeWidth() / 2, edge.getStrokeWidth() / 2);
        canvas.drawRoundRect(r, h / 2, h / 2, edge);
        float radius = 10 * d, inset = 3 * d;
        float x = inset + radius + (w - 2 * (inset + radius)) * t;
        knob.setColor(blend(knobOff, knobOn, t));
        canvas.drawCircle(x, h / 2, radius, knob);
    }

    private static int blend(int a, int b, float t) {
        return Color.argb(Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
}
