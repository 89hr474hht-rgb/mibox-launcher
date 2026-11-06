package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Chooses what the remote's Netflix, YouTube and Prime Video buttons open. The remote is drawn on the left
 * (RemoteView) and the button whose row has the focus lights up on it; OK on a row shows the apps, and the chosen
 * one is kept in RemoteRemap, read by RemoteKeysService at every press. A notice says when that service is off.
 */
public final class RemoteKeysActivity extends Activity {
    private RemoteView remote;
    private LinearLayout rows, apps;
    private ScrollView picker;
    private TextView title, subtitle;
    private View service;
    private int picking;   // the key whose app list is open, 0 for none
    private Typeface semibold, regular;
    private float dp;
    private List<App> appList;

    private static final class App {
        final String pkg, label;
        final Drawable icon;

        App(String pkg, String label, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.mibox_activity_remote_keys);
        dp = getResources().getDisplayMetrics().density;
        semibold = UiFonts.semibold(this);
        regular = UiFonts.regular(this);
        rows = findViewById(R.id.mibox_rk_rows);
        apps = findViewById(R.id.mibox_rk_apps);
        picker = findViewById(R.id.mibox_rk_picker);
        title = findViewById(R.id.mibox_rk_title);
        subtitle = findViewById(R.id.mibox_rk_subtitle);
        service = findViewById(R.id.mibox_rk_service);
        remote = new RemoteView(this);
        ((FrameLayout) findViewById(R.id.mibox_rk_remote)).addView(remote, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        findViewById(R.id.mibox_rk_service_open).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(this, R.string.mibox_screen_unavailable, Toast.LENGTH_LONG).show();
            }
        });
        buildRows();
        UiFonts.apply(getWindow().getDecorView());
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
        if (state == null) enterSide();
    }

    @Override
    protected void onResume() {
        super.onResume();
        service.setVisibility(RemoteRemap.serviceOn(this) ? View.GONE : View.VISIBLE);
        if (picking == 0) refreshRows();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (picking != 0) showRows(picking);
        else super.onBackPressed();
    }

    // The three button rows.

    private void buildRows() {
        rows.removeAllViews();
        for (int key : RemoteRemap.KEYS) {
            LinearLayout row = row();
            row.setTag(key);
            row.addView(new RemoteView.Chip(this, key), new LinearLayout.LayoutParams(px(104), px(44)));
            LinearLayout texts = texts(row, getString(buttonName(key)), "");
            texts.setPadding(px(16), 0, 0, 0);
            ImageView icon = new ImageView(this);
            icon.setDuplicateParentStateEnabled(true);
            row.addView(icon, new LinearLayout.LayoutParams(px(40), px(40)));
            ImageView chevron = new ImageView(this);
            chevron.setImageResource(R.drawable.mibox_set_chevron);
            chevron.setDuplicateParentStateEnabled(true);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            cl.leftMargin = px(14);
            row.addView(chevron, cl);
            row.setOnFocusChangeListener((v, has) -> {
                if (has) remote.select(key);
            });
            row.setOnClickListener(v -> showPicker(key));
            rows.addView(row);
        }
        refreshRows();
    }

    private void refreshRows() {
        PackageManager pm = getPackageManager();
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            int key = (Integer) row.getTag();
            String target = RemoteRemap.target(this, key);
            TextView sub = row.findViewWithTag("sub");
            ImageView icon = (ImageView) ((ViewGroup) row).getChildAt(2);
            if (RemoteRemap.NONE.equals(target)) {
                sub.setText(R.string.mibox_rk_none_sub);
                icon.setImageResource(R.drawable.mibox_ic_block);
                continue;
            }
            try {
                ApplicationInfo ai = pm.getApplicationInfo(target, 0);
                sub.setText(ai.loadLabel(pm));
                icon.setImageDrawable(ai.loadIcon(pm));
            } catch (PackageManager.NameNotFoundException e) {
                sub.setText(R.string.mibox_rk_missing);
                icon.setImageResource(R.drawable.mibox_ic_block);
            }
        }
    }

    // The app list for one button.

    private void showPicker(int key) {
        picking = key;
        remote.select(key);
        if (appList == null) appList = loadApps();
        apps.removeAllViews();
        String current = RemoteRemap.target(this, key);
        View focus = addAppRow(key, RemoteRemap.NONE, getString(R.string.mibox_rk_none), getDrawable(R.drawable.mibox_ic_block),
                RemoteRemap.NONE.equals(current));
        for (App a : appList) {
            View r = addAppRow(key, a.pkg, a.label, a.icon, a.pkg.equals(current));
            if (a.pkg.equals(current)) focus = r;
        }
        UiFonts.apply(apps);
        swapTexts(getString(buttonName(key)), getString(R.string.mibox_rk_pick_sub));
        slide(rows, picker, true);
        View f = focus;
        picker.post(() -> f.requestFocus());
    }

    private View addAppRow(int key, String pkg, String label, Drawable icon, boolean selected) {
        LinearLayout row = row();
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        iv.setDuplicateParentStateEnabled(true);
        row.addView(iv, new LinearLayout.LayoutParams(px(40), px(40)));
        LinearLayout texts = texts(row, label, null);
        texts.setPadding(px(16), 0, 0, 0);
        if (selected) {
            ImageView tick = new ImageView(this);
            tick.setImageResource(R.drawable.mibox_check_green);
            row.addView(tick, new LinearLayout.LayoutParams(px(28), px(28)));
        }
        row.setOnClickListener(v -> {
            RemoteRemap.set(this, key, pkg);
            showRows(key);
            remote.press();
        });
        apps.addView(row);
        return row;
    }

    private void showRows(int key) {
        picking = 0;
        refreshRows();
        swapTexts(getString(R.string.mibox_rk_headline), getString(R.string.mibox_rk_sub));
        slide(picker, rows, false);
        for (int i = 0; i < rows.getChildCount(); i++) {
            View r = rows.getChildAt(i);
            if ((Integer) r.getTag() == key) {
                r.post(r::requestFocus);
                // The new choice lands with a small flash of its row.
                r.setScaleX(0.97f);
                r.setScaleY(0.97f);
                r.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new DecelerateInterpolator(2f)).start();
            }
        }
    }

    /** Every app with a TV or phone launcher entry, LaunchOS itself left out, by name. */
    private List<App> loadApps() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> found = new ArrayList<>();
        found.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        found.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        Set<String> seen = new HashSet<>();
        List<App> out = new ArrayList<>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            out.add(new App(pkg, String.valueOf(ri.activityInfo.applicationInfo.loadLabel(pm)),
                    ri.activityInfo.applicationInfo.loadIcon(pm)));
        }
        Locale l = getResources().getConfiguration().getLocales().get(0);
        out.sort((a, b) -> a.label.toLowerCase(l).compareTo(b.label.toLowerCase(l)));
        return out;
    }

    // Building blocks.

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.mibox_set_row_bg);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(px(14), 0, px(16), 0);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(74)));
        return row;
    }

    /** The title and, unless sub is null, a second line tagged "sub", in the row's state colours. */
    private LinearLayout texts(LinearLayout row, String name, String sub) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setDuplicateParentStateEnabled(true);
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextSize(18);
        t.setSingleLine(true);
        t.setTextColor(getColorStateList(R.color.mibox_row_text));
        t.setDuplicateParentStateEnabled(true);
        if (semibold != null) t.setTypeface(semibold);
        box.addView(t);
        if (sub != null) {
            TextView s = new TextView(this);
            s.setTag("sub");
            s.setText(sub);
            s.setTextSize(14);
            s.setSingleLine(true);
            s.setTextColor(getColorStateList(R.color.mibox_row_text_dim));
            s.setDuplicateParentStateEnabled(true);
            if (regular != null) s.setTypeface(regular);
            box.addView(s);
        }
        row.addView(box, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return box;
    }

    private void swapTexts(String head, String sub) {
        title.animate().cancel();
        title.animate().alpha(0f).setDuration(120).withEndAction(() -> {
            title.setText(head);
            subtitle.setText(sub);
            title.animate().alpha(1f).setDuration(200).start();
        }).start();
    }

    /** The outgoing panel slides away, the incoming one slides in, to the left going in and to the right coming back. */
    private void slide(View out, View in, boolean forward) {
        if (still()) {
            out.setVisibility(View.GONE);
            in.setVisibility(View.VISIBLE);
            return;
        }
        float d = 50 * dp * (forward ? 1 : -1);
        out.animate().alpha(0f).translationX(-d).setDuration(180).withEndAction(() -> {
            out.setVisibility(View.GONE);
            out.setTranslationX(0f);
            out.setAlpha(1f);
        }).start();
        in.setVisibility(View.VISIBLE);
        in.setAlpha(0f);
        in.setTranslationX(d);
        in.animate().alpha(1f).translationX(0f).setStartDelay(90).setDuration(300)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    private void enterSide() {
        if (still()) return;
        View side = findViewById(R.id.mibox_rk_side);
        side.setAlpha(0f);
        side.setTranslationX(36 * dp);
        side.animate().alpha(1f).translationX(0f).setStartDelay(150).setDuration(480)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    private boolean still() {
        return Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    private static int buttonName(int key) {
        return key == RemoteRemap.NETFLIX ? R.string.mibox_rk_btn_netflix
                : key == RemoteRemap.YOUTUBE ? R.string.mibox_rk_btn_youtube : R.string.mibox_rk_btn_prime;
    }
}
