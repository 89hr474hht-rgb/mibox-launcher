package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Starts with the launcher's process (a provider is created before any activity) and wires the updater in
 * without changing the upstream code: the update prompt and the home screen's extras (HomeExtras), the updates row
 * of the launcher's settings, and the scheduled checks.
 */
public final class MiboxInit extends ContentProvider {
    static final String HOME = "com.gathin.tvhome.MainActivity";
    static final String SETTINGS = "com.gathin.tvhome.LauncherSettingsActivity";

    private static WeakReference<Activity> resumedHome = new WeakReference<>(null);
    private static WeakReference<UpdatePrompt> prompt = new WeakReference<>(null);

    @Override
    public boolean onCreate() {
        Application app = (Application) getContext().getApplicationContext();
        french(app.getResources());
        app.registerActivityLifecycleCallbacks(new Callbacks());
        UpdateScheduler.schedule(app);
        UpdateScheduler.checkSoon(app);
        return true;
    }

    /**
     * LaunchOS in French whatever the device's language (mibox_french): the app's resources, and each activity's
     * before it builds its views, are switched to French. Android's own screens keep the device's language.
     */
    @SuppressWarnings("deprecation") // updateConfiguration is the only way without changing upstream's activities
    static void french(android.content.res.Resources res) {
        if (!res.getBoolean(R.bool.mibox_french)) return;
        android.content.res.Configuration cfg = res.getConfiguration();
        if ("fr".equals(cfg.getLocales().get(0).getLanguage())) return;
        android.content.res.Configuration fr = new android.content.res.Configuration(cfg);
        fr.setLocale(java.util.Locale.FRANCE);
        java.util.Locale.setDefault(java.util.Locale.FRANCE);
        res.updateConfiguration(fr, res.getDisplayMetrics());
    }

    /** Shows the prompt when the home screen is in front and there is a release to offer. */
    static void promptIfHomeVisible() {
        Activity home = resumedHome.get();
        UpdatePrompt shown = prompt.get();
        if (home == null || home.isFinishing() || (shown != null && shown.isShowing())) return;
        if (BootSplash.isShowing(home)) return;   // asked again once the start-up screen is gone
        UpdateState state = new UpdateState(home);
        if (!state.shouldPrompt(home)) return;
        Release r = state.available();
        UpdatePrompt p = new UpdatePrompt(home, r);
        prompt = new WeakReference<>(p);
        p.show();
    }

    private static void dismissPrompt() {
        UpdatePrompt p = prompt.get();
        if (p != null) p.dismissQuietly();
        prompt = new WeakReference<>(null);
    }

    private static final Map<Activity, HomeExtras> EXTRAS = new WeakHashMap<>();

    private static final class Callbacks implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityResumed(Activity activity) {
            PlayingBubble.screenIn(activity);
            // A LaunchOS screen is in front, so no app that refuses accessibility is: the helpers come back.
            A11yPause.resume(activity);
            RemoteKeysService.refreshPause();
            String name = activity.getClass().getName();
            if (HOME.equals(name)) {
                resumedHome = new WeakReference<>(activity);
                HomeExtras extras = EXTRAS.get(activity);
                if (extras == null) {
                    extras = HomeExtras.attach(activity);
                    EXTRAS.put(activity, extras);
                }
                extras.resume();
                // The Mi Box S Netflix key's stand-in follows Netflix being disabled or back (NetflixKeyActivity).
                NetflixKeyActivity.sync(activity);
                // After the home screen has drawn, so the prompt never delays the first frame.
                activity.getWindow().getDecorView().postDelayed(MiboxInit::promptIfHomeVisible, 1500);
            } else if (SETTINGS.equals(name)) {
                addSettingsRow(activity);
            }
        }

        @Override
        public void onActivityPaused(Activity activity) {
            PlayingBubble.screenOut(activity);
            if (SETTINGS.equals(activity.getClass().getName())) SettingsPanes.paused(activity);
            HomeExtras extras = EXTRAS.get(activity);
            if (extras != null) extras.pause();
            if (resumedHome.get() == activity) {
                resumedHome = new WeakReference<>(null);
                dismissPrompt();
            }
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            BootSplash.dismissFor(activity);
            HomeExtras extras = EXTRAS.remove(activity);
            if (extras != null) extras.destroy();
            UpdatePrompt p = prompt.get();
            if (p != null && p.owner() == activity) dismissPrompt();
        }

        @Override
        public void onActivityPreCreated(Activity activity, Bundle state) {
            // Android 10+ calls this before the activity reads its resources.
            french(activity.getResources());
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle state) {
            // Before Android 10 there is no pre-created call: this is the earliest point left.
            if (Build.VERSION.SDK_INT < 29) french(activity.getResources());
            // LaunchOS's start-up screen, on the first home screen of a boot only. Shown as the home screen is
            // created, before its first frame, so the home screen never flashes up before it.
            if (HOME.equals(activity.getClass().getName()) && BootSplash.firstHomeOfBoot(activity)) {
                BootSplash.show(activity, MiboxInit::promptIfHomeVisible);
            }
            windowMotion(activity);
        }

        /**
         * How each screen opens and closes: Réglages, the player and the screensaver preview fade in rising a
         * little; the screens opened from a settings row come in from the right. Set on the window before it is
         * shown, so the launcher's own theme (no window motion) stays as it is.
         */
        private void windowMotion(Activity activity) {
            String name = activity.getClass().getName();
            int style = 0;
            if (SETTINGS.equals(name) || activity instanceof MusicActivity || activity instanceof DreamPreviewActivity) {
                style = R.style.LoWindowPage;
            } else if (activity instanceof UpdatesActivity || activity instanceof RemoteKeysActivity) {
                style = R.style.LoWindowSub;
            }
            if (style != 0) activity.getWindow().setWindowAnimations(style);
        }

        @Override
        public void onActivityStarted(Activity activity) {
        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle state) {
        }
    }

    /**
     * Adds "Launcher updates" at the top of the launcher's settings, styled like its neighbours; the device
     * settings row stays last. The launcher hides its own package from its app library, so this row and the prompt
     * are the way in. When the upstream layout changes and the anchor row is not found, nothing is added.
     */
    static void addSettingsRow(Activity activity) {
        // The Mi Box settings page (mibox/app-res) has its own row and subtitle: fill those in.
        int ownId = UpstreamViews.id(activity, "miboxUpdatesChoice");
        View own = ownId == 0 ? null : activity.findViewById(ownId);
        if (own != null) {
            own.setOnClickListener(v -> activity.startActivity(new Intent(activity, UpdatesActivity.class)));
            int remoteId = UpstreamViews.id(activity, "miboxRemoteChoice");
            View remote = remoteId == 0 ? null : activity.findViewById(remoteId);
            if (remote != null) {
                remote.setOnClickListener(v -> activity.startActivity(new Intent(activity, RemoteKeysActivity.class)));
            }
            int subId = UpstreamViews.id(activity, "miboxSettingsSubtitle");
            View sub = subId == 0 ? null : activity.findViewById(subId);
            if (sub instanceof TextView) {
                ((TextView) sub).setText(activity.getString(R.string.mibox_set_subtitle, BuildConfig.MIBOX_VERSION));
            }
            SettingsPanes.attach(activity);
            return;
        }
        int anchorId = UpstreamViews.id(activity, "tvSettingsChoice");
        int styleId = UpstreamViews.style(activity, "SettingsRow");
        if (anchorId == 0 || styleId == 0) return;
        View anchor = activity.findViewById(anchorId);
        if (anchor == null || !(anchor.getParent() instanceof LinearLayout)) return;
        LinearLayout parent = (LinearLayout) anchor.getParent();
        if (parent.findViewWithTag(SETTINGS_ROW_TAG) != null) return;

        TextView updates = settingsRow(activity, styleId, R.string.mibox_settings_row, UpdatesActivity.class);
        updates.setTag(SETTINGS_ROW_TAG);
        LinearLayout.LayoutParams top = rowParams(anchor);
        top.topMargin = Math.round(28 * activity.getResources().getDisplayMetrics().density);
        // Right under the page title, the first child.
        parent.addView(updates, Math.min(1, parent.getChildCount()), top);
    }

    private static TextView settingsRow(Activity activity, int styleId, int text, Class<? extends Activity> target) {
        TextView row = new TextView(activity, null, 0, styleId);
        row.setText(text);
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnClickListener(v -> activity.startActivity(new Intent(activity, target)));
        return row;
    }

    private static LinearLayout.LayoutParams rowParams(View anchor) {
        ViewGroup.LayoutParams base = anchor.getLayoutParams();
        return base instanceof LinearLayout.LayoutParams
                ? new LinearLayout.LayoutParams((LinearLayout.LayoutParams) base)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static final String SETTINGS_ROW_TAG = "mibox_updates_row";

    // A ContentProvider is only used here for its early start; it serves no data.
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
