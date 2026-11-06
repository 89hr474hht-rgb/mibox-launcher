package com.gathin.launchos.tv;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Checks, downloads, verifies and hands an update to PackageInstaller, one step at a time on one thread. */
final class Updater {
    enum Phase { IDLE, CHECKING, DOWNLOADING, VERIFYING, WAITING_USER }

    interface Listener {
        void onUpdaterChanged();
    }

    private static final Updater INSTANCE = new Updater();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "launchos-tv-updater");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile Phase phase = Phase.IDLE;
    private volatile int percent;
    /** The last download or install error, shown on the updates page until the next attempt. */
    private volatile String error;

    static Updater get() {
        return INSTANCE;
    }

    Phase phase() {
        return phase;
    }

    int percent() {
        return percent;
    }

    String error() {
        return error;
    }

    void addListener(Listener l) {
        listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void set(Phase p, int pct) {
        phase = p;
        percent = pct;
        main.post(() -> {
            for (Listener l : listeners) l.onUpdaterChanged();
        });
    }

    static String userAgent(Context context) {
        return "launchos-tv/" + UpdateState.installed(context) + " (Android " + Build.VERSION.RELEASE + ")";
    }

    /** Looks for a newer release; done runs on the main thread, also when a check was already running. */
    void check(Context context, Runnable done) {
        Context app = context.getApplicationContext();
        if (phase != Phase.IDLE) {
            if (done != null) main.post(done);
            return;
        }
        set(Phase.CHECKING, 0);
        worker.execute(() -> {
            UpdateState state = new UpdateState(app);
            try {
                List<Release> releases = ReleaseClient.fetch(userAgent(app));
                SemVer installed = UpdateState.installed(app);
                Release newest = releases.isEmpty() ? null : releases.get(0);
                state.checked(newest != null && newest.version.isNewerThan(installed) ? newest : null, null);
            } catch (UpdateException e) {
                state.checked(null, e.text(app));
            } catch (RuntimeException e) {
                state.checked(null, app.getString(R.string.upd_err_format));
            }
            set(Phase.IDLE, 0);
            if (done != null) main.post(done);
        });
    }

    /** Downloads and verifies the release, then opens Android's install confirmation. */
    void install(Context context, Release release) {
        Context app = context.getApplicationContext();
        if (phase != Phase.IDLE && phase != Phase.WAITING_USER) return;
        error = null;
        set(Phase.DOWNLOADING, 0);
        worker.execute(() -> {
            File dir = new File(app.getFilesDir(), "updates");
            File apk = new File(dir, Release.ASSET_PREFIX + release.version + ".apk");
            try {
                if (!app.getPackageManager().canRequestPackageInstalls()) {
                    throw new UpdateException(R.string.upd_err_permission);
                }
                clean(dir);
                if (!dir.isDirectory() && !dir.mkdirs()) throw new UpdateException(R.string.upd_err_format);
                if (release.apkSize > ReleaseClient.MAX_APK) throw new UpdateException(R.string.upd_err_size);
                // The file, its copy in the install session and Android's own unpacked copy.
                long need = release.apkSize * 3 + (20L << 20);
                // A rough floor is enough here: the APK is about 1 MB and Android checks the space again itself.
                if (dir.getUsableSpace() < need) {
                    throw new UpdateException(R.string.upd_err_space, (int) (need >> 20));
                }
                String ua = userAgent(app);
                String published = ReleaseClient.fetchSha(release, ua);
                String fromApi = Checksums.fromGitHubDigest(release.apkDigest);
                if (fromApi != null && !fromApi.equals(published)) throw new UpdateException(R.string.upd_err_checksum);
                String actual;
                try (OutputStream out = new FileOutputStream(apk)) {
                    actual = ReleaseClient.download(release, out, ua, (done, total) -> {
                        int p = (int) (done * 100 / Math.max(1, total));
                        if (p != percent) set(Phase.DOWNLOADING, p);
                    });
                }
                if (!actual.equals(published)) throw new UpdateException(R.string.upd_err_checksum);
                set(Phase.VERIFYING, 100);
                ApkVerifier.verify(app, apk, release);
                new UpdateState(app).pending(release.version);
                commit(app, apk, release);
                set(Phase.WAITING_USER, 100);
            } catch (UpdateException e) {
                fail(app, release, e.text(app));
            } catch (IOException | RuntimeException e) {
                fail(app, release, app.getString(R.string.upd_err_install, e.getClass().getSimpleName()));
            } finally {
                clean(dir);
            }
        });
    }

    private void fail(Context app, Release release, String message) {
        error = message;
        UpdateState state = new UpdateState(app);
        state.pending(null);
        state.add(release.version.toString(), UpdateState.R_FAILED, message);
        set(Phase.IDLE, 0);
    }

    private static void commit(Context app, File apk, Release release) throws IOException {
        PackageInstaller installer = app.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(app.getPackageName());
        params.setSize(apk.length());
        int id = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(id);
        try {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
            }
            Intent result = new Intent(app, InstallResultReceiver.class)
                    .putExtra(InstallResultReceiver.EXTRA_VERSION, release.version.toString());
            // The system adds the status extras to this intent, so it has to stay mutable on Android 12+.
            int flags = PendingIntent.FLAG_UPDATE_CURRENT
                    | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
            session.commit(PendingIntent.getBroadcast(app, id, result, flags).getIntentSender());
        } catch (IOException | RuntimeException e) {
            session.abandon();
            throw e;
        } finally {
            session.close();
        }
    }

    /** Called by InstallResultReceiver when Android reports the end of a session. */
    void finished(Context context, String version, boolean cancelled, String message) {
        Context app = context.getApplicationContext();
        UpdateState state = new UpdateState(app);
        state.pending(null);
        if (cancelled) {
            state.add(version, UpdateState.R_CANCELLED, null);
            error = null;
        } else if (message != null) {
            error = app.getString(R.string.upd_err_install, message);
            state.add(version, UpdateState.R_FAILED, error);
        }
        set(Phase.IDLE, 0);
    }

    private static void clean(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }
}
