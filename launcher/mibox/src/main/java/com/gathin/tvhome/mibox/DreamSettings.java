package com.gathin.tvhome.mibox;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;

/**
 * The Veille page's choices. What the sleep screen shows is kept here; when it starts, when the TV goes off and
 * which screensaver Android uses are Android's own settings, written only when the owner picks a value on the page
 * and only with WRITE_SECURE_SETTINGS (granted once over ADB). The screensaver that was set before LaunchOS's is
 * kept, so switching LaunchOS's off gives it back.
 */
final class DreamSettings {
    enum Style { CLOCK, DATE, SMALL, BLACK }

    static final int[] START_MIN = {5, 10, 15, 30};
    /** 0 is never. */
    static final int[] OFF_HOURS = {1, 2, 4, 0};

    private static final String PREFS = "mibox_dream";
    private static final String COMPONENTS = "screensaver_components";
    private static final String ENABLED = "screensaver_enabled";
    private static final String ON_SLEEP = "screensaver_activate_on_sleep";
    private static final String SLEEP_TIMEOUT = "sleep_timeout";

    private DreamSettings() {
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Style style(Context c) {
        try {
            return Style.valueOf(p(c).getString("style", Style.DATE.name()));
        } catch (IllegalArgumentException e) {
            return Style.DATE;
        }
    }

    static void setStyle(Context c, Style s) {
        p(c).edit().putString("style", s.name()).apply();
    }

    static boolean nightDim(Context c) {
        return p(c).getBoolean("night", true);
    }

    static void setNightDim(Context c, boolean on) {
        p(c).edit().putBoolean("night", on).apply();
    }

    static ComponentName component(Context c) {
        return new ComponentName(c.getPackageName(), OledDream.class.getName());
    }

    static boolean canWrite(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    /** Android's screensaver is LaunchOS's and screensavers are on. */
    static boolean active(Context c) {
        String set = Settings.Secure.getString(c.getContentResolver(), COMPONENTS);
        boolean enabled = Settings.Secure.getInt(c.getContentResolver(), ENABLED, 1) != 0;
        return enabled && set != null && set.contains(component(c).flattenToString());
    }

    /** Switches LaunchOS's sleep screen on or off; false when Android refused. */
    static boolean setActive(Context c, boolean on) {
        if (!canWrite(c)) return false;
        try {
            String mine = component(c).flattenToString();
            String now = Settings.Secure.getString(c.getContentResolver(), COMPONENTS);
            if (on) {
                if (now != null && !now.contains(mine)) p(c).edit().putString("previous", now).apply();
                Settings.Secure.putString(c.getContentResolver(), COMPONENTS, mine);
                Settings.Secure.putInt(c.getContentResolver(), ENABLED, 1);
                Settings.Secure.putInt(c.getContentResolver(), ON_SLEEP, 1);
            } else {
                String previous = p(c).getString("previous", null);
                if (previous != null && !previous.isEmpty()) {
                    Settings.Secure.putString(c.getContentResolver(), COMPONENTS, previous);
                } else {
                    Settings.Secure.putInt(c.getContentResolver(), ENABLED, 0);
                }
            }
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    /** Minutes without the remote before the sleep screen (Android's screen-off timeout), or -1 when unknown. */
    static int startMinutes(Context c) {
        int ms = Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, -1);
        return ms <= 0 ? -1 : Math.round(ms / 60_000f);
    }

    static boolean setStartMinutes(Context c, int minutes) {
        if (!canWrite(c)) return false;
        try {
            return Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, minutes * 60_000);
        } catch (SecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Hours of sleep screen before the box sleeps and the TV goes off (HDMI-CEC); 0 never; -1 another value. */
    static int offHours(Context c) {
        long ms = Settings.Secure.getLong(c.getContentResolver(), SLEEP_TIMEOUT, -2);
        if (ms == -1) return 0;
        if (ms <= 0 || ms % 3_600_000L != 0) return -1;
        return (int) (ms / 3_600_000L);
    }

    static boolean setOffHours(Context c, int hours) {
        if (!canWrite(c)) return false;
        try {
            return Settings.Secure.putLong(c.getContentResolver(), SLEEP_TIMEOUT, hours <= 0 ? -1 : hours * 3_600_000L);
        } catch (SecurityException e) {
            return false;
        }
    }
}
