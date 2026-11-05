package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The launcher's App info, opened from a tile's menu (holding OK on an app, then "App info"): the app's name and
 * icon, its version, when it was installed and last updated, where it came from, the Android version it targets
 * and its size, with "Open" and "More info…", which is Android's own App info page. Over the home screen, in the
 * look of the launcher's menus. It only reads what Android tells any app about the packages it can see.
 */
public final class AppInfoActivity extends Activity {
    /** The upstream launcher's SystemSettings.ACTION_APP_INFO and EXTRA_PACKAGE. */
    static final String EXTRA_PACKAGE = "com.gathin.tvhome.extra.PACKAGE";

    private View root, panel;
    private boolean closing;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String pkg = getIntent().getStringExtra(EXTRA_PACKAGE);
        PackageManager pm = getPackageManager();
        PackageInfo info;
        try {
            info = pkg == null ? null : pm.getPackageInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            info = null;
        }
        if (info == null || info.applicationInfo == null) {
            Toast.makeText(this, R.string.mibox_screen_unavailable, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(R.layout.mibox_activity_app_info);
        root = findViewById(R.id.mibox_ai_root);
        panel = findViewById(R.id.mibox_ai_panel);
        root.setOnClickListener(v -> close());
        panel.setClickable(true);   // a click on the panel is not a click on the backdrop

        ApplicationInfo ai = info.applicationInfo;
        Typeface medium = UiFonts.medium(this);
        TextView name = findViewById(R.id.mibox_ai_name);
        name.setText(ai.loadLabel(pm));
        if (medium != null) name.setTypeface(medium);
        ((TextView) findViewById(R.id.mibox_ai_package)).setText(pkg);
        ((ImageView) findViewById(R.id.mibox_ai_icon)).setImageDrawable(ai.loadIcon(pm));

        LinearLayout rows = findViewById(R.id.mibox_ai_rows);
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        DateFormat date = DateFormat.getDateInstance(DateFormat.LONG, locale);
        long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : versionCode(info);
        addRow(rows, R.drawable.mibox_sym_new_releases, R.string.mibox_ai_version, AppFacts.version(info.versionName, code));
        addRow(rows, R.drawable.mibox_sym_event, R.string.mibox_ai_installed, date.format(new Date(info.firstInstallTime)));
        if (info.lastUpdateTime - info.firstInstallTime > 60_000) {
            addRow(rows, R.drawable.mibox_sym_update, R.string.mibox_ai_updated, date.format(new Date(info.lastUpdateTime)));
        }
        addRow(rows, R.drawable.mibox_sym_shop, R.string.mibox_ai_source, source(pm, pkg, ai));
        addRow(rows, R.drawable.mibox_sym_android, R.string.mibox_ai_target, AppFacts.android(ai.targetSdkVersion));
        long size = apkSize(ai);
        if (size > 0) addRow(rows, R.drawable.mibox_sym_package_2, R.string.mibox_ai_size, AppFacts.size(size, locale));

        TextView open = findViewById(R.id.mibox_ai_open);
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        final Intent start = launch;
        if (start == null) open.setVisibility(View.GONE);
        else open.setOnClickListener(v -> go(start));
        TextView more = findViewById(R.id.mibox_ai_more);
        Intent system = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        if (pm.resolveActivity(system, PackageManager.MATCH_DEFAULT_ONLY) == null) more.setVisibility(View.GONE);
        else more.setOnClickListener(v -> go(system));
        if (medium != null) {
            open.setTypeface(medium);
            more.setTypeface(medium);
        }
        (start != null ? open : more).requestFocus();
        enter();
    }

    @SuppressWarnings("deprecation")
    private static long versionCode(PackageInfo info) {
        return info.versionCode;
    }

    private void addRow(LinearLayout rows, int icon, int label, String value) {
        View row = LayoutInflater.from(this).inflate(R.layout.mibox_item_info_row, rows, false);
        ((ImageView) row.findViewById(R.id.mibox_ir_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.mibox_ir_label)).setText(label);
        ((TextView) row.findViewById(R.id.mibox_ir_value)).setText(value);
        rows.addView(row);
    }

    private String source(PackageManager pm, String pkg, ApplicationInfo ai) {
        String installer = null;
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                InstallSourceInfo src = pm.getInstallSourceInfo(pkg);
                installer = src.getInstallingPackageName();
            } else {
                installer = installerBefore30(pm, pkg);
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            // Unknown: decided from the system flags alone.
        }
        boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        boolean updated = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        switch (AppFacts.source(installer, system, updated)) {
            case AppFacts.SOURCE_PLAY: return getString(R.string.mibox_ai_source_play);
            case AppFacts.SOURCE_PREINSTALLED: return getString(R.string.mibox_ai_source_system);
            case AppFacts.SOURCE_MANUAL: return getString(R.string.mibox_ai_source_manual);
            default:
                try {
                    return pm.getApplicationLabel(pm.getApplicationInfo(installer, 0)).toString();
                } catch (PackageManager.NameNotFoundException | RuntimeException e) {
                    return installer;
                }
        }
    }

    @SuppressWarnings("deprecation")
    private static String installerBefore30(PackageManager pm, String pkg) {
        return pm.getInstallerPackageName(pkg);
    }

    /** The APK and its splits; the app's data is not counted (Android only tells that to the app itself). */
    private static long apkSize(ApplicationInfo ai) {
        long total = 0;
        if (ai.sourceDir != null) total += new File(ai.sourceDir).length();
        if (ai.splitSourceDirs != null) for (String s : ai.splitSourceDirs) total += new File(s).length();
        return total;
    }

    private void go(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
            finish();
            overridePendingTransition(0, 0);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.mibox_screen_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private boolean reducedMotion() {
        return Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }

    /** The backdrop fades in and the panel rises into place, a little like an iOS sheet. */
    private void enter() {
        if (reducedMotion()) return;
        float dp = getResources().getDisplayMetrics().density;
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(160).start();
        panel.setScaleX(0.94f);
        panel.setScaleY(0.94f);
        panel.setTranslationY(16 * dp);
        panel.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(260)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
    }

    private void close() {
        if (closing) return;
        closing = true;
        if (reducedMotion()) {
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        float dp = getResources().getDisplayMetrics().density;
        panel.animate().scaleX(0.96f).scaleY(0.96f).translationY(10 * dp).setDuration(150)
                .setInterpolator(new AccelerateInterpolator(1.4f)).start();
        root.animate().alpha(0f).setDuration(160).withEndAction(() -> {
            finish();
            overridePendingTransition(0, 0);
        }).start();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        close();
    }
}
