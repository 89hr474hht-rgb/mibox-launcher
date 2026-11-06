package com.gathin.launchos.tv;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Runs the background check; the app shows what it found the next time it is opened. */
public final class UpdateJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        Updater.get().check(this, () -> jobFinished(params, false));
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // The request has its own timeouts; the daily job runs again tomorrow anyway.
        return false;
    }
}
