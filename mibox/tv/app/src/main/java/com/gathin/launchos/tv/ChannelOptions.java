package com.gathin.launchos.tv;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.function.Consumer;

/**
 * The small sheet for one channel (long press on OK): add to Favoris or take it out, hide it from every list, and
 * play one of its other versions (HD, SD…) when the panel has several. A channel shown on a shelf stands for all its
 * versions: Favoris applies to the version already in Favoris when there is one, Masquer hides every version.
 */
final class ChannelOptions {
    private ChannelOptions() {
    }

    static void show(Activity a, Item item, List<Item> versions, Consumer<Item> play, Runnable changed) {
        show(a, item, versions, play, changed, null);
    }

    /**
     * moved, when given, is told after the channel moved among Favoris (Monter, Descendre, En premier): the sheet
     * stays open, so OK again moves it on, and the list behind follows.
     */
    static void show(Activity a, Item item, List<Item> versions, Consumer<Item> play, Runnable changed,
                     Consumer<String> moved) {
        Channels chs = Channels.get(a);
        if (chs == null) return;
        Item target = item;
        for (Item v : versions) if (chs.starred(v.id)) target = v;
        Item fav = target;
        Dialog d = GroupOptions.dialog(a, R.layout.dialog_group);
        ((TextView) d.findViewById(R.id.dialog_title)).setText(LivePage.display(a, item));
        TextView sub = d.findViewById(R.id.dialog_sub);
        sub.setText(R.string.channel_options_sub);
        sub.setVisibility(View.VISIBLE);
        LinearLayout body = d.findViewById(R.id.dialog_body);
        boolean starred = chs.starred(fav.id);
        TextView first = GroupOptions.action(a, body, starred ? R.drawable.ic_star : R.drawable.ic_star_line,
                a.getString(starred ? R.string.channel_unstar : R.string.channel_star), () -> {
                    chs.setStarred(fav, !starred);
                    d.dismiss();
                    changed.run();
                });
        if (moved != null) {
            LinearLayout moves = new LinearLayout(a);
            moves.setOrientation(LinearLayout.VERTICAL);
            body.addView(moves);
            fillMoves(a, chs, fav, moves, moved);
        }
        int n = 1;
        for (Item v : versions) {
            n++;
            if (v.id.equals(item.id)) continue;
            String label = v.tag.isEmpty() ? a.getString(R.string.channel_play_version_n, n - 1)
                    : a.getString(R.string.channel_play_version, v.tag);
            GroupOptions.action(a, body, R.drawable.ic_play, label, () -> {
                d.dismiss();
                play.accept(v);
            });
        }
        GroupOptions.action(a, body, R.drawable.ic_hide, a.getString(R.string.channel_hide), () -> {
            for (Item v : versions) chs.setHidden(v, true);
            if (versions.isEmpty()) chs.setHidden(item, true);
            d.dismiss();
            changed.run();
        });
        d.show();
        first.requestFocus();
    }

    /** Monter, Descendre and En premier, for a channel added to Favoris by hand; rebuilt after each move. */
    private static void fillMoves(Activity a, Channels chs, Item fav, LinearLayout moves, Consumer<String> moved) {
        moves.removeAllViews();
        int at = chs.starredIndex(fav.id), count = chs.starredItems().size();
        if (at < 0 || count < 2) return;
        Consumer<Integer> to = place -> {
            chs.moveStarred(fav.id, place);
            moved.accept(fav.id);
            View focused = moves.findFocus();
            int keep = focused == null ? -1 : moves.indexOfChild(focused);
            fillMoves(a, chs, fav, moves, moved);
            // OK again on the same action keeps moving the channel; at the end of the list the focus goes to the next one.
            if (moves.getChildCount() > 0) moves.getChildAt(Math.max(0, Math.min(keep, moves.getChildCount() - 1))).requestFocus();
        };
        if (at > 0) GroupOptions.action(a, moves, R.drawable.ic_move_up, a.getString(R.string.channel_move_up),
                () -> to.accept(chs.starredIndex(fav.id) - 1));
        if (at < count - 1) GroupOptions.action(a, moves, R.drawable.ic_move_down, a.getString(R.string.channel_move_down),
                () -> to.accept(chs.starredIndex(fav.id) + 1));
        if (at > 1) GroupOptions.action(a, moves, R.drawable.ic_move_top, a.getString(R.string.channel_move_first),
                () -> to.accept(0));
    }
}
