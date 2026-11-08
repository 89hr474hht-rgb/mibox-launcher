package com.gathin.launchos.tv;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;

/**
 * A button that becomes its own progress gauge: at rest it shows what it refreshes and when that was last done;
 * pressed, its glass fills from the left as the work goes, with the percentage in the corner (or a light that
 * sweeps across when the end cannot be known); then it turns green, or red with the reason, and settles back.
 */
public final class GaugeButton extends FrameLayout {
    enum Look { IDLE, RUNNING, DONE, FAILED }

    private final View fill;
    private final ImageView icon;
    private final TextView title, status, percent;
    private Look look = Look.IDLE;
    private ObjectAnimator sweep;
    private float shown;

    public GaugeButton(Context c) {
        this(c, null);
    }

    public GaugeButton(Context c, AttributeSet attrs) {
        super(c, attrs);
        LayoutInflater.from(c).inflate(R.layout.view_gauge_button, this, true);
        setBackgroundResource(R.drawable.ds_tile_bg);
        setFocusable(true);
        setClipToOutline(true);
        fill = findViewById(R.id.gauge_fill);
        icon = findViewById(R.id.gauge_icon);
        title = findViewById(R.id.gauge_title);
        status = findViewById(R.id.gauge_status);
        percent = findViewById(R.id.gauge_percent);
        fill.setPivotX(0);
        fill.setScaleX(0);
        Ui.lift(this, 1.05f);
    }

    void set(int iconRes, int titleRes) {
        icon.setImageResource(iconRes);
        title.setText(titleRes);
    }

    Look look() {
        return look;
    }

    /** At rest: what it holds now. */
    void idle(CharSequence text) {
        enter(Look.IDLE, R.drawable.ds_gauge_fill);
        animateFill(0);
        percent.setVisibility(GONE);
        say(text);
    }

    /** Working: fraction from 0 to 1, or below 0 when the end is not known. */
    void running(float fraction, CharSequence text) {
        enter(Look.RUNNING, R.drawable.ds_gauge_fill);
        if (fraction < 0) {
            percent.setVisibility(GONE);
            startSweep();
        } else {
            stopSweep();
            percent.setVisibility(VISIBLE);
            percent.setText(String.format(Locale.FRANCE, "%d %%", Math.round(fraction * 100)));
            animateFill(fraction);
        }
        say(text);
    }

    void done(CharSequence text) {
        enter(Look.DONE, R.drawable.ds_gauge_fill_ok);
        animateFill(1);
        percent.setVisibility(VISIBLE);
        percent.setText("✓");
        say(text);
    }

    void failed(CharSequence text) {
        enter(Look.FAILED, R.drawable.ds_gauge_fill_error);
        animateFill(1);
        percent.setVisibility(VISIBLE);
        percent.setText("!");
        say(text);
    }

    private void enter(Look next, int fillRes) {
        if (look != next) {
            if (look == Look.RUNNING && next != Look.RUNNING) stopSweep();
            fill.setBackgroundResource(fillRes);
            look = next;
        }
    }

    private void say(CharSequence text) {
        status.setText(text);
        setContentDescription(title.getText() + ", " + text);
    }

    private void animateFill(float to) {
        if (Math.abs(to - shown) < 0.001f && sweep == null) return;
        shown = to;
        fill.setTranslationX(0);
        fill.animate().scaleX(to).setDuration(to == 0 ? 260 : 220).setInterpolator(new DecelerateInterpolator()).start();
    }

    /** The end is unknown: a third of the gauge travels across, again and again. */
    private void startSweep() {
        if (sweep != null) return;
        fill.animate().cancel();
        shown = -1;
        fill.setScaleX(0.3f);
        int w = Math.max(getWidth(), 1);
        sweep = ObjectAnimator.ofFloat(fill, View.TRANSLATION_X, -0.3f * w, w);
        sweep.setDuration(1400);
        sweep.setRepeatCount(ValueAnimator.INFINITE);
        sweep.setInterpolator(new LinearInterpolator());
        sweep.start();
    }

    private void stopSweep() {
        if (sweep == null) return;
        sweep.cancel();
        sweep = null;
        fill.setTranslationX(0);
        fill.setScaleX(0);
        shown = 0;
    }

    @Override
    protected void onDetachedFromWindow() {
        stopSweep();
        super.onDetachedFromWindow();
    }
}
