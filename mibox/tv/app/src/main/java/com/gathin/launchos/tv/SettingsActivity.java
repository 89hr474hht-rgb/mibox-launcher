package com.gathin.launchos.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

/** Réglages: the groups of each section, the mini player, the next episode, tidy names, and clearing history. */
public final class SettingsActivity extends Activity {
    private ViewGroup column;
    private Rows.Row mini, next, tidy;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_refresh);
        Rows.header(this, R.drawable.ic_settings, R.string.settings_title, R.string.settings_hint);
        column = findViewById(R.id.column);

        Rows.section(column, R.string.settings_section_groups);
        View first = Rows.add(column, R.drawable.ic_live, R.string.settings_groups_live).sub(R.string.settings_groups_sub)
                .opens().click(v -> groups(Xtream.Section.LIVE)).view;
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
        states();
        first.requestFocus();
    }

    private void groups(Xtream.Section s) {
        startActivity(new Intent(this, GroupsActivity.class).putExtra(GroupsActivity.EXTRA_SECTION, s.name()));
    }

    private void states() {
        mini.toggle(Options.miniPlayer(this));
        next.toggle(Options.nextEpisode(this));
        tidy.toggle(Options.tidyNames(this));
    }
}
