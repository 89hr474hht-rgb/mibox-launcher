package com.gathin.tvhome.mibox;

import android.app.Activity;

/**
 * What the Mi Box edition adds to one home screen, attached once when it first comes to the front: the Bluetooth
 * pill and its menu, the network pill's menu, the living glow, the rolling clock and the highlighted tile name. Each part is skipped on its
 * own when upstream's layout no longer has what it hooks into.
 */
final class HomeExtras {
    private final BluetoothPill bluetooth;
    private final NetworkPill network;
    private final LivingGlow glow;
    private final ClockRoll clock;
    private final NameHighlight names;

    private HomeExtras(Activity home) {
        bluetooth = BluetoothPill.attach(home);
        network = NetworkPill.attach(home);
        glow = LivingGlow.attach(home);
        clock = ClockRoll.attach(home);
        names = NameHighlight.attach(home);
    }

    static HomeExtras attach(Activity home) {
        return new HomeExtras(home);
    }

    void resume() {
        if (bluetooth != null) bluetooth.resume();
        if (glow != null) glow.resume();
    }

    void pause() {
        if (bluetooth != null) bluetooth.pause();
        if (network != null) network.pause();
        if (glow != null) glow.pause();
    }

    void destroy() {
        if (bluetooth != null) bluetooth.destroy();
        if (network != null) network.destroy();
        if (glow != null) glow.pause();
        if (clock != null) clock.destroy();
        if (names != null) names.destroy();
    }
}
