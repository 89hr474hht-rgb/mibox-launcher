package com.gathin.tvhome.mibox;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.gathin.launchos.design.Focus;
import com.gathin.launchos.design.Motion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The music player. It opens on the hub (MusicHub); an entry opens a page: a side menu of icons (it widens with
 * names while it has the focus), the tracks of a collection (TrackListAdapter) with a search field, and what plays
 * with its synced lyrics and the controls. "Now playing" gives the lyrics the whole width. Back goes from a page to
 * the hub and from the hub out of the player. The lyrics options panel (LyricsOptionsPanel) opens over either.
 *
 * <p>Listens to MusicLibrary and MusicPlayer while resumed; everything runs on the main thread.
 */
public final class MusicActivity extends Activity implements MusicLibrary.Listener, MusicPlayer.Listener,
        MusicHub.Host {
    static final String NOW = "\u0000now", SEARCH = "\u0000search", LYRICS = "\u0000lyrics";
    private static final int ASK = 7;
    private static final long TICK_MS = 250, SEEK_STEP_MS = 10_000;

    private MusicLibrary library;
    private MusicPlayer player;
    private MusicHub hub;
    private LyricsOptionsPanel options;
    private TrackListAdapter adapter;
    private LinearLayout rail;
    private View railScrim, content, libraryColumn, nowColumn, titles;
    private EditText search;
    private ListView list;
    private TextView heading, source, empty, allow, title, artist, time, length;
    private View emptyBox, seek, track, fill, cover, optionsButton;
    private ImageView play, fav;
    private EqualizerView eq;
    private LyricsView lyrics;
    private MusicLibrary.Collection shown;
    private String railFor = "";
    private float coverHue = -1;
    private boolean focusedOnce, railOpen, immersive, entered, inHub = true;
    private android.net.Uri titleFor;
    private ValueAnimator railAnim;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            progress();
            if (player.isPlaying()) seek.postDelayed(this, TICK_MS);
        }
    };
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusWatch = (from, to) -> {
        boolean inRail = to != null && LyricsOptionsPanel.inside(to, rail);
        if (inRail != railOpen) openRail(inRail);
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.mibox_activity_music);
        library = MusicLibrary.get(this);
        player = MusicPlayer.get(this);
        rail = findViewById(R.id.music_rail);
        content = findViewById(R.id.music_content);
        railScrim = findViewById(R.id.music_rail_scrim);
        libraryColumn = findViewById(R.id.music_library);
        nowColumn = findViewById(R.id.music_now);
        titles = findViewById(R.id.music_titles);
        search = findViewById(R.id.music_search);
        list = findViewById(R.id.music_list);
        heading = findViewById(R.id.music_heading);
        source = findViewById(R.id.music_source);
        empty = findViewById(R.id.music_empty);
        emptyBox = findViewById(R.id.music_empty_box);
        allow = findViewById(R.id.music_allow);
        cover = findViewById(R.id.music_cover);
        eq = findViewById(R.id.music_eq);
        title = findViewById(R.id.music_title);
        artist = findViewById(R.id.music_artist);
        time = findViewById(R.id.music_time);
        length = findViewById(R.id.music_length);
        seek = findViewById(R.id.music_seek);
        track = findViewById(R.id.music_track);
        fill = findViewById(R.id.music_fill);
        play = findViewById(R.id.music_play);
        fav = findViewById(R.id.music_fav);
        optionsButton = findViewById(R.id.music_options);
        lyrics = findViewById(R.id.music_lyrics);
        fill.setPivotX(0);
        UiFonts.apply(findViewById(android.R.id.content));
        if (UiFonts.semibold(this) != null) {
            heading.setTypeface(UiFonts.semibold(this));
            title.setTypeface(UiFonts.semibold(this));
            ((TextView) findViewById(R.id.music_panel_title)).setTypeface(UiFonts.semibold(this));
        }
        hub = new MusicHub(this, this, library, player);
        options = new LyricsOptionsPanel(this, player, lyrics);
        adapter = new TrackListAdapter(this, list, library, player);
        setPage(false);
        list.setItemsCanFocus(true);
        list.setAdapter(adapter);
        for (View v : new View[]{play, fav, optionsButton, findViewById(R.id.music_prev), findViewById(R.id.music_next)}) {
            Focus.lift(v);
        }
        play.setOnClickListener(v -> player.toggle());
        findViewById(R.id.music_prev).setOnClickListener(v -> player.previous());
        findViewById(R.id.music_next).setOnClickListener(v -> player.next());
        fav.setOnClickListener(v -> library.toggleFavorite(player.track()));
        optionsButton.setOnClickListener(v -> openOptions());
        seek.setOnClickListener(v -> player.toggle());
        seek.setOnFocusChangeListener((v, focused) -> {
            // The bar thickens while it has the focus: Left and Right then move through the track.
            float s = focused ? 2f : 1f;
            track.animate().scaleY(s).setDuration(Motion.SHORT).start();
            fill.animate().scaleY(s).setDuration(Motion.SHORT).start();
        });
        seek.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            player.seekBy(code == KeyEvent.KEYCODE_DPAD_LEFT ? -SEEK_STEP_MS : SEEK_STEP_MS);
            progress();
            return true;
        });
        allow.setOnClickListener(v -> askAccess());
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
        lyrics.setClock(player::lyricsPosition);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener(focusWatch);
    }

    @Override
    protected void onDestroy() {
        getWindow().getDecorView().getViewTreeObserver().removeOnGlobalFocusChangeListener(focusWatch);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        library.addListener(this);
        player.addListener(this);
        // The library follows drives and files on its own (mounts, the media index): read it here only once.
        if (!library.scanned()) library.refresh();
        libraryChanged();
        playerChanged();
        if (!entered) {
            entered = true;
            hub.comeIn();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        library.removeListener(this);
        player.removeListener(this);
        seek.removeCallbacks(tick);
        lyrics.setPlaying(false);
        eq.setPlaying(false);
        hub.stopBars();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        library.refresh();
    }

    // ---- MusicHub.Host ----

    /** From the hub to a page, or from one page to another. */
    @Override
    public void open(String key) {
        if (inHub) {
            setPage(true);
            hub.view().setVisibility(View.GONE);
            comeIn();
        }
        lyrics.setPlaying(player.isPlaying());
        hub.stopBars();
        pick(key);
    }

    @Override
    public void openOptions() {
        options.open(() -> {
            if (inHub) hub.focusLast();
            else optionsButton.requestFocus();
        });
    }

    @Override
    public void askAccess() {
        requestPermissions(new String[]{MusicLibrary.permission()}, ASK);
    }

    @Override
    public String describe(MusicLibrary.Collection c) {
        int n = c.tracks.size();
        String count = getResources().getQuantityString(R.plurals.mibox_music_tracks, n, n);
        return c.volume.isEmpty() ? count : count + " · " + c.volume;
    }

    // ---- pages ----

    /** The page comes in: the menu from the left, then the columns. */
    private void comeIn() {
        if (!Motion.on()) return;
        float d = getResources().getDisplayMetrics().density;
        View[] parts = {rail, libraryColumn, nowColumn};
        for (int i = 0; i < parts.length; i++) {
            View v = parts[i];
            v.setAlpha(0f);
            v.setTranslationY(12 * d);
            v.animate().alpha(1f).translationY(0).setStartDelay(i * 60L).setDuration(Motion.LONG)
                    .setInterpolator(Motion.ENTER).start();
        }
    }

    /** Back to the hub. */
    private void showHub() {
        if (inHub) return;
        if (railOpen) openRail(false);
        setPage(false);
        hub.view().setVisibility(View.VISIBLE);
        if (Motion.on()) {
            hub.view().setAlpha(0f);
            hub.view().animate().alpha(1f).setDuration(Motion.MEDIUM).setInterpolator(Motion.ENTER).start();
        }
        lyrics.setPlaying(false);
        hub.invalidate();
        hub.build(library.collections(), true);
        hub.showPlaying(player.collection(), true);
    }

    /** The page (menu, tracks, what plays) is shown and takes the keys, or the hub is. */
    private void setPage(boolean page) {
        inHub = !page;
        int v = page ? View.VISIBLE : View.INVISIBLE;
        content.setVisibility(v);
        rail.setVisibility(v);
        railScrim.setVisibility(v);
    }

    // ---- the side menu ----

    private void openRail(boolean open) {
        railOpen = open;
        float d = getResources().getDisplayMetrics().density;
        int to = Math.round((open ? 250 : 76) * d);
        if (railAnim != null) railAnim.cancel();
        if (!Motion.on()) {
            setRailWidth(to);
        } else {
            railAnim = ValueAnimator.ofInt(rail.getWidth(), to);
            railAnim.setDuration(Motion.MEDIUM);
            railAnim.setInterpolator(Motion.MOVE);
            railAnim.addUpdateListener(a -> setRailWidth((int) a.getAnimatedValue()));
            railAnim.start();
        }
        railScrim.animate().alpha(open ? 1f : 0f).setDuration(Motion.MEDIUM).start();
        for (int i = 0; i < rail.getChildCount(); i++) {
            rail.getChildAt(i).findViewById(R.id.rail_label).animate().alpha(open ? 1f : 0f)
                    .setDuration(open ? Motion.MEDIUM : Motion.SHORT).start();
        }
        // The page behind steps back while the menu is open.
        libraryColumn.animate().alpha(open ? 0.35f : 1f).setDuration(Motion.MEDIUM).start();
    }

    private void setRailWidth(int w) {
        ViewGroup.LayoutParams lp = rail.getLayoutParams();
        lp.width = w;
        rail.setLayoutParams(lp);
    }

    private void buildRail(List<MusicLibrary.Collection> collections) {
        StringBuilder sig = new StringBuilder();
        for (MusicLibrary.Collection c : collections) sig.append(c.key).append('|').append(c.name).append('\n');
        if (sig.toString().equals(railFor)) {
            markRail();
            return;
        }
        railFor = sig.toString();
        boolean had = rail.hasFocus();
        rail.removeAllViews();
        railItem(NOW, R.drawable.mibox_ic_line_now, getString(R.string.mibox_music_menu_now));
        railItem(MusicLibrary.FAVORITES, R.drawable.mibox_ic_line_heart, getString(R.string.mibox_music_favorites));
        for (MusicLibrary.Collection c : collections) railItem(c.key, R.drawable.mibox_ic_line_library, c.name);
        railItem(SEARCH, R.drawable.mibox_ic_line_search, getString(R.string.mibox_music_search));
        railItem(LYRICS, R.drawable.mibox_ic_line_lyrics, getString(R.string.mibox_music_menu_lyrics));
        markRail();
        if (had && rail.getChildCount() > 0) rail.getChildAt(0).requestFocus();
    }

    private void railItem(String key, int icon, String name) {
        View item = LayoutInflater.from(this).inflate(R.layout.mibox_item_rail, rail, false);
        ((ImageView) item.findViewById(R.id.rail_icon)).setImageResource(icon);
        TextView label = item.findViewById(R.id.rail_label);
        label.setText(name);
        label.setAlpha(railOpen ? 1f : 0f);
        if (UiFonts.medium(this) != null) label.setTypeface(UiFonts.medium(this));
        item.setTag(key);
        item.setContentDescription(name);
        item.setOnClickListener(v -> pick(key));
        rail.addView(item);
    }

    /** The section shown is marked in the menu. */
    private void markRail() {
        String on = immersive ? NOW : shown == null ? "" : shown.key;
        for (int i = 0; i < rail.getChildCount(); i++) {
            View item = rail.getChildAt(i);
            item.setActivated(on.equals(item.getTag()));
        }
    }

    private void pick(String key) {
        switch (key) {
            case NOW:
                showNowPlaying(true);
                play.requestFocus();
                return;
            case SEARCH:
                showNowPlaying(false);
                search.requestFocus();
                InputMethodManager im = getSystemService(InputMethodManager.class);
                if (im != null) im.showSoftInput(search, 0);
                return;
            case LYRICS:
                openOptions();
                return;
            default:
                break;
        }
        MusicLibrary.Collection c = MusicLibrary.FAVORITES.equals(key) ? library.favorites() : library.find(key);
        if (c == null) return;
        showNowPlaying(false);
        show(c);
        list.post(() -> {
            View row = rowAt(list.getFirstVisiblePosition());
            if (row != null) row.requestFocus();
            else search.requestFocus();
        });
    }

    /** "Now playing": the tracks step aside and the lyrics get the whole width; or back. */
    private void showNowPlaying(boolean on) {
        if (on == immersive) return;
        immersive = on;
        libraryColumn.animate().cancel();
        libraryColumn.setVisibility(on ? View.GONE : View.VISIBLE);
        libraryColumn.setAlpha(railOpen ? 0.35f : 1f);
        if (Motion.on()) {
            nowColumn.setAlpha(0f);
            nowColumn.animate().alpha(1f).setDuration(Motion.MEDIUM).setInterpolator(Motion.ENTER).start();
        }
        markRail();
    }

    // ---- the library ----

    @Override
    public void libraryChanged() {
        List<MusicLibrary.Collection> all = new ArrayList<>(library.collections());
        boolean allowed = MusicLibrary.canRead(this);
        hub.build(all, inHub && !options.isOpen() && !hub.view().hasFocus() && getCurrentFocus() == null);
        buildRail(all);
        if (!allowed || all.isEmpty()) {
            heading.setText(R.string.mibox_music_title);
            source.setText("");
            adapter.set(null);
            emptyBox.setVisibility(View.VISIBLE);
            empty.setText(!allowed ? R.string.mibox_music_need_access
                    : library.scanned() ? R.string.mibox_music_nothing : R.string.mibox_music_searching);
            allow.setVisibility(allowed ? View.GONE : View.VISIBLE);
            return;
        }
        MusicLibrary.Collection playing = player.collection();
        String key = shown != null ? shown.key : playing != null ? playing.key : null;
        MusicLibrary.Collection want = key == null ? null
                : MusicLibrary.FAVORITES.equals(key) ? library.favorites() : library.find(key);
        show(want == null ? all.get(0) : want);
        playerChanged();   // the heart, when a favourite was added or taken out
        if (!focusedOnce && !inHub) {
            focusedOnce = true;
            list.post(this::focusCurrent);
        }
    }

    private void show(MusicLibrary.Collection c) {
        shown = c;
        heading.setText(c.name);
        source.setText(describe(c));
        boolean none = c.tracks.isEmpty();
        emptyBox.setVisibility(none ? View.VISIBLE : View.GONE);
        allow.setVisibility(View.GONE);
        if (none) empty.setText(R.string.mibox_music_favorites_empty);
        adapter.set(c);
        markRail();
        tintCover();
    }

    /** The track playing, or the first, gets the focus, once the list has laid its rows out. */
    private void focusCurrent() {
        int at = adapter.positionOf(player.track());
        list.setSelection(Math.max(0, at - 2));
        focusRow(Math.max(0, at), 12);
    }

    private void focusRow(int at, int tries) {
        list.postDelayed(() -> {
            View row = rowAt(at);
            if (row != null) row.requestFocus();
            else if (tries > 0 && adapter.getCount() > 0) focusRow(at, tries - 1);
        }, 50);
    }

    private View rowAt(int position) {
        return list.getChildAt(position - list.getFirstVisiblePosition());
    }

    // ---- the player ----

    @Override
    public void playerChanged() {
        MusicLibrary.Track t = player.track();
        MusicLibrary.Collection c = player.collection();
        boolean playing = player.isPlaying();
        android.net.Uri now = t == null ? null : t.uri;
        boolean newTrack = now != null && !now.equals(titleFor);
        titleFor = now;
        if (t == null && player.lastTitle() != null) {
            // Nothing played since LaunchOS started: the track from last time, which Play resumes.
            title.setText(player.lastTitle());
            artist.setText(getString(R.string.mibox_music_resume, player.lastCollectionName()));
            lyrics.setLyrics(null, "");
        } else if (t == null) {
            title.setText(R.string.mibox_music_none);
            artist.setText(R.string.mibox_music_pick);
            lyrics.setLyrics(null, "");
        } else {
            title.setText(t.title);
            artist.setText(t.artist.isEmpty() ? c.name : t.artist + " · " + c.name);
            lyrics.setLyrics(player.lyrics(), getString(player.lyricsLoading() ? R.string.mibox_music_lyrics_loading
                    : R.string.mibox_music_no_lyrics));
        }
        if (newTrack && entered && Motion.on()) {
            titles.setAlpha(0f);
            titles.animate().alpha(1f).setDuration(Motion.MEDIUM).setInterpolator(Motion.ENTER).start();
        }
        boolean isFav = library.isFavorite(t);
        fav.setImageResource(isFav ? R.drawable.mibox_ic_heart : R.drawable.mibox_ic_heart_outline);
        fav.setContentDescription(getString(isFav ? R.string.mibox_music_favorite_remove
                : R.string.mibox_music_favorite_add));
        fav.setVisibility(t == null ? View.INVISIBLE : View.VISIBLE);
        fav.setFocusable(t != null);
        play.setImageResource(playing ? R.drawable.mibox_ic_pause : R.drawable.mibox_ic_play);
        play.setContentDescription(getString(playing ? R.string.mibox_music_pause : R.string.mibox_music_play));
        eq.setPlaying(playing);
        lyrics.setPlaying(playing && !inHub);
        hub.showPlaying(c, inHub);
        tintCover();
        adapter.refreshShown();
        progress();
        seek.removeCallbacks(tick);
        if (playing) seek.postDelayed(tick, TICK_MS);
    }

    /** The cover takes the colour of what plays, or of the collection shown. */
    private void tintCover() {
        MusicLibrary.Collection c = player.collection() != null ? player.collection() : shown;
        String name = c != null ? c.name : player.lastCollectionName();
        float hue = MusicArt.hue(c == null ? "" : c.key, name);
        if (hue == coverHue) return;
        coverHue = hue;
        cover.setBackground(MusicArt.cover(hue, getResources().getDimension(R.dimen.lo_radius_row)));
    }

    private void progress() {
        long pos = player.position(), dur = player.duration();
        time.setText(clock(pos));
        length.setText(dur > 0 ? clock(dur) : "");
        fill.setScaleX(dur > 0 ? Math.max(0f, Math.min(1f, pos / (float) dur)) : 0f);
    }

    /** "1:02:07" or "4:05". */
    static String clock(long ms) {
        long s = Math.max(0, ms) / 1000, h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    // ---- keys ----

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_BACK:
                    if (options.close()) return true;
                    if (!inHub) {
                        // Back from a page goes to the hub; from the hub, out of the player.
                        showHub();
                        return true;
                    }
                    break;
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
            if (options.onKeyDown(e, getCurrentFocus())) return true;
            if (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT && railOpen) {
                // Right from the menu goes to the page: the track playing, the first track, or the controls.
                View to = immersive ? play : rowAt(Math.max(list.getFirstVisiblePosition(), adapter.positionOf(player.track())));
                if (to == null) to = immersive || list.getChildCount() == 0 ? play : list.getChildAt(0);
                if (to != null) {
                    to.requestFocus();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(e);
    }
}
