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
}
