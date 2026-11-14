package com.gathin.tvhome.mibox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LaunchOS's music player: plays a collection's tracks one after the other with Android's own MediaPlayer, and finds
 * each track's synced lyrics (an .lrc file beside it, or the lyrics inside the MP3). One per process, so the home
 * screen's card, the player screen and the sleep screen all show the same thing. A media session lets the remote's
 * play/pause keys work anywhere; another app taking the sound (a film) pauses it, and so does the box going to sleep.
 * While it plays, {@link MusicService} keeps the process in the foreground. The last track and position are kept, so
 * the card can offer to resume after a restart.
 */
final class MusicPlayer {
    interface Listener {
        /** The track, play state or lyrics changed (main thread). */
        void playerChanged();
    }

    /** Holds the application context only, never an activity. */
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static MusicPlayer instance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService lyricsWorker = Executors.newSingleThreadExecutor();
    private final List<Listener> listeners = new ArrayList<>();
    private final AudioManager audio;
    private final MediaSession session;
    private AudioFocusRequest focus;
    private MediaPlayer player;
    private MusicLibrary.Collection collection;
    private int index = -1;
    private boolean prepared, playing, wantPlay;
    private long startAt;
    private Lrc lyrics;
    private Uri lyricsFor;
    private boolean lyricsLoading;
    /** The last tracks' lyrics, so going back to one shows them at once. */
    private final Map<Uri, Lrc> lyricsCache = new LinkedHashMap<Uri, Lrc>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Uri, Lrc> e) {
            return size() > 4;
        }
    };
    private boolean receiverOn;
    /** A position read from the player and when, to move smoothly between two reads. */
    private long anchorPos, anchorAt = -1;
    private int lyricsOffset;

    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            pause();   // the box is going to sleep (the sleep timer, or the remote's power key)
        }
    };

    /** A pause that lasts lets the foreground service go; a quick resume finds it still there. */
    private static final long IDLE_STOP_MS = 10 * 60_000L;
    private final Runnable idleStop = this::stopForeground;
    /** Play was asked for before the library had been read once (just after boot): resume when it has. */
    private boolean resumeWhenScanned;

    private final Runnable save = new Runnable() {
        @Override
        public void run() {
            remember();
            if (playing) main.postDelayed(this, 10_000);
        }
    };

    private MusicPlayer(Context app) {
        this.app = app;
        lyricsOffset = LyricsStyle.offset(app);
        audio = app.getSystemService(AudioManager.class);
        session = new MediaSession(app, "LaunchOS");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                resume();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                next();
            }

            @Override
            public void onSkipToPrevious() {
                previous();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo(pos);
            }

            @Override
            public void onStop() {
                pause();
            }
        });
    }

    static synchronized MusicPlayer get(Context c) {
        if (instance == null) instance = new MusicPlayer(c.getApplicationContext());
        return instance;
    }

    /** The player if one was made in this process, without making one (the sleep screen only reads it). */
    static synchronized MusicPlayer peek() {
        return instance;
    }

    void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    // ---- state ----

    boolean isPlaying() {
        return playing;
    }

    MusicLibrary.Collection collection() {
        return collection;
    }

    MusicLibrary.Track track() {
        return collection == null || index < 0 || index >= collection.tracks.size() ? null : collection.tracks.get(index);
    }

    /** The current track's synced lyrics, or null (none, or still being read). */
    Lrc lyrics() {
        MusicLibrary.Track t = track();
        return t != null && t.uri.equals(lyricsFor) ? lyrics : null;
    }

    /** The current track's lyrics are still being read. */
    boolean lyricsLoading() {
        return lyricsLoading && track() != null;
    }

    /**
     * Where the lyrics are: the position, moving smoothly between two reads of the player (some boxes only update it
     * a few times a second), plus the shift set in the lyrics options.
     */
    long lyricsPosition() {
        return smoothPosition() + lyricsOffset;
    }

    void setLyricsOffset(int ms) {
        lyricsOffset = ms;
        LyricsStyle.setOffset(app, ms);
        changed();
    }

    int lyricsOffset() {
        return lyricsOffset;
    }

    private long smoothPosition() {
        if (!playing || player == null || !prepared) {
            anchorAt = -1;
            return position();
        }
        long now = SystemClock.elapsedRealtime();
        if (anchorAt < 0 || now - anchorAt >= 250) {
            long real = position();
            long guess = anchorAt < 0 ? real : anchorPos + (now - anchorAt);
            // Small differences are eased in, so the highlight never jumps back and forth; a big one is a seek.
            anchorPos = Math.abs(real - guess) > 150 ? real : guess + (real - guess) / 4;
            anchorAt = now;
        }
        return anchorPos + (now - anchorAt);
    }

    long position() {
        if (player == null || !prepared) return startAt;
        try {
            return player.getCurrentPosition();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    long duration() {
        if (player != null && prepared) {
            try {
                return player.getDuration();
            } catch (IllegalStateException ignored) {
                // falls back on the index's duration
            }
        }
        MusicLibrary.Track t = track();
        return t == null ? 0 : t.durationMs;
    }

    /** The track kept from last time, when nothing has been played in this process yet. */
    String lastTitle() {
        return prefs().getString(MusicPrefs.LAST_TITLE, null);
    }

    String lastCollectionName() {
        return prefs().getString(MusicPrefs.LAST_COLLECTION_NAME, null);
    }

    // ---- control ----

    void play(MusicLibrary.Collection c, int i) {
        play(c, i, 0);
    }

    private void play(MusicLibrary.Collection c, int i, long at) {
        if (c == null || i < 0 || i >= c.tracks.size()) return;
        collection = c;
        index = i;
        startAt = at;
        wantPlay = true;
        release();
        MediaPlayer mp = new MediaPlayer();
        player = mp;
        prepared = false;
        mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        mp.setOnPreparedListener(p -> {
            if (p != player) return;
            prepared = true;
            if (startAt > 0) p.seekTo((int) startAt);
            if (wantPlay) start();
            else changed();
        });
        mp.setOnCompletionListener(p -> {
            if (p != player) return;
            if (collection != null && index + 1 < collection.tracks.size()) {
                play(collection, index + 1, 0);
            } else {
                startAt = 0;
                stopAll(false);
            }
        });
        mp.setOnErrorListener((p, what, extra) -> {
            if (p != player) return true;
            // An unreadable file, or the drive taken out: stop here rather than skip through everything.
            stopAll(true);
            MusicLibrary.get(app).refresh();
            return true;
        });
        try {
            mp.setDataSource(app, track().uri);
            mp.prepareAsync();
        } catch (java.io.IOException | IllegalArgumentException | IllegalStateException | SecurityException e) {
            // The file is gone or unreadable (a drive taken out): the same stop as a playback error.
            Logs.failed("MusicPlayer.play", e);
            stopAll(true);
            return;
        }
        loadLyrics(track());
        metadata();
        changed();
    }

    /** Everything stops: the player, the foreground service, the screen-off listener and the sound focus. */
    private void stopAll(boolean releasePlayer) {
        playing = false;
        wantPlay = false;
        if (releasePlayer) release();
        main.removeCallbacks(idleStop);
        stopForeground();
        changed();
    }

    void toggle() {
        if (playing) pause();
        else resume();
    }

    /** Plays again; after a restart, the track kept from last time when the library still has it. */
    void resume() {
        if (player != null) {
            wantPlay = true;
            if (prepared) start();
            return;
        }
        if (collection != null && index >= 0) {
            play(collection, index, startAt);
            return;
        }
        MusicLibrary library = MusicLibrary.get(app);
        if (!library.scanned()) {
            resumeWhenScanned = true;
            library.refresh();
            return;
        }
        SharedPreferences p = prefs();
        MusicLibrary.Collection c = library.find(p.getString(MusicPrefs.LAST_COLLECTION, ""));
        if (c == null) return;
        int i = c.indexOf(Uri.parse(p.getString(MusicPrefs.LAST_URI, "")));
        play(c, Math.max(0, i), i >= 0 ? p.getLong(MusicPrefs.LAST_POSITION, 0) : 0);
    }

    void pause() {
        wantPlay = false;
        if (player != null && prepared && playing) {
            try {
                player.pause();
            } catch (IllegalStateException ignored) {
                // already stopped
            }
        }
        if (playing) {
            playing = false;
            remember();
            // The service stays a while, so a quick resume does not race its start; the sound focus goes now.
            if (focus != null && audio != null) audio.abandonAudioFocusRequest(focus);
            main.removeCallbacks(idleStop);
            main.postDelayed(idleStop, IDLE_STOP_MS);
            changed();
        }
    }

    /**
     * The library was read again: the collection playing is swapped for its new copy (tracks added or gone), the
     * track playing kept. Called by MusicLibrary on the main thread.
     */
    void libraryRead(MusicLibrary library) {
        if (collection != null) {
            MusicLibrary.Collection fresh = library.find(collection.key);
            MusicLibrary.Track t = track();
            if (fresh != null && t != null) {
                int i = fresh.indexOf(t.uri);
                if (i >= 0) {
                    collection = fresh;
                    index = i;
                }
            }
        }
        if (resumeWhenScanned) {
            resumeWhenScanned = false;
            resume();
        }
    }

    void next() {
        if (collection != null && index + 1 < collection.tracks.size()) play(collection, index + 1, 0);
    }

    /** Back to the start of the track, or the one before when it has only just begun. */
    void previous() {
        if (collection == null) return;
        if (position() > 4000 || index == 0) seekTo(0);
        else play(collection, index - 1, 0);
    }

    void seekTo(long ms) {
        long to = Math.max(0, Math.min(ms, Math.max(0, duration() - 500)));
        if (player != null && prepared) {
            try {
                player.seekTo((int) to);
            } catch (IllegalStateException ignored) {
                // not ready yet: the start position below is used
            }
        }
        startAt = to;
        anchorAt = -1;
        state();
        changed();
    }

    void seekBy(long ms) {
        seekTo(position() + ms);
    }

    private void start() {
        if (!requestFocus()) return;
        try {
            player.start();
        } catch (IllegalStateException e) {
            return;
        }
        playing = true;
        anchorAt = -1;
        main.removeCallbacks(idleStop);
        session.setActive(true);
        if (!receiverOn) {
            Receivers.register(app, screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
            receiverOn = true;
        }
        MusicService.start(app);
        main.removeCallbacks(save);
        main.postDelayed(save, 10_000);
        changed();
    }

    private void release() {
        if (player == null) return;
        try {
            player.release();
        } catch (RuntimeException ignored) {
            // already gone
        }
        player = null;
        prepared = false;
    }

    private void stopForeground() {
        main.removeCallbacks(idleStop);
        MusicService.stop(app);
        if (receiverOn) {
            try {
                app.unregisterReceiver(screenOff);
            } catch (IllegalArgumentException ignored) {
                // not registered
            }
            receiverOn = false;
        }
        if (focus != null) audio.abandonAudioFocusRequest(focus);
    }

    // ---- sound focus: another app's sound pauses the music ----

    private boolean requestFocus() {
        if (audio == null) return true;
        if (focus == null) {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setOnAudioFocusChangeListener(this::focusChanged, main)
                    .build();
        }
        return audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void focusChanged(int change) {
        if (player == null) return;
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            pause();
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            player.setVolume(0.3f, 0.3f);
        } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
            player.setVolume(1f, 1f);
        }
    }

    // ---- lyrics ----

    private void loadLyrics(MusicLibrary.Track t) {
        if (t == null || t.uri.equals(lyricsFor)) return;
        if (lyricsCache.containsKey(t.uri)) {
            lyrics = lyricsCache.get(t.uri);
            lyricsFor = t.uri;
            lyricsLoading = false;
            return;
        }
        lyrics = null;
        lyricsFor = null;
        lyricsLoading = true;
        lyricsWorker.execute(() -> {
            Lrc l = readLyrics(app, t);
            main.post(() -> {
                lyricsCache.put(t.uri, l);
                MusicLibrary.Track now = track();
                if (now == null || !now.uri.equals(t.uri)) return;
                lyrics = l;
                lyricsFor = t.uri;
                lyricsLoading = false;
                changed();
            });
        });
    }

    /** An .lrc beside the file when the box lets it be read, else the lyrics inside the file. Background thread. */
    static Lrc readLyrics(Context c, MusicLibrary.Track t) {
        try {
            File side = new File(t.path.replaceFirst("\\.[^./]{1,5}$", "") + ".lrc");
            if (side.canRead()) {
                byte[] b = new byte[(int) Math.min(side.length(), Id3Lyrics.MAX_TAG)];
                try (InputStream in = new FileInputStream(side)) {
                    int got = 0, r;
                    while (got < b.length && (r = in.read(b, got, b.length - got)) > 0) got += r;
                }
                Lrc l = Lrc.parse(new String(b, StandardCharsets.UTF_8));
                if (l != null) return l;
            }
        } catch (java.io.IOException | SecurityException e) {
            Logs.failed("MusicPlayer.readLyrics.lrc", e);
            // not readable: the tag inside the file is tried next
        }
        try (InputStream in = c.getContentResolver().openInputStream(t.uri)) {
            return in == null ? null : Lrc.parse(Id3Lyrics.read(new java.io.BufferedInputStream(in, 64 * 1024)));
        } catch (java.io.IOException | RuntimeException e) {   // unreadable file, or a tag malformed in a new way
            Logs.failed("MusicPlayer.readLyrics.tag", e);
            return null;
        }
    }

    // ---- session and memory ----

    private void metadata() {
        MusicLibrary.Track t = track();
        if (t == null) return;
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, t.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, collection.name)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, t.durationMs)
                .build());
    }

    private void state() {
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, position(),
                        playing ? 1f : 0f)
                .build());
    }

    private void remember() {
        MusicLibrary.Track t = track();
        if (t == null) return;
        prefs().edit().putString(MusicPrefs.LAST_COLLECTION, collection.key).putString(MusicPrefs.LAST_COLLECTION_NAME, collection.name)
                .putString(MusicPrefs.LAST_URI, t.uri.toString()).putString(MusicPrefs.LAST_TITLE, t.title).putLong(MusicPrefs.LAST_POSITION, position()).apply();
    }

    private SharedPreferences prefs() {
        return MusicPrefs.get(app);
    }

    private void changed() {
        state();
        for (Listener l : new ArrayList<>(listeners)) l.playerChanged();
        PlayingBubble.playerChanged(app);
    }
}
