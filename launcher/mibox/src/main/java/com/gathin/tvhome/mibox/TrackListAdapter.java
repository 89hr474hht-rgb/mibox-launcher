package com.gathin.tvhome.mibox;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.gathin.launchos.design.Focus;

import java.util.ArrayList;
import java.util.List;

/**
 * The tracks of one collection in the player's list, filtered by the search field. OK plays a track (the collection
 * carries on after it); a long press adds it to the favourites or takes it out. Main thread only.
 */
final class TrackListAdapter extends BaseAdapter {
    private final Context context;
    private final ListView list;
    private final MusicLibrary library;
    private final MusicPlayer player;
    private final int accent;
    private MusicLibrary.Collection collection;
    private final List<Integer> visible = new ArrayList<>();
    private String query = "";

    TrackListAdapter(Context context, ListView list, MusicLibrary library, MusicPlayer player) {
        this.context = context;
        this.list = list;
        this.library = library;
        this.player = player;
        accent = context.getColor(R.color.lo_accent);
    }

    /** Shows a collection; the same tracks as now only redraws the rows in place, so the focus stays. */
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
            MusicLibrary.Query q = MusicLibrary.Query.of(query);
            for (int i = 0; i < collection.tracks.size(); i++) {
                if (q.matches(collection.tracks.get(i).searchKey)) visible.add(i);
            }
        }
        notifyDataSetChanged();
    }

    /** Where a track is in the list as filtered, or 0. */
    int positionOf(MusicLibrary.Track t) {
        if (t == null || collection == null) return 0;
        for (int p = 0; p < visible.size(); p++) {
            if (collection.tracks.get(visible.get(p)).equals(t)) return p;
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
        View row = convert;
        if (row == null) {
            row = LayoutInflater.from(context).inflate(R.layout.mibox_item_track, parent, false);
            UiFonts.apply(row);
            Focus.lift(row, Focus.SCALE_LARGE);
        }
        int index = visible.get(p);
        MusicLibrary.Track t = collection.tracks.get(index);
        boolean now = t.equals(player.track());
        TextView number = row.findViewById(R.id.track_number);
        EqualizerView bars = row.findViewById(R.id.track_eq);
        number.setText(String.valueOf(index + 1));
        number.setVisibility(now ? View.GONE : View.VISIBLE);
        bars.setVisibility(now ? View.VISIBLE : View.GONE);
        bars.setColor(row.isFocused() ? context.getColor(R.color.lo_on_focus) : accent);
        bars.setPlaying(now && player.isPlaying());
        ((TextView) row.findViewById(R.id.track_title)).setText(t.title);
        ((TextView) row.findViewById(R.id.track_length)).setText(t.durationMs > 0 ? MusicActivity.clock(t.durationMs) : "");
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
