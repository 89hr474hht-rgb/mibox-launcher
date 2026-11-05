package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * A Bluetooth pill in the home screen's top bar, right of the network pill, in the launcher's own pill style.
 * It reads "Bluetooth", or the connected speaker or headphones by name, scrolling when the name is longer than
 * the pill. The remote is always connected, so it is not named. OK opens the Bluetooth page.
 */
final class BluetoothPill implements BluetoothStatus.Listener {
    private static final int MAX_WIDTH_DP = 210;
    /** The name scrolls this many times when it changes or the pill takes focus, then stops (no endless redraw). */
    private static final int MARQUEE_LOOPS = 3;

    private final TextView view;
    private final BluetoothStatus status;

    private BluetoothPill(TextView view, BluetoothStatus status) {
        this.view = view;
        this.status = status;
    }

    /** Adds the pill once per home screen; null when the upstream layout has changed and it cannot be placed. */
    @SuppressWarnings("DiscouragedApi") // the app's R is not visible from this library
    static BluetoothPill attach(Activity home) {
        String pkg = home.getPackageName();
        int wifiId = home.getResources().getIdentifier("btnWifi", "id", pkg);
        int styleId = home.getResources().getIdentifier("TopButton", "style", pkg);
        if (wifiId == 0 || styleId == 0) return null;
        View wifi = home.findViewById(wifiId);
        if (wifi == null || !(wifi.getParent() instanceof LinearLayout)) return null;
        LinearLayout bar = (LinearLayout) wifi.getParent();

        TextView pill = new TextView(home, null, 0, styleId);
        pill.setId(View.generateViewId());   // the bar's key guard tells its ends apart by id
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.mibox_ic_bluetooth, 0, 0, 0);
        pill.setSingleLine(true);
        pill.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        pill.setMarqueeRepeatLimit(MARQUEE_LOOPS);
        pill.setHorizontalFadingEdgeEnabled(true);
        pill.setMaxWidth(Math.round(MAX_WIDTH_DP * home.getResources().getDisplayMetrics().density));
        pill.setText(R.string.mibox_bluetooth);
        pill.setOnClickListener(v -> home.startActivity(new Intent(home, BluetoothActivity.class)));
        // Same keys as the other pills: Up stays in the bar, Down goes back to the tile Up came from (the network
        // pill's own handler does that, so it is asked to).
        pill.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return wifi.dispatchKeyEvent(event);
            return false;
        });
        pill.setOnFocusChangeListener((v, focused) -> {
            if (focused) restartMarquee(pill);
        });
        ViewGroup.LayoutParams base = wifi.getLayoutParams();
        LinearLayout.LayoutParams lp = base instanceof LinearLayout.LayoutParams
                ? new LinearLayout.LayoutParams((LinearLayout.LayoutParams) base)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bar.addView(pill, bar.indexOfChild(wifi) + 1, lp);

        BluetoothPill[] self = new BluetoothPill[1];
        self[0] = new BluetoothPill(pill, new BluetoothStatus(home, () -> {
            if (self[0] != null) self[0].onBluetoothChanged();
        }));
        return self[0];
    }

    void resume() {
        status.start();
        onBluetoothChanged();
    }

    void pause() {
        status.stop();
    }

    void destroy() {
        status.close();
    }

    @Override
    public void onBluetoothChanged() {
        view.post(this::refresh);
    }

    private void refresh() {
        List<BluetoothDevice> audio = status.connectedAudio();
        String text;
        if (audio.isEmpty()) {
            text = view.getContext().getString(R.string.mibox_bluetooth);
        } else {
            text = BluetoothStatus.name(audio.get(0));
            if (audio.size() > 1) text += " +" + (audio.size() - 1);
        }
        view.setContentDescription(text);
        if (!TextUtils.equals(view.getText(), text)) {
            view.setText(text);
            restartMarquee(view);
        }
    }

    private static void restartMarquee(TextView v) {
        v.setSelected(false);
        v.setSelected(true);
    }
}
