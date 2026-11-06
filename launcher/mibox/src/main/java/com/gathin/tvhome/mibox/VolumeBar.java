package com.gathin.tvhome.mibox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * PROTOTYPE. LaunchOS's volume bar, laid exactly over Android TV's own volume panel (a dark capsule at the right
 * edge, centred) so that panel is hidden under it. Android TV never hands the volume keys to an app, so the bar
 * follows Android instead: the volume broadcasts give the level, and the accessibility service that owns this bar
 * tells it when Android's panel opens (from the class name of the window that opened, never what is on screen).
 * The window is an accessibility overlay, which Android 11 stacks above its volume panel (layer 31 against 22); it
 * takes neither focus nor touches, so the remote keeps working as before.
 */
final class VolumeBar extends View {
    private static final long HOLD_MS = 3300;
    private static final String VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    private static final String MUTE_CHANGED = "android.media.STREAM_MUTE_CHANGED_ACTION";

    private final WindowManager wm;
    private final AudioManager am;
    private final float dp;
    private final boolean still;
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG), track = new Paint(Paint.ANTI_ALIAS_FLAG),
            fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG),
            icon = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private boolean added, systemPanel;
    private int max = 15, value, lastValue = -1;
    private boolean muted, bluetooth;
    private float shown, level, levelV, stretch, stretchV;
    private long lastChange = -HOLD_MS, lastFrame;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int stream = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", AudioManager.STREAM_MUSIC);
            // The broadcast comes within a frame or two of Android's panel (its window event can come 0.7 s late):
            // the bar covers at once.
            if (stream == AudioManager.STREAM_MUSIC) systemPanel();
        }
    };

    VolumeBar(Context service) {
        super(service);
        wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        am = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
        dp = service.getResources().getDisplayMetrics().density;
        still = Settings.Global.getFloat(service.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        body.setShader(null);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.2f * dp);
        icon.setStyle(Paint.Style.STROKE);
        icon.setStrokeCap(Paint.Cap.ROUND);
        icon.setStrokeJoin(Paint.Join.ROUND);
        icon.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xFFFFFFFF);
        Typeface t = UiFonts.semibold(service);
        if (t != null) text.setTypeface(t);
        IntentFilter f = new IntentFilter(VOLUME_CHANGED);
        f.addAction(MUTE_CHANGED);
        service.registerReceiver(receiver, f);
        attach();   // ready before the first change, so covering Android's panel costs no extra frame
    }

    void stop() {
        try {
            getContext().unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
            // Already gone.
        }
        if (added) wm.removeViewImmediate(this);
        added = false;
    }

    /** Android's own volume panel opened (or was shown again): cover it at once, for a little longer than it stays. */
    void systemPanel() {
        systemPanel = true;
        shown = 1f;   // no fade: Android's panel must never show through
        changed(true);
    }

    private void changed(boolean fromPanel) {
        int v = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        muted = am.isStreamMute(AudioManager.STREAM_MUSIC);
        bluetooth = false;
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) bluetooth = true;
        }
        // Pushing past the top or the bottom: the panel came back without a change, at an end.
        if (fromPanel && v == lastValue && (v == max || v == 0) && !still) stretchV += (v == 0 ? -1 : 1) * 9f;
        value = v;
        lastValue = v;
        lastChange = SystemClock.uptimeMillis();
        attach();
        invalidate();
    }

    private void attach() {
        if (added) return;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(Math.round(120 * dp), Math.round(380 * dp),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        lp.setTitle("LaunchOS volume");
        lp.windowAnimations = 0;
        wm.addView(this, lp);
        added = true;
        level = am.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        boolean on = now - lastChange < HOLD_MS;
        if (!on) systemPanel = false;
        float target = muted ? 0 : value;
        if (still) {
            shown = on ? 1 : 0;
            level = target;
            stretch = 0;
        } else {
            // Springs: the capsule's presence, the level, and the rubber-band stretch at the ends.
            shown += ((on ? 1f : 0f) - shown) * Math.min(1f, dt * (on ? 16f : 9f));
            levelV += ((target - level) * 260f - levelV * 26f) * dt;
            level += levelV * dt;
            stretchV += (-stretch * 320f - stretchV * 18f) * dt;
            stretch += stretchV * dt;
        }
        if (shown < 0.004f && !on) {
            lastFrame = 0;
            return;   // nothing to draw until the next change
        }

        float w = getWidth(), h = getHeight();
        float cw = 64 * dp, ch = 300 * dp;
        float cx = w - 48 * dp;                // the centre of Android's panel, 48 dp from the right edge
        float cy = h / 2f;
        float appear = still ? 1f : shown;
        // The capsule always fully covers Android's panel while that one is up: it only slides in from the right
        // when Android's is not there yet.
        float slide = systemPanel ? 0f : (1 - appear) * 40 * dp;
        float sx = 1f, sy = 1f + Math.abs(stretch) * 0.006f;
        c.save();
        c.translate(slide, -stretch * 0.6f * dp);
        c.scale(sx, sy, cx, stretch >= 0 ? cy + ch / 2 : cy - ch / 2);
        int a = Math.round(255 * Math.min(1f, appear * 1.4f));

        r.set(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2);
        body.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0xFF1D1F26, 0xFF101115, Shader.TileMode.CLAMP));
        body.setAlpha(systemPanel ? 255 : a);
        c.drawRoundRect(r, cw / 2, cw / 2, body);
        rim.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0x40FFFFFF, 0x0DFFFFFF, Shader.TileMode.CLAMP));
        rim.setAlpha(a);
        c.drawRoundRect(r, cw / 2, cw / 2, rim);

        // The level: a thick track filling from the bottom in LaunchOS's colours.
        float tw = 26 * dp, top = r.top + 58 * dp, bottom = r.bottom - 64 * dp;
        RectF t = new RectF(cx - tw / 2, top, cx + tw / 2, bottom);
        track.setColor(0x26FFFFFF);
        track.setAlpha(Math.round(a * 0.15f));
        c.drawRoundRect(t, tw / 2, tw / 2, track);
        float p = Math.max(0f, Math.min(1f, level / max));
        float fillTop = bottom - (bottom - top) * p;
        if (p > 0.001f) {
            RectF fr = new RectF(t.left, Math.min(fillTop, bottom - tw), t.right, bottom);
            fill.setShader(new LinearGradient(0, bottom, 0, top, new int[]{0xFF4F7BFF, 0xFF9A5CFF, 0xFFFF5C9A}, null,
                    Shader.TileMode.CLAMP));
            fill.setAlpha(Math.round(a * (muted ? 0.35f : 1f)));
            c.save();
            c.clipRect(t.left, fillTop, t.right, bottom);
            c.drawRoundRect(t, tw / 2, tw / 2, fill);
            c.restore();
        }

        // The number on top, the output at the bottom.
        text.setTextSize(19 * dp);
        text.setAlpha(a);
        c.drawText(muted ? "–" : String.valueOf(value), cx, r.top + 38 * dp, text);
        icon.setStrokeWidth(2 * dp);
        icon.setAlpha(a);
        float iy = r.bottom - 32 * dp;
        if (bluetooth) drawSpeakerBox(c, cx, iy);
        else drawSpeaker(c, cx, iy, muted ? -1 : Math.round(p * 3 + 0.49f));
        c.restore();
        postInvalidateOnAnimation();
    }

    /** A loudspeaker with 0 to 3 waves, or crossed out (waves = -1). */
    private void drawSpeaker(Canvas c, float x, float y, int waves) {
        float u = dp;
        path.reset();
        path.moveTo(x - 11 * u, y - 4 * u);
        path.lineTo(x - 6 * u, y - 4 * u);
        path.lineTo(x, y - 9 * u);
        path.lineTo(x, y + 9 * u);
        path.lineTo(x - 6 * u, y + 4 * u);
        path.lineTo(x - 11 * u, y + 4 * u);
        path.close();
        c.drawPath(path, icon);
        if (waves < 0) {
            c.drawLine(x + 4 * u, y - 5 * u, x + 12 * u, y + 5 * u, icon);
            c.drawLine(x + 12 * u, y - 5 * u, x + 4 * u, y + 5 * u, icon);
            return;
        }
        for (int i = 1; i <= waves; i++) {
            float rr = (2 + i * 4) * u;
            r.set(x - rr + 1 * u, y - rr, x + rr + 1 * u, y + rr);
            c.drawArc(r, -45, 90, false, icon);
        }
    }

    /** A Bluetooth speaker (the JBL): a rounded box with a woofer. */
    private void drawSpeakerBox(Canvas c, float x, float y) {
        float u = dp;
        r.set(x - 9 * u, y - 11 * u, x + 9 * u, y + 11 * u);
        c.drawRoundRect(r, 4 * u, 4 * u, icon);
        c.drawCircle(x, y + 3 * u, 4.5f * u, icon);
        c.drawCircle(x, y - 6 * u, 1.2f * u, icon);
    }
}
