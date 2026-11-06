package com.gathin.launchos.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Réglages: the groups of each section, the mini player, the next episode, tidy names, and clearing history. */
public final class SettingsActivity extends Activity {
    private ViewGroup column;
    private TextView mini, next, tidy;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_refresh);
        ((TextView) findViewById(R.id.page_title)).setText(R.string.settings_title);
        ((TextView) findViewById(R.id.page_hint)).setText(R.string.settings_hint);
        column = findViewById(R.id.column);
        View first = row(R.string.settings_groups_live, R.string.settings_groups_sub, v -> groups(Xtream.Section.LIVE)).view;
        row(R.string.settings_groups_movies, R.string.settings_groups_sub, v -> groups(Xtream.Section.MOVIES));
        row(R.string.settings_groups_series, R.string.settings_groups_sub, v -> groups(Xtream.Section.SERIES));
        mini = row(R.string.settings_mini, R.string.settings_mini_sub, v -> {
            Options.setMiniPlayer(this, !Options.miniPlayer(this));
            states();
        }).state;
        next = row(R.string.settings_next, R.string.settings_next_sub, v -> {
            Options.setNextEpisode(this, !Options.nextEpisode(this));
            states();
        }).state;
        tidy = row(R.string.settings_tidy, R.string.settings_tidy_sub, v -> {
            Options.setTidyNames(this, !Options.tidyNames(this));
            Groups.touch();
            states();
        }).state;
        Row fav = row(R.string.settings_clear_fav, R.string.settings_clear_fav_sub, null);
        fav.view.setOnClickListener(v -> {
            History h = History.get(this);
            if (h != null) h.clearWatched();
            fav.state.setText(R.string.cleared);
        });
        Row resume = row(R.string.settings_clear_resume, R.string.settings_clear_resume_sub, null);
        resume.view.setOnClickListener(v -> {
            History h = History.get(this);
            if (h != null) h.clearPlayback();
            resume.state.setText(R.string.cleared);
        });
        states();
        first.requestFocus();
    }

    private void groups(Xtream.Section s) {
        startActivity(new Intent(this, GroupsActivity.class).putExtra(GroupsActivity.EXTRA_SECTION, s.name()));
    }

    private void states() {
        mini.setText(Options.miniPlayer(this) ? R.string.on : R.string.off);
        next.setText(Options.nextEpisode(this) ? R.string.on : R.string.off);
        tidy.setText(Options.tidyNames(this) ? R.string.on : R.string.off);
    }

    private static final class Row {
        final View view;
        final TextView state;

        Row(View view) {
            this.view = view;
            state = view.findViewById(R.id.state);
        }
    }

    private Row row(int title, int sub, View.OnClickListener click) {
        View v = LayoutInflater.from(this).inflate(R.layout.item_refresh, column, false);
        ((TextView) v.findViewById(R.id.title)).setText(title);
        ((TextView) v.findViewById(R.id.sub)).setText(sub);
        Ui.lift(v, 1.02f);
        if (click != null) v.setOnClickListener(click);
        column.addView(v);
        return new Row(v);
    }
}
