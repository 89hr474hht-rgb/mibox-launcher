package com.gathin.tvhome.mibox;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.net.Inet4Address;

/**
 * A read-only snapshot of the network for the network menu: what the box is on, whether Wi-Fi is on, whether the
 * internet answers, the box's address and its private DNS. It reads; it never switches anything.
 */
final class NetworkStatus {
    boolean ethernet;       // a cable is connected
    boolean wifiEnabled;
    boolean wifiConnected;
    String ssid;            // null unless Android lets the launcher read it (it needs location on Android 10+)
    boolean online;         // a default network is up
    boolean internet;       // and Android validated it reaches the internet
    String address;         // IPv4 of the default network, or null
    boolean privateDns;
    String privateDnsHost;  // null for automatic

    @SuppressWarnings("deprecation") // getAllNetworks and getConnectionInfo: still the plain way on Android 11
    static NetworkStatus read(Context context) {
        NetworkStatus s = new NetworkStatus();
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        WifiManager wm = context.getApplicationContext().getSystemService(WifiManager.class);
        try {
            if (wm != null) s.wifiEnabled = wm.isWifiEnabled();
        } catch (RuntimeException ignored) {
            // No Wi-Fi service on this device.
        }
        if (cm == null) return s;
        try {
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities c = cm.getNetworkCapabilities(n);
                if (c == null) continue;
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) s.ethernet = true;
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) s.wifiConnected = true;
            }
            Network active = cm.getActiveNetwork();
            NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
            if (caps != null) {
                s.online = true;
                s.internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }
            LinkProperties lp = active == null ? null : cm.getLinkProperties(active);
            if (lp != null) {
                for (LinkAddress a : lp.getLinkAddresses()) {
                    if (a.getAddress() instanceof Inet4Address) {
                        s.address = a.getAddress().getHostAddress();
                        break;
                    }
                }
                if (Build.VERSION.SDK_INT >= 28) {
                    s.privateDns = lp.isPrivateDnsActive();
                    s.privateDnsHost = lp.getPrivateDnsServerName();
                }
            }
        } catch (RuntimeException ignored) {
            // A network went away while it was read: the menu shows what was read.
        }
        if (s.wifiConnected && wm != null) {
            try {
                WifiInfo info = wm.getConnectionInfo();
                String n = info == null ? null : info.getSSID();
                if (n != null && !n.contains("<unknown ssid>")) s.ssid = n.replace("\"", "");
            } catch (RuntimeException ignored) {
                // Not allowed to read it.
            }
        }
        return s;
    }
}
