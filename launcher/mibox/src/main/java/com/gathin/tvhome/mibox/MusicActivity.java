package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The music player, like a music app's "now playing" with lyrics. On the left: the collections found on the box's
 * storage (one per folder), a search field and the tracks of the collection chosen. On the right: the track playing,
 * previous / play-pause / next, a seek bar (Left and Right move 10 s while it has the focus) and the synced lyrics,
 * which follow the sound. OK on a track plays it, and the collection carries on after it.
 */
public final class MusicActivity extends Activity implements MusicLibrary.Listener, MusicPlayer.Listener {
    private static final int ASK = 7;
    private static final long TICK_MS = 200, SEEK_STEP_MS = 10_000;

    private MusicLibrary library;
    private MusicPlayer player;
    private LinearLayout chips;
    private EditText search;
    private ListView list;
    private TextView source, empty, allow, cover, title, artist, time, length;
    private View emptyBox, seek, fill;
    private ImageView play;
    private LyricsView lyrics;
    private final TrackAdapter adapter = new TrackAdapter();
    private MusicLibrary.Collection shown;
    private boolean focusedOnce;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            progress();
            lyrics.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.mibox_activity_music);
        library = MusicLibrary.get(this);
        player = MusicPlayer.get(this);
        chips = findViewById(R.id.music_collections);
        search = findViewById(R.id.music_search);
        list = findViewById(R.id.music_list);
        source = findViewById(R.id.music_source);
        empty = findViewById(R.id.music_empty);
        emptyBox = findViewById(R.id.music_empty_box);
        allow = findViewById(R.id.music_allow);
        cover = findViewById(R.id.music_cover);
        title = findViewById(R.id.music_title);
        artist = findViewById(R.id.music_artist);
        time = findViewById(R.id.music_time);
        length = findViewById(R.id.music_length);
        seek = findViewById(R.id.music_seek);
        fill = findViewById(R.id.music_fill);
        play = findViewById(R.id.music_play);
        lyrics = findViewById(R.id.music_lyrics);
        fill.setPivotX(0);
        UiFonts.apply(findViewById(android.R.id.content));
        if (UiFonts.bold(this) != null) {
            ((TextView) findViewById(R.id.music_heading)).setTypeface(UiFonts.bold(this));
            title.setTypeface(UiFonts.bold(this));
        }
        list.setItemsCanFocus(true);
        list.setAdapter(adapter);
        play.setOnClickListener(v -> player.toggle());
        findViewById(R.id.music_prev).setOnClickListener(v -> player.previous());
        findViewById(R.id.music_next).setOnClickListener(v -> player.next());
        seek.setOnClickListener(v -> player.toggle());
        seek.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                player.seekBy(-SEEK_STEP_MS);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                player.seekBy(SEEK_STEP_MS);
                return true;
            }
            return false;
        });
        allow.setOnClickListener(v -> requestPermissions(new String[]{MusicLibrary.permission()}, ASK));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                adapter.filter(e.toString());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        library.addListener(this);
        player.addListener(this);
        library.refresh();
        libraryChanged();
        playerChanged();
        lyrics.removeCallbacks(tick);
        lyrics.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        library.removeListener(this);
        player.removeListener(this);
        lyrics.removeCallbacks(tick);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        library.refresh();
    }

    // ---- the library ----

    @Override
    public void libraryChanged() {
        List<MusicLibrary.Collection> all = library.collections();
        boolean allowed = MusicLibrary.canRead(this);
        source.setText(sourceText());
        if (!allowed || all.isEmpty()) {
            chips.removeAllViews();
            adapter.set(null);
            emptyBox.setVisibility(View.VISIBLE);
            empty.setText(!allowed ? R.string.mibox_music_need_access
                    : library.scanned() ? R.string.mibox_music_nothing : R.string.mibox_music_searching);
            allow.setVisibility(allowed ? View.GONE : View.VISIBLE);
            if (!allowed) allow.requestFocus();
            return;
        }
        emptyBox.setVisibility(View.GONE);
        MusicLibrary.Collection playing = player.collection();
        MusicLibrary.Collection want = shown != null ? library.find(shown.key)
                : playing != null ? library.find(playing.key) : null;
        if (want == null) want = all.get(0);
        chips.removeAllViews();
        for (MusicLibrary.Collection c : all) {
            TextView chip = new TextView(this);
            chip.setText(c.name);
            chip.setTextColor(getColorStateList(R.color.mibox_music_text));
            chip.setTextSize(15);
            chip.setSingleLine(true);
            chip.setFocusable(true);
            chip.setBackgroundResource(R.drawable.mibox_music_chip_bg);
            int pad = Math.round(16 * getResources().getDisplayMetrics().density);
            chip.setPadding(pad, 0, pad, 0);
            chip.setGravity(android.view.Gravity.CENTER);
            if (UiFonts.medium(this) != null) chip.setTypeface(UiFonts.medium(this));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    Math.round(40 * getResources().getDisplayMetrics().density));
            lp.setMarginEnd(Math.round(8 * getResources().getDisplayMetrics().density));
            chip.setTag(c.key);
            chip.setOnClickListener(v -> show(c));
            chips.addView(chip, lp);
        }
        show(want);
        if (!focusedOnce) {
            focusedOnce = true;
            list.post(this::focusCurrent);
        }
    }

    private String sourceText() {
        int n = 0;
        List<String> where = new ArrayList<>();
        for (MusicLibrary.Volume v : library.volumes()) {
            if (v.tracks == 0) continue;
            n += v.tracks;
            where.add(v.name);
        }
        if (n == 0) return "";
        return getResources().getQuantityString(R.plurals.mibox_music_tracks, n, n) + " · " + String.join(", ", where);
    }

    private void show(MusicLibrary.Collection c) {
        shown = c;
        for (int i = 0; i < chips.getChildCount(); i++) {
            View chip = chips.getChildAt(i);
            chip.setSelected(c.key.equals(chip.getTag()));
        }
        adapter.set(c);
    }

    /** The track playing, or the first, gets the focus. */
    private void focusCurrent() {
        int at = adapter.positionOf(player.track());
        list.setSelection(Math.max(0, at - 2));
        list.post(() -> {
            View row = rowAt(Math.max(0, at));
            if (row != null) row.requestFocus();
        });
    }

    private View rowAt(int position) {
        int first = list.getFirstVisiblePosition();
        return list.getChildAt(position - first);
    }

    // ---- the player ----

    @Override
    public void playerChanged() {
        MusicLibrary.Track t = player.track();
        MusicLibrary.Collection c = player.collection();
        if (t == null && player.lastTitle() != null) {
            // Nothing played since LaunchOS started: the track from last time, which Play resumes.
            title.setText(player.lastTitle());
            artist.setText(getString(R.string.mibox_music_resume, player.lastCollectionName()));
            cover.setText(initial(player.lastCollectionName()));
            lyrics.setLyrics(null, "");
        } else if (t == null) {
            title.setText(R.string.mibox_music_none);
            artist.setText(R.string.mibox_music_pick);
            cover.setText("");
            lyrics.setLyrics(null, "");
        } else {
            title.setText(t.title);
            artist.setText(t.artist.isEmpty() ? c.name : t.artist + " · " + c.name);
            cover.setText(initial(c.name));
            lyrics.setLyrics(player.lyrics(), getString(R.string.mibox_music_no_lyrics));
        }
        boolean playing = player.isPlaying();
        play.setImageResource(playing ? R.drawable.mibox_ic_pause : R.drawable.mibox_ic_play);
        play.setContentDescription(getString(playing ? R.string.mibox_music_pause : R.string.mibox_music_play));
        adapter.notifyDataSetChanged();
        progress();
    }

    private void progress() {
        long pos = player.position(), dur = player.duration();
        time.setText(clock(pos));
        length.setText(dur > 0 ? clock(dur) : "");
        fill.setScaleX(dur > 0 ? Math.max(0f, Math.min(1f, pos / (float) dur)) : 0f);
        lyrics.setPosition(pos);
    }

    static String initial(String name) {
        if (name == null || name.isEmpty()) return "";
        int cp = name.codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(cp)));
    }

    /** "1:02:07" or "4:05". */
    static String clock(long ms) {
        long s = Math.max(0, ms) / 1000, h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    player.toggle();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    player.resume();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    player.pause();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    player.next();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    player.previous();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    player.seekBy(SEEK_STEP_MS);
                    return true;
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    player.seekBy(-SEEK_STEP_MS);
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    // ---- the list ----

    private final class TrackAdapter extends BaseAdapter {
        private MusicLibrary.Collection collection;
        private final List<Integer> visible = new ArrayList<>();
        private String query = "";

        void set(MusicLibrary.Collection c) {
            collection = c;
            refilter();
        }

        void filter(String q) {
            query = q == null ? "" : q;
            refilter();
        }

        private void refilter() {
            visible.clear();
            if (collection != null) {
                for (int i = 0; i < collection.tracks.size(); i++) {
                    MusicLibrary.Track t = collection.tracks.get(i);
                    if (MusicLibrary.matches(t.title, t.fileName, query)) visible.add(i);
                }
            }
            notifyDataSetChanged();
        }

        int positionOf(MusicLibrary.Track t) {
            if (t == null || collection == null) return 0;
            for (int p = 0; p < visible.size(); p++) {
                if (collection.tracks.get(visible.get(p)).uri.equals(t.uri)) return p;
            }
            return 0;
        }

        @Override
        public int getCount() {
            return visible.size();
        }

        @Override
        public Object getItem(int p) {
            return collection.tracks.get(visible.get(p));
        }

        @Override
        public long getItemId(int p) {
            return visible.get(p);
        }

        @Override
        public View getView(int p, View convert, ViewGroup parent) {
            View row = convert != null ? convert
                    : LayoutInflater.from(MusicActivity.this).inflate(R.layout.mibox_item_track, parent, false);
            if (convert == null) UiFonts.apply(row);
            int index = visible.get(p);
            MusicLibrary.Track t = collection.tracks.get(index);
            boolean now = player.collection() != null && player.collection().key.equals(collection.key)
                    && player.index() == index;
            ((TextView) row.findViewById(R.id.track_number)).setText(now ? "♪" : String.valueOf(index + 1));
            ((TextView) row.findViewById(R.id.track_title)).setText(t.title);
            ((TextView) row.findViewById(R.id.track_length)).setText(t.durationMs > 0 ? clock(t.durationMs) : "");
            row.setActivated(now);
            row.setOnClickListener(v -> {
                if (now && player.isPlaying()) return;
                if (now) player.resume();
                else player.play(collection, index);
            });
            return row;
        }
    }
}
