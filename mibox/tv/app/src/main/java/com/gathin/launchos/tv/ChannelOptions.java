package com.gathin.launchos.tv;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The small sheet for one channel (long press on OK): add to Favoris or take it out, hide it from every list. */
final class ChannelOptions {
    private ChannelOptions() {
    }

    static void show(Activity a, Item item, Runnable changed) {
        Channels chs = Channels.get(a);
        if (chs == null) return;
        Dialog d = GroupOptions.dialog(a, R.layout.dialog_group);
        ((TextView) d.findViewById(R.id.dialog_title)).setText(LivePage.display(a, item));
        TextView sub = d.findViewById(R.id.dialog_sub);
        sub.setText(R.string.channel_options_sub);
        sub.setVisibility(View.VISIBLE);
        LinearLayout body = d.findViewById(R.id.dialog_body);
        boolean starred = chs.starred(item.id);
        TextView first = GroupOptions.action(a, body, starred ? R.drawable.ic_star : R.drawable.ic_star_line,
                a.getString(starred ? R.string.channel_unstar : R.string.channel_star), () -> {
                    chs.setStarred(item, !starred);
                    d.dismiss();
                    changed.run();
                });
        GroupOptions.action(a, body, R.drawable.ic_hide, a.getString(R.string.channel_hide), () -> {
            chs.setHidden(item, true);
            d.dismiss();
            changed.run();
        });
        d.show();
        first.requestFocus();
    }
}
