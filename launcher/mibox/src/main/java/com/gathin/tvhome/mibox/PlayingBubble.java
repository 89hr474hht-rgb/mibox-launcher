package com.gathin.tvhome.mibox;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * A small bubble at the top of the screen while LaunchOS's music plays and another app is in front (YouTube's menus,
 * the box's settings…), so it is clear where the sound comes from. It shows bars dancing and the title, never takes
 * the focus or a key, and goes as soon as the music pauses or a LaunchOS screen comes back. It moves a few pixels
 * every minute so an OLED panel is never marked. Android must allow LaunchOS to show over other apps (asked from
 * Settings → Musique); without that, or switched off there, nothing shows.
 */
final class PlayingBubble {
    private static final String PREFS = "mibox_music";
    private static final long SETTLE_MS = 350, DRIFT_MS = 60_000;
    private static final int[][] DRIFT = {{0, 0}, {8, 3}, {16, 0}, {8, -3}};

    /** Holds the application context only. */
    @SuppressLint("StaticFieldLeak")
    private static PlayingBubble instance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final WindowManager wm;
    private View view;
    private TextView title;
    private EqualizerView eq;
    private WindowManager.LayoutParams params;
    private int ourScreens, drift;
    private final Runnable settle = this::apply;
    private final Runnable move = new Runnable() {
        @Override
        public void run() {
            if (view == null) return;
            drift = (drift + 1) % DRIFT.length;
            place();
            try {
                wm.updateViewLayout(view, params);
            } catch (RuntimeException ignored) {
                // the window went away meanwhile
            }
            main.postDelayed(this, DRIFT_MS);
        }
    };

    private PlayingBubble(Context app) {
        this.app = app;
        wm = app.getSystemService(WindowManager.class);
    }

    private static synchronized PlayingBubble get(Context c) {
        if (instance == null) instance = new PlayingBubble(c.getApplicationContext());
        return instance;
    }

    // ---- what decides it ----

    /** A LaunchOS screen (home, player, settings, the sleep screen) came to the front. */
    static void screenIn(Context c) {
        PlayingBubble b = get(c);
        b.ourScreens++;
        b.later();
    }

    static void screenOut(Context c) {
        PlayingBubble b = get(c);
        b.ourScreens = Math.max(0, b.ourScreens - 1);
        b.later();
    }

    /** The music started, paused or moved to another track. */
    static void playerChanged(Context c) {
        get(c).later();
    }

    static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("bubble", true);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("bubble", on).apply();
        get(c).later();
    }

    static boolean allowed(Context c) {
        return Settings.canDrawOverlays(c);
    }

    /** The box's screen that grants it, when the box has one. */
    static Intent permissionScreen(Context c) {
        Intent withApp = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.getPackageName()));
        if (withApp.resolveActivity(c.getPackageManager()) != null) return withApp;
        Intent list = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        return list.resolveActivity(c.getPackageManager()) != null ? list : null;
    }

    private void later() {
        // Moving from one LaunchOS screen to another passes through "none in front": wait for it to settle.
        main.removeCallbacks(settle);
        main.postDelayed(settle, SETTLE_MS);
    }

    private void apply() {
        MusicPlayer p = MusicPlayer.peek();
        boolean want = p != null && p.isPlaying() && ourScreens == 0 && enabled(app) && allowed(app);
        if (want) show(p);
        else hide();
    }

    // ---- the window ----

    private void show(MusicPlayer p) {
        MusicLibrary.Track t = p.track();
        if (view == null) {
            // A window of its own: there is no parent to inflate into.
            @SuppressLint("InflateParams")
            View v = LayoutInflater.from(app).inflate(R.layout.mibox_music_bubble, null);
            view = v;
            title = view.findViewById(R.id.bubble_title);
            eq = view.findViewById(R.id.bubble_eq);
            UiFonts.apply(view);
            if (UiFonts.medium(app) != null) title.setTypeface(UiFonts.medium(app));
            params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.END;
            params.windowAnimations = android.R.style.Animation_Toast;
            params.setTitle("LaunchOS music");
            drift = 0;
            place();
            try {
                wm.addView(view, params);
            } catch (RuntimeException e) {
                // Not allowed after all, or no window possible now: no bubble.
                view = null;
                return;
            }
            main.removeCallbacks(move);
            main.postDelayed(move, DRIFT_MS);
        }
        title.setText(t == null ? app.getString(R.string.mibox_music_title) : t.title);
        eq.setPlaying(true);
    }

    private void place() {
        float d = app.getResources().getDisplayMetrics().density;
        params.x = Math.round((40 + DRIFT[drift][0]) * d);
        params.y = Math.round((26 + DRIFT[drift][1]) * d);
    }

    private void hide() {
        main.removeCallbacks(move);
        if (view == null) return;
        eq.setPlaying(false);
        try {
            wm.removeView(view);
        } catch (RuntimeException ignored) {
            // already gone
        }
        view = null;
    }
}
