package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.launchos.design.Focus;
import com.gathin.launchos.design.Motion;

import java.util.List;

/**
 * The player's first page: a menu (what plays or what to resume, Favourites, each collection, Search, Lyrics
 * options), what plays in a line at the bottom, and behind it the album name very large and dim. It only shows; what
 * an entry does is up to the Host. Main thread only.
 */
final class MusicHub {
    interface Host {
        /** Opens a page: a collection's key, MusicLibrary.FAVORITES, or MusicActivity's NOW / SEARCH. */
        void open(String key);

        void openOptions();

        void askAccess();

        String describe(MusicLibrary.Collection c);
    }

    private final Activity activity;
    private final Host host;
    private final MusicLibrary library;
    private final MusicPlayer player;
    private final View root, now;
    private final LinearLayout items;
    private final ShineText word;
    private final TextView nowTitle;
    private final EqualizerView nowBars;
    /** What the menu was built from, so it is not rebuilt (and the focus lost) for nothing. */
    private String builtFor = "";

    MusicHub(Activity a, Host host, MusicLibrary library, MusicPlayer player) {
        activity = a;
        this.host = host;
        this.library = library;
        this.player = player;
        root = a.findViewById(R.id.music_hub);
        items = a.findViewById(R.id.hub_items);
        word = a.findViewById(R.id.hub_word);
        now = a.findViewById(R.id.hub_now);
        nowTitle = a.findViewById(R.id.hub_now_title);
        nowBars = a.findViewById(R.id.hub_eq);
        nowBars.setColor(a.getColor(R.color.lo_accent));
        if (UiFonts.medium(a) != null) ((TextView) a.findViewById(R.id.hub_label)).setTypeface(UiFonts.medium(a));
    }

    View view() {
        return root;
    }

    /** The menu fades in from below, one entry after the other, the first time the player opens. */
    void comeIn() {
        if (!Motion.on()) return;
        float d = activity.getResources().getDisplayMetrics().density;
        for (int i = 0; i < items.getChildCount(); i++) {
            View v = items.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(12 * d);
            v.animate().alpha(1f).translationY(0).setStartDelay(60 + i * 40L).setDuration(Motion.LONG)
                    .setInterpolator(Motion.ENTER).start();
        }
    }

    /** The entries, rebuilt only when what they show changed. focus: give the focus to the menu. */
    void build(List<MusicLibrary.Collection> all, boolean focus) {
        MusicLibrary.Track t = player.track();
        MusicLibrary.Collection favs = library.favorites();
        boolean allowed = MusicLibrary.canRead(activity);
        StringBuilder sig = new StringBuilder();
        sig.append(t == null ? player.lastTitle() : t.title).append('|').append(favs.tracks.size()).append('|');
        for (MusicLibrary.Collection c : all) sig.append(c.key).append(c.tracks.size()).append('|');
        sig.append(allowed);
        if (sig.toString().equals(builtFor)) {
            if (focus && !items.hasFocus() && items.getChildCount() > 0) items.getChildAt(0).requestFocus();
            return;
        }
        builtFor = sig.toString();
        int focused = -1;
        for (int i = 0; i < items.getChildCount(); i++) if (items.getChildAt(i).hasFocus()) focused = i;
        items.removeAllViews();
        if (t != null) {
            item(R.drawable.mibox_ic_line_now, activity.getString(R.string.mibox_music_menu_now), t.title,
                    () -> host.open(MusicActivity.NOW));
        } else if (player.lastTitle() != null) {
            item(R.drawable.mibox_ic_line_play, activity.getString(R.string.mibox_music_card_resume), player.lastTitle(),
                    () -> {
                        player.resume();
                        host.open(MusicActivity.NOW);
                    });
        }
        if (!allowed) {
            item(R.drawable.mibox_ic_line_library, activity.getString(R.string.mibox_music_allow),
                    activity.getString(R.string.mibox_music_card_access), host::askAccess);
        }
        int nf = favs.tracks.size();
        item(R.drawable.mibox_ic_line_heart, activity.getString(R.string.mibox_music_favorites),
                nf == 0 ? activity.getString(R.string.mibox_music_hub_no_favorites)
                        : activity.getResources().getQuantityString(R.plurals.mibox_music_tracks, nf, nf),
                () -> host.open(MusicLibrary.FAVORITES));
        for (MusicLibrary.Collection c : all) {
            item(R.drawable.mibox_ic_line_library, c.name, host.describe(c), () -> host.open(c.key));
        }
        item(R.drawable.mibox_ic_line_search, activity.getString(R.string.mibox_music_search),
                activity.getString(R.string.mibox_music_hub_search), () -> host.open(MusicActivity.SEARCH));
        item(R.drawable.mibox_ic_line_lyrics, activity.getString(R.string.mibox_music_lyrics_options),
                activity.getString(R.string.mibox_music_hub_lyrics), host::openOptions);
        if ((focus || focused >= 0) && items.getChildCount() > 0) {
            items.getChildAt(Math.max(0, Math.min(focused, items.getChildCount() - 1))).requestFocus();
        }
    }

    /** Forgets what the menu was built from: the next build() rebuilds it. */
    void invalidate() {
        builtFor = "";
    }

    void focusLast() {
        if (items.getChildCount() > 0) items.getChildAt(items.getChildCount() - 1).requestFocus();
    }

    private void item(int icon, String name, String line, Runnable go) {
        View item = LayoutInflater.from(activity).inflate(R.layout.mibox_item_hub, items, false);
        ((ImageView) item.findViewById(R.id.hub_icon)).setImageResource(icon);
        TextView n = item.findViewById(R.id.hub_name), l = item.findViewById(R.id.hub_line);
        n.setText(name);
        l.setText(line);
        if (UiFonts.medium(activity) != null) n.setTypeface(UiFonts.medium(activity));
        Focus.lift(item, Focus.SCALE_LARGE);
        item.setOnClickListener(v -> go.run());
        item.setContentDescription(name + ", " + line);
        items.addView(item);
    }

    /** What plays, in a line under the menu, and the album name behind. */
    void showPlaying(MusicLibrary.Collection playing, boolean visible) {
        MusicLibrary.Track t = player.track();
        now.setVisibility(t == null ? View.GONE : View.VISIBLE);
        if (t != null) nowTitle.setText(t.title);
        nowBars.setPlaying(visible && player.isPlaying());
        MusicLibrary.Collection c = playing != null && !MusicLibrary.FAVORITES.equals(playing.key) ? playing
                : !library.collections().isEmpty() ? library.collections().get(0) : null;
        // The album name from the tags when there is one (it may be in another script), else the folder's.
        word.setText(c == null ? activity.getString(R.string.mibox_music_title) : !c.album.isEmpty() ? c.album : c.name);
    }

    void stopBars() {
        nowBars.setPlaying(false);
    }
}
