package com.gathin.tvhome.mibox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * A few bars that dance while music plays and rest, low, while it is paused: the "now playing" mark of the player,
 * the home screen's card and the bubble over other apps. About sixteen frames a second, only while it plays and shows.
 */
public final class EqualizerView extends View {
    private static final int BARS = 4;
    private static final float[] SPEED = {1.9f, 2.7f, 2.2f, 3.1f};
    private static final float[] PHASE = {0f, 1.3f, 2.6f, 0.7f};
    private static final float[] REST = {0.35f, 0.6f, 0.45f, 0.3f};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean playing;

    public EqualizerView(Context c) {
        this(c, null);
    }

    public EqualizerView(Context c, AttributeSet attrs) {
        super(c, attrs);
        paint.setColor(0xFFFFFFFF);
    }

    void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    void setPlaying(boolean on) {
        if (on == playing) return;
        playing = on;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        if (w <= 0 || h <= 0) return;
        float bar = w / (BARS * 2f - 1), r = bar / 2f;
        double t = SystemClock.uptimeMillis() / 1000.0;
        for (int i = 0; i < BARS; i++) {
            float level = playing ? 0.25f + 0.75f * (float) (0.5 + 0.5 * Math.sin(t * SPEED[i] * Math.PI + PHASE[i]))
                    : REST[i];
            float bh = Math.max(bar, h * level);
            float x = getPaddingLeft() + i * bar * 2;
            float bottom = getPaddingTop() + h;
            canvas.drawRoundRect(x, bottom - bh, x + bar, bottom, r, r, paint);
        }
        if (playing && isShown()) postInvalidateDelayed(60);
    }
}
