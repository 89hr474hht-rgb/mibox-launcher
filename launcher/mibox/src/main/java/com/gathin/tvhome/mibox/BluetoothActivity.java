package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * The Bluetooth page: on or off, the paired devices with what each is and whether it is connected, and the way to
 * Android's own screens for pairing and for everything else. It only reads Bluetooth; it changes nothing.
 */
public final class BluetoothActivity extends Activity implements BluetoothStatus.Listener {
    /** Google TV's "pair a remote or accessory" screen. */
    static final String ACTION_CONNECT_INPUT = "com.google.android.intent.action.CONNECT_INPUT";

    private BluetoothStatus status;
    private TextView state;
    private LinearLayout devices;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.mibox_activity_bluetooth);
        state = findViewById(R.id.mibox_bt_state);
        devices = findViewById(R.id.mibox_bt_devices);
        status = new BluetoothStatus(this, this);
        findViewById(R.id.mibox_bt_pair).setOnClickListener(v -> open(new Intent(ACTION_CONNECT_INPUT)));
        findViewById(R.id.mibox_bt_device_settings).setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));
        findViewById(R.id.mibox_bt_pair).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        status.start();
        refresh();
    }

    @Override
    protected void onPause() {
        status.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        status.close();
        super.onDestroy();
    }

    @Override
    public void onBluetoothChanged() {
        runOnUiThread(this::refresh);
    }

    private void refresh() {
        if (!status.available()) {
            state.setText(R.string.mibox_bt_unavailable);
        } else {
            state.setText(status.enabled() ? R.string.mibox_bt_on : R.string.mibox_bt_off);
        }
        View focused = getCurrentFocus();
        boolean focusInList = focused != null && focused.getParent() == devices;
        int focusedIndex = focusInList ? devices.indexOfChild(focused) : -1;
        devices.removeAllViews();
        List<BluetoothDevice> list = status.bonded();
        LayoutInflater inflater = getLayoutInflater();
        if (list.isEmpty()) {
            TextView none = (TextView) inflater.inflate(R.layout.mibox_item_text, devices, false);
            none.setText(R.string.mibox_bt_none);
            devices.addView(none);
        }
        for (BluetoothDevice d : list) {
            View row = inflater.inflate(R.layout.mibox_item_bt_device, devices, false);
            boolean on = status.connected(d);
            ((TextView) row.findViewById(R.id.mibox_bt_name)).setText(BluetoothStatus.name(d));
            ((TextView) row.findViewById(R.id.mibox_bt_kind)).setText(BluetoothStatus.kind(d));
            TextView link = row.findViewById(R.id.mibox_bt_link);
            link.setText(on ? R.string.mibox_bt_connected : R.string.mibox_bt_not_connected);
            link.setEnabled(on);
            row.setOnClickListener(v -> Toast.makeText(this, R.string.mibox_bt_manage_hint, Toast.LENGTH_LONG).show());
            devices.addView(row);
        }
        if (focusedIndex >= 0 && devices.getChildCount() > 0) {
            devices.getChildAt(Math.min(focusedIndex, devices.getChildCount() - 1)).requestFocus();
        }
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (ActivityNotFoundException | SecurityException e2) {
                Toast.makeText(this, R.string.mibox_screen_unavailable, Toast.LENGTH_LONG).show();
            }
        }
    }
}
