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
    /**
     * One audio file. Two tracks are the same file when their URI is. The folded keys used to sort, search and keep
     * favourites are worked out once, when the file is read from the index.
     */
    static final class Track {
        final Uri uri;
        final String title, artist, fileName, path;
        final long durationMs;
        final String sortKey, searchKey, favoriteKey;

        Track(Uri uri, String title, String artist, String fileName, String path, long durationMs) {
            this.uri = uri;
            this.title = title;
            this.artist = artist;
            this.fileName = fileName;
            this.path = path;
            this.durationMs = durationMs;
            sortKey = fold(fileName);
            searchKey = fold(title) + " " + sortKey;
            favoriteKey = MusicLibrary.favoriteKey(path);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Track && ((Track) o).uri.equals(uri);
        }

        @Override
        public int hashCode() {
            return uri.hashCode();
        }
    }

    /**
     * A folder of tracks. key is stable across scans (volume and folder). album is the album name most of its tracks'
     * tags give, in whatever script, or "" (the hub shows it large).
     */
    static final class Collection {
        final String key, name, volume, album;
        final List<Track> tracks;

        Collection(String key, String name, String volume, List<Track> tracks) {
            this(key, name, volume, "", tracks);
        }

        Collection(String key, String name, String volume, String album, List<Track> tracks) {
            this.key = key;
            this.name = name;
            this.volume = volume;
            this.album = album;
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
        Receivers.register(app, mounts, f);
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

    void toggleFavorite(Track t) {
        if (t == null) return;
        java.util.Set<String> keys = new java.util.HashSet<>(favoriteKeys());
        if (!keys.remove(t.favoriteKey)) keys.add(t.favoriteKey);
        MusicPrefs.get(app).edit().putStringSet(MusicPrefs.FAVORITES, keys).apply();
        favoritesCache = null;
        for (Listener l : new ArrayList<>(listeners)) l.libraryChanged();
    }

    /** The favourite tracks found on the drives now, in folder then file name order; kept until either changes. */
    Collection favorites() {
        if (favoritesCache != null) return favoritesCache;
        java.util.Set<String> keys = favoriteKeys();
        List<Track> tracks = new ArrayList<>();
        if (!keys.isEmpty()) {
            for (Collection c : collections) for (Track t : c.tracks) if (keys.contains(t.favoriteKey)) tracks.add(t);
        }
        tracks.sort((a, b) -> naturalCompareFolded(fold(a.favoriteKey), fold(b.favoriteKey)));
        favoritesCache = new Collection(FAVORITES, app.getString(R.string.mibox_music_favorites), "",
                Collections.unmodifiableList(tracks));
        return favoritesCache;
    }

    private Collection favoritesCache;

    private java.util.Set<String> favoriteKeys() {
        return MusicPrefs.get(app).getStringSet(MusicPrefs.FAVORITES, Collections.emptySet());
    }

    boolean isFavorite(Track t) {
        return t != null && favoriteKeys().contains(t.favoriteKey);
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
                favoritesCache = null;
                for (Listener l : new ArrayList<>(listeners)) l.libraryChanged();
                MusicPlayer player = MusicPlayer.peek();
                if (player != null) player.libraryRead(this);
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
                MediaStore.Audio.Media.ALBUM};
        Map<String, List<Track>> byFolder = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> albums = new java.util.HashMap<>();
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
                        title(c.getString(1), file), clean(c.getString(2)), file, data, c.getLong(3));
                byFolder.computeIfAbsent(parent.getPath(), k -> new ArrayList<>()).add(t);
                String album = clean(c.getString(6));
                if (!album.isEmpty()) albums.computeIfAbsent(parent.getPath(), k -> new java.util.HashMap<>()).merge(album, 1, Integer::sum);
            }
        } catch (RuntimeException e) {
            Logs.failed("MusicLibrary.scan", e);
            return Collections.emptyList();   // a drive pulled out during the query, or no permission
        }
        List<Collection> out = new ArrayList<>();
        for (Map.Entry<String, List<Track>> e : byFolder.entrySet()) {
            List<Track> tracks = e.getValue();
            tracks.sort((a, b) -> naturalCompareFolded(a.sortKey, b.sortKey));
            String volume = volumeOf(e.getKey(), vols);
            String name = new File(e.getKey()).getName();
            out.add(new Collection(e.getKey(), name.isEmpty() ? volume : name, volume, mostOf(albums.get(e.getKey())),
                    Collections.unmodifiableList(tracks)));
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

    /** The name given most often, or "" (none, or a folder name the media index made up). */
    static String mostOf(Map<String, Integer> counts) {
        if (counts == null) return "";
        String best = "";
        int n = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > n || e.getValue() == n && e.getKey().compareTo(best) < 0) {
                best = e.getKey();
                n = e.getValue();
            }
        }
        return best;
    }

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
        return naturalCompareFolded(fold(a), fold(b));
    }

    /** naturalCompare on strings already folded. */
    static int naturalCompareFolded(String x, String y) {
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
        return Query.of(query).matches(fold(title) + " " + fold(fileName));
    }

    /** What was typed in the search field, folded and split once, then tried against each track's searchKey. */
    static final class Query {
        private final String[] words;

        private Query(String[] words) {
            this.words = words;
        }

        static Query of(String typed) {
            String q = fold(typed).trim();
            return new Query(q.isEmpty() ? new String[0] : q.split("\\s+"));
        }

        boolean matches(String hay) {
            for (String w : words) {
                if (isDigits(w)) {
                    // A number finds the track with that number, "2" as well as "002".
                    if (!hasNumber(hay, w.replaceFirst("^0+(?=\\d)", ""))) return false;
                } else if (!hay.contains(w)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isDigits(String w) {
            for (int i = 0; i < w.length(); i++) if (!Character.isDigit(w.charAt(i))) return false;
            return !w.isEmpty();
        }

        /** Whether hay holds n as a whole number, leading zeros allowed: "002" holds 2, "12" does not. */
        private static boolean hasNumber(String hay, String n) {
            int from = 0;
            while (true) {
                int at = hay.indexOf(n, from);
                if (at < 0) return false;
                int start = at;
                while (start > 0 && hay.charAt(start - 1) == '0') start--;
                boolean before = start == 0 || !Character.isDigit(hay.charAt(start - 1));
                int end = at + n.length();
                boolean after = end == hay.length() || !Character.isDigit(hay.charAt(end));
                if (before && after) return true;
                from = at + 1;
            }
        }
    }
}
