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
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LayoutAnimationController;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
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
 * The music player. On the far left, a menu of icons that opens with their names over the page while it has the
 * focus: Now playing, Favourites, a collection per folder found on the box's storage, Search and Lyrics. Then the
 * section chosen: its name, a search field and its tracks. On the right, what plays: its cover in the collection's
 * colour with bars dancing while it plays, the title, the favourite and lyrics options buttons, the synced lyrics
 * large and lit word by word, and at the bottom the seek bar (Left and Right move 10 s while it has the focus) with
 * previous / play / next. "Now playing" slides the tracks away and gives the lyrics the whole width; Back brings them
 * back. OK on a track plays it and the collection carries on after it; a long press on a track adds it to the
 * favourites or takes it out. Everything moves softly: the page comes in piece by piece, the tracks one after the
 * other, a new track slides its title in and the cover pops.
 *
 * <p>It opens on its hub: the collection's name very large behind, carved a little and crossed by light, and a
 * menu (what plays or what to resume, Favourites, each collection, Search, Lyrics). OK opens an entry; Back from a
 * page comes back to the hub, and Back from the hub leaves the player.
 */
public final class MusicActivity extends Activity implements MusicLibrary.Listener, MusicPlayer.Listener {
    private static final int ASK = 7;
    private static final long TICK_MS = 250, SEEK_STEP_MS = 10_000;
    private static final int GOLD = 0xFFF2C879;
    private static final String NOW = "\u0000now", SEARCH = "\u0000search", LYRICS = "\u0000lyrics";
    /** A soft "ease out" for things coming in. */
    private static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private MusicLibrary library;
    private MusicPlayer player;
    private LinearLayout rail;
    private View railScrim, libraryColumn, nowColumn, titles;
    private EditText search;
    private ListView list;
    private TextView heading, source, empty, allow, title, artist, time, length;
    private View emptyBox, seek, track, fill, cover, glow, panel, options;
    private ImageView play, fav;
    private EqualizerView eq;
    private LyricsView lyrics;
    private View optSize, optTranslation, optWords, optOffset;
    private final TrackAdapter adapter = new TrackAdapter();
    private MusicLibrary.Collection shown;
    private String railFor = "";
    private float glowHue = -1;
    private boolean focusedOnce, railOpen, immersive, entered;
    private android.net.Uri titleFor;
    private View hub, content, hubNow;
    private LinearLayout hubItems;
    private ShineText hubWord;
    private TextView hubNowTitle;
    private EqualizerView hubEq;
    private boolean inHub = true;
    private String hubFor = "";
    private ValueAnimator railAnim;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            progress();
            seek.postDelayed(this, TICK_MS);
        }
    };
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusWatch = (from, to) -> {
        boolean inRail = to != null && isInside(to, rail);
        if (inRail != railOpen) openRail(inRail);
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.mibox_activity_music);
        library = MusicLibrary.get(this);
        player = MusicPlayer.get(this);
        rail = findViewById(R.id.music_rail);
        hub = findViewById(R.id.music_hub);
        content = findViewById(R.id.music_content);
        hubItems = findViewById(R.id.hub_items);
        hubWord = findViewById(R.id.hub_word);
        hubNow = findViewById(R.id.hub_now);
        hubNowTitle = findViewById(R.id.hub_now_title);
        hubEq = findViewById(R.id.hub_eq);
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
        glow = findViewById(R.id.music_glow);
        title = findViewById(R.id.music_title);
        artist = findViewById(R.id.music_artist);
        time = findViewById(R.id.music_time);
        length = findViewById(R.id.music_length);
        seek = findViewById(R.id.music_seek);
        track = findViewById(R.id.music_track);
        fill = findViewById(R.id.music_fill);
        play = findViewById(R.id.music_play);
        fav = findViewById(R.id.music_fav);
        options = findViewById(R.id.music_options);
        lyrics = findViewById(R.id.music_lyrics);
        panel = findViewById(R.id.music_panel);
        optSize = findViewById(R.id.music_opt_size);
        optTranslation = findViewById(R.id.music_opt_translation);
        optWords = findViewById(R.id.music_opt_words);
        optOffset = findViewById(R.id.music_opt_offset);
        fill.setPivotX(0);
        UiFonts.apply(findViewById(android.R.id.content));
        if (UiFonts.semibold(this) != null) {
            heading.setTypeface(UiFonts.semibold(this));
            title.setTypeface(UiFonts.semibold(this));
        }
        if (UiFonts.medium(this) != null) ((TextView) findViewById(R.id.hub_label)).setTypeface(UiFonts.medium(this));
        setPage(false);
        if (UiFonts.semibold(this) != null) {
            ((TextView) findViewById(R.id.music_panel_title)).setTypeface(UiFonts.semibold(this));
        }
        list.setItemsCanFocus(true);
        list.setAdapter(adapter);
        list.setLayoutAnimation(rowsComeIn());
        play.setOnClickListener(v -> {
            player.toggle();
            pop(play, 0.86f);
        });
        findViewById(R.id.music_prev).setOnClickListener(v -> {
            player.previous();
            pop(v, 0.86f);
        });
        findViewById(R.id.music_next).setOnClickListener(v -> {
            player.next();
            pop(v, 0.86f);
        });
        fav.setOnClickListener(v -> {
            library.toggleFavorite(player.track());
            pop(fav, 1.25f);
        });
        options.setOnClickListener(v -> openPanel());
        seek.setOnClickListener(v -> player.toggle());
        seek.setOnFocusChangeListener((v, focused) -> {
            float s = focused ? 2f : 1f;
            track.animate().scaleY(s).setDuration(160).setInterpolator(EASE).start();
            fill.animate().scaleY(s).setDuration(160).setInterpolator(EASE).start();
        });
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
        lyrics.setClock(player::lyricsPosition);
        setUpOptions();
        applyStyle();
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
        library.refresh();
        libraryChanged();
        playerChanged();
        seek.removeCallbacks(tick);
        seek.post(tick);
        if (!entered) {
            entered = true;
            hubComeIn();
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
        hubEq.setPlaying(false);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        library.refresh();
    }

    // ---- motion ----

    /** The page comes in piece by piece: the menu, the section, then what plays. */
    private void comeIn() {
        float d = getResources().getDisplayMetrics().density;
        View[] parts = {rail, libraryColumn, nowColumn};
        for (int i = 0; i < parts.length; i++) {
            View v = parts[i];
            v.setAlpha(0f);
            v.setTranslationX(i == 0 ? -24 * d : 0);
            v.setTranslationY(i == 0 ? 0 : 18 * d);
            v.animate().alpha(1f).translationX(0).translationY(0).setStartDelay(60 + i * 90L).setDuration(520)
                    .setInterpolator(EASE).start();
        }
    }

    /** The tracks come in one after the other. */
    private LayoutAnimationController rowsComeIn() {
        AnimationSet set = new AnimationSet(true);
        AlphaAnimation fade = new AlphaAnimation(0f, 1f);
        TranslateAnimation rise = new TranslateAnimation(0, 0, 14 * getResources().getDisplayMetrics().density, 0);
        set.addAnimation(fade);
        set.addAnimation(rise);
        set.setDuration(360);
        set.setInterpolator(new DecelerateInterpolator(1.8f));
        LayoutAnimationController c = new LayoutAnimationController(set, 0.12f);
        c.setOrder(LayoutAnimationController.ORDER_NORMAL);
        return c;
    }

    /** A quick squeeze or swell and back. */
    private static void pop(View v, float to) {
        v.animate().cancel();
        v.animate().scaleX(to).scaleY(to).setDuration(90).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f)
                .setDuration(320).setInterpolator(new OvershootInterpolator(2.2f)).start()).start();
    }

    // ---- the hub ----

    private void hubComeIn() {
        float d = getResources().getDisplayMetrics().density;
        hubWord.setAlpha(0f);
        hubWord.setTranslationX(40 * d);
        hubWord.animate().alpha(1f).translationX(0).setStartDelay(120).setDuration(1100).setInterpolator(EASE).start();
        for (int i = 0; i < hubItems.getChildCount(); i++) {
            View v = hubItems.getChildAt(i);
            float to = v.isFocused() ? 1f : 0.55f;
            v.setAlpha(0f);
            v.setTranslationY(16 * d);
            v.animate().alpha(to).translationY(0).setStartDelay(160 + i * 70L).setDuration(520).setInterpolator(EASE).start();
        }
        glow.setAlpha(0f);
        glow.animate().alpha(1f).setDuration(900).start();
    }

    /** The hub's entries: what plays (or what to resume), Favourites, each collection, Search and Lyrics. */
    private void buildHub(List<MusicLibrary.Collection> all) {
        MusicLibrary.Track t = player.track();
        MusicLibrary.Collection favs = library.favorites();
        StringBuilder sig = new StringBuilder();
        sig.append(t == null ? player.lastTitle() : t.title).append('|').append(favs.tracks.size()).append('|');
        for (MusicLibrary.Collection c : all) sig.append(c.key).append(c.tracks.size()).append('|');
        sig.append(MusicLibrary.canRead(this));
        if (sig.toString().equals(hubFor)) return;
        hubFor = sig.toString();
        int focused = -1;
        for (int i = 0; i < hubItems.getChildCount(); i++) if (hubItems.getChildAt(i).hasFocus()) focused = i;
        hubItems.removeAllViews();
        if (t != null) {
            hubItem(R.drawable.mibox_ic_line_now, getString(R.string.mibox_music_menu_now), t.title, () -> open(NOW));
        } else if (player.lastTitle() != null) {
            hubItem(R.drawable.mibox_ic_line_play, getString(R.string.mibox_music_card_resume), player.lastTitle(), () -> {
                player.resume();
                open(NOW);
            });
        }
        if (!MusicLibrary.canRead(this)) {
            hubItem(R.drawable.mibox_ic_line_library, getString(R.string.mibox_music_allow),
                    getString(R.string.mibox_music_card_access), () -> requestPermissions(new String[]{MusicLibrary.permission()}, ASK));
        }
        int nf = favs.tracks.size();
        hubItem(R.drawable.mibox_ic_line_heart, getString(R.string.mibox_music_favorites),
                nf == 0 ? getString(R.string.mibox_music_hub_no_favorites)
                        : getResources().getQuantityString(R.plurals.mibox_music_tracks, nf, nf),
                () -> open(MusicLibrary.FAVORITES));
        for (MusicLibrary.Collection c : all) {
            hubItem(R.drawable.mibox_ic_line_library, c.name, sourceText(c), () -> open(c.key));
        }
        hubItem(R.drawable.mibox_ic_line_search, getString(R.string.mibox_music_search),
                getString(R.string.mibox_music_hub_search), () -> open(SEARCH));
        hubItem(R.drawable.mibox_ic_line_lyrics, getString(R.string.mibox_music_lyrics_options),
                getString(R.string.mibox_music_hub_lyrics), this::openPanel);
        int at = Math.max(0, Math.min(focused, hubItems.getChildCount() - 1));
        if (inHub && hubItems.getChildCount() > 0 && (focused >= 0 || getCurrentFocus() == null
                || !isInside(getCurrentFocus(), panel))) {
            hubItems.getChildAt(at).requestFocus();
        }
    }

    private void hubItem(int icon, String name, String line, Runnable go) {
        View item = LayoutInflater.from(this).inflate(R.layout.mibox_item_hub, hubItems, false);
        ((ImageView) item.findViewById(R.id.hub_icon)).setImageResource(icon);
        TextView n = item.findViewById(R.id.hub_name), l = item.findViewById(R.id.hub_line);
        n.setText(name);
        l.setText(line);
        if (UiFonts.medium(this) != null) n.setTypeface(UiFonts.medium(this));
        if (UiFonts.regular(this) != null) l.setTypeface(UiFonts.regular(this));
        View mark = item.findViewById(R.id.hub_mark);
        float d = getResources().getDisplayMetrics().density;
        item.setOnFocusChangeListener((v, f) -> {
            v.animate().alpha(f ? 1f : 0.55f).translationX(f ? 10 * d : 0).setDuration(220).setInterpolator(EASE).start();
            mark.animate().alpha(f ? 1f : 0f).scaleY(f ? 1f : 0.4f).setDuration(220).setInterpolator(EASE).start();
        });
        item.setOnClickListener(v -> go.run());
        item.setContentDescription(name + ", " + line);
        hubItems.addView(item);
    }

    /** The big word behind the hub: the collection playing, or the first one. */
    private void hubWordFor(MusicLibrary.Collection playing) {
        MusicLibrary.Collection c = playing != null && !MusicLibrary.FAVORITES.equals(playing.key) ? playing
                : !library.collections().isEmpty() ? library.collections().get(0) : null;
        // The album name from the tags when there is one (it may be in another script), else the folder's.
        hubWord.setText(c == null ? getString(R.string.mibox_music_title) : !c.album.isEmpty() ? c.album : c.name);
    }

    /** From the hub to a page: the hub zooms gently through and the page comes in. */
    private void open(String key) {
        if (inHub) {
            setPage(true);
            hub.animate().alpha(0f).scaleX(1.04f).scaleY(1.04f).setDuration(320).setInterpolator(EASE)
                    .withEndAction(() -> {
                        if (!inHub) hub.setVisibility(View.GONE);
                    }).start();
            comeIn();
        }
        lyrics.setPlaying(player.isPlaying());
        hubEq.setPlaying(false);
        pick(key);
    }

    /** Back to the hub. */
    private void showHub() {
        if (inHub) return;
        if (railOpen) openRail(false);
        setPage(false);
        hub.setVisibility(View.VISIBLE);
        hub.setScaleX(1.04f);
        hub.setScaleY(1.04f);
        hub.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(EASE).start();
        lyrics.setPlaying(false);
        hubEq.setPlaying(player.isPlaying());
        hubFor = "";
        buildHub(library.collections());
        if (hubItems.getChildCount() > 0) hubItems.getChildAt(0).requestFocus();
    }

    /** The page (menu, tracks, what plays) is shown and takes the keys, or the hub is. */
    private void setPage(boolean page) {
        inHub = !page;
        int v = page ? View.VISIBLE : View.INVISIBLE;
        content.setVisibility(v);
        rail.setVisibility(v);
        railScrim.setVisibility(v);
        content.setAlpha(page ? 1f : 0f);
        if (!page) {
            hub.setAlpha(hub.getVisibility() == View.VISIBLE ? hub.getAlpha() : 0f);
        }
    }

    // ---- the menu ----

    private void openRail(boolean open) {
        railOpen = open;
        float d = getResources().getDisplayMetrics().density;
        int from = rail.getWidth(), to = Math.round((open ? 250 : 76) * d);
        if (railAnim != null) railAnim.cancel();
        railAnim = ValueAnimator.ofInt(from, to);
        railAnim.setDuration(open ? 300 : 240);
        railAnim.setInterpolator(EASE);
        railAnim.addUpdateListener(a -> {
            ViewGroup.LayoutParams lp = rail.getLayoutParams();
            lp.width = (int) a.getAnimatedValue();
            rail.setLayoutParams(lp);
        });
        railAnim.start();
        railScrim.animate().alpha(open ? 1f : 0f).setDuration(open ? 300 : 220).start();
        for (int i = 0; i < rail.getChildCount(); i++) {
            View label = rail.getChildAt(i).findViewById(R.id.rail_label);
            label.animate().alpha(open ? 1f : 0f).translationX(open ? 0 : -8 * d)
                    .setStartDelay(open ? 60 + i * 25L : 0).setDuration(open ? 260 : 120).start();
        }
        // The page behind steps back a little while the menu is open.
        libraryColumn.animate().alpha(open ? 0.35f : 1f).setDuration(260).start();
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
            item.setSelected(on.equals(item.getTag()));
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
                openPanel();
                return;
            default:
                break;
        }
        MusicLibrary.Collection c = MusicLibrary.FAVORITES.equals(key) ? library.favorites() : library.find(key);
        if (c == null) return;
        showNowPlaying(false);
        show(c, true);
        list.post(() -> {
            View row = rowAt(list.getFirstVisiblePosition());
            if (row != null) row.requestFocus();
            else search.requestFocus();
        });
    }

    /** "Now playing": the tracks slide away and the lyrics get the whole width; or back. */
    private void showNowPlaying(boolean on) {
        if (on == immersive) return;
        immersive = on;
        float d = getResources().getDisplayMetrics().density;
        if (on) {
            libraryColumn.animate().alpha(0f).translationX(-40 * d).setDuration(260).setInterpolator(EASE)
                    .withEndAction(() -> {
                        if (immersive) {
                            libraryColumn.setVisibility(View.GONE);
                            nowColumn.setAlpha(0.4f);
                            nowColumn.animate().alpha(1f).setDuration(380).setInterpolator(EASE).start();
                        }
                    }).start();
        } else {
            libraryColumn.setVisibility(View.VISIBLE);
            libraryColumn.setTranslationX(-40 * d);
            libraryColumn.animate().alpha(railOpen ? 0.35f : 1f).translationX(0).setDuration(380)
                    .setInterpolator(EASE).start();
        }
        markRail();
    }

    // ---- the library ----

    @Override
    public void libraryChanged() {
        List<MusicLibrary.Collection> all = new ArrayList<>(library.collections());
        boolean allowed = MusicLibrary.canRead(this);
        buildHub(all);
        if (!allowed || all.isEmpty()) {
            buildRail(all);
            heading.setText(R.string.mibox_music_title);
            source.setText("");
            adapter.set(null);
            emptyBox.setVisibility(View.VISIBLE);
            empty.setText(!allowed ? R.string.mibox_music_need_access
                    : library.scanned() ? R.string.mibox_music_nothing : R.string.mibox_music_searching);
            allow.setVisibility(allowed ? View.GONE : View.VISIBLE);
            if (!allowed) allow.requestFocus();
            return;
        }
        buildRail(all);
        MusicLibrary.Collection playing = player.collection();
        String key = shown != null ? shown.key : playing != null ? playing.key : null;
        MusicLibrary.Collection want = key == null ? null
                : MusicLibrary.FAVORITES.equals(key) ? library.favorites() : library.find(key);
        if (want == null) want = all.get(0);
        show(want, shown == null || !shown.key.equals(want.key));
        playerChanged();   // the heart, when a favourite was added or taken out
        if (!focusedOnce && !inHub) {
            focusedOnce = true;
            list.post(this::focusCurrent);
        }
    }

    private void show(MusicLibrary.Collection c, boolean animate) {
        shown = c;
        heading.setText(c.name);
        source.setText(sourceText(c));
        boolean none = c.tracks.isEmpty();
        emptyBox.setVisibility(none ? View.VISIBLE : View.GONE);
        allow.setVisibility(View.GONE);
        if (none) empty.setText(R.string.mibox_music_favorites_empty);
        adapter.set(c);
        if (animate) {
            list.scheduleLayoutAnimation();
            heading.setAlpha(0f);
            heading.setTranslationY(10 * getResources().getDisplayMetrics().density);
            heading.animate().alpha(1f).translationY(0).setDuration(360).setInterpolator(EASE).start();
        }
        markRail();
        tint();
    }

    private String sourceText(MusicLibrary.Collection c) {
        int n = c.tracks.size();
        String count = getResources().getQuantityString(R.plurals.mibox_music_tracks, n, n);
        return c.volume.isEmpty() ? count : count + " · " + c.volume;
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
        int first = list.getFirstVisiblePosition();
        return list.getChildAt(position - first);
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
        if (newTrack && entered) {
            // A new track: its title slides in and the cover pops.
            float d = getResources().getDisplayMetrics().density;
            titles.setAlpha(0f);
            titles.setTranslationX(24 * d);
            titles.animate().alpha(1f).translationX(0).setDuration(420).setInterpolator(EASE).start();
            cover.setScaleX(0.86f);
            cover.setScaleY(0.86f);
            cover.animate().scaleX(1f).scaleY(1f).setDuration(520).setInterpolator(new OvershootInterpolator(1.6f)).start();
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
        hubNow.setVisibility(t == null ? View.GONE : View.VISIBLE);
        if (t != null) hubNowTitle.setText(t.title);
        hubEq.setPlaying(playing && inHub);
        hubWordFor(c);
        tint();
        adapter.refreshShown();
        progress();
    }

    /** The cover and the glow take the colour of what plays, or of the collection shown; the glow fades across. */
    private void tint() {
        MusicLibrary.Collection c = player.collection() != null ? player.collection() : shown;
        String name = c != null ? c.name : player.lastCollectionName();
        float hue = MusicArt.hue(c == null ? "" : c.key, name);
        if (hue == glowHue) return;
        boolean first = glowHue < 0;
        glowHue = hue;
        cover.setBackground(MusicArt.cover(hue, dp(18)));
        int w = getResources().getDisplayMetrics().widthPixels;
        if (first) {
            glow.setBackground(MusicArt.glow(hue, w * 0.8f));
            return;
        }
        glow.animate().cancel();
        glow.animate().alpha(0f).setDuration(260).withEndAction(() -> {
            glow.setBackground(MusicArt.glow(hue, w * 0.8f));
            glow.animate().alpha(1f).setDuration(700).start();
        }).start();
    }

    private void progress() {
        long pos = player.position(), dur = player.duration();
        time.setText(clock(pos));
        length.setText(dur > 0 ? clock(dur) : "");
        fill.setScaleX(dur > 0 ? Math.max(0f, Math.min(1f, pos / (float) dur)) : 0f);
    }

    // ---- lyrics options ----

    private void setUpOptions() {
        option(optSize, R.string.mibox_music_opt_size,
                () -> LyricsStyle.setSize(this, (LyricsStyle.size(this) + 2) % 3),
                () -> LyricsStyle.setSize(this, (LyricsStyle.size(this) + 1) % 3));
        Runnable translation = () -> LyricsStyle.setTranslation(this, !LyricsStyle.translation(this));
        option(optTranslation, R.string.mibox_music_opt_translation, translation, translation);
        Runnable words = () -> LyricsStyle.setWords(this, !LyricsStyle.words(this));
        option(optWords, R.string.mibox_music_opt_words, words, words);
        option(optOffset, R.string.mibox_music_opt_offset,
                () -> player.setLyricsOffset(player.lyricsOffset() - LyricsStyle.OFFSET_STEP),
                () -> player.setLyricsOffset(player.lyricsOffset() + LyricsStyle.OFFSET_STEP));
        // OK on the shift sets it back to none.
        optOffset.setOnClickListener(v -> {
            player.setLyricsOffset(0);
            applyStyle();
        });
    }

    private void option(View row, int name, Runnable left, Runnable right) {
        ((TextView) row.findViewById(R.id.option_name)).setText(name);
        row.setOnClickListener(v -> {
            right.run();
            applyStyle();
        });
        row.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                left.run();
                applyStyle();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                right.run();
                applyStyle();
                return true;
            }
            return false;
        });
    }

    private void applyStyle() {
        int size = LyricsStyle.size(this);
        boolean tr = LyricsStyle.translation(this), w = LyricsStyle.words(this);
        lyrics.setStyle(size, tr, w);
        value(optSize, getString(size == LyricsStyle.SMALL ? R.string.mibox_music_size_small
                : size == LyricsStyle.LARGE ? R.string.mibox_music_size_large : R.string.mibox_music_size_medium));
        value(optTranslation, getString(tr ? R.string.mibox_music_shown : R.string.mibox_music_hidden));
        value(optWords, getString(w ? R.string.mibox_music_on : R.string.mibox_music_off));
        value(optOffset, LyricsStyle.offsetText(player.lyricsOffset()));
    }

    private void value(View row, String text) {
        ((TextView) row.findViewById(R.id.option_value)).setText(getString(R.string.mibox_music_option_value, text));
    }

    private void openPanel() {
        applyStyle();
        panel.setVisibility(View.VISIBLE);
        panel.setAlpha(0f);
        panel.setTranslationX(dp(32));
        panel.setScaleX(0.97f);
        panel.setScaleY(0.97f);
        panel.animate().alpha(1f).translationX(0).scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(EASE).start();
        optSize.requestFocus();
    }

    private boolean closePanel() {
        if (panel.getVisibility() != View.VISIBLE) return false;
        panel.animate().alpha(0f).translationX(dp(24)).setDuration(180)
                .withEndAction(() -> panel.setVisibility(View.GONE)).start();
        if (inHub && hubItems.getChildCount() > 0) hubItems.getChildAt(hubItems.getChildCount() - 1).requestFocus();
        else options.requestFocus();
        return true;
    }

    // ---- keys ----

    /** "1:02:07" or "4:05". */
    static String clock(long ms) {
        long s = Math.max(0, ms) / 1000, h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_BACK:
                    if (closePanel()) return true;
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
            // The options panel keeps the focus while it is open.
            if (panel.getVisibility() == View.VISIBLE) {
                View f = getCurrentFocus();
                if (f == null || !isInside(f, panel)) {
                    optSize.requestFocus();
                    return true;
                }
                int code = e.getKeyCode();
                if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    // Up and Down go from row to row, and stay in the panel.
                    View[] rows = {optSize, optTranslation, optWords, optOffset};
                    int at = 0;
                    for (int i = 0; i < rows.length; i++) if (isInside(f, rows[i])) at = i;
                    int to = Math.max(0, Math.min(rows.length - 1, at + (code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1)));
                    rows[to].requestFocus();
                    return true;
                }
            } else if (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT && railOpen) {
                // Right from the menu goes to the page: the tracks, or what plays.
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

    private static boolean isInside(View v, View parent) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == parent) return true;
        }
        return false;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---- the list ----

    private final class TrackAdapter extends BaseAdapter {
        private MusicLibrary.Collection collection;
        private final List<Integer> visible = new ArrayList<>();
        private String query = "";

        void set(MusicLibrary.Collection c) {
            boolean same = c != null && collection != null && c.key.equals(collection.key)
                    && c.tracks.equals(collection.tracks);
            collection = c;
            if (same) refreshShown();
            else refilter();
        }

        /** The rows on screen drawn again in place: the focus stays where it is. */
        void refreshShown() {
            int first = list.getFirstVisiblePosition();
            for (int i = 0; i < list.getChildCount(); i++) {
                int p = first + i;
                if (p < getCount()) getView(p, list.getChildAt(i), list);
            }
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
            if (convert == null) {
                UiFonts.apply(row);
                row.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.025f : 1f).scaleY(f ? 1.025f : 1f)
                        .setDuration(160).setInterpolator(EASE).start());
            }
            int index = visible.get(p);
            MusicLibrary.Track t = collection.tracks.get(index);
            MusicLibrary.Track playingNow = player.track();
            boolean now = playingNow != null && playingNow.uri.equals(t.uri);
            TextView number = row.findViewById(R.id.track_number);
            EqualizerView bars = row.findViewById(R.id.track_eq);
            number.setText(String.valueOf(index + 1));
            number.setVisibility(now ? View.GONE : View.VISIBLE);
            bars.setVisibility(now ? View.VISIBLE : View.GONE);
            bars.setColor(GOLD);
            bars.setPlaying(now && player.isPlaying());
            ((TextView) row.findViewById(R.id.track_title)).setText(t.title);
            ((TextView) row.findViewById(R.id.track_length)).setText(t.durationMs > 0 ? clock(t.durationMs) : "");
            row.findViewById(R.id.track_fav).setVisibility(library.isFavorite(t) ? View.VISIBLE : View.GONE);
            row.setActivated(now);
            MusicLibrary.Collection from = collection;
            row.setOnClickListener(v -> {
                if (now && player.isPlaying()) return;
                if (now) player.resume();
                else player.play(from, index);
            });
            row.setOnLongClickListener(v -> {
                library.toggleFavorite(t);
                return true;
            });
            return row;
        }
    }
}
