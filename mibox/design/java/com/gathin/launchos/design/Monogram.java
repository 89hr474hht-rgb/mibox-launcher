package com.gathin.launchos.design;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.view.View;

/**
 * The "lo" mark: one cream line from the top of the L, round its foot and once round the o, and a dot in the o in
 * the app's accent. {@link #written} draws the line up to that share of its length and {@link #dot} the dot at that
 * share of its size, so a start-up screen can show it being written. Same geometry as res/drawable/lo_mark.xml, on
 * its 108 grid (MonogramTest checks the two agree).
 */
public final class Monogram extends View {
    public static final int CREAM = 0xFFF3EEE4;
    static final float GRID = 108f, STROKE = 7f, LEFT = 30f, TOP = 33.5f, BASE = 74f, ELBOW = 7f;
    static final float CX = 64f, R = 14.5f, DOT = 4.8f;
    static final float CY = BASE - R;

    public float written = 1f, dot = 1f;
    private final Path line = new Path(), part = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float length;

    public Monogram(Context c, int accent) {
        super(c);
        line.moveTo(LEFT, TOP);
        line.lineTo(LEFT, BASE - ELBOW);
        line.quadTo(LEFT, BASE, LEFT + ELBOW, BASE);
        line.lineTo(CX, BASE);
        line.arcTo(new RectF(CX - R, CY - R, CX + R, CY + R), 90f, -359.9f, false);
        measure.setPath(line, false);
        length = measure.getLength();
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeWidth(STROKE);
        stroke.setColor(CREAM);
        fill.setColor(accent);
    }

    @Override
    protected void onDraw(Canvas c) {
        float scale = Math.min(getWidth(), getHeight()) / GRID;
        c.save();
        c.scale(scale, scale);
        if (written > 0) {
            part.reset();
            measure.getSegment(0, length * written, part, true);
            c.drawPath(part, stroke);
        }
        if (dot > 0) c.drawCircle(CX, CY, DOT * dot, fill);
        c.restore();
    }
}
