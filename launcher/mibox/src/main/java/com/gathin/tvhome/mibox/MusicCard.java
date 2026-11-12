package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * The music card on the home screen, above the dock on the right, centred on the dock app's name on the left: the
 * cover in the collection's colour with bars dancing while it plays, "Now playing", the title, the line of lyrics
 * being sung, the play state and a thin progress line. Up from the dock reaches it; OK opens the player; the remote's
 * play/pause key works on it. It follows the dock when the dock moves, and steps aside with the dock's name when the
 * app grid scrolls. Switched off in Settings → Musique.
 */
final class MusicCard implements MusicPlayer.Listener, MusicLibrary.Listener {
    private static final String PREFS = "mibox_music";
    private static final long TICK_MS = 400;

    private final Activity home;
    private final View card, hero, cover, track, fill;
    private final TextView label, title, line;
    private final ImageView state;
    private final EqualizerView eq;
    private final MusicPlayer player;
    private final MusicLibrary library;
    private float hue = -1;
    private boolean resumed, shown;
    private final ViewTreeObserver.OnPreDrawListener follow = this::place;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            fillIn();
            if (resumed && player.isPlaying()) card.postDelayed(this, TICK_MS);
        }
    };

    private MusicCard(Activity home, ViewGroup root, View hero) {
        this.home = home;
        this.hero = hero;
        player = MusicPlayer.get(home);
        library = MusicLibrary.get(home);
        card = LayoutInflater.from(home).inflate(R.layout.mibox_music_card, root, false);
        cover = card.findViewById(R.id.card_cover);
        eq = card.findViewById(R.id.card_eq);
        label = card.findViewById(R.id.card_label);
        title = card.findViewById(R.id.card_title);
        line = card.findViewById(R.id.card_line);
        state = card.findViewById(R.id.card_state);
        track = card.findViewById(R.id.card_track);
        fill = card.findViewById(R.id.card_fill);
        fill.setPivotX(0);
        UiFonts.apply(card);
        if (UiFonts.semibold(home) != null) title.setTypeface(UiFonts.semibold(home));
        if (UiFonts.medium(home) != null) label.setTypeface(UiFonts.medium(home));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(card.getLayoutParams().width,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        lp.setMarginEnd(Math.round(60 * home.getResources().getDisplayMetrics().density));
        card.setAlpha(0f);
        card.setVisibility(View.INVISIBLE);
        root.addView(card, lp);
        card.setOnClickListener(v -> home.startActivity(new Intent(home, MusicActivity.class)));
        card.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                player.toggle();
                return true;
            }
            // Left and Right stay on the card; Up goes to the top bar and Down back to the dock as usual.
            return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        });
        card.setOnFocusChangeListener((v, focused) ->
                v.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(180).start());
        root.getViewTreeObserver().addOnPreDrawListener(follow);
    }

    /** Adds the card; null when the home screen no longer has the dock's name to line up with. */
    @SuppressWarnings("DiscouragedApi") // the app's R is not visible from this library
    static MusicCard attach(Activity home) {
        String pkg = home.getPackageName();
        int rootId = home.getResources().getIdentifier("root", "id", pkg);
        int heroId = home.getResources().getIdentifier("hero", "id", pkg);
        if (rootId == 0 || heroId == 0) return null;
        View root = home.findViewById(rootId), hero = home.findViewById(heroId);
        if (!(root instanceof FrameLayout) || hero == null) return null;
        return new MusicCard(home, (ViewGroup) root, hero);
    }

    static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("home_card", true);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("home_card", on).apply();
    }

    void resume() {
        resumed = true;
        player.addListener(this);
        library.addListener(this);
        if (!library.scanned()) library.refresh();
        fillIn();
        card.removeCallbacks(tick);
        if (player.isPlaying()) card.post(tick);
    }

    void pause() {
        resumed = false;
        player.removeListener(this);
        library.removeListener(this);
        card.removeCallbacks(tick);
        eq.setPlaying(false);
    }

    void destroy() {
        pause();
        card.getViewTreeObserver().removeOnPreDrawListener(follow);
    }

    @Override
    public void playerChanged() {
        fillIn();
        card.removeCallbacks(tick);
        if (resumed && player.isPlaying()) card.post(tick);
    }

    @Override
    public void libraryChanged() {
        fillIn();
    }

    /** Centred on the dock app's name, on the right; shown while the name is. */
    private boolean place() {
        // A very long app name would run into the card: the card steps aside while it shows.
        float gap = 16 * home.getResources().getDisplayMetrics().density;
        boolean clash = card.getWidth() > 0 && hero.getX() + hero.getWidth() * hero.getScaleX() > card.getLeft() - gap;
        boolean want = enabled(home) && hero.getVisibility() == View.VISIBLE && hero.getHeight() > 0
                && (hero.getAlpha() > 0.5f && !clash || card.hasFocus());
        if (want && hero.getAlpha() > 0.5f && card.getHeight() > 0) {
            // A real layout position, not a translation: the remote's focus search reads the layout, and finds the
            // card just above the dock only if it is laid out there.
            int y = Math.round(hero.getY() + (hero.getHeight() - card.getHeight()) / 2f);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
            if (lp.topMargin != y) {
                lp.topMargin = y;
                card.setLayoutParams(lp);
            }
        }
        if (want != shown) {
            shown = want;
            card.animate().cancel();
            if (want) {
                card.setVisibility(View.VISIBLE);
                card.setFocusable(true);
                card.setTranslationY(card.getHeight() * 0.15f);
                card.animate().alpha(1f).translationY(0).setDuration(280).start();
            } else {
                card.setFocusable(false);
                card.animate().alpha(0f).setDuration(160)
                        .withEndAction(() -> {
                            if (!shown) card.setVisibility(View.INVISIBLE);
                        }).start();
            }
        }
        return true;
    }

    private void fillIn() {
        MusicLibrary.Track t = player.track();
        MusicLibrary.Collection c = player.collection();
        boolean playing = player.isPlaying();
        eq.setPlaying(playing && resumed);
        state.setImageResource(playing ? R.drawable.mibox_ic_pause : R.drawable.mibox_ic_play);
        if (t != null) {
            tint(c.key, c.name);
            label.setText(playing ? R.string.mibox_music_card_playing : R.string.mibox_music_card_paused);
            title.setText(t.title);
            line.setText(currentLine(c.name));
            long d = player.duration();
            track.setVisibility(View.VISIBLE);
            fill.setScaleX(d > 0 ? Math.max(0f, Math.min(1f, player.position() / (float) d)) : 0f);
            state.setVisibility(View.VISIBLE);
            return;
        }
        track.setVisibility(View.GONE);
        String last = player.lastTitle();
        if (last != null) {
            tint(null, player.lastCollectionName());
            label.setText(R.string.mibox_music_card_resume);
            title.setText(last);
            line.setText(player.lastCollectionName());
            state.setVisibility(View.VISIBLE);
            return;
        }
        tint(null, "");
        label.setText(R.string.mibox_music_title);
        state.setVisibility(View.GONE);
        if (!MusicLibrary.canRead(home)) {
            title.setText(R.string.mibox_music_card_access_title);
            line.setText(R.string.mibox_music_card_access);
            return;
        }
        List<MusicLibrary.Collection> all = library.collections();
        if (all.isEmpty()) {
            title.setText(R.string.mibox_music_card_empty_title);
            line.setText(library.scanned() ? R.string.mibox_music_card_nothing : R.string.mibox_music_searching);
            return;
        }
        StringBuilder names = new StringBuilder();
        for (MusicLibrary.Collection x : all) {
            if (names.length() > 0) names.append(", ");
            names.append(x.name);
        }
        title.setText(R.string.mibox_music_card_pick);
        line.setText(names);
    }

    private void tint(String key, String name) {
        float h = MusicArt.hue(key, name);
        if (h == hue) return;
        hue = h;
        cover.setBackground(MusicArt.cover(h, 14 * home.getResources().getDisplayMetrics().density));
    }

    /** The line of lyrics being sung (its first line when it is long), or the collection's name. */
    private String currentLine(String fallback) {
        Lrc l = player.lyrics();
        if (l == null) return fallback;
        int i = l.indexAt(player.lyricsPosition());
        if (i < 0) return fallback;
        Lrc.Line ln = l.lines.get(i);
        String s = !ln.text.isEmpty() ? ln.text : ln.sub;
        return s.isEmpty() ? fallback : s;
    }
}
