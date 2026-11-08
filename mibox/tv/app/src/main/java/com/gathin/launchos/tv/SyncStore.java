package com.gathin.launchos.tv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The one place that knows whether the provider's lists are being read again, and how far along each one is.
 * Screens never run a refresh themselves: they ask the store, then listen. The work runs on {@link Bg}'s threads
 * and keeps going when the screen that started it is left; the screen that comes back gets the current state at
 * once. Progress comes from the bytes of the list as they arrive, and reaches the main thread at most every
 * {@link #FRAME_MS}, so a fast connection never floods the screen with redraws.
 *
 * <p>Each state is an immutable {@link Job}: a screen draws what it is given, nothing more (one-way data flow).
 * Main thread only, apart from the progress callbacks, which only write two volatile numbers.
 */
final class SyncStore {
    enum Phase { IDLE, RUNNING, DONE, FAILED }

    /** One section's refresh, as the screens see it. */
    static final class Job {
        final Xtream.Section section;
        final Phase phase;
        /** 0 to 1, or -1 when it cannot be known (the server gave no size and the list was never read here). */
        final float progress;
        final long bytes;
        /** The error in French, when FAILED. */
        final String error;
        final long at;

        Job(Xtream.Section section, Phase phase, float progress, long bytes, String error) {
            this.section = section;
            this.phase = phase;
            this.progress = progress;
            this.bytes = bytes;
            this.error = error;
            this.at = System.currentTimeMillis();
        }
    }

    interface Listener {
        void changed(Job job);
    }

    /** Where the progress starts once the request is out, and where the list's bytes stop (the rest is sorting). */
    static final float START = 0.04f, READ_END = 0.95f;
    private static final long FRAME_MS = 90;
    private static final String PREFS = "sync";

    /** Holds the application context only, never an activity. */
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static SyncStore instance;

    private final Context app;
    private final Job[] jobs = new Job[Xtream.Section.values().length];
    private final List<Listener> listeners = new ArrayList<>();

    private SyncStore(Context app) {
        this.app = app;
        for (Xtream.Section s : Xtream.Section.values()) jobs[s.ordinal()] = new Job(s, Phase.IDLE, 0, 0, null);
    }

    static SyncStore get(Context c) {
        if (instance == null) instance = new SyncStore(c.getApplicationContext());
        return instance;
    }

    Job job(Xtream.Section s) {
        return jobs[s.ordinal()];
    }

    boolean running() {
        for (Job j : jobs) if (j.phase == Phase.RUNNING) return true;
        return false;
    }

    /** Starts listening, and hears every section's state at once. */
    void observe(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
        for (Job j : jobs) l.changed(j);
    }

    void forget(Listener l) {
        listeners.remove(l);
    }

    /** Reads every list again and empties the programme guide. Sections already running are left as they are. */
    void refreshAll() {
        Epg.clear();
        for (Xtream.Section s : Xtream.Section.values()) refresh(s);
    }

    /** Reads one section's lists again; nothing when it is already being read. */
    void refresh(Xtream.Section s) {
        if (job(s).phase == Phase.RUNNING) return;
        Account a = AccountStore.load(app);
        if (a == null) {
            set(new Job(s, Phase.FAILED, 0, 0, app.getString(R.string.refresh_no_account)));
            return;
        }
        Library.forAccount(AccountStore.version());
        Library lib = Library.of(s);
        lib.forget();
        Meter meter = new Meter(estimate(s));
        set(new Job(s, Phase.RUNNING, START, 0, null));
        Runnable frame = new Runnable() {
            @Override
            public void run() {
                if (job(s).phase != Phase.RUNNING) return;
                set(new Job(s, Phase.RUNNING, meter.fraction(), meter.read, null));
                Bg.MAIN.postDelayed(this, FRAME_MS);
            }
        };
        Bg.MAIN.postDelayed(frame, FRAME_MS);
        lib.load(new Xtream(a), () -> {
            Bg.MAIN.removeCallbacks(frame);
            if (lib.ready()) {
                if (meter.read > 0) prefs().edit().putLong(key(s), meter.read).apply();
                set(new Job(s, Phase.DONE, 1f, meter.read, null));
            } else {
                XtreamException e = lib.error();
                set(new Job(s, Phase.FAILED, 0, meter.read, e == null ? app.getString(R.string.sync_failed) : e.french()));
            }
        }, meter);
    }

    private void set(Job j) {
        jobs[j.section.ordinal()] = j;
        for (Listener l : new ArrayList<>(listeners)) l.changed(j);
    }

    private SharedPreferences prefs() {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(Xtream.Section s) {
        return "bytes_" + s.name().toLowerCase(Locale.ROOT);
    }

    /** The size of this section's list the last time it was read here, or 0. */
    private long estimate(Xtream.Section s) {
        return prefs().getLong(key(s), 0);
    }

    /** Written by the download thread, read by the main thread's frames. */
    private static final class Meter implements Http.Progress {
        private final long estimate;
        volatile long read, total = -1;

        Meter(long estimate) {
            this.estimate = estimate;
        }

        @Override
        public void bytes(long read, long total) {
            this.total = total;
            this.read = read;
        }

        float fraction() {
            return SyncStore.fraction(read, total, estimate);
        }
    }

    /**
     * How far along a list is: from the server's size when it gave one, else from the size it had last time (never
     * past {@link #READ_END}, as it may have grown), else unknown (-1). Plain arithmetic, tested on the JVM.
     */
    static float fraction(long read, long total, long estimate) {
        long of = total > 0 ? total : estimate;
        if (of <= 0) return read > 0 ? -1f : START;
        float f = START + (READ_END - START) * Math.min(1f, (float) read / of);
        return Math.min(READ_END, f);
    }
}
