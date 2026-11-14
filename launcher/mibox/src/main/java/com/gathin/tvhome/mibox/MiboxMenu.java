package com.gathin.tvhome.mibox;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.launchos.design.Motion;

/**
 * The menu that drops from a top-bar pill: Wi-Fi and network, Bluetooth and the sleep timer all show through it, so
 * they look and move the same; what each one lists and does stays in NetworkPill, BluetoothPopover and TimerPill.
 *
 * <p>A title and a state on the first line, then rows (information, or a device to pick), then actions. It opens with
 * a short fade down from the pill and closes the same way, with Back too; the focus lands on the first row that can
 * take it, else the first action, and goes back to the pill when the menu closes. Main thread only.
 */
final class MiboxMenu {
    private static final int WIDTH_DP = 300;

    private final Activity home;
    private final View anchor;
    private final View panel;
    private final LinearLayout rows, actions;
    private final View rowsLine;
    private final TextView state;
    private final Popup popup;
    private final Typeface medium, body;
    private final float density;
    /** Which row had the focus before clearRows(), so a list rebuilt while open keeps the remote's place. */
    private int keptFocus = -1;

    @SuppressLint("InflateParams") // a PopupWindow's content has no parent to inflate into
    MiboxMenu(Activity home, View anchor, int title) {
        this.home = home;
        this.anchor = anchor;
        density = home.getResources().getDisplayMetrics().density;
        medium = UiFonts.medium(home);
        body = UiFonts.regular(home);
        panel = LayoutInflater.from(home).inflate(R.layout.mibox_popup_bluetooth, null, false);
        rows = panel.findViewById(R.id.mibox_bt_rows);
        actions = panel.findViewById(R.id.mibox_bt_actions);
        rowsLine = panel.findViewById(R.id.mibox_bt_rows_line);
        state = panel.findViewById(R.id.mibox_bt_state);
        TextView t = panel.findViewById(R.id.mibox_bt_title);
        t.setText(title);
        if (medium != null) t.setTypeface(medium);
        if (body != null) state.setTypeface(body);
        popup = new Popup(panel, Math.round(WIDTH_DP * density));
    }

    void setOnDismiss(Runnable r) {
        popup.setOnDismissListener(r::run);
    }

    void setState(CharSequence text) {
        state.setText(text);
    }

    /** Empties the rows; the focus goes back to the same place once they are added again. */
    void clearRows() {
        View focused = panel.findFocus();
        keptFocus = focused != null && focused.getParent() == rows ? rows.indexOfChild(focused) : -1;
        rows.removeAllViews();
    }

    /** An information row: not focusable, so the remote moves straight between the actions. */
    void addInfo(int icon, CharSequence name, CharSequence value, boolean good) {
        View row = row(rows, icon, name, value, good);
        row.setFocusable(false);
        row.setClickable(false);
        rows.addView(row);
        lines();
    }

    /** A row that can be picked (a Bluetooth device): focusable, does something on OK. */
    void addRow(int icon, CharSequence name, CharSequence value, boolean good, Runnable onClick) {
        View row = row(rows, icon, name, value, good);
        row.setOnClickListener(v -> onClick.run());
        rows.addView(row);
        if (keptFocus >= 0 && rows.getChildCount() - 1 == keptFocus) row.requestFocus();
        lines();
    }

    /** A line of text in place of the rows ("No device paired."). */
    void addNote(CharSequence text) {
        TextView none = new TextView(home);
        none.setText(text);
        none.setTextColor(home.getColor(R.color.lo_text_3));
        none.setTextSize(TypedValue.COMPLEX_UNIT_PX, home.getResources().getDimension(R.dimen.lo_text_small));
        if (body != null) none.setTypeface(body);
        int pad = Math.round(12 * density);
        none.setPadding(pad, pad / 2, pad, pad / 2);
        rows.addView(none);
        lines();
    }

    void addAction(int icon, CharSequence name, Runnable action) {
        View row = row(actions, icon, name, null, false);
        row.setOnClickListener(v -> action.run());
        actions.addView(row);
    }

    boolean isShowing() {
        return popup.isShowing();
    }

    /** Closes the menu the same way it opened. */
    void dismiss() {
        if (popup.isShowing()) popup.dismiss();
    }

    /** Opens under the pill, right-aligned, with a short fade down from the top edge. */
    void show() {
        lines();
        panel.setAlpha(0f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, Math.round(10 * density), Gravity.END);
        panel.animate().alpha(1f).translationY(0f).setDuration(Motion.on() ? Motion.MEDIUM : 0)
                .setInterpolator(Motion.ENTER).start();
        View first = null;
        for (int i = 0; i < rows.getChildCount() && first == null; i++) {
            if (rows.getChildAt(i).isFocusable()) first = rows.getChildAt(i);
        }
        if (first == null && actions.getChildCount() > 0) first = actions.getChildAt(0);
        if (first != null) first.requestFocus();
    }

    /** Closes the menu at once and opens a screen, falling back to Android's settings when the TV has no such screen. */
    void open(Intent intent) {
        popup.dismissNow();
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

    /** One line under the title; a second one only between rows and actions when there are both. */
    private void lines() {
        rowsLine.setVisibility(rows.getChildCount() > 0 && actions.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private View row(ViewGroup parent, int icon, CharSequence name, CharSequence value, boolean good) {
        View row = LayoutInflater.from(home).inflate(R.layout.mibox_item_bt_row, parent, false);
        row.findViewById(R.id.mibox_bt_disc).setBackgroundResource(
                good ? R.drawable.mibox_bt_circle_on : R.drawable.mibox_bt_circle_off);
        ImageView glyph = row.findViewById(R.id.mibox_bt_icon);
        glyph.setImageResource(icon);
        glyph.setImageTintList(ColorStateList.valueOf(home.getColor(good ? R.color.lo_on_accent : R.color.lo_text)));
        TextView title = row.findViewById(R.id.mibox_bt_name);
        title.setText(name);
        if (medium != null) title.setTypeface(medium);
        TextView st = row.findViewById(R.id.mibox_bt_status);
        st.setText(value);
        if (body != null) st.setTypeface(body);
        row.findViewById(R.id.mibox_bt_dot).setVisibility(good ? View.VISIBLE : View.GONE);
        return row;
    }

    /**
     * The popup window, closing with a short fade up (Back, a click outside or dismiss()). When the screen is going
     * away, or animations are off, it closes at once so no window is left behind.
     */
    private final class Popup extends PopupWindow {
        private boolean closing;

        Popup(View content, int width) {
            super(content, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
            setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // Back dismisses only with a background
            setOutsideTouchable(true);
            setAnimationStyle(0);
        }

        @Override
        public void dismiss() {
            if (closing || !isShowing() || !Motion.on() || home.isFinishing() || !anchor.isAttachedToWindow()) {
                dismissNow();
                return;
            }
            closing = true;
            panel.animate().cancel();
            panel.animate().alpha(0f).translationY(-6 * density).setDuration(Motion.SHORT).setInterpolator(Motion.MOVE)
                    .withEndAction(this::dismissNow).start();
        }

        void dismissNow() {
            closing = true;
            panel.animate().cancel();
            if (isShowing()) super.dismiss();
        }
    }
}
