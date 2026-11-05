package com.gathin.tvhome.mibox;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

/**
 * JobScheduler rather than WorkManager: the platform API costs nothing in APK size. Two jobs, both waiting for a
 * network: a daily check that survives reboots, and a one-off check when the launcher process starts.
 */
final class UpdateScheduler {
    static final int DAILY_JOB = 0x6d62_0001;
    static final int START_JOB = 0x6d62_0002;

    private UpdateScheduler() {
    }

    /** Schedules the daily check, leaving an existing one alone so its period is not restarted. */
    static void schedule(Context context) {
        JobScheduler js = context.getSystemService(JobScheduler.class);
        if (js == null || js.getPendingJob(DAILY_JOB) != null) return;
        js.schedule(new JobInfo.Builder(DAILY_JOB, new ComponentName(context, UpdateJobService.class))
                .setPeriodic(UpdatePolicy.DAY_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build());
    }

    /** The check at start, unless one ran less than an hour ago. */
    static void checkSoon(Context context) {
        if (!UpdatePolicy.checkAtStart(new UpdateState(context).lastCheck(), System.currentTimeMillis())) return;
        JobScheduler js = context.getSystemService(JobScheduler.class);
        if (js == null) return;
        js.schedule(new JobInfo.Builder(START_JOB, new ComponentName(context, UpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(10L * 60 * 1000)
                .build());
    }
}
