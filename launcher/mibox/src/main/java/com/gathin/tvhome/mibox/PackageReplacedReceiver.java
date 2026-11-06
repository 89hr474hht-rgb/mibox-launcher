package com.gathin.tvhome.mibox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** Runs in the new version after an update: records it in the history, says so on screen, and re-arms a running sleep timer. */
public final class PackageReplacedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        SemVer now = UpdateState.installed(context);
        boolean ours = new UpdateState(context).replaced(now);
        UpdateScheduler.schedule(context);
        SleepTimer.restore(context);   // an update cancels the app's alarms
        if (ours) {
            Toast.makeText(context, context.getString(R.string.mibox_updated_toast, String.valueOf(now)),
                    Toast.LENGTH_LONG).show();
        }
    }
}
