package com.gathin.tvhome.mibox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * A drawing of the Xiaomi Bluetooth remote, in vectors (a 200 x 620 grid scaled to the view): power, microphone,
 * the D-pad, Back, Home and Menu, the volume rocker and the four shortcut buttons. The three that LaunchOS can remap
 * stand out; the one being set glows in its colour, sends soft ripples and gives a press when a new app is chosen.
 * The remote floats gently. It stands still when Android's animations are off.
 */
final class RemoteView extends View {
    private static final float VW = 200, VH = 620;

    /** One shortcut button: where it is on the remote, its label and colours. */
    private static final class Key {
        final int code;
        final RectF r;
        final String label;
        final int text, glow;
        final boolean bold;

        Key(int code, float cx, float cy, String label, int text, int glow, boolean bold) {
            this.code = code;
            r = new RectF(cx - 30, cy - 14, cx + 30, cy + 14);
            this.label = label;
            this.text = text;
            this.glow = glow;
            this.bold = bold;
        }
    }

    private final Key[] keys = {
            new Key(RemoteRemap.YOUTUBE, 66, 500, "YouTube", 0xFFFFFFFF, 0xFFFF2D3C, true),
            new Key(RemoteRemap.NETFLIX, 134, 500, "NETFLIX", 0xFFE50914, 0xFFE50914, true),
            new Key(RemoteRemap.PRIME, 66, 544, "prime video", 0xFF5EC8F2, 0xFF1EA8E5, false),
            new Key(0, 134, 544, "Apps", 0xFFD0D3DA, 0xFF9A5CFF, false),
    };

    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG),
            part = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG),
            label = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG),
            ripple = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private final Path path = new Path();
    private final OvershootInterpolator overshoot = new OvershootInterpolator(3f);
    private final boolean still;
    private final long born = SystemClock.uptimeMillis();
    private int selected;
    private long selectedSince, pressedAt = -1;

    RemoteView(Context context) {
        super(context);
        still = Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        edge.setStyle(Paint.Style.STROKE);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        ripple.setStyle(Paint.Style.STROKE);
        label.setTextAlign(Paint.Align.CENTER);
        Typeface bold = UiFonts.bold(context), regular = UiFonts.medium(context);
        label.setTypeface(bold != null ? bold : Typeface.DEFAULT_BOLD);
        boldFace = bold != null ? bold : Typeface.DEFAULT_BOLD;
        regularFace = regular != null ? regular : Typeface.DEFAULT;
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    private final Typeface boldFace, regularFace;

    /** Highlights the button for this key code (0 for none). */
    void select(int code) {
        if (code == selected) return;
        selected = code;
        selectedSince = SystemClock.uptimeMillis();
        invalidate();
    }

    /** The selected button gives one press, after a new app was chosen for it. */
    void press() {
        pressedAt = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float t = still ? 0 : (now - born) / 1000f;
        float scale = Math.min(getWidth() / VW, getHeight() / VH) * 0.94f;
        float enter = still ? 1f : Math.min(1f, (now - born) / 700f);
        float ease = 1 - (1 - enter) * (1 - enter) * (1 - enter);
        float floatY = (float) Math.sin(t * 1.1f) * 5f;
        c.save();
        c.translate(getWidth() / 2f, getHeight() / 2f + (1 - ease) * 60 * scale);
        c.scale(scale, scale);
        c.translate(-VW / 2, -VH / 2 + floatY);
        int alpha = Math.round(255 * ease);

        // Shadow and body.
        glow.setShader(new RadialGradient(100, 330, 220, 0x55000000, 0x00000000, Shader.TileMode.CLAMP));
        glow.setAlpha(alpha);
        c.drawOval(-20, 40, 220, 660, glow);
        tmp.set(32, 12, 168, 608);
        body.setShader(new LinearGradient(0, 12, 0, 608, new int[]{0xFF34363E, 0xFF22242A, 0xFF17181C}, null,
                Shader.TileMode.CLAMP));
        body.setAlpha(alpha);
        c.drawRoundRect(tmp, 68, 68, body);
        edge.setStrokeWidth(1.6f);
        edge.setShader(new LinearGradient(0, 12, 0, 608, 0x66FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP));
        edge.setAlpha(alpha);
        c.drawRoundRect(tmp, 68, 68, edge);

        part.setAlpha(255);
        // LED, power, microphone.
        part.setColor(0xFF4F7BFF);
        part.setAlpha(Math.round(alpha * (0.5f + 0.5f * (float) Math.sin(t * 2.4f))));
        c.drawCircle(100, 32, 2.2f, part);
        button(c, 100, 66, 13, alpha);
        icon(c, alpha, 0xFFFF6B6B, 2.2f);
        c.drawArc(93, 59, 107, 73, -60, 300, false, line);
        c.drawLine(100, 57, 100, 65, line);
        button(c, 100, 114, 15, alpha);
        icon(c, alpha, 0xFFC9CDD6, 2f);
        tmp.set(96, 105, 104, 117);
        c.drawRoundRect(tmp, 4, 4, line);
        c.drawArc(92, 106, 108, 122, 20, 140, false, line);
        c.drawLine(100, 122, 100, 125, line);

        // D-pad and OK.
        part.setShader(new LinearGradient(0, 160, 0, 272, 0xFF2C2E35, 0xFF1C1D22, Shader.TileMode.CLAMP));
        part.setAlpha(alpha);
        c.drawCircle(100, 216, 56, part);
        part.setShader(null);
        edge.setShader(null);
        edge.setColor(0x33FFFFFF);
        edge.setAlpha(Math.round(alpha * 0.2f));
        c.drawCircle(100, 216, 56, edge);
        part.setColor(0xFF33353D);
        part.setAlpha(alpha);
        c.drawCircle(100, 216, 23, part);
        edge.setAlpha(Math.round(alpha * 0.25f));
        c.drawCircle(100, 216, 23, edge);
        icon(c, alpha, 0x99FFFFFF, 2f);
        chevron(c, 100, 172, 0);
        chevron(c, 144, 216, 90);
        chevron(c, 100, 260, 180);
        chevron(c, 56, 216, 270);

        // Back, Home, Menu.
        button(c, 58, 314, 15, alpha);
        icon(c, alpha, 0xFFC9CDD6, 2f);
        path.reset();
        path.moveTo(62, 308);
        path.lineTo(55, 314);
        path.lineTo(62, 320);
        c.drawPath(path, line);
        button(c, 100, 314, 15, alpha);
        c.drawCircle(100, 314, 6, line);
        button(c, 142, 314, 15, alpha);
        c.drawLine(136, 309, 148, 309, line);
        c.drawLine(136, 314, 148, 314, line);
        c.drawLine(136, 319, 148, 319, line);

        // Volume rocker.
        tmp.set(82, 356, 118, 448);
        part.setColor(0xFF2A2C33);
        part.setAlpha(alpha);
        c.drawRoundRect(tmp, 18, 18, part);
        edge.setAlpha(Math.round(alpha * 0.18f));
        c.drawRoundRect(tmp, 18, 18, edge);
        icon(c, alpha, 0xFFC9CDD6, 2.2f);
        c.drawLine(94, 378, 106, 378, line);
        c.drawLine(100, 372, 100, 384, line);
        c.drawLine(94, 426, 106, 426, line);

        // The shortcut buttons, the selected one glowing.
        for (Key k : keys) drawKey(c, k, now, t, alpha);
        c.restore();
        if (!still && isShown()) postInvalidateOnAnimation();
    }

    private void drawKey(Canvas c, Key k, long now, float t, int alpha) {
        boolean sel = k.code != 0 && k.code == selected;
        boolean remappable = k.code != 0;
        float s = 1f;
        if (sel && pressedAt > 0) {
            float p = (now - pressedAt) / 420f;
            if (p < 1f) s = p < 0.25f ? 1f - 0.12f * (p / 0.25f) : 0.88f + 0.12f * overshoot.getInterpolation((p - 0.25f) / 0.75f);
        }
        float on = sel ? (still ? 1f : Math.min(1f, (now - selectedSince) / 300f)) : 0f;
        c.save();
        c.scale(s, s, k.r.centerX(), k.r.centerY());
        if (sel) {
            float pulse = 0.75f + 0.25f * (float) Math.sin(t * 4f);
            glow.setShader(new RadialGradient(k.r.centerX(), k.r.centerY(), 58,
                    new int[]{withAlpha(k.glow, 0.7f * on * pulse), withAlpha(k.glow, 0.2f * on), 0}, new float[]{0, 0.5f, 1},
                    Shader.TileMode.CLAMP));
            glow.setAlpha(255);
            c.drawCircle(k.r.centerX(), k.r.centerY(), 58, glow);
            for (int i = 0; i < 2 && !still; i++) {   // ripples, one every 0.8 s
                float f = ((t / 1.6f) + i / 2f) % 1f;
                ripple.setStrokeWidth(1.6f);
                ripple.setColor(withAlpha(k.glow, 0.6f * (1 - f) * on));
                tmp.set(k.r.left - 14 * f, k.r.top - 14 * f, k.r.right + 14 * f, k.r.bottom + 14 * f);
                c.drawRoundRect(tmp, 14 + 14 * f, 14 + 14 * f, ripple);
            }
        }
        part.setShader(null);
        part.setColor(sel ? 0xFF0B0B0E : 0xFF101114);
        part.setAlpha(alpha);
        c.drawRoundRect(k.r, 14, 14, part);
        edge.setShader(null);
        edge.setStrokeWidth(sel ? 2.2f : 1.2f);
        edge.setColor(sel ? k.glow : remappable ? 0x40FFFFFF : 0x24FFFFFF);
        edge.setAlpha(Math.round(alpha * (sel ? 1f : remappable ? 0.25f : 0.14f)));
        c.drawRoundRect(k.r, 14, 14, edge);
        label.setColor(k.text);
        label.setAlpha(Math.round(alpha * (selected == 0 || sel || !remappable ? 1f : 0.55f)));
        label.setTypeface(k.bold ? boldFace : regularFace);
        label.setTextSize(k.code == RemoteRemap.YOUTUBE ? 9.5f : k.label.length() > 8 ? 9.5f : 11f);
        float y = k.r.centerY() - (label.descent() + label.ascent()) / 2f;
        if (k.code == RemoteRemap.YOUTUBE) {   // a small red play mark before the name
            part.setColor(0xFFFF2D3C);
            part.setAlpha(label.getAlpha());
            tmp.set(k.r.left + 5, k.r.centerY() - 4, k.r.left + 15, k.r.centerY() + 4);
            c.drawRoundRect(tmp, 2.5f, 2.5f, part);
            path.reset();
            path.moveTo(k.r.left + 8.8f, k.r.centerY() - 2.2f);
            path.lineTo(k.r.left + 12.2f, k.r.centerY());
            path.lineTo(k.r.left + 8.8f, k.r.centerY() + 2.2f);
            path.close();
            part.setColor(0xFFFFFFFF);
            part.setAlpha(label.getAlpha());
            c.drawPath(path, part);
            c.drawText(k.label, k.r.centerX() + 7.5f, y, label);
        } else {
            c.drawText(k.label, k.r.centerX(), y, label);
        }
        c.restore();
    }

    private void button(Canvas c, float cx, float cy, float r, int alpha) {
        part.setShader(null);
        part.setColor(0xFF2A2C33);
        part.setAlpha(alpha);
        c.drawCircle(cx, cy, r, part);
        edge.setShader(null);
        edge.setStrokeWidth(1.2f);
        edge.setColor(0xFFFFFFFF);
        edge.setAlpha(Math.round(alpha * 0.16f));
        c.drawCircle(cx, cy, r, edge);
    }

    private void icon(Canvas c, int alpha, int color, float width) {
        line.setColor(color);
        line.setAlpha(Math.round(alpha * ((color >>> 24) / 255f)));
        line.setStrokeWidth(width);
    }

    private void chevron(Canvas c, float cx, float cy, float deg) {
        c.save();
        c.rotate(deg, cx, cy);
        path.reset();
        path.moveTo(cx - 5, cy + 3);
        path.lineTo(cx, cy - 2);
        path.lineTo(cx + 5, cy + 3);
        c.drawPath(path, line);
        c.restore();
    }

    private static int withAlpha(int color, float a) {
        return Math.round(255 * Math.max(0f, Math.min(1f, a))) << 24 | (color & 0xFFFFFF);
    }
}
