package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

/**
 * The sleep timer's pill in the home screen's top bar, just before LaunchOS's settings. It reads "Sleep timer", or
 * "Sleep in 42 min" while a timer runs (seen only when the pill has focus: the countdown is never on screen). OK drops
 * a menu with the durations, and "Stop the timer" while one runs.
 */
final class TimerPill {
    private final Activity home;
    private final TextView view;
    private MiboxMenu menu;

    private TimerPill(Activity home, TextView view) {
        this.home = home;
        this.view = view;
    }

    /** Adds the pill; null when the upstream layout has changed and it cannot be placed. */
    @SuppressWarnings("DiscouragedApi") // the app's R is not visible from this library
    static TimerPill attach(Activity home) {
        String pkg = home.getPackageName();
        int wifiId = home.getResources().getIdentifier("btnWifi", "id", pkg);
        int settingsId = home.getResources().getIdentifier("btnSettings", "id", pkg);
        int styleId = home.getResources().getIdentifier("TopButton", "style", pkg);
        if (wifiId == 0 || settingsId == 0 || styleId == 0) return null;
        View wifi = home.findViewById(wifiId), settings = home.findViewById(settingsId);
        if (wifi == null || settings == null || !(settings.getParent() instanceof LinearLayout)) return null;
        LinearLayout bar = (LinearLayout) settings.getParent();

        TextView pill = new TextView(home, null, 0, styleId);
        pill.setId(View.generateViewId());   // the bar's key guard tells its ends apart by id
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.mibox_ic_timer, 0, 0, 0);
        pill.setSingleLine(true);
        // Same keys as the Bluetooth pill: Up stays in the bar, Down goes back to the tile Up came from.
        pill.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return wifi.dispatchKeyEvent(event);
            return false;
        });
        ViewGroup.LayoutParams base = settings.getLayoutParams();
        LinearLayout.LayoutParams lp = base instanceof LinearLayout.LayoutParams
                ? new LinearLayout.LayoutParams((LinearLayout.LayoutParams) base)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bar.addView(pill, bar.indexOfChild(settings), lp);

        TimerPill p = new TimerPill(home, pill);
        pill.setOnClickListener(v -> p.toggleMenu());
        // Before the round-pill animation measures the label (RoundPills chains this listener first).
        pill.setOnFocusChangeListener((v, focused) -> {
            if (focused) p.refresh();
        });
        p.refresh();
        return p;
    }

    TextView view() {
        return view;
    }

    void resume() {
        refresh();
    }

    void pause() {
        if (menu != null) menu.dismiss();
    }

    private void refresh() {
        boolean on = SleepTimer.active(home);
        String text = on ? home.getString(R.string.mibox_timer_in, SleepTimer.duration(SleepTimer.remaining(home)))
                : home.getString(R.string.mibox_timer);
        view.setText(text);
        view.setContentDescription(text);
    }

    private void toggleMenu() {
        if (menu != null && menu.isShowing()) {
            menu.dismiss();
            return;
        }
        menu = new MiboxMenu(home, view, R.string.mibox_timer_title);
        if (SleepTimer.active(home)) {
            String at = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(SleepTimer.endAt(home)));
            menu.setState(home.getString(R.string.mibox_timer_at, at));
            menu.addAction(R.drawable.mibox_ic_block, home.getString(R.string.mibox_timer_stop), () -> {
                SleepTimer.cancel(home);
                done();
            });
        } else {
            menu.setState(home.getString(R.string.mibox_timer_off));
        }
        for (int minutes : SleepTimer.CHOICES) {
            String d = SleepTimer.duration(minutes * 60_000L);
            menu.addAction(R.drawable.mibox_ic_timer, home.getString(R.string.mibox_timer_set, d), () -> {
                SleepTimer.start(home, minutes * 60_000L);
                done();
            });
        }
        menu.show();
    }

    private void done() {
        menu.dismiss();
        refresh();
    }
}
