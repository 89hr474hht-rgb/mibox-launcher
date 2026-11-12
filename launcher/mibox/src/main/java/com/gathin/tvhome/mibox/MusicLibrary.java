package com.gathin.tvhome.mibox;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The music found on the box's storage: the internal storage and any USB drive. Android's media index is read, and
 * every folder that holds audio files becomes a collection named after the folder, its tracks in file name order
 * (so "001 …", "002 …" play in order). A drive plugged in or taken out, or files added, update it on their own.
 * Reading needs the audio permission, asked for from the player.
 */
final class MusicLibrary {
    /** One audio file. */
    static final class Track {
        final Uri uri;
        final String title, artist, fileName, path;
        final long durationMs;
        final int number;

        Track(Uri uri, String title, String artist, String fileName, String path, long durationMs, int number) {
            this.uri = uri;
            this.title = title;
            this.artist = artist;
            this.fileName = fileName;
            this.path = path;
            this.durationMs = durationMs;
            this.number = number;
        }
    }

    /** A folder of tracks. key is stable across scans (volume and folder). */
    static final class Collection {
        final String key, name, volume;
        final List<Track> tracks;

        Collection(String key, String name, String volume, List<Track> tracks) {
            this.key = key;
            this.name = name;
            this.volume = volume;
            this.tracks = tracks;
        }

        int indexOf(Uri uri) {
            for (int i = 0; i < tracks.size(); i++) if (tracks.get(i).uri.equals(uri)) return i;
            return -1;
        }
    }

    /** A storage space, for the Stockage page. */
    static final class Volume {
        final String name, mediaName;
        final boolean removable, mounted;
        final long total, free;
        int tracks;

        Volume(String name, String mediaName, boolean removable, boolean mounted, long total, long free) {
            this.name = name;
            this.mediaName = mediaName;
            this.removable = removable;
            this.mounted = mounted;
            this.total = total;
            this.free = free;
        }
    }

    interface Listener {
        void libraryChanged();
    }

    /** The key of the Favoris collection: never a folder's path. */
    static final String FAVORITES = "\u2605favorites";
    private static final String PREFS = "mibox_music";

    /** Holds the application context only, never an activity. */
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static MusicLibrary instance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Listener> listeners = new ArrayList<>();
    private volatile List<Collection> collections = Collections.emptyList();
    private volatile List<Volume> volumes = Collections.emptyList();
    private boolean scanned, scanning, again;
    private final Runnable rescan = this::refresh;

    private MusicLibrary(Context app) {
        this.app = app;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_MEDIA_MOUNTED);
        f.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        f.addAction(Intent.ACTION_MEDIA_REMOVED);
        f.addAction(Intent.ACTION_MEDIA_EJECT);
        f.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        f.addDataScheme("file");
        BroadcastReceiver mounts = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                later(800);
            }
        };
        // Sent by the system only: a receiver that is not exported still gets them.
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(mounts, f, Context.RECEIVER_NOT_EXPORTED);
        else app.registerReceiver(mounts, f);
    }

    static synchronized MusicLibrary get(Context c) {
        if (instance == null) instance = new MusicLibrary(c.getApplicationContext());
        return instance;
    }

    static boolean canRead(Context c) {
        String p = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    static String permission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    List<Collection> collections() {
        return collections;
    }

    List<Volume> volumes() {
        return volumes;
    }

    boolean scanned() {
        return scanned;
    }

    Collection find(String key) {
        if (FAVORITES.equals(key)) {
            Collection f = favorites();
            return f.tracks.isEmpty() ? null : f;
        }
        for (Collection c : collections) if (c.key.equals(key)) return c;
        return null;
    }

    // ---- favourites: kept by folder and file name, so they come back when the drive is plugged in again ----

    boolean isFavorite(Track t) {
        return t != null && favoriteKeys().contains(favoriteKey(t.path));
    }

    void toggleFavorite(Track t) {
        if (t == null) return;
        java.util.Set<String> keys = new java.util.HashSet<>(favoriteKeys());
        String k = favoriteKey(t.path);
        if (!keys.remove(k)) keys.add(k);
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("favorites", keys).apply();
        for (Listener l : new ArrayList<>(listeners)) l.libraryChanged();
    }

    /** The favourite tracks found on the drives now, in folder then file name order. */
    Collection favorites() {
        java.util.Set<String> keys = favoriteKeys();
        List<Track> tracks = new ArrayList<>();
        if (!keys.isEmpty()) {
            for (Collection c : collections) for (Track t : c.tracks) if (keys.contains(favoriteKey(t.path))) tracks.add(t);
        }
        tracks.sort((a, b) -> naturalCompare(favoriteKey(a.path), favoriteKey(b.path)));
        return new Collection(FAVORITES, app.getString(R.string.mibox_music_favorites), "",
                Collections.unmodifiableList(tracks));
    }

    private java.util.Set<String> favoriteKeys() {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet("favorites", Collections.emptySet());
    }

    /** "Folder/file.mp3": the same track on the same drive, whatever Android numbers it this time. */
    static String favoriteKey(String path) {
        if (path == null) return "";
        File f = new File(path);
        File parent = f.getParentFile();
        return (parent == null ? "" : parent.getName() + "/") + f.getName();
    }

    void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    private ContentObserver observer;

    /** Reads the index again in the background; listeners hear about it on the main thread. */
    void refresh() {
        if (scanning) {
            again = true;
            return;
        }
        scanning = true;
        if (observer == null && canRead(app)) {
            // Files the media scanner indexes later (a drive just plugged in) come in a moment after.
            observer = new ContentObserver(main) {
                @Override
                public void onChange(boolean self) {
                    later(1500);
                }
            };
            try {
                app.getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer);
            } catch (SecurityException e) {
                observer = null;
            }
        }
        worker.execute(() -> {
            List<Volume> vols = readVolumes();
            List<Collection> found = canRead(app) ? scan(vols) : Collections.emptyList();
            main.post(() -> {
                volumes = vols;
                collections = found;
                scanned = true;
                scanning = false;
                for (Listener l : new ArrayList<>(listeners)) l.libraryChanged();
                if (again) {
                    again = false;
                    refresh();
                }
            });
        });
    }

    private void later(long ms) {
        main.removeCallbacks(rescan);
        main.postDelayed(rescan, ms);
    }

    // ---- reading (background) ----

    private List<Volume> readVolumes() {
        List<Volume> out = new ArrayList<>();
        StorageManager sm = app.getSystemService(StorageManager.class);
        if (sm == null) return out;
        for (StorageVolume v : sm.getStorageVolumes()) {
            String media = Build.VERSION.SDK_INT >= 30 ? v.getMediaStoreVolumeName()
                    : v.isPrimary() ? "external_primary" : v.getUuid() == null ? "" : v.getUuid().toLowerCase(Locale.ROOT);
            File dir = Build.VERSION.SDK_INT >= 30 ? v.getDirectory() : null;
            long total = dir == null ? 0 : dir.getTotalSpace(), free = dir == null ? 0 : dir.getUsableSpace();
            boolean mounted = "mounted".equals(v.getState()) || "mounted_ro".equals(v.getState());
            out.add(new Volume(v.getDescription(app), media == null ? "" : media, v.isRemovable(), mounted, total, free));
        }
        return out;
    }

    private List<Collection> scan(List<Volume> vols) {
        Uri uri = Build.VERSION.SDK_INT >= 29 ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] cols = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.TRACK};
        Map<String, List<Object[]>> byFolder = new LinkedHashMap<>();
        try (Cursor c = app.getContentResolver().query(uri, cols, null, null, null)) {
            if (c == null) return Collections.emptyList();
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String data = c.getString(5);
                String file = c.getString(4);
                if (data == null || file == null) continue;
                File parent = new File(data).getParentFile();
                if (parent == null) continue;
                Track t = new Track(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        title(c.getString(1), file), clean(c.getString(2)), file, data, c.getLong(3), c.getInt(6) % 1000);
                byFolder.computeIfAbsent(parent.getPath(), k -> new ArrayList<>()).add(new Object[]{t});
            }
        } catch (RuntimeException e) {
            return Collections.emptyList();   // a drive pulled out during the query, or no permission
        }
        List<Collection> out = new ArrayList<>();
        for (Map.Entry<String, List<Object[]>> e : byFolder.entrySet()) {
            List<Track> tracks = new ArrayList<>();
            for (Object[] o : e.getValue()) tracks.add((Track) o[0]);
            tracks.sort((a, b) -> naturalCompare(a.fileName, b.fileName));
            String volume = volumeOf(e.getKey(), vols);
            String name = new File(e.getKey()).getName();
            out.add(new Collection(e.getKey(), name.isEmpty() ? volume : name, volume, Collections.unmodifiableList(tracks)));
        }
        for (Volume v : vols) {
            for (Collection c : out) if (c.volume.equals(v.name)) v.tracks += c.tracks.size();
        }
        out.sort((a, b) -> naturalCompare(a.name, b.name));
        return Collections.unmodifiableList(out);
    }

    private String volumeOf(String path, List<Volume> vols) {
        StorageManager sm = app.getSystemService(StorageManager.class);
        if (sm != null) {
            StorageVolume v = sm.getStorageVolume(new File(path));
            if (v != null) return v.getDescription(app);
        }
        return vols.isEmpty() ? "" : vols.get(0).name;
    }

    // ---- names (plain Java, tested) ----

    /** The title from the file's tags, or its file name without the extension and the leading number. */
    static String title(String tagged, String fileName) {
        String t = clean(tagged);
        String base = fileName == null ? "" : fileName.replaceFirst("\\.[^.]{1,5}$", "");
        if (t.isEmpty() || t.equals(base)) t = base.replaceFirst("^\\d{1,4}\\s*[-._)]*\\s*", "").trim();
        return t.isEmpty() ? base : t;
    }

    private static String clean(String s) {
        if (s == null) return "";
        s = s.trim();
        return s.equals("<unknown>") ? "" : s;
    }

    /** "2" before "10", case and accents ignored: the order people expect in a folder. */
    static int naturalCompare(String a, String b) {
        String x = fold(a), y = fold(b);
        int i = 0, j = 0;
        while (i < x.length() && j < y.length()) {
            char cx = x.charAt(i), cy = y.charAt(j);
            if (Character.isDigit(cx) && Character.isDigit(cy)) {
                int si = i, sj = j;
                while (i < x.length() && Character.isDigit(x.charAt(i))) i++;
                while (j < y.length() && Character.isDigit(y.charAt(j))) j++;
                String nx = x.substring(si, i).replaceFirst("^0+(?=\\d)", ""), ny = y.substring(sj, j).replaceFirst("^0+(?=\\d)", "");
                if (nx.length() != ny.length()) return nx.length() - ny.length();
                int c = nx.compareTo(ny);
                if (c != 0) return c;
            } else {
                if (cx != cy) return cx - cy;
                i++;
                j++;
            }
        }
        return (x.length() - i) - (y.length() - j);
    }

    /** Lower case without accents, for sorting and search. */
    static String fold(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    /** Whether a track matches what was typed: every word, in its title or file name, accents ignored. */
    static boolean matches(String title, String fileName, String query) {
        String q = fold(query).trim();
        if (q.isEmpty()) return true;
        String hay = fold(title) + " " + fold(fileName);
        for (String w : q.split("\\s+")) {
            if (w.matches("\\d+")) {
                // A number finds the track with that number, "2" as well as "002".
                String n = w.replaceFirst("^0+(?=\\d)", "");
                if (!hay.matches("(?s).*(^|\\D)0*" + n + "(\\D|$).*")) return false;
            } else if (!hay.contains(w)) {
                return false;
            }
        }
        return true;
    }
}
