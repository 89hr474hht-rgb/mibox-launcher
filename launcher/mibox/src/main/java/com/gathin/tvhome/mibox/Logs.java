package com.gathin.tvhome.mibox;

import android.util.Log;

/**
 * The one way this app writes to logcat: where a failure happened and the type of the exception, at warning level.
 * Never the exception's message or any value it was given, since those can hold an address, a file name or an
 * account. Read on the box with {@code adb logcat -s LaunchOS}.
 */
final class Logs {
    static final String TAG = "LaunchOS";

    private Logs() {
    }

    /** Something failed and was handled; where is a short "Class.method" label. */
    static void failed(String where, Throwable t) {
        Log.w(TAG, where + ": " + (t == null ? "failed" : t.getClass().getSimpleName()));
    }

    /** Something expected was not there (a view, a resource, a setting); nothing was thrown. */
    static void missing(String where, String what) {
        Log.w(TAG, where + ": missing " + what);
    }
}
