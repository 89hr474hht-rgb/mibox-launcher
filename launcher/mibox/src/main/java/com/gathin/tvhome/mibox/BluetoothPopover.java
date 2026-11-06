package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The Bluetooth menu: a small panel that drops from the Bluetooth pill, in the look of the launcher's own menus
 * (the Inputs menu), laid out like Apple's: connected devices first with their icon in a blue disc, then the
 * others, then "Pair a device…" and "Bluetooth settings…". It only reads Bluetooth.
 */
final class BluetoothPopover implements BluetoothStatus.Listener {
    /** Google TV's "pair a remote or accessory" screen. */
    static final String ACTION_CONNECT_INPUT = "com.google.android.intent.action.CONNECT_INPUT";
    private static final int WIDTH_DP = 300;

    private final Activity home;
    private final View anchor;
    private final BluetoothStatus status;
    private final View panel;
    private final LinearLayout rows;
    private final TextView state;
    private final PopupWindow popup;
    private final Typeface medium, body;
    private final float density;

    @android.annotation.SuppressLint("InflateParams") // a PopupWindow's content has no parent to inflate into
    private BluetoothPopover(Activity home, View anchor) {
        this.home = home;
        this.anchor = anchor;
        density = home.getResources().getDisplayMetrics().density;
        medium = UiFonts.medium(home);
        body = UiFonts.regular(home);
        panel = LayoutInflater.from(home).inflate(R.layout.mibox_popup_bluetooth, null, false);
        rows = panel.findViewById(R.id.mibox_bt_rows);
        state = panel.findViewById(R.id.mibox_bt_state);
        if (medium != null) ((TextView) panel.findViewById(R.id.mibox_bt_title)).setTypeface(medium);
        if (body != null) state.setTypeface(body);
        LinearLayout actions = panel.findViewById(R.id.mibox_bt_actions);
        actions.addView(row(actions, R.drawable.mibox_ic_add, home.getString(R.string.mibox_bt_pair_more), false, null,
                v -> open(new Intent(ACTION_CONNECT_INPUT))));
        actions.addView(row(actions, R.drawable.mibox_ic_gear, home.getString(R.string.mibox_bt_more), false, null,
                v -> open(new Intent(Settings.ACTION_SETTINGS))));

        popup = new PopupWindow(panel, Math.round(WIDTH_DP * density), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // Back dismisses only with a background
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        status = new BluetoothStatus(home, this);
        popup.setOnDismissListener(status::close);
    }

    /** Opens the menu under the pill; it closes with Back, like the launcher's other menus. */
    static BluetoothPopover show(Activity home, View anchor) {
        BluetoothPopover p = new BluetoothPopover(home, anchor);
        p.status.start();
        p.refresh();
        p.dropIn();
        return p;
    }

    boolean isShowing() {
        return popup.isShowing();
    }

    void dismiss() {
        if (popup.isShowing()) popup.dismiss();
    }

    /** The launcher's drop-in: fade and unfold from the top edge, hanging right-aligned under the pill. */
    private void dropIn() {
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, Math.round(10 * density), Gravity.END);
        boolean reduced = Settings.Global.getFloat(home.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(reduced ? 0 : 220)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        View first = rows.getChildCount() > 0 ? rows.getChildAt(0) : null;
        if (first != null && first.isFocusable()) first.requestFocus();
        else panel.findViewById(R.id.mibox_bt_actions).requestFocus();
    }

    @Override
    public void onBluetoothChanged() {
        panel.post(() -> {
            if (popup.isShowing()) refresh();
        });
    }

    private void refresh() {
        state.setText(status.enabled() ? R.string.mibox_bt_on : R.string.mibox_bt_off);
        View focused = panel.findFocus();
        int focusIndex = focused != null && focused.getParent() == rows ? rows.indexOfChild(focused) : -1;
        rows.removeAllViews();
        List<BluetoothDevice> on = new ArrayList<>(), off = new ArrayList<>();
        for (BluetoothDevice d : status.bonded()) (status.connected(d) ? on : off).add(d);
        on.addAll(off);
        if (on.isEmpty()) {
            TextView none = new TextView(home);
            none.setText(R.string.mibox_bt_none);
            none.setTextColor(0x73FFFFFF);
            none.setTextSize(13);
            if (body != null) none.setTypeface(body);
            int pad = Math.round(12 * density);
            none.setPadding(pad, pad / 2, pad, pad / 2);
            rows.addView(none);
        }
        int connected = on.size() - off.size();
        for (int i = 0; i < on.size(); i++) {
            BluetoothDevice d = on.get(i);
            rows.addView(row(rows, BluetoothStatus.icon(d), BluetoothStatus.name(d), i < connected,
                    i < connected ? home.getString(R.string.mibox_bt_connected) : null,
                    v -> Toast.makeText(home, R.string.mibox_bt_manage_hint, Toast.LENGTH_LONG).show()));
        }
        if (focusIndex >= 0 && rows.getChildCount() > 0) {
            rows.getChildAt(Math.min(focusIndex, rows.getChildCount() - 1)).requestFocus();
        }
    }

    private View row(ViewGroup parent, int icon, String name, boolean connected, String statusText,
                     View.OnClickListener click) {
        View row = LayoutInflater.from(home).inflate(R.layout.mibox_item_bt_row, parent, false);
        row.findViewById(R.id.mibox_bt_disc).setBackgroundResource(
                connected ? R.drawable.mibox_bt_circle_on : R.drawable.mibox_bt_circle_off);
        ((ImageView) row.findViewById(R.id.mibox_bt_icon)).setImageResource(icon);
        TextView title = row.findViewById(R.id.mibox_bt_name);
        title.setText(name);
        if (medium != null) title.setTypeface(medium);
        TextView st = row.findViewById(R.id.mibox_bt_status);
        st.setText(statusText);
        if (body != null) st.setTypeface(body);
        row.findViewById(R.id.mibox_bt_dot).setVisibility(connected ? View.VISIBLE : View.GONE);
        row.setOnClickListener(click);
        return row;
    }

    private void open(Intent intent) {
        dismiss();
        try {
            home.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            try {
                home.startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (ActivityNotFoundException | SecurityException e2) {
                Toast.makeText(home, R.string.mibox_screen_unavailable, Toast.LENGTH_LONG).show();
            }
        }
    }
}
