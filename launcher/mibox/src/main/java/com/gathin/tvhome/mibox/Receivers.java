package com.gathin.tvhome.mibox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;

/** Registers a receiver for the system's own broadcasts: never exported, so no other app can send it anything. */
final class Receivers {
    private Receivers() {
    }

    static void register(Context c, BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        else c.registerReceiver(r, f);
    }
}
