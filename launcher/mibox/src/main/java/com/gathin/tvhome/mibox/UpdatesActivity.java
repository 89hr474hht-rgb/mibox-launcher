package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * The LaunchOS updates page: the logo with what the updater is doing around it (UpdateHalo), the installed version,
 * the state in one line and the actions on the left; what is new, notices and the history in cards on the right.
 */
public final class UpdatesActivity extends Activity implements Updater.Listener {
    static final String EXTRA_INSTALL = "com.gathin.tvhome.mibox.INSTALL";
    /** A check that answers at once still shows the search for this long, so it reads as a search. */
    private static final long MIN_SEARCH_MS = 1600;

    private UpdateState state;
    private UpdateHalo halo;
    private TextView installed, stateLine, status, availableTitle, notes, progressText, ignoredText, history, permissionText;
    private View availableBox, aboutBox, ignoredBox, offerActions, install, ignore, check, permissionBox;
    private ProgressBar progress;
    private long searchUntil;
    private boolean busy;
    private final Runnable refreshRunnable = this::refresh;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.mibox_activity_updates);
        state = new UpdateState(this);
        halo = findViewById(R.id.mibox_halo);
        installed = findViewById(R.id.mibox_installed);
        stateLine = findViewById(R.id.mibox_state);
        status = findViewById(R.id.mibox_status);
        availableBox = findViewById(R.id.mibox_available_box);
        availableTitle = findViewById(R.id.mibox_available_title);
        notes = findViewById(R.id.mibox_notes);
        aboutBox = findViewById(R.id.mibox_about_box);
        offerActions = findViewById(R.id.mibox_offer_actions);
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
        UiFonts.apply(getWindow().getDecorView());

        install.setOnClickListener(v -> {
            if (!busy || Updater.get().phase() == Updater.Phase.WAITING_USER) startInstall();
        });
        ignore.setOnClickListener(v -> {
            if (busy) return;
            Release r = state.available();
            if (r != null) state.ignore(r.version);
            refresh();
            check.requestFocus();
        });
        findViewById(R.id.mibox_unignore).setOnClickListener(v -> {
            state.unignore();
            refresh();
        });
        check.setOnClickListener(v -> {
            if (busy) return;
            searchUntil = SystemClock.uptimeMillis() + MIN_SEARCH_MS;
            Updater.get().check(this, this::refresh);
            refresh();
        });
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
        else if (offerActions.getVisibility() == View.VISIBLE) install.requestFocus();
        else check.requestFocus();
        if (saved == null) enter();
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
        halo.removeCallbacks(refreshRunnable);
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
        installed.setText(getString(R.string.mibox_version_line, String.valueOf(current)));

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
        aboutBox.setVisibility(offer ? View.GONE : View.VISIBLE);
        if (offer) {
            availableTitle.setText(getString(R.string.mibox_available_card, r.version.toString(),
                    Math.max(1, Math.round(r.apkSize / 1024f))));
            notes.setText(plainNotes(r.notes));
        }

        // The state, in the halo and in one line under the logo. A quick answer keeps the search on screen a moment.
        long now = SystemClock.uptimeMillis();
        boolean searching = phase == Updater.Phase.CHECKING || (phase == Updater.Phase.IDLE && now < searchUntil);
        UpdateHalo.Mode mode;
        String line;
        if (searching) {
            mode = UpdateHalo.Mode.SEARCH;
            line = getString(R.string.mibox_state_search);
        } else if (phase == Updater.Phase.DOWNLOADING) {
            mode = UpdateHalo.Mode.DOWNLOAD;
            line = getString(R.string.mibox_state_download);
        } else if (phase == Updater.Phase.VERIFYING) {
            mode = UpdateHalo.Mode.VERIFY;
            line = getString(R.string.mibox_state_verify);
        } else if (phase == Updater.Phase.WAITING_USER) {
            mode = UpdateHalo.Mode.WAIT;
            line = getString(R.string.mibox_state_wait);
        } else if (offer) {
            mode = UpdateHalo.Mode.AVAILABLE;
            line = getString(R.string.mibox_state_available, r.version.toString());
        } else if (state.lastError() != null || u.error() != null) {
            mode = UpdateHalo.Mode.ERROR;
            line = getString(R.string.mibox_state_error);
        } else if (last > 0) {
            mode = UpdateHalo.Mode.OK;
            line = getString(R.string.mibox_state_ok);
        } else {
            mode = UpdateHalo.Mode.IDLE;
            line = getString(R.string.mibox_state_idle);
        }
        halo.setMode(mode, u.percent());
        if (!line.contentEquals(stateLine.getText())) {
            stateLine.setText(line);
            stateLine.setAlpha(0.2f);
            stateLine.animate().alpha(1f).setDuration(260).start();
        }
        halo.removeCallbacks(refreshRunnable);
        if (phase == Updater.Phase.IDLE && now < searchUntil) halo.postDelayed(refreshRunnable, searchUntil - now);

        boolean busy = phase != Updater.Phase.IDLE || searching;
        offerActions.setVisibility(offer ? View.VISIBLE : View.GONE);
        check.setVisibility(offer ? View.GONE : View.VISIBLE);
        // Dimmed rather than disabled while busy: a disabled view gives its focus away, and the focus would land on
        // another card instead of coming back to the button just pressed. The clicks check busy themselves.
        this.busy = busy;
        boolean canInstall = !busy || phase == Updater.Phase.WAITING_USER;
        install.setAlpha(canInstall ? 1f : 0.45f);
        ignore.setAlpha(busy ? 0.45f : 1f);
        check.setAlpha(busy ? 0.45f : 1f);
        if (!offer && !check.hasFocus() && getCurrentFocus() == null) check.requestFocus();
        progress.setVisibility(phase == Updater.Phase.DOWNLOADING || phase == Updater.Phase.VERIFYING
                ? View.VISIBLE : View.GONE);
        progress.setProgress(u.percent());
        String p = null;
        switch (phase) {
            case DOWNLOADING: p = getString(R.string.mibox_downloading, u.percent()); break;
            case VERIFYING: p = getString(R.string.mibox_verifying); break;
            case WAITING_USER: p = getString(R.string.mibox_waiting_user); break;
            case CHECKING: break;
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

    /** The left column rises in, then the cards slide in from the right one after the other. */
    private void enter() {
        if (Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) return;
        float dp = getResources().getDisplayMetrics().density;
        ViewGroup left = findViewById(R.id.mibox_left);
        for (int i = 0; i < left.getChildCount(); i++) {
            View v = left.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(14 * dp);
            v.animate().alpha(1f).translationY(0f).setStartDelay(60L * i).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator(1.8f)).start();
        }
        ViewGroup cards = findViewById(R.id.mibox_cards);
        for (int i = 0, n = 0; i < cards.getChildCount(); i++) {
            View v = cards.getChildAt(i);
            if (v.getVisibility() != View.VISIBLE) continue;
            v.setAlpha(0f);
            v.setTranslationX(40 * dp);
            v.animate().alpha(1f).translationX(0f).setStartDelay(180 + 90L * n++).setDuration(460)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
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
