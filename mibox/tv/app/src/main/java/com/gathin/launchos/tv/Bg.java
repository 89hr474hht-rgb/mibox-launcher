package com.gathin.launchos.tv;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Background work for the API calls, with the result handed back on the main thread. */
final class Bg {
    interface Task<T> {
        T run() throws XtreamException;
    }

    interface Done<T> {
        void done(T result, XtreamException error);
    }

    static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);

    private Bg() {
    }

    static <T> void run(Task<T> task, Done<T> done) {
        POOL.execute(() -> {
            T result = null;
            XtreamException error = null;
            try {
                result = task.run();
            } catch (XtreamException e) {
                error = e;
            } catch (RuntimeException | OutOfMemoryError e) {   // a list too big for the box, or a panel's odd JSON
                error = new XtreamException(XtreamException.Kind.BAD_ANSWER, 0);
            }
            T r = result;
            XtreamException err = error;
            MAIN.post(() -> done.done(r, err));
        });
    }
}
