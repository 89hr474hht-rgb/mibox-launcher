package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

/**
 * The Mi Box S remote's Netflix key, when Netflix is not there. That box's system catches the key itself, before
 * any app or accessibility service sees it, and asks Android to open nflx://www.netflix.com/. With Netflix disabled
 * nobody answers, so this invisible page does: it opens what the Netflix button is set to in LaunchOS (RemoteRemap),
 * and closes. It is switched on only on that box and only while Netflix is disabled or absent ({@link #sync}), so a
 * box with Netflix never sees a second handler for its links.
 */
public final class NetflixKeyActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String target = RemoteRemap.target(this, RemoteRemap.S_NETFLIX);
        if (RemoteRemap.model() == RemoteRemap.Model.MIBOX_S && !RemoteRemap.NONE.equals(target)
                && !RemoteRemap.NETFLIX_APP.equals(target)) {
            open(this, target);
        }
        finish();
    }

    static void open(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(i);
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // Nothing to open.
        }
    }

    /** On while the box is the Mi Box S and Netflix is disabled or absent; off everywhere else. */
    static void sync(Context c) {
        boolean on = RemoteRemap.model() == RemoteRemap.Model.MIBOX_S && !enabled(c, RemoteRemap.NETFLIX_APP);
        PackageManager pm = c.getPackageManager();
        ComponentName me = new ComponentName(c, NetflixKeyActivity.class);
        int want = on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        try {
            if (pm.getComponentEnabledSetting(me) != want) pm.setComponentEnabledSetting(me, want, PackageManager.DONT_KILL_APP);
        } catch (SecurityException | IllegalArgumentException e) {
            Logs.failed("NetflixKeyActivity.sync", e);
        }
    }

    private static boolean enabled(Context c, String pkg) {
        try {
            ApplicationInfo ai = c.getPackageManager().getApplicationInfo(pkg, 0);
            return ai.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
