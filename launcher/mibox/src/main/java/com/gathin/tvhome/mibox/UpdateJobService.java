package com.gathin.tvhome.mibox;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Runs the background check; the prompt itself only appears on the home screen. */
public final class UpdateJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        Updater.get().check(this, () -> {
            MiboxInit.promptIfHomeVisible();
            jobFinished(params, false);
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // The request has its own timeouts; the daily job runs again tomorrow anyway.
        return false;
    }
}
