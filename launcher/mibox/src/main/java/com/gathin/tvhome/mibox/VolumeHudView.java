package com.gathin.tvhome.mibox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.View;

/**
 * The volume bar, drawn by hand in one view: a black capsule at the top centre of the screen that grows out of a
 * small pill (the way the iPhone's Dynamic Island opens), a thick rounded bar that fills with white, the speaker
 * icon inside the bar (white on the empty part, black where the white has reached it), and the speaker's name.
 * Pushed past either end, the whole capsule stretches that way like a rubber band and springs back.
 *
 * Every motion is a {@link Spring} stepped by the frame clock, and frames stop as soon as all of them are at
 * rest: nothing runs while the bar is still or hidden. No blur, no bitmaps, no layout passes.
 */
final class VolumeHudView extends View {
    interface OnClosed {
        void onClosed();
    }

    // Sizes in dp. The capsule opens from CLOSED to OPEN with its top edge fixed.
    private static final float OPEN_W = 380, OPEN_H = 56, CLOSED_W = 48, CLOSED_H = 16, TOP = 22;
    private static final float PAD_START = 10, PAD_END = 18, TRACK_H = 36, GAP = 12, ICON = 22, ICON_INSET = 11;
    private static final float LABEL_MAX = 120, LABEL_SP = 15;

    private final Spring open = new Spring(240, 18, 0);       // bounces once as it opens
    private final Spring fill = new Spring(420, 30, 0);
    private final Spring stretch = new Spring(620, 15, 1);    // wobbles back when let go
    private final Spring pulse = new Spring(700, 22, 0);      // the icon's tap on each step
    private final Spring wave1 = new Spring(300, 35, 0);      // critically damped fades
    private final Spring wave2 = new Spring(300, 35, 0);
    private final Spring off = new Spring(300, 35, 0);
    private final Spring[] springs = {open, fill, stretch, pulse, wave1, wave2, off};

    private final float dp;
    private final Paint capsule = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hairline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable body, waveIn, waveOut, crossed;
    private final RectF rect = new RectF();
    private final Path clip = new Path();

    private String name = "";
    private CharSequence label = "";
    private float labelWidth;
    private boolean pushUp = true;
    private int pushes;
    private boolean running;
    private long lastFrame;
    private OnClosed onClosed;

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long now) {
            float dt = lastFrame == 0 ? 1 / 60f : Math.min(0.05f, (now - lastFrame) / 1e9f);
            lastFrame = now;
            boolean moving = false;
            for (Spring s : springs) moving |= s.step(dt);
            invalidate();
            if (moving) {
                Choreographer.getInstance().postFrameCallback(this);
                return;
            }
            running = false;
            lastFrame = 0;
            if (open.target == 0 && onClosed != null) onClosed.onClosed();
        }
    };

    VolumeHudView(Context context) {
        this(context, null);
    }

    VolumeHudView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = context.getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        capsule.setColor(Color.BLACK);
        hairline.setStyle(Paint.Style.STROKE);
        hairline.setStrokeWidth(dp);
        hairline.setColor(Color.WHITE);
        track.setColor(Color.WHITE);
        fillPaint.setColor(Color.WHITE);
        text.setColor(Color.WHITE);
        text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, LABEL_SP,
                context.getResources().getDisplayMetrics()));
        Typeface outfit = UiFonts.medium(context);
        if (outfit != null) text.setTypeface(outfit);
        body = icon(context, R.drawable.mibox_ic_vol_body);
        waveIn = icon(context, R.drawable.mibox_ic_vol_wave1);
        waveOut = icon(context, R.drawable.mibox_ic_vol_wave2);
        crossed = icon(context, R.drawable.mibox_ic_vol_off);
    }

    private static Drawable icon(Context context, int id) {
        Drawable d = context.getDrawable(id);
        return d == null ? null : d.mutate();
    }

    void setOnClosed(OnClosed listener) {
        onClosed = listener;
    }

    /** Before opening from hidden: the bar starts at the level the key is moving away from. */
    void prime(float fraction, int waves) {
        fill.value = fill.target = fraction;
        fill.velocity = 0;
        setWaves(waves);
        for (Spring s : new Spring[]{wave1, wave2, off}) s.snap();
    }

    /** One key press: the level it left, and push (+1 or -1) when it had nowhere to go. */
    void update(float fraction, int waves, String name, int push, boolean moved, boolean reducedMotion) {
        open.target = 1;
        fill.target = fraction;
        setWaves(waves);
        if (push != 0) {
            pushes++;
            pushUp = push > 0;
            stretch.target = VolumeLevels.stretch(pushes);
            pulse.velocity += 1.2f;
        } else if (moved) {
            pulse.velocity += 3.2f;
        }
        setLabel(name);
        if (reducedMotion) {
            for (Spring s : springs) s.snap();
            pulse.value = 0;
        }
        kick();
    }

    /** The key stopped pushing past the end: the capsule springs back to its size. */
    void release(boolean reducedMotion) {
        pushes = 0;
        stretch.target = 1;
        if (reducedMotion) stretch.snap();
        kick();
    }

    /** Folds back into the small pill and fades; OnClosed follows once it is gone. */
    void close(boolean reducedMotion) {
        open.target = 0;
        if (reducedMotion) open.snap();
        kick();
    }

    void stop() {
        Choreographer.getInstance().removeFrameCallback(frame);
        running = false;
        lastFrame = 0;
    }

    private void setWaves(int waves) {
        wave1.target = waves >= 1 ? 1 : 0;
        wave2.target = waves >= 2 ? 1 : 0;
        off.target = waves == 0 ? 1 : 0;
    }

    private void setLabel(String name) {
        String n = name == null ? "" : name.trim();
        if (n.equals(this.name)) return;
        this.name = n;
        label = TextUtils.ellipsize(n, text, LABEL_MAX * dp, TextUtils.TruncateAt.END);
        labelWidth = label.length() == 0 ? 0 : text.measureText(label, 0, label.length());
    }

    private void kick() {
        if (running) return;
        running = true;
        lastFrame = 0;
        Choreographer.getInstance().postFrameCallback(frame);
    }

    private static float clamp(float x) {
        return Math.max(0f, Math.min(1f, x));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float shown = clamp(open.value * 4);
        if (shown <= 0) return;
        float p = Math.max(0f, open.value);
        float w = (CLOSED_W + (OPEN_W - CLOSED_W) * p) * dp;
        float h = (CLOSED_H + (OPEN_H - CLOSED_H) * p) * dp;
        float cx = getWidth() / 2f, top = TOP * dp, cy = top + h / 2;
        float left = cx - w / 2, right = cx + w / 2;

        // Stretched from the edge it is pushed away from, a little thinner as it gets longer.
        float s = stretch.value;
        canvas.save();
        canvas.scale(s, 1 - (s - 1) * 1.5f, pushUp ? left : right, cy);

        rect.set(left, top, right, top + h);
        capsule.setAlpha(Math.round(255 * shown));
        canvas.drawRoundRect(rect, h / 2, h / 2, capsule);
        hairline.setAlpha(Math.round(0x24 * shown));
        canvas.drawRoundRect(rect, h / 2, h / 2, hairline);

        float c = clamp((open.value - 0.45f) / 0.55f);   // the content shows once the capsule is half open
        if (c > 0) {
            canvas.save();
            clip.reset();
            clip.addRoundRect(rect, h / 2, h / 2, Path.Direction.CW);
            canvas.clipPath(clip);
            float k = 0.92f + 0.08f * c;
            canvas.scale(k, k, cx, cy);
            drawContent(canvas, cx, cy, c);
            canvas.restore();
        }
        canvas.restore();
    }

    /** The bar, the icon and the name, laid out at the capsule's full size and revealed by its clip. */
    private void drawContent(Canvas canvas, float cx, float cy, float alpha) {
        int a = Math.round(255 * alpha);
        float start = cx - OPEN_W * dp / 2;
        float l = start + PAD_START * dp;
        float r = start + OPEN_W * dp - PAD_END * dp - (labelWidth > 0 ? labelWidth + GAP * dp : 0);
        float t = cy - TRACK_H * dp / 2, b = cy + TRACK_H * dp / 2, radius = TRACK_H * dp / 2;

        rect.set(l, t, r, b);
        track.setAlpha(Math.round(0x29 * alpha));
        canvas.drawRoundRect(rect, radius, radius, track);

        canvas.save();
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);
        float edge = l + clamp(fill.value) * (r - l);
        fillPaint.setAlpha(a);
        canvas.drawRect(l, t, edge, b, fillPaint);
        float ix = l + (ICON_INSET + ICON / 2) * dp;
        canvas.save();
        canvas.clipRect(l, t, edge, b);
        drawIcon(canvas, ix, cy, Color.BLACK, alpha);
        canvas.restore();
        canvas.save();
        canvas.clipRect(edge, t, r, b);
        drawIcon(canvas, ix, cy, Color.WHITE, alpha);
        canvas.restore();
        canvas.restore();

        if (labelWidth > 0) {
            text.setAlpha(Math.round(0xA0 * alpha));
            float baseline = cy - (text.ascent() + text.descent()) / 2;
            canvas.drawText(label, 0, label.length(), r + GAP * dp, baseline, text);
        }
    }

    private void drawIcon(Canvas canvas, float x, float y, int color, float alpha) {
        float half = ICON * dp * (1 + pulse.value) / 2;
        int l = Math.round(x - half), t = Math.round(y - half), r = Math.round(x + half), b = Math.round(y + half);
        float speaker = 1 - clamp(off.value);
        layer(canvas, body, l, t, r, b, color, alpha * speaker);
        layer(canvas, waveIn, l, t, r, b, color, alpha * speaker * clamp(wave1.value));
        layer(canvas, waveOut, l, t, r, b, color, alpha * speaker * clamp(wave2.value));
        layer(canvas, crossed, l, t, r, b, color, alpha * clamp(off.value));
    }

    private static void layer(Canvas canvas, Drawable d, int l, int t, int r, int b, int color, float alpha) {
        if (d == null || alpha <= 0.004f) return;
        d.setBounds(l, t, r, b);
        d.setTint(color);
        d.setAlpha(Math.round(255 * alpha));
        d.draw(canvas);
    }
}
