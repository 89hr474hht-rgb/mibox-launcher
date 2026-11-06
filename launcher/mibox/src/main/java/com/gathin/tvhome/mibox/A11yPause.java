package com.gathin.tvhome.mibox;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Takes LaunchOS's accessibility helpers away while an app that refuses them is in front (mibox_a11y_pause_packages).
 * SFR TV 7.1.0 checks AccessibilityManager.isEnabled() each time one of its screens comes up, and while it is true it
 * shows "L'interface n'est pas encore compatible avec le mode Accessibilité" and the remote's OK does nothing there.
 * isEnabled() is true as soon as any service is bound, even one that only reads keys, so the only way through is to have
 * none bound: RemoteKeysService sees the app's window come up and drops every LaunchOS helper from the enabled list; SFR
 * TV hears accessibility go off and removes its message. They are put back as soon as a LaunchOS screen comes up (Home,
 * the settings, the sleep timer's warning). While the app is in front the remote's app buttons do nothing.
 * Needs WRITE_SECURE_SETTINGS (pm grant); without it, nothing changes.
 */
final class A11yPause {
    private static final String PREFS = "mibox_a11y_pause";
    private static final String KEY_PAUSED = "paused";
    /** The sleep timer's warning put the helpers back over a paused app (main thread only). */
    private static boolean restoredByWarning;
    /** Bumped by every LaunchOS screen, so a late re-pause never lands on one. */
    private static int epoch;

    private A11yPause() {
    }

    /** The apps to pause for; empty turns the feature off. */
    static String[] packages(Context c) {
        return c.getResources().getStringArray(R.array.mibox_a11y_pause_packages);
    }

    /** Whether there are apps to pause for and LaunchOS may rewrite the enabled list (pm grant WRITE_SECURE_SETTINGS). */
    static boolean active(Context c) {
        return packages(c).length > 0
                && c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean pausesFor(String[] packages, CharSequence pkg) {
        if (pkg == null) return false;
        for (String p : packages) if (p.contentEquals(pkg)) return true;
        return false;
    }

    /** Drops this app's services from the enabled list and notes them, to be put back by {@link #resume}. */
    static void pause(Context c) {
        if (!active(c)) return;
        try {
            String list = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            List<String> removed = new ArrayList<>();
            String kept = without(list, c.getPackageName(), removed);
            if (removed.isEmpty()) return;   // already paused: the note from the first time stays
            // Noted before the list changes, so a crash in between can't lose them.
            Set<String> paused = new LinkedHashSet<>(split(prefs(c).getString(KEY_PAUSED, "")));
            paused.addAll(removed);
            prefs(c).edit().putString(KEY_PAUSED, String.join(":", paused)).commit();
            Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, kept);
        } catch (RuntimeException ignored) {
            // The settings are the system's; a refusal leaves the helpers as they were.
        }
    }

    /** Puts back the services {@link #pause} took away, if any; called whenever a LaunchOS screen comes up. */
    static void resume(Context c) {
        boolean warning = c instanceof SleepWarningActivity;
        epoch++;
        if (!warning) restoredByWarning = false;
        String noted = prefs(c).getString(KEY_PAUSED, "");
        if (noted.isEmpty()) return;
        if (c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return;
        try {
            String list = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    with(list, split(noted)));
            // Android writes 0 here once the list is empty.
            Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            prefs(c).edit().remove(KEY_PAUSED).commit();
            // The warning needs RemoteKeysService to put the box to sleep; the app stays behind it.
            if (warning) restoredByWarning = true;
        } catch (RuntimeException ignored) {
            // Left noted: the next LaunchOS screen tries again.
        }
    }

    /**
     * The sleep timer's warning closes. If it was what put the helpers back, the paused app is in front again (or the
     * box is going to sleep over it), so they go again, after {@code delayMs} (time for the sleep action to land).
     * A LaunchOS screen that comes up in between cancels it.
     */
    static void warningClosed(Context c, long delayMs) {
        if (!restoredByWarning) return;
        restoredByWarning = false;
        Context app = c.getApplicationContext();
        int at = epoch;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (epoch == at) pause(app);
        }, delayMs);
    }

    /** The colon-separated list without the entries of {@code pkg}, which go to {@code removed}. */
    static String without(String list, String pkg, List<String> removed) {
        List<String> kept = new ArrayList<>();
        for (String entry : split(list)) {
            if (entry.startsWith(pkg + "/")) removed.add(entry);
            else kept.add(entry);
        }
        return String.join(":", kept);
    }

    /** The list with {@code add} appended, skipping the entries already on it (in either name form). */
    static String with(String list, List<String> add) {
        List<String> out = new ArrayList<>(split(list));
        Set<String> present = new LinkedHashSet<>();
        for (String e : out) present.add(full(e));
        for (String entry : add) {
            if (present.add(full(entry))) out.add(entry);
        }
        return String.join(":", out);
    }

    /** "pkg/.Cls" and "pkg/pkg.Cls" name the same service: both read as the second. */
    static String full(String entry) {
        int slash = entry.indexOf('/');
        if (slash < 0 || slash + 1 >= entry.length() || entry.charAt(slash + 1) != '.') return entry;
        return entry.substring(0, slash + 1) + entry.substring(0, slash) + entry.substring(slash + 1);
    }

    static List<String> split(String list) {
        List<String> out = new ArrayList<>();
        if (list == null || list.equals("null")) return out;
        for (String s : list.split(":")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
