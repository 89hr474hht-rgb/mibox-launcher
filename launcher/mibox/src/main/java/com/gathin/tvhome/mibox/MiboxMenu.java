package com.gathin.tvhome.mibox;

import android.annotation.SuppressLint;
import android.app.Activity;
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

/**
 * A small menu that drops from a top-bar pill, in the look of the launcher's own menus: a title and a state on
 * the first line, rows with an icon in a disc, then actions. The network menu is built on it; the Bluetooth menu
 * has the same layout.
 */
final class MiboxMenu {
    private static final int WIDTH_DP = 320;

    private final Activity home;
    private final View anchor;
    private final View panel;
    private final LinearLayout rows, actions;
    private final TextView state;
    private final PopupWindow popup;
    private final Typeface medium;
    private final float density;

    @SuppressLint("InflateParams") // a PopupWindow's content has no parent to inflate into
    MiboxMenu(Activity home, View anchor, int title) {
        this.home = home;
        this.anchor = anchor;
        density = home.getResources().getDisplayMetrics().density;
        medium = UiFonts.medium(home);
        panel = LayoutInflater.from(home).inflate(R.layout.mibox_popup_bluetooth, null, false);
        rows = panel.findViewById(R.id.mibox_bt_rows);
        actions = panel.findViewById(R.id.mibox_bt_actions);
        state = panel.findViewById(R.id.mibox_bt_state);
        TextView t = panel.findViewById(R.id.mibox_bt_title);
        t.setText(title);
        if (medium != null) t.setTypeface(medium);
        popup = new PopupWindow(panel, Math.round(WIDTH_DP * density), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // Back dismisses only with a background
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
    }

    void setOnDismiss(Runnable r) {
        popup.setOnDismissListener(r::run);
    }

    void setState(CharSequence text) {
        state.setText(text);
    }

    void clearRows() {
        rows.removeAllViews();
    }

    /** An information row: not focusable, so the remote moves straight between the actions. */
    void addInfo(int icon, CharSequence name, CharSequence value, boolean good) {
        View row = row(rows, icon, name, value, good);
        row.setFocusable(false);
        row.setClickable(false);
        rows.addView(row);
    }

    void addAction(int icon, CharSequence name, Runnable action) {
        View row = row(actions, icon, name, null, false);
        row.setOnClickListener(v -> action.run());
        actions.addView(row);
    }

    boolean isShowing() {
        return popup.isShowing();
    }

    void dismiss() {
        if (popup.isShowing()) popup.dismiss();
    }

    /** The launcher's drop-in: fade and unfold from the top edge, hanging right-aligned under the pill. */
    void show() {
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, Math.round(10 * density), Gravity.END);
        boolean reduced = Settings.Global.getFloat(home.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(reduced ? 0 : 220)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        if (actions.getChildCount() > 0) actions.getChildAt(0).requestFocus();
    }

    /** Closes the menu and opens a screen, falling back to Android's settings when the TV has no such screen. */
    void open(Intent intent) {
        dismiss();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            home.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            try {
                home.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException e2) {
                Toast.makeText(home, R.string.mibox_screen_unavailable, Toast.LENGTH_LONG).show();
            }
        }
    }

    private View row(ViewGroup parent, int icon, CharSequence name, CharSequence value, boolean good) {
        View row = LayoutInflater.from(home).inflate(R.layout.mibox_item_bt_row, parent, false);
        row.findViewById(R.id.mibox_bt_disc).setBackgroundResource(
                good ? R.drawable.mibox_bt_circle_on : R.drawable.mibox_bt_circle_off);
        ((ImageView) row.findViewById(R.id.mibox_bt_icon)).setImageResource(icon);
        TextView title = row.findViewById(R.id.mibox_bt_name);
        title.setText(name);
        if (medium != null) title.setTypeface(medium);
        TextView st = row.findViewById(R.id.mibox_bt_status);
        st.setText(value);
        row.findViewById(R.id.mibox_bt_dot).setVisibility(good ? View.VISIBLE : View.GONE);
        return row;
    }
}
