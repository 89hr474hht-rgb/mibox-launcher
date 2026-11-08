package com.gathin.launchos.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;

/**
 * Réglages, the one place for everything that is not watching: the account, refreshing the lists and updates at the
 * top; then how channels are sorted (smart order, TNT numbers, doubles, adult groups, hidden channels, groups); the
 * groups of films and series; playback; and clearing the history. Each row says its state.
 */
public final class SettingsActivity extends Activity {
    private static final Smart.GroupOrder[] GROUP_ORDERS = Smart.GroupOrder.values();
    private static final Smart.ChannelOrder[] CHANNEL_ORDERS = Smart.ChannelOrder.values();

    private Rows.Row account, updates, groupOrder, channelOrder, dedupe, adult, hiddenChannels, mini, next, tidy;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_refresh);
        Rows.header(this, R.drawable.ic_settings, R.string.settings_title, R.string.settings_hint);
        ViewGroup column = findViewById(R.id.column);

        Rows.section(column, R.string.settings_section_app);
        account = Rows.add(column, R.drawable.ic_account, R.string.nav_account).opens()
                .click(v -> startActivity(new Intent(this, AccountActivity.class)));
        Rows.add(column, R.drawable.ic_refresh, R.string.refresh_title).sub(R.string.settings_refresh_sub).opens()
                .click(v -> startActivity(new Intent(this, RefreshActivity.class)));
        updates = Rows.add(column, R.drawable.ic_update, R.string.nav_updates).opens()
                .click(v -> startActivity(new Intent(this, UpdatesActivity.class)));

        Rows.section(column, R.string.settings_section_channels);
        groupOrder = Rows.add(column, R.drawable.ic_sort, R.string.settings_group_order).click(v -> {
            Smart.GroupOrder o = Options.groupOrder(this);
            Options.setGroupOrder(this, GROUP_ORDERS[(o.ordinal() + 1) % GROUP_ORDERS.length]);
            Groups.touch();
            states();
        });
        channelOrder = Rows.add(column, R.drawable.ic_live, R.string.settings_channel_order).click(v -> {
            Smart.ChannelOrder o = Options.channelOrder(this);
            Options.setChannelOrder(this, CHANNEL_ORDERS[(o.ordinal() + 1) % CHANNEL_ORDERS.length]);
            states();
        });
        dedupe = Rows.add(column, R.drawable.ic_all, R.string.settings_dedupe).sub(R.string.settings_dedupe_sub).click(v -> {
            Options.setDedupe(this, !Options.dedupe(this));
            states();
        });
        adult = Rows.add(column, R.drawable.ic_hide, R.string.settings_adult).sub(R.string.settings_adult_sub).click(v -> {
            Options.setHideAdult(this, !Options.hideAdult(this));
            Groups.touch();
            states();
        });
        hiddenChannels = Rows.add(column, R.drawable.ic_hide, R.string.settings_hidden_channels).opens()
                .click(v -> startActivity(new Intent(this, HiddenChannelsActivity.class)));
        Rows.add(column, R.drawable.ic_folder, R.string.settings_groups_live).sub(R.string.settings_groups_sub).opens()
                .click(v -> groups(Xtream.Section.LIVE));

        Rows.section(column, R.string.settings_section_vod);
        Rows.add(column, R.drawable.ic_movie, R.string.settings_groups_movies).sub(R.string.settings_groups_sub)
                .opens().click(v -> groups(Xtream.Section.MOVIES));
        Rows.add(column, R.drawable.ic_series, R.string.settings_groups_series).sub(R.string.settings_groups_sub)
                .opens().click(v -> groups(Xtream.Section.SERIES));

        Rows.section(column, R.string.settings_section_play);
        mini = Rows.add(column, R.drawable.ic_mini, R.string.settings_mini).sub(R.string.settings_mini_sub).click(v -> {
            Options.setMiniPlayer(this, !Options.miniPlayer(this));
            states();
        });
        next = Rows.add(column, R.drawable.ic_next, R.string.settings_next).sub(R.string.settings_next_sub).click(v -> {
            Options.setNextEpisode(this, !Options.nextEpisode(this));
            states();
        });
        tidy = Rows.add(column, R.drawable.ic_text, R.string.settings_tidy).sub(R.string.settings_tidy_sub).click(v -> {
            Options.setTidyNames(this, !Options.tidyNames(this));
            Groups.touch();
            states();
        });

        Rows.section(column, R.string.settings_section_history);
        Rows.Row fav = Rows.add(column, R.drawable.ic_star_line, R.string.settings_clear_fav)
                .sub(R.string.settings_clear_fav_sub);
        fav.click(v -> {
            History h = History.get(this);
            if (h != null) h.clearWatched();
            fav.state(getString(R.string.cleared), Rows.Tone.OK);
        });
        Rows.Row resume = Rows.add(column, R.drawable.ic_history, R.string.settings_clear_resume)
                .sub(R.string.settings_clear_resume_sub);
        resume.click(v -> {
            History h = History.get(this);
            if (h != null) h.clearPlayback();
            resume.state(getString(R.string.cleared), Rows.Tone.OK);
        });
        account.view.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        states();
    }

    private void groups(Xtream.Section s) {
        startActivity(new Intent(this, GroupsActivity.class).putExtra(GroupsActivity.EXTRA_SECTION, s.name()));
    }

    /** What each row says now; also after coming back from Compte, Mises à jour or the hidden channels. */
    private void states() {
        Account a = AccountStore.load(this);
        account.sub(a == null ? getString(R.string.settings_account_none) : getString(R.string.settings_account_user, a.user));
        UpdateState u = new UpdateState(this);
        Release r = u.available();
        boolean newer = r != null && r.version.isNewerThan(UpdateState.installed(this));
        updates.sub(getString(R.string.upd_installed, String.valueOf(UpdateState.installed(this))));
        updates.state(newer ? getString(R.string.upd_available, String.valueOf(r.version)) : "", Rows.Tone.OK);

        Smart.GroupOrder go = Options.groupOrder(this);
        groupOrder.sub(go == Smart.GroupOrder.SMART ? R.string.order_smart_sub
                : go == Smart.GroupOrder.AZ ? R.string.order_az_sub : R.string.order_panel_sub);
        groupOrder.state(getString(go == Smart.GroupOrder.SMART ? R.string.order_smart
                : go == Smart.GroupOrder.AZ ? R.string.order_az : R.string.order_panel), Rows.Tone.NORMAL);
        Smart.ChannelOrder co = Options.channelOrder(this);
        channelOrder.sub(co == Smart.ChannelOrder.TNT ? R.string.order_tnt_sub
                : co == Smart.ChannelOrder.AZ ? R.string.order_az_channels_sub : R.string.order_panel_channels_sub);
        channelOrder.state(getString(co == Smart.ChannelOrder.TNT ? R.string.order_tnt
                : co == Smart.ChannelOrder.AZ ? R.string.order_az : R.string.order_panel), Rows.Tone.NORMAL);
        dedupe.toggle(Options.dedupe(this));
        adult.toggle(Options.hideAdult(this));
        Channels chs = Channels.get(this);
        int hidden = chs == null ? 0 : chs.hiddenItems().size();
        hiddenChannels.sub(hidden == 0 ? getString(R.string.settings_hidden_none)
                : getResources().getQuantityString(R.plurals.settings_hidden_count, hidden, hidden));
        mini.toggle(Options.miniPlayer(this));
        next.toggle(Options.nextEpisode(this));
        tidy.toggle(Options.tidyNames(this));
    }
}
