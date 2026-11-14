package com.gathin.launchos.tv;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background work with the result handed back on the main thread. Two separate executors, so a long catalogue
 * download never makes the screens wait:
 * <ul>
 *   <li>{@link #sync}: one thread for reading a section's whole catalogue (Library.load, hence SyncStore). Those lists
 *       can take tens of seconds and much memory, so they run one at a time.</li>
 *   <li>{@link #run}: a small pool for interactive work (a film's sheet, a series' episodes, a category, blur, search,
 *       the login test, the poster wall, the cache's size).</li>
 * </ul>
 * Both catch a runtime failure or an out-of-memory error and report it as an {@link XtreamException}.
 */
final class Bg {
    interface Task<T> {
        T run() throws XtreamException;
    }

    interface Done<T> {
        void done(T result, XtreamException error);
    }

    static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService CATALOGUE = Executors.newSingleThreadExecutor(named("catalogue"));
    private static final ExecutorService WORK = Executors.newFixedThreadPool(3, named("work"));

    private Bg() {
    }

    /** Interactive work: task on the pool, done on the main thread. */
    static <T> void run(Task<T> task, Done<T> done) {
        submit(WORK, task, done);
    }

    /** A whole catalogue download: on its own thread, one at a time, done on the main thread. */
    static <T> void sync(Task<T> task, Done<T> done) {
        submit(CATALOGUE, task, done);
    }

    /** Plain interactive work that reports on the main thread itself (the guide's programmes). */
    static void execute(Runnable r) {
        WORK.execute(r);
    }

    private static <T> void submit(ExecutorService pool, Task<T> task, Done<T> done) {
        pool.execute(() -> {
            T result = null;
            XtreamException error = null;
            try {
                result = task.run();
            } catch (XtreamException e) {
                error = e;
            } catch (OutOfMemoryError e) {   // a list too big for the box
                Logs.failed("Bg.run", e);
                error = new XtreamException(XtreamException.Kind.TOO_BIG, 0);
            } catch (RuntimeException e) {   // a panel's odd JSON
                Logs.failed("Bg.run", e);
                error = new XtreamException(XtreamException.Kind.BAD_ANSWER, 0);
            }
            T r = result;
            XtreamException err = error;
            MAIN.post(() -> done.done(r, err));
        });
    }

    private static ThreadFactory named(String name) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "launchos-tv-" + name + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
