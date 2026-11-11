package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Mi Box edition adds to one home screen, attached once when it first comes to the front: the Bluetooth
 * pill and its menu, the network pill's menu, the sleep timer's pill, the sliders icon on LaunchOS's settings pill,
 * round pills that open on focus, the living glow, the rolling clock, the highlighted tile name and the music card
 * above the dock. Each part is
 * skipped on its own when upstream's layout no longer has what it hooks into.
 */
final class HomeExtras {
    /** Top-bar pills made round, by their id in the launcher's layout; the Bluetooth and timer pills are added. */
    private static final String[] ROUND = {"btnInputs", "btnWifi", "btnReorder", "btnLibraryTop", "btnSettings"};

    private final BluetoothPill bluetooth;
    private final NetworkPill network;
    private final TimerPill timer;
    private final LivingGlow glow;
    private final ClockRoll clock;
    private final NameHighlight names;
    private final MusicCard music;

    private HomeExtras(Activity home) {
        bluetooth = BluetoothPill.attach(home);
        network = NetworkPill.attach(home);
        timer = TimerPill.attach(home);
        settingsIcon(home);
        roundPills(home);
        glow = LivingGlow.attach(home);
        clock = ClockRoll.attach(home);
        names = NameHighlight.attach(home);
        music = MusicCard.attach(home);
    }

    static HomeExtras attach(Activity home) {
        return new HomeExtras(home);
    }

    @SuppressWarnings("DiscouragedApi") // the app's R is not visible from this library
    private static View find(Activity home, String name) {
        int id = home.getResources().getIdentifier(name, "id", home.getPackageName());
        return id == 0 ? null : home.findViewById(id);
    }

    /** LaunchOS's settings pill shows sliders rather than the launcher's house. */
    private static void settingsIcon(Activity home) {
        View s = find(home, "btnSettings");
        if (s instanceof TextView) {
            ((TextView) s).setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.mibox_ic_tune, 0, 0, 0);
        }
    }

    private void roundPills(Activity home) {
        List<TextView> pills = new ArrayList<>();
        for (String name : ROUND) {
            View v = find(home, name);
            if (v instanceof TextView) pills.add((TextView) v);
        }
        if (bluetooth != null) pills.add(bluetooth.view());
        if (timer != null) pills.add(timer.view());
        RoundPills.attach(pills);
    }

    void resume() {
        if (bluetooth != null) bluetooth.resume();
        if (timer != null) timer.resume();
        if (glow != null) glow.resume();
        if (music != null) music.resume();
    }

    void pause() {
        if (bluetooth != null) bluetooth.pause();
        if (network != null) network.pause();
        if (timer != null) timer.pause();
        if (glow != null) glow.pause();
        if (music != null) music.pause();
    }

    void destroy() {
        if (bluetooth != null) bluetooth.destroy();
        if (network != null) network.destroy();
        if (timer != null) timer.pause();
        if (glow != null) glow.pause();
        if (clock != null) clock.destroy();
        if (names != null) names.destroy();
        if (music != null) music.destroy();
    }
}
