package com.gathin.tvhome.mibox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** PackageInstaller's answers for an update session: open the confirmation, or record how it ended. */
public final class InstallResultReceiver extends BroadcastReceiver {
    static final String EXTRA_VERSION = "com.gathin.tvhome.mibox.VERSION";

    @Override
    @SuppressWarnings("deprecation") // getParcelableExtra(String, Class) is API 33+
    public void onReceive(Context context, Intent intent) {
        String version = intent.getStringExtra(EXTRA_VERSION);
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION: {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm == null) {
                    Updater.get().finished(context, version, false, "no confirmation");
                    return;
                }
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirm);
                } catch (RuntimeException e) {
                    Updater.get().finished(context, version, false, e.getClass().getSimpleName());
                }
                return;
            }
            case PackageInstaller.STATUS_SUCCESS:
                // Usually never seen: Android stops this process to replace the app, and the new version
                // records the install in PackageReplacedReceiver.
                return;
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                Updater.get().finished(context, version, true, null);
                return;
            default: {
                String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Updater.get().finished(context, version, false, message != null ? message : "status " + status);
            }
        }
    }
}
