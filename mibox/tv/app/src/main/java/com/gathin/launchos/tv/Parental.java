package com.gathin.launchos.tv;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * The parental code: four digits that guard what is for adults. Kept on the box only, as a salted SHA-256, never in
 * clear. While a code is set, content for adults stays hidden until the code is given; it then shows until the app
 * leaves the screen ({@link #lock}, from MainActivity), so the next person to pick up the remote finds it hidden
 * again. Without a code, Réglages' switch works as before. Main thread only.
 */
final class Parental {
    private static final String PREFS = "parental";
    private static final String SALT = "salt", HASH = "hash";
    private static boolean unlocked;

    private Parental() {
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean hasCode(Context c) {
        return p(c).contains(HASH);
    }

    /** A code is set and was not given since the app last left the screen. */
    static boolean locked(Context c) {
        return !unlocked && hasCode(c);
    }

    static boolean check(Context c, String code) {
        SharedPreferences prefs = p(c);
        String salt = prefs.getString(SALT, null), hash = prefs.getString(HASH, null);
        return salt != null && hash != null && MessageDigest.isEqual(
                hash.getBytes(StandardCharsets.US_ASCII), hash(salt, code).getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * A new code (locked at once), or none when code is null. Either way content for adults is hidden again: taking
     * the code away must not leave on show what the code was guarding.
     */
    static void setCode(Context c, String code) {
        Options.setHideAdult(c, true);
        SharedPreferences.Editor e = p(c).edit();
        if (code == null) {
            e.remove(SALT).remove(HASH);
        } else {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            String s = Base64.encodeToString(salt, Base64.NO_WRAP);
            e.putString(SALT, s).putString(HASH, hash(s, code));
        }
        e.apply();
        unlocked = false;
        Groups.touch();
    }

    /** The app left the screen: what is for adults hides again. */
    static void lock() {
        if (!unlocked) return;
        unlocked = false;
        Groups.touch();
    }

    /** Runs then at once without a code, or once the right code is given. */
    static void unlockThen(Activity a, Runnable then) {
        if (!locked(a)) {
            then.run();
            return;
        }
        PinDialog.ask(a, a.getString(R.string.pin_enter), a.getString(R.string.pin_enter_sub), code -> {
            if (!check(a, code)) return false;
            unlocked = true;
            Groups.touch();
            then.run();
            return true;
        });
    }

    static String hash(String salt, String code) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] out = d.digest((salt + ":" + code).getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder(out.length * 2);
            for (byte x : out) b.append(String.format(java.util.Locale.ROOT, "%02x", x));
            return b.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
