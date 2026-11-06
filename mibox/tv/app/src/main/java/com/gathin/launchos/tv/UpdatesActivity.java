package com.gathin.launchos.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * LaunchOS TV's updates, with the same logic as LaunchOS: a check of the repository's tv-v releases (daily in the
 * background, or now), then download, SHA-256 and signature checks, and Android's own install confirmation.
 */
public final class UpdatesActivity extends Activity implements Updater.Listener {
    private static final int HISTORY_LINES = 6;

    private TextView status, statusSub, notes, progress, install, historyTitle;
    private ImageView statusIcon;
    private ViewGroup history;
    private View later, permissionBox;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_updates);
        Rows.header(this, R.drawable.ic_update, R.string.upd_title, 0);
        status = findViewById(R.id.status);
        statusSub = findViewById(R.id.status_sub);
        statusIcon = findViewById(R.id.status_icon);
        notes = findViewById(R.id.notes);
        progress = findViewById(R.id.progress);
        install = findViewById(R.id.install);
        later = findViewById(R.id.later);
        history = findViewById(R.id.history);
        historyTitle = findViewById(R.id.history_title);
        permissionBox = findViewById(R.id.permission_box);

        findViewById(R.id.check).setOnClickListener(v -> Updater.get().check(this, this::refresh));
        install.setOnClickListener(v -> {
            Release r = new UpdateState(this).available();
            if (r != null) Updater.get().install(this, r);
        });
        later.setOnClickListener(v -> {
            Release r = new UpdateState(this).available();
            if (r != null) new UpdateState(this).snooze(r.version);
            finish();
        });
        findViewById(R.id.permission_open).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.upd_permission_no_screen, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Updater.get().addListener(this);
        refresh();
        (install.getVisibility() == View.VISIBLE ? install : findViewById(R.id.check)).requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Updater.get().removeListener(this);
    }

    @Override
    public void onUpdaterChanged() {
        refresh();
    }

    private void refresh() {
        UpdateState state = new UpdateState(this);
        SemVer now = UpdateState.installed(this);
        TextView hint = findViewById(R.id.page_hint);
        hint.setVisibility(View.VISIBLE);
        hint.setText(getString(R.string.upd_installed, String.valueOf(now)));
        Updater u = Updater.get();
        Release r = state.available();
        boolean newer = r != null && r.version.isNewerThan(now);

        String sub = null;
        int icon = R.drawable.ic_check_circle, tint = R.color.ok;
        status.setTextColor(getColor(R.color.text));
        if (u.phase() == Updater.Phase.CHECKING) {
            status.setText(R.string.upd_checking);
            icon = R.drawable.ic_refresh;
            tint = R.color.accent_light;
        } else if (state.lastError() != null) {
            status.setText(state.lastError());
            status.setTextColor(getColor(R.color.danger));
            icon = R.drawable.ic_alert;
            tint = R.color.danger;
        } else if (newer) {
            status.setText(getString(R.string.upd_available, String.valueOf(r.version)));
            icon = R.drawable.ic_update;
            tint = R.color.accent_light;
        } else if (state.lastCheck() <= 0) {
            status.setText(R.string.upd_never_checked);
            icon = R.drawable.ic_history;
            tint = R.color.text_dim;
        } else {
            status.setText(R.string.upd_up_to_date);
        }
        if (state.lastCheck() > 0 && u.phase() != Updater.Phase.CHECKING) {
            sub = getString(R.string.upd_last_check, DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT,
                    Locale.FRANCE).format(new Date(state.lastCheck())));
        }
        statusIcon.setImageResource(icon);
        statusIcon.setImageTintList(getColorStateList(tint));
        statusSub.setVisibility(sub == null ? View.GONE : View.VISIBLE);
        statusSub.setText(sub);

        notes.setVisibility(newer && !r.notes.isEmpty() ? View.VISIBLE : View.GONE);
        if (newer) notes.setText(plainNotes(r.notes));

        boolean busy = u.phase() == Updater.Phase.DOWNLOADING || u.phase() == Updater.Phase.VERIFYING;
        install.setVisibility(newer && !busy ? View.VISIBLE : View.GONE);
        later.setVisibility(newer && !busy ? View.VISIBLE : View.GONE);
        if (newer) install.setText(getString(R.string.upd_install, String.valueOf(r.version)));

        String p = null;
        boolean bad = false;
        switch (u.phase()) {
            case DOWNLOADING:
                p = getString(R.string.upd_downloading, u.percent());
                break;
            case VERIFYING:
                p = getString(R.string.upd_verifying);
                break;
            case WAITING_USER:
                p = getString(R.string.upd_waiting);
                break;
            default:
                if (u.error() != null) {
                    p = u.error();
                    bad = true;
                }
        }
        progress.setVisibility(p == null ? View.GONE : View.VISIBLE);
        if (p != null) {
            progress.setText(p);
            progress.setTextColor(getColor(bad ? R.color.danger : R.color.text));
        }

        permissionBox.setVisibility(getPackageManager().canRequestPackageInstalls() ? View.GONE : View.VISIBLE);

        List<UpdateState.Entry> entries = state.entries();
        historyTitle.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
        history.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
        history.removeAllViews();
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.FRANCE);
        for (int i = 0; i < entries.size() && i < HISTORY_LINES; i++) {
            UpdateState.Entry e = entries.get(i);
            View line = LayoutInflater.from(this).inflate(R.layout.item_history, history, false);
            boolean ok = UpdateState.R_INSTALLED.equals(e.result);
            boolean failed = !ok && !UpdateState.R_CANCELLED.equals(e.result) && !UpdateState.R_IGNORED.equals(e.result);
            ImageView i1 = line.findViewById(R.id.icon);
            i1.setImageResource(ok ? R.drawable.ic_check : failed ? R.drawable.ic_alert : R.drawable.ic_history);
            i1.setImageTintList(getColorStateList(ok ? R.color.ok : failed ? R.color.danger : R.color.text_dim));
            String what = e.version + "  ·  " + result(e.result);
            if (e.detail != null && !e.detail.isEmpty()) what += " (" + e.detail + ")";
            ((TextView) line.findViewById(R.id.what)).setText(what);
            ((TextView) line.findViewById(R.id.when)).setText(df.format(new Date(e.time)));
            history.addView(line);
        }
    }

    private String result(String r) {
        switch (r) {
            case UpdateState.R_INSTALLED:
                return getString(R.string.upd_h_installed);
            case UpdateState.R_CANCELLED:
                return getString(R.string.upd_h_cancelled);
            case UpdateState.R_IGNORED:
                return getString(R.string.upd_h_ignored);
            default:
                return getString(R.string.upd_h_failed);
        }
    }

    /** Release notes as plain lines: no Markdown marks, no checksum line. */
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
