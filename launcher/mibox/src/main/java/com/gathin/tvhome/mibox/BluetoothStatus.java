package com.gathin.tvhome.mibox;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Read-only view of Bluetooth: whether it is on, the paired devices and which ones are connected. It never
 * switches Bluetooth, pairs, connects or forgets anything: that stays in Android's own settings.
 */
@SuppressLint("MissingPermission") // every call is behind allowed(): BLUETOOTH up to Android 11, BLUETOOTH_CONNECT after
final class BluetoothStatus implements BluetoothProfile.ServiceListener {
    interface Listener {
        void onBluetoothChanged();
    }

    /** BluetoothProfile.HID_HOST, public only from Android 12: the remote control and keyboards. */
    private static final int HID_HOST = 4;
    private static final int[] PROFILES = {BluetoothProfile.A2DP, BluetoothProfile.HEADSET, HID_HOST};
    private static final String ACTION_ALIAS_CHANGED = "android.bluetooth.device.action.ALIAS_CHANGED";

    private final Context app;
    private final Listener listener;
    private final BluetoothAdapter adapter;
    private final SparseArray<BluetoothProfile> proxies = new SparseArray<>();
    private boolean registered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            listener.onBluetoothChanged();
        }
    };

    BluetoothStatus(Context context, Listener listener) {
        this.app = context.getApplicationContext();
        this.listener = listener;
        BluetoothManager bm = app.getSystemService(BluetoothManager.class);
        adapter = bm != null ? bm.getAdapter() : null;
        if (adapter != null && allowed()) {
            for (int profile : PROFILES) {
                try {
                    adapter.getProfileProxy(app, this, profile);
                } catch (RuntimeException ignored) {
                    // A profile this box does not have.
                }
            }
        }
    }

    private boolean allowed() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || app.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == PackageManager.PERMISSION_GRANTED;
    }

    void start() {
        if (registered) return;
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        f.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        f.addAction(ACTION_ALIAS_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
        else app.registerReceiver(receiver, f);
        registered = true;
    }

    void stop() {
        if (!registered) return;
        app.unregisterReceiver(receiver);
        registered = false;
    }

    void close() {
        stop();
        if (adapter == null) return;
        for (int i = 0; i < proxies.size(); i++) {
            try {
                adapter.closeProfileProxy(proxies.keyAt(i), proxies.valueAt(i));
            } catch (RuntimeException ignored) {
                // Already gone with the Bluetooth service.
            }
        }
        proxies.clear();
    }

    @Override
    public void onServiceConnected(int profile, BluetoothProfile proxy) {
        proxies.put(profile, proxy);
        listener.onBluetoothChanged();
    }

    @Override
    public void onServiceDisconnected(int profile) {
        proxies.remove(profile);
        listener.onBluetoothChanged();
    }

    boolean available() {
        return adapter != null && allowed();
    }

    boolean enabled() {
        try {
            return available() && adapter.isEnabled();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Connected speakers and headphones (A2DP, then hands-free), the devices the pill names. */
    List<BluetoothDevice> connectedAudio() {
        Set<BluetoothDevice> out = new LinkedHashSet<>();
        if (!enabled()) return new ArrayList<>(out);
        for (int profile : new int[]{BluetoothProfile.A2DP, BluetoothProfile.HEADSET}) {
            BluetoothProfile p = proxies.get(profile);
            if (p == null) continue;
            try {
                out.addAll(p.getConnectedDevices());
            } catch (RuntimeException ignored) {
                // The profile service restarted; the next broadcast refreshes.
            }
        }
        return new ArrayList<>(out);
    }

    /** True when the device is connected on any profile this class watches. */
    boolean connected(BluetoothDevice device) {
        if (!enabled()) return false;
        for (int profile : PROFILES) {
            BluetoothProfile p = proxies.get(profile);
            try {
                if (p != null && p.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) return true;
            } catch (RuntimeException ignored) {
                // See connectedAudio.
            }
        }
        return false;
    }

    List<BluetoothDevice> bonded() {
        if (!enabled()) return Collections.emptyList();
        try {
            List<BluetoothDevice> list = new ArrayList<>(adapter.getBondedDevices());
            list.sort((a, b) -> name(a).compareToIgnoreCase(name(b)));
            return list;
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    /** The name the user gave the device, else its own; never its address. */
    static String name(BluetoothDevice d) {
        String n = null;
        try {
            n = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? d.getAlias() : null;
            if (n == null || n.trim().isEmpty()) n = d.getName();
        } catch (RuntimeException ignored) {
            // No permission: fall through.
        }
        return n == null || n.trim().isEmpty() ? "?" : n.trim();
    }

    /** A string resource for what the device is: audio, remote or input, or another device. */
    static int kind(BluetoothDevice d) {
        try {
            BluetoothClass c = d.getBluetoothClass();
            if (c != null) {
                int major = c.getMajorDeviceClass();
                if (major == BluetoothClass.Device.Major.AUDIO_VIDEO) return R.string.mibox_bt_kind_audio;
                if (major == BluetoothClass.Device.Major.PERIPHERAL) return R.string.mibox_bt_kind_input;
                if (major == BluetoothClass.Device.Major.PHONE) return R.string.mibox_bt_kind_phone;
            }
        } catch (RuntimeException ignored) {
            // See name.
        }
        return R.string.mibox_bt_kind_other;
    }
}
