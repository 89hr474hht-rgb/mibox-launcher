package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** The updates page: installed version, the offered release and its notes, progress, errors and history. */
public final class UpdatesActivity extends Activity implements Updater.Listener {
    static final String EXTRA_INSTALL = "com.gathin.tvhome.mibox.INSTALL";

    private UpdateState state;
    private TextView installed, status, availableTitle, notes, progressText, ignoredText, history, permissionText;
    private View availableBox, ignoredBox, install, ignore, check, permissionBox;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.mibox_activity_updates);
        state = new UpdateState(this);
        installed = findViewById(R.id.mibox_installed);
        status = findViewById(R.id.mibox_status);
        availableBox = findViewById(R.id.mibox_available_box);
        availableTitle = findViewById(R.id.mibox_available_title);
        notes = findViewById(R.id.mibox_notes);
        install = findViewById(R.id.mibox_install);
        ignore = findViewById(R.id.mibox_ignore);
        progress = findViewById(R.id.mibox_progress);
        progressText = findViewById(R.id.mibox_progress_text);
        ignoredBox = findViewById(R.id.mibox_ignored_box);
        ignoredText = findViewById(R.id.mibox_ignored_text);
        check = findViewById(R.id.mibox_check);
        history = findViewById(R.id.mibox_history);
        permissionBox = findViewById(R.id.mibox_permission_box);
        permissionText = findViewById(R.id.mibox_permission_text);

        install.setOnClickListener(v -> startInstall());
        ignore.setOnClickListener(v -> {
            Release r = state.available();
            if (r != null) state.ignore(r.version);
            refresh();
            check.requestFocus();
        });
        findViewById(R.id.mibox_unignore).setOnClickListener(v -> {
            state.unignore();
            refresh();
        });
        check.setOnClickListener(v -> Updater.get().check(this, this::refresh));
        findViewById(R.id.mibox_permission_open).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(this, R.string.mibox_permission_no_screen, Toast.LENGTH_LONG).show();
            }
        });

        boolean installNow = saved == null && getIntent().getBooleanExtra(EXTRA_INSTALL, false);
        refresh();
        if (installNow) startInstall();
        else if (availableBox.getVisibility() == View.VISIBLE) install.requestFocus();
        else check.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Updater.get().addListener(this);
        refresh();
    }

    @Override
    protected void onPause() {
        Updater.get().removeListener(this);
        super.onPause();
    }

    @Override
    public void onUpdaterChanged() {
        refresh();
    }

    private void startInstall() {
        Release r = state.available();
        if (r == null) return;
        Updater.get().install(this, r);
        refresh();
    }

    private void refresh() {
        Updater u = Updater.get();
        Updater.Phase phase = u.phase();
        SemVer current = UpdateState.installed(this);
        installed.setText(getString(R.string.mibox_installed_version, String.valueOf(current)));

        long last = state.lastCheck();
        String when = last > 0 ? DateUtils.formatDateTime(this, last, DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR) : null;
        StringBuilder s = new StringBuilder(when != null ? getString(R.string.mibox_last_check, when)
                : getString(R.string.mibox_never_checked));
        if (state.lastError() != null) s.append('\n').append(state.lastError());
        status.setText(s);

        Release r = state.available();
        boolean offer = r != null && r.version.isNewerThan(current);
        availableBox.setVisibility(offer ? View.VISIBLE : View.GONE);
        if (offer) {
            availableTitle.setText(getString(R.string.mibox_available, r.version.toString(),
                    Math.max(1, Math.round(r.apkSize / 1024f))));
            notes.setText(plainNotes(r.notes));
        } else if (state.lastError() == null && last > 0) {
            status.append("\n" + getString(R.string.mibox_up_to_date));
        }

        boolean busy = phase != Updater.Phase.IDLE;
        install.setEnabled(!busy || phase == Updater.Phase.WAITING_USER);
        ignore.setEnabled(!busy);
        check.setEnabled(!busy);
        progress.setVisibility(phase == Updater.Phase.DOWNLOADING || phase == Updater.Phase.VERIFYING
                ? View.VISIBLE : View.GONE);
        progress.setProgress(u.percent());
        String p = null;
        switch (phase) {
            case CHECKING: p = getString(R.string.mibox_checking); break;
            case DOWNLOADING: p = getString(R.string.mibox_downloading, u.percent()); break;
            case VERIFYING: p = getString(R.string.mibox_verifying); break;
            case WAITING_USER: p = getString(R.string.mibox_waiting_user); break;
            default: if (u.error() != null) p = u.error();
        }
        progressText.setVisibility(p == null ? View.GONE : View.VISIBLE);
        progressText.setText(p);

        boolean allowed = getPackageManager().canRequestPackageInstalls();
        permissionBox.setVisibility(allowed ? View.GONE : View.VISIBLE);
        if (!allowed) permissionText.setText(R.string.mibox_err_permission);

        String ign = state.ignored();
        ignoredBox.setVisibility(ign == null ? View.GONE : View.VISIBLE);
        if (ign != null) ignoredText.setText(getString(R.string.mibox_ignored, ign));

        history.setText(historyText());
    }

    private CharSequence historyText() {
        List<UpdateState.Entry> entries = state.entries();
        if (entries.isEmpty()) return getString(R.string.mibox_history_empty);
        StringBuilder sb = new StringBuilder();
        for (UpdateState.Entry e : entries) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(DateUtils.formatDateTime(this, e.time, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME
                    | DateUtils.FORMAT_NUMERIC_DATE)).append("  ·  ").append(e.version).append("  ·  ")
                    .append(resultLabel(e.result));
            if (e.detail != null && !e.detail.isEmpty()) sb.append(" (").append(e.detail).append(')');
        }
        return sb;
    }

    private String resultLabel(String result) {
        switch (result) {
            case UpdateState.R_INSTALLED: return getString(R.string.mibox_r_installed);
            case UpdateState.R_CANCELLED: return getString(R.string.mibox_r_cancelled);
            case UpdateState.R_IGNORED: return getString(R.string.mibox_r_ignored);
            default: return getString(R.string.mibox_r_failed);
        }
    }

    /** Release notes as plain lines: headings without their #, list items as bullets, no checksum line. */
    static String plainNotes(String notes) {
        StringBuilder sb = new StringBuilder();
        for (String line : notes.split("\n")) {
            String t = line.trim();
            if (t.startsWith("SHA-256")) continue;
            while (t.startsWith("#")) t = t.substring(1).trim();
            if (t.startsWith("- ") || t.startsWith("* ")) t = "• " + t.substring(2);
            t = t.replace("**", "").replace("`", "");
            if (sb.length() > 0) sb.append('\n');
            sb.append(t);
        }
        return sb.toString().trim();
    }
}
