package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.view.View;

/**
 * OK on the network pill drops a menu that tells what the box is on: the cable, Wi-Fi switched on or off,
 * whether the internet answers, the box's address and its private DNS, kept up to date while it is open. Its
 * action opens Android's network settings, which is what the pill used to do (holding OK still does). Joining a
 * Wi-Fi network stays in Android's settings: since Android 10 an app cannot connect the box to one.
 */
final class NetworkPill {
    /** Google TV's network screen; ACTION_WIFI_SETTINGS on other firmware. */
    private static final ComponentName TV_NETWORK = new ComponentName("com.android.tv.settings",
            "com.android.tv.settings.connectivity.NetworkActivity");

    private final Activity home;
    private final View pill;
    private MiboxMenu menu;
    private boolean watching;

    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            refreshSoon();
        }

        @Override
        public void onLost(Network network) {
            refreshSoon();
        }

        @Override
        public void onCapabilitiesChanged(Network network, android.net.NetworkCapabilities caps) {
            refreshSoon();
        }
    };

    private final BroadcastReceiver wifiState = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshSoon();
        }
    };

    private NetworkPill(Activity home, View pill) {
        this.home = home;
        this.pill = pill;
    }

    /** Takes over the pill's OK; null when the upstream layout has changed and the pill is not found. */
    @SuppressWarnings("DiscouragedApi") // the app's R is not visible from this library
    static NetworkPill attach(Activity home) {
        int id = home.getResources().getIdentifier("btnWifi", "id", home.getPackageName());
        View pill = id == 0 ? null : home.findViewById(id);
        if (pill == null) return null;
        NetworkPill p = new NetworkPill(home, pill);
        pill.setOnClickListener(v -> p.toggle());
        return p;
    }

    void pause() {
        if (menu != null) menu.dismiss();
    }

    void destroy() {
        pause();
        stopWatching();
    }

    private void toggle() {
        if (menu != null && menu.isShowing()) {
            menu.dismiss();
            return;
        }
        menu = new MiboxMenu(home, pill, R.string.mibox_net_title);
        menu.addAction(R.drawable.mibox_sym_settings, home.getString(R.string.mibox_net_settings),
                () -> menu.open(networkSettings()));
        menu.setOnDismiss(this::stopWatching);
        fill();
        startWatching();
        menu.show();
    }

    private Intent networkSettings() {
        Intent tv = new Intent().setComponent(TV_NETWORK);
        if (home.getPackageManager().resolveActivity(tv, 0) != null) return tv;
        return new Intent(Settings.ACTION_WIFI_SETTINGS);
    }

    private void refreshSoon() {
        pill.post(() -> {
            if (menu != null && menu.isShowing()) fill();
        });
    }

    private void fill() {
        NetworkStatus s = NetworkStatus.read(home);
        menu.setState(home.getString(s.ethernet ? R.string.mibox_net_ethernet
                : s.wifiConnected ? R.string.mibox_net_wifi : R.string.mibox_net_offline));
        menu.clearRows();
        menu.addInfo(R.drawable.mibox_sym_lan, home.getString(R.string.mibox_net_ethernet),
                home.getString(s.ethernet ? R.string.mibox_net_connected : R.string.mibox_net_unplugged), s.ethernet);
        String wifi = !s.wifiEnabled ? home.getString(R.string.mibox_net_off)
                : s.wifiConnected ? (s.ssid != null ? s.ssid : home.getString(R.string.mibox_net_connected))
                : home.getString(R.string.mibox_net_on_idle);
        menu.addInfo(s.wifiEnabled ? R.drawable.mibox_sym_wifi : R.drawable.mibox_sym_wifi_off,
                home.getString(R.string.mibox_net_wifi), wifi, s.wifiConnected);
        menu.addInfo(s.internet ? R.drawable.mibox_sym_public : R.drawable.mibox_sym_public_off,
                home.getString(R.string.mibox_net_internet),
                home.getString(s.internet ? R.string.mibox_net_available
                        : s.online ? R.string.mibox_net_no_internet : R.string.mibox_net_offline), s.internet);
        if (s.address != null) {
            menu.addInfo(R.drawable.mibox_sym_router, home.getString(R.string.mibox_net_address), s.address, false);
        }
        menu.addInfo(R.drawable.mibox_sym_dns, home.getString(R.string.mibox_net_private_dns),
                !s.privateDns ? home.getString(R.string.mibox_net_off)
                        : s.privateDnsHost != null ? s.privateDnsHost : home.getString(R.string.mibox_net_automatic),
                s.privateDns);
    }

    private void startWatching() {
        if (watching) return;
        ConnectivityManager cm = home.getSystemService(ConnectivityManager.class);
        try {
            if (cm != null) cm.registerDefaultNetworkCallback(callback);
        } catch (RuntimeException ignored) {
            // Too many callbacks for the app: the menu just stays as first read.
        }
        IntentFilter f = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
        if (Build.VERSION.SDK_INT >= 33) home.registerReceiver(wifiState, f, Context.RECEIVER_EXPORTED);
        else home.registerReceiver(wifiState, f);
        watching = true;
    }

    private void stopWatching() {
        if (!watching) return;
        watching = false;
        ConnectivityManager cm = home.getSystemService(ConnectivityManager.class);
        try {
            if (cm != null) cm.unregisterNetworkCallback(callback);
        } catch (RuntimeException ignored) {
            // Never registered.
        }
        try {
            home.unregisterReceiver(wifiState);
        } catch (RuntimeException ignored) {
            // Already gone.
        }
    }
}
