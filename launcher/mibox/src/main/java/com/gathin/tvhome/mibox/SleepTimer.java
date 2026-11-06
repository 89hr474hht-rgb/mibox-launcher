package com.gathin.tvhome.mibox;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * The sleep timer: the box goes to sleep at a chosen time, and HDMI-CEC then switches the TV off. Nothing counts down
 * on screen. Two alarms do the work: one a minute before the end opens the last-minute warning over whatever app is
 * in front (SleepWarningActivity), one at the end puts the box to sleep through RemoteKeysService, in case the warning
 * could not open. Turning the TV off by hand, or a restart, ends the timer; Back on the warning stops it. An update of
 * LaunchOS keeps it (PackageReplacedReceiver re-arms the alarms Android cancelled).
 */
final class SleepTimer {
    static final long WARN_MS = 60_000L;
    static final long ADD_MS = 30 * 60_000L;
    /** The menu's choices, in minutes. */
    static final int[] CHOICES = {15, 30, 45, 60, 90, 120};

    static final String ACTION_WARN = "com.gathin.tvhome.mibox.SLEEP_WARN";
    static final String ACTION_SLEEP = "com.gathin.tvhome.mibox.SLEEP_NOW";
    private static final String PREFS = "mibox_sleep_timer";
    private static final String KEY_END = "end";

    private SleepTimer() {
    }

    /** When the box goes to sleep (System.currentTimeMillis), or 0 when no timer is set. */
    static long endAt(Context c) {
        return prefs(c).getLong(KEY_END, 0);
    }

    static boolean active(Context c) {
        return endAt(c) > System.currentTimeMillis();
    }

    static long remaining(Context c) {
        return Math.max(0, endAt(c) - System.currentTimeMillis());
    }

    static void start(Context c, long durationMs) {
        set(c, System.currentTimeMillis() + durationMs);
    }

    /** Adds time from now or from the current end, whichever is later. */
    static void extend(Context c, long ms) {
        set(c, Math.max(endAt(c), System.currentTimeMillis()) + ms);
    }

    static void cancel(Context c) {
        prefs(c).edit().remove(KEY_END).apply();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        am.cancel(alarm(c, ACTION_WARN));
        am.cancel(alarm(c, ACTION_SLEEP));
    }

    /** After an update, which cancels the app's alarms: a timer still running gets them back, an old one is dropped. */
    static void restore(Context c) {
        long end = endAt(c);
        if (end > System.currentTimeMillis()) set(c, end);
        else if (end != 0) cancel(c);
    }

    /** Ends the timer and puts the box to sleep; false when the accessibility service is off. */
    static boolean sleepNow(Context c) {
        cancel(c);
        return RemoteKeysService.sleep();
    }

    private static void set(Context c, long end) {
        prefs(c).edit().putLong(KEY_END, end).apply();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        schedule(am, Math.max(System.currentTimeMillis(), end - WARN_MS), alarm(c, ACTION_WARN));
        schedule(am, end, alarm(c, ACTION_SLEEP));
    }

    private static void schedule(AlarmManager am, long at, PendingIntent pi) {
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (SecurityException e) {
            // Android 13+ when the exact-alarm permission was refused: within half a minute is fine for a sleep timer.
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 30_000L, pi);
        }
    }

    private static PendingIntent alarm(Context c, String action) {
        Intent i = new Intent(c, SleepAlarmReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(c, ACTION_WARN.equals(action) ? 1 : 2, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** "15 min", "1 h", "1 h 30": a duration rounded up to the minute, as the menu and the pill say it. */
    static String duration(long ms) {
        long min = Math.max(1, (ms + 59_999) / 60_000);
        if (min < 60) return min + " min";
        long h = min / 60, m = min % 60;
        return m == 0 ? h + " h" : String.format(Locale.ROOT, "%d h %02d", h, m);
    }

    /** "0:59": the last minute, as the warning's chip shows it. */
    static String countdown(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
