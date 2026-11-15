package com.gathin.tvhome.mibox;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.KeyEvent;

/**
 * What the remote's app buttons open, for the remote each box this edition knows ships with. Both are Xiaomi's
 * Bluetooth remote, the same device to Android (vendor 0x2717, product 0x32b9); the box's key layout is what gives
 * their buttons different codes, so the box tells which remote it is ({@link Model}):
 * <ul>
 * <li>Mi Box S 2nd gen ({@code jaws}, Google TV, build 773 layout): Netflix (KEYCODE_BUTTON_6, 193), YouTube (BUTTON_3,
 * 190) and Prime Video (BUTTON_7, 194). Until changed, YouTube opens YouTube and the two others nothing, as the owner
 * chose.</li>
 * <li>Mi Box S ({@code oneday}, MIBOX4, Android TV 9): Netflix (the vendor's NETFLIX, 1001) and Live (BUTTON_2, 3 or 4 in its
 * layout, the remote has one of them). Until changed, Netflix opens Netflix and Live opens Mi TV+, what Xiaomi's own
 * home did with them.</li>
 * </ul>
 * On any other box, or for a key from another device, nothing is taken: every button stays Android's own. Each box
 * keeps its own profile here; a new box gets a new {@link Model} rather than a change to another's.
 */
final class RemoteRemap {
    /** The remote of the box, as told by the box. */
    enum Model { MIBOX_S2, MIBOX_S }

    static final int REMOTE_VENDOR = 0x2717, REMOTE_PRODUCT = 0x32b9;

    // Mi Box S 2nd gen.
    static final int NETFLIX = KeyEvent.KEYCODE_BUTTON_6, YOUTUBE = KeyEvent.KEYCODE_BUTTON_3, PRIME = KeyEvent.KEYCODE_BUTTON_7;
    /** The 2nd gen's buttons, in the order the settings page lists them. */
    static final int[] KEYS = {NETFLIX, YOUTUBE, PRIME};

    // Mi Box S (1st gen): its Netflix key and the code its Live button is kept under. Netflix is a vendor key code,
    // 1001 in the box's own label table (libinput: NETFLIX), not in Android's SDK.
    static final int S_NETFLIX = 1001, S_LIVE = KeyEvent.KEYCODE_BUTTON_2;
    static final int[] S_KEYS = {S_NETFLIX, S_LIVE};

    static final String NONE = "none";
    static final String YOUTUBE_APP = "com.google.android.youtube.tv";
    static final String NETFLIX_APP = "com.netflix.ninja";
    static final String MI_TV_PLUS_APP = "com.mitv.tvhome.mitvplus";

    /** The box's remote, from the box's codename; null for a box this edition does not know. */
    static Model model() {
        return modelFor(android.os.Build.DEVICE);
    }

    static Model modelFor(String device) {
        if ("jaws".equals(device)) return Model.MIBOX_S2;
        if ("oneday".equals(device)) return Model.MIBOX_S;
        return null;
    }

    /** The buttons the settings page lists for this remote; none for an unknown box. */
    static int[] keys(Model m) {
        return m == Model.MIBOX_S2 ? KEYS : m == Model.MIBOX_S ? S_KEYS : new int[0];
    }

    /**
     * The button a key press is, as kept in the settings (one of {@link #keys}), or -1 when LaunchOS leaves it alone:
     * an unknown box, a key from another device than the box's remote, or a key that is not one of its app buttons.
     */
    static int button(Model m, int vendor, int product, int keyCode) {
        if (m == null || vendor != REMOTE_VENDOR || product != REMOTE_PRODUCT) return -1;
        if (m == Model.MIBOX_S2) return isAppButton(keyCode) ? keyCode : -1;
        if (keyCode == S_NETFLIX) return S_NETFLIX;
        if (keyCode == KeyEvent.KEYCODE_BUTTON_2 || keyCode == KeyEvent.KEYCODE_BUTTON_3
                || keyCode == KeyEvent.KEYCODE_BUTTON_4) return S_LIVE;
        return -1;
    }

    static int button(KeyEvent e) {
        android.view.InputDevice d = e.getDevice();
        return d == null ? -1 : button(model(), d.getVendorId(), d.getProductId(), e.getKeyCode());
    }
    private static final String PREFS = "mibox_remote_keys";

    private RemoteRemap() {
    }

    /** One of the 2nd gen remote's app buttons. */
    static boolean isAppButton(int keyCode) {
        return keyCode == NETFLIX || keyCode == YOUTUBE || keyCode == PRIME;
    }

    /**
     * The button's default. 2nd gen: YouTube for its own button, nothing for the others. Mi Box S: Netflix for its
     * Netflix button, Mi TV+ for Live.
     */
    static String defaultFor(int keyCode) {
        if (keyCode == S_NETFLIX) return NETFLIX_APP;
        if (keyCode == S_LIVE) return MI_TV_PLUS_APP;
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
