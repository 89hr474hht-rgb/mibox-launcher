package com.gathin.tvhome.mibox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * The big round picture of the updates page: the LaunchOS logo in a glow, with what the updater is doing drawn around
 * it. Searching sends rings out like a sonar under a turning sweep; a download fills a ring; a check spins; up to date
 * shows a green tick, a new version a violet arrow, a failure an amber mark, each popping in when the state changes.
 * It animates only while on screen, and stands still when Android's animations are off.
 */
public final class UpdateHalo extends View {
    enum Mode { IDLE, SEARCH, AVAILABLE, DOWNLOAD, VERIFY, WAIT, OK, ERROR }

    private static final int BLUE = 0xFF4F7BFF, VIOLET = 0xFF9A5CFF, PINK = 0xFFFF5C9A, GREEN = 0xFF34C759,
            AMBER = 0xFFFFB340;

    private final Drawable logo;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG),
            badge = new Paint(Paint.ANTI_ALIAS_FLAG), mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Path path = new Path();
    private final OvershootInterpolator pop = new OvershootInterpolator(2.2f);
    private final float dp;
    private final boolean still;
    private Mode mode = Mode.IDLE;
    private long modeSince = SystemClock.uptimeMillis();
    private float progress;
    private SweepGradient sweep, fill;
    private float cx, cy;

    public UpdateHalo(Context context) {
        this(context, null);
    }

    public UpdateHalo(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = context.getResources().getDisplayMetrics().density;
        logo = context.getDrawable(R.drawable.mibox_logo);
        still = Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        mark.setStyle(Paint.Style.STROKE);
        mark.setStrokeCap(Paint.Cap.ROUND);
        mark.setStrokeJoin(Paint.Join.ROUND);
        mark.setColor(0xFFFFFFFF);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void setMode(Mode m, int percent) {
        progress = Math.max(0, Math.min(100, percent)) / 100f;
        if (m != mode) {
            mode = m;
            modeSince = SystemClock.uptimeMillis();
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w / 2f;
        cy = h / 2f;
        sweep = new SweepGradient(cx, cy, new int[]{0x00FFFFFF, 0x004F7BFF, BLUE, 0xFFB7C6FF}, new float[]{0f, 0.55f, 0.92f, 1f});
        fill = new SweepGradient(cx, cy, new int[]{BLUE, VIOLET, PINK, BLUE}, null);
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float t = still ? 0f : (now - startOf()) / 1000f;
        float since = still ? 1f : Math.min(1f, (now - modeSince) / 520f);
        float size = Math.min(getWidth(), getHeight());
        float logoR = size * 0.2f, ringR = size * 0.36f;
        int tone = tone();

        // The glow behind the logo, in the state's colour, breathing slowly.
        float breathe = 0.85f + 0.15f * (float) Math.sin(t * 1.7f);
        glow.setShader(new RadialGradient(cx, cy, size * 0.5f, new int[]{alpha(tone, 0.55f * breathe), alpha(tone, 0.18f), 0},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, size * 0.5f, glow);

        switch (mode) {
            case SEARCH:
                for (int i = 0; i < 3; i++) {   // sonar rings, one every 0.6 s
                    float f = ((t / 1.8f) + i / 3f) % 1f;
                    ring.setShader(null);
                    ring.setStrokeWidth(2 * dp);
                    ring.setColor(alpha(BLUE, 0.7f * (1 - f)));
                    c.drawCircle(cx, cy, logoR + (size * 0.5f - logoR) * f, ring);
                }
                drawTrack(c, ringR);
                c.save();
                c.rotate((t * 300f) % 360f, cx, cy);
                ring.setShader(sweep);
                ring.setStrokeWidth(5 * dp);
                oval.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
                c.drawArc(oval, 0, 340, false, ring);
                ring.setShader(null);
                c.restore();
                break;
            case DOWNLOAD:
                drawTrack(c, ringR);
                c.save();
                c.rotate(-90, cx, cy);
                ring.setShader(fill);
                ring.setStrokeWidth(7 * dp);
                oval.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
                c.drawArc(oval, 0, Math.max(2f, 360f * progress), false, ring);
                ring.setShader(null);
                c.restore();
                break;
            case VERIFY:
            case WAIT:
                drawTrack(c, ringR);
                c.save();
                c.rotate((t * 240f) % 360f, cx, cy);
                ring.setShader(fill);
                ring.setStrokeWidth(6 * dp);
                oval.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
                c.drawArc(oval, 0, 70 + 50 * (float) Math.sin(t * 3f), false, ring);
                ring.setShader(null);
                c.restore();
                break;
            default:
                ring.setShader(null);
                ring.setStrokeWidth(3 * dp);
                ring.setColor(alpha(tone, mode == Mode.IDLE ? 0.25f : 0.55f + 0.25f * (float) Math.sin(t * 2.2f)));
                c.drawCircle(cx, cy, ringR, ring);
        }

        // The logo, floating very slightly.
        float lift = still ? 0f : (float) Math.sin(t * 1.3f) * 2.5f * dp;
        float s = 1f + (mode == Mode.SEARCH ? 0.025f * (float) Math.sin(t * 5f) : 0f);
        int r = Math.round(logoR * s);
        logo.setBounds(Math.round(cx - r), Math.round(cy - r + lift), Math.round(cx + r), Math.round(cy + r + lift));
        logo.draw(c);

        if (mode == Mode.OK || mode == Mode.AVAILABLE || mode == Mode.ERROR) drawBadge(c, logoR, lift, pop.getInterpolation(since));
        if (!still && isShown()) postInvalidateOnAnimation();
    }

    private long start = -1;

    private long startOf() {
        if (start < 0) start = SystemClock.uptimeMillis();
        return start;
    }

    private void drawTrack(Canvas c, float ringR) {
        ring.setShader(null);
        ring.setStrokeWidth(7 * dp);
        ring.setColor(0x1FFFFFFF);
        c.drawCircle(cx, cy, ringR, ring);
    }

    /** The round badge at the logo's lower right: a tick, an arrow or an exclamation mark. */
    private void drawBadge(Canvas c, float logoR, float lift, float scale) {
        float br = logoR * 0.42f * scale;
        float bx = cx + logoR * 0.78f, by = cy + logoR * 0.78f + lift;
        if (br <= 0) return;
        badge.setColor(0xFF0E0F12);
        c.drawCircle(bx, by, br + 3 * dp * scale, badge);
        badge.setColor(tone());
        c.drawCircle(bx, by, br, badge);
        mark.setStrokeWidth(Math.max(1f, br * 0.22f));
        path.reset();
        if (mode == Mode.OK) {
            path.moveTo(bx - br * 0.42f, by + br * 0.02f);
            path.lineTo(bx - br * 0.1f, by + br * 0.34f);
            path.lineTo(bx + br * 0.45f, by - br * 0.3f);
        } else if (mode == Mode.AVAILABLE) {
            path.moveTo(bx, by - br * 0.45f);
            path.lineTo(bx, by + br * 0.38f);
            path.moveTo(bx - br * 0.36f, by + br * 0.05f);
            path.lineTo(bx, by + br * 0.42f);
            path.lineTo(bx + br * 0.36f, by + br * 0.05f);
        } else {
            path.moveTo(bx, by - br * 0.45f);
            path.lineTo(bx, by + br * 0.1f);
            path.moveTo(bx, by + br * 0.42f);
            path.lineTo(bx, by + br * 0.43f);
        }
        c.drawPath(path, mark);
    }

    private int tone() {
        switch (mode) {
            case OK: return GREEN;
            case AVAILABLE: case WAIT: return VIOLET;
            case ERROR: return AMBER;
            default: return BLUE;
        }
    }

    private static int alpha(int color, float a) {
        return Math.round(255 * Math.max(0f, Math.min(1f, a))) << 24 | (color & 0xFFFFFF);
    }
}
