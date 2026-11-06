package com.gathin.tvhome.mibox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.widget.Toast;

/**
 * The sleep timer's two alarms. A minute before the end it opens the warning over the app in front; at the end it puts
 * the box to sleep if the warning has not already. When the TV was switched off in the meantime, the timer just ends.
 */
public final class SleepAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SleepTimer.cancel(context);   // a restart ends the timer; its alarms are gone anyway
            return;
        }
        long end = SleepTimer.endAt(context);
        if (end == 0) return;
        PowerManager pm = context.getSystemService(PowerManager.class);
        if (pm != null && !pm.isInteractive()) {
            SleepTimer.cancel(context);   // already asleep: nothing to do, and nothing to show on waking
            return;
        }
        String action = intent.getAction();
        if (SleepTimer.ACTION_WARN.equals(action)) {
            try {
                context.startActivity(new Intent(context, SleepWarningActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            } catch (RuntimeException ignored) {
                // Android refused to open it over the app in front: the end alarm still puts the box to sleep.
            }
        } else if (SleepTimer.ACTION_SLEEP.equals(action)) {
            if (end - System.currentTimeMillis() > 5_000) return;   // extended since: its own alarm will come
            if (!SleepTimer.sleepNow(context)) {
                Toast.makeText(context, R.string.mibox_sleep_failed, Toast.LENGTH_LONG).show();
            }
        }
    }
}
