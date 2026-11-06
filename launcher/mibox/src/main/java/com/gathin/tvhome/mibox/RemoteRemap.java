package com.gathin.tvhome.mibox;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.KeyEvent;

/**
 * What the Xiaomi remote's three app buttons open: Netflix (KEYCODE_BUTTON_6, 193), YouTube (BUTTON_3, 190) and Prime
 * Video (BUTTON_7, 194), read from the build 773 key layout. Each opens the app chosen in LaunchOS's settings, or
 * nothing. Until it is changed, YouTube opens YouTube and the two others do nothing, as the owner chose.
 */
final class RemoteRemap {
    static final int NETFLIX = KeyEvent.KEYCODE_BUTTON_6, YOUTUBE = KeyEvent.KEYCODE_BUTTON_3, PRIME = KeyEvent.KEYCODE_BUTTON_7;
    /** In the order the settings page lists them. */
    static final int[] KEYS = {NETFLIX, YOUTUBE, PRIME};
    static final String NONE = "none";
    static final String YOUTUBE_APP = "com.google.android.youtube.tv";
    private static final String PREFS = "mibox_remote_keys";

    private RemoteRemap() {
    }

    static boolean isAppButton(int keyCode) {
        return keyCode == NETFLIX || keyCode == YOUTUBE || keyCode == PRIME;
    }

    /** The button's default: YouTube for its own button, nothing for the others. */
    static String defaultFor(int keyCode) {
        return keyCode == YOUTUBE ? YOUTUBE_APP : NONE;
    }

    /** A package name, or NONE. */
    static String target(Context context, int keyCode) {
        String v = prefs(context).getString(key(keyCode), null);
        return v == null || v.isEmpty() ? defaultFor(keyCode) : v;
    }

    static void set(Context context, int keyCode, String target) {
        prefs(context).edit().putString(key(keyCode), target == null ? NONE : target).apply();
    }

    static String key(int keyCode) {
        return "key_" + keyCode;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static ComponentName service(Context context) {
        return new ComponentName(context.getPackageName(), RemoteKeysService.class.getName());
    }

    /** Whether Android runs the buttons' service (switched on once, in Accessibility settings or over adb). */
    static boolean serviceOn(Context context) {
        String list = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return isListed(list, service(context));
    }

    /** True when the colon-separated enabled_accessibility_services list names this component, in either form. */
    static boolean isListed(String list, ComponentName c) {
        if (list == null) return false;
        for (String s : list.split(":")) {
            ComponentName n = ComponentName.unflattenFromString(s.trim());
            if (c.equals(n)) return true;
        }
        return false;
    }
}
