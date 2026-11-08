package com.gathin.launchos.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * A grey rounded bar with a light passing over it, shown where a value, a picture or a list is still loading,
 * so the page keeps its shape. It only animates while it is on screen; one shader, no bitmap.
 */
public final class SkeletonView extends View {
    private static final long SWEEP_MS = 1300;

    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF box = new RectF();
    private final float radius;
    private ValueAnimator sweep;
    private float at;

    public SkeletonView(Context c) {
        this(c, null);
    }

    public SkeletonView(Context c, AttributeSet attrs) {
        super(c, attrs);
        radius = Ui.dp(c, 6);
        base.setColor(c.getColor(R.color.ds_skeleton));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int glow = getContext().getColor(R.color.ds_skeleton_shine);
        shine.setShader(new LinearGradient(0, 0, Math.max(1, w * 0.6f), 0,
                new int[]{0x00FFFFFF, glow, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (isShown()) start();
        else stop();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isShown()) start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void start() {
        if (sweep != null) return;
        sweep = ValueAnimator.ofFloat(-0.6f, 1.2f);
        sweep.setDuration(SWEEP_MS);
        sweep.setRepeatCount(ValueAnimator.INFINITE);
        sweep.setInterpolator(new LinearInterpolator());
        sweep.addUpdateListener(a -> {
            at = (float) a.getAnimatedValue();
            invalidate();
        });
        sweep.start();
    }

    private void stop() {
        if (sweep == null) return;
        sweep.cancel();
        sweep = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        box.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(box, radius, radius, base);
        if (shine.getShader() == null) return;
        matrix.setTranslate(at * getWidth(), 0);
        shine.getShader().setLocalMatrix(matrix);
        canvas.drawRoundRect(box, radius, radius, shine);
    }
}
