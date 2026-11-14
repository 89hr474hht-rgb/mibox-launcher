package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The Bluetooth menu under the Bluetooth pill: connected devices first, their icon in a disc of the accent, then the
 * other paired ones, then "Pair a device…" and "Bluetooth settings…". It only reads Bluetooth; how it looks and how
 * the remote moves in it is {@link MiboxMenu}, shared with the network and timer menus.
 */
final class BluetoothPopover implements BluetoothStatus.Listener {
    /** Google TV's "pair a remote or accessory" screen. */
    static final String ACTION_CONNECT_INPUT = "com.google.android.intent.action.CONNECT_INPUT";

    private final Activity home;
    private final View anchor;
    private final BluetoothStatus status;
    private final MiboxMenu menu;

    private BluetoothPopover(Activity home, View anchor) {
        this.home = home;
        this.anchor = anchor;
        menu = new MiboxMenu(home, anchor, R.string.mibox_bluetooth);
        menu.addAction(R.drawable.mibox_ic_add, home.getString(R.string.mibox_bt_pair_more),
                () -> menu.open(new Intent(ACTION_CONNECT_INPUT)));
        menu.addAction(R.drawable.mibox_ic_gear, home.getString(R.string.mibox_bt_more),
                () -> menu.open(new Intent(Settings.ACTION_SETTINGS)));
        status = new BluetoothStatus(home, this);
        menu.setOnDismiss(status::close);
    }

    /** Opens the menu under the pill; it closes with Back, like the other menus of the top bar. */
    static BluetoothPopover show(Activity home, View anchor) {
        BluetoothPopover p = new BluetoothPopover(home, anchor);
        p.status.start();
        p.refresh();
        p.menu.show();
        return p;
    }

    boolean isShowing() {
        return menu.isShowing();
    }

    void dismiss() {
        menu.dismiss();
    }

    @Override
    public void onBluetoothChanged() {
        anchor.post(() -> {
            if (menu.isShowing()) refresh();
        });
    }

    private void refresh() {
        menu.setState(home.getString(status.enabled() ? R.string.mibox_bt_on : R.string.mibox_bt_off));
        menu.clearRows();
        List<BluetoothDevice> on = new ArrayList<>(), off = new ArrayList<>();
        for (BluetoothDevice d : status.bonded()) (status.connected(d) ? on : off).add(d);
        int connected = on.size();
        on.addAll(off);
        if (on.isEmpty()) menu.addNote(home.getString(R.string.mibox_bt_none));
        for (int i = 0; i < on.size(); i++) {
            BluetoothDevice d = on.get(i);
            boolean isOn = i < connected;
            menu.addRow(BluetoothStatus.icon(d), BluetoothStatus.name(d),
                    isOn ? home.getString(R.string.mibox_bt_connected) : null, isOn,
                    () -> Toast.makeText(home, R.string.mibox_bt_manage_hint, Toast.LENGTH_LONG).show());
        }
    }
}
