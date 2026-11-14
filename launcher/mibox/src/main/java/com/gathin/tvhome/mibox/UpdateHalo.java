package com.gathin.tvhome.mibox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import com.gathin.launchos.design.Motion;

/**
 * The logo of the updates page with a thin ring around it that says what the updater is doing: an arc turning while
 * it searches or checks, the ring filling while it downloads, the ring whole in the accent once up to date or a
 * version is ready, in the error colour after a failure, nothing at rest. Redraws only while an arc turns or the ring
 * fills, and stands still when Android's animations are off.
 */
public final class UpdateHalo extends View {
    enum Mode { IDLE, SEARCH, AVAILABLE, DOWNLOAD, VERIFY, WAIT, OK, ERROR }

    private static final long TURN_MS = 1400;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Drawable logo;
    private final int accent, error;
    private Mode mode = Mode.IDLE;
    private int percent;

    public UpdateHalo(Context context) {
        this(context, null);
    }

    public UpdateHalo(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = context.getResources().getDisplayMetrics().density;
        accent = context.getColor(R.color.lo_accent);
        error = context.getColor(R.color.lo_error);
        for (Paint p : new Paint[]{track, ring}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * d);
            p.setStrokeCap(Paint.Cap.ROUND);
        }
        track.setColor(context.getColor(R.color.lo_line));
        logo = context.getDrawable(R.drawable.mibox_logo);
    }

    void setMode(Mode m, int percent) {
        mode = m == null ? Mode.IDLE : m;
        this.percent = Math.max(0, Math.min(100, percent));
        invalidate();
    }

    private boolean turning() {
        return mode == Mode.SEARCH || mode == Mode.VERIFY || mode == Mode.WAIT;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), size = Math.min(w, h);
        float stroke = ring.getStrokeWidth();
        float r = size / 2f - stroke;
        float cx = w / 2f, cy = h / 2f;
        int l = Math.round(size * 0.46f);
        if (logo != null) {
            logo.setBounds(Math.round(cx - l / 2f), Math.round(cy - l / 2f), Math.round(cx + l / 2f), Math.round(cy + l / 2f));
            logo.draw(c);
        }
        box.set(cx - r, cy - r, cx + r, cy + r);
        if (mode == Mode.IDLE) return;
        c.drawOval(box, track);
        switch (mode) {
            case DOWNLOAD:
                ring.setColor(accent);
                c.drawArc(box, -90, 360f * percent / 100f, false, ring);
                break;
            case OK:
            case AVAILABLE:
                ring.setColor(accent);
                c.drawOval(box, ring);
                break;
            case ERROR:
                ring.setColor(error);
                c.drawOval(box, ring);
                break;
            default:
                ring.setColor(accent);
                float start = Motion.on() ? (SystemClock.uptimeMillis() % TURN_MS) / (float) TURN_MS * 360f : 0f;
                c.drawArc(box, start - 90, 90, false, ring);
                break;
        }
        if (turning() && Motion.on() && isShown()) postInvalidateOnAnimation();
    }
}
