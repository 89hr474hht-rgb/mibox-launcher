package com.gathin.launchos.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * A film: its poster blurred behind the page; on the left its year, length, genre, summary and people, on the right
 * the poster and Lecture or Reprendre, Depuis le début, and the trailer when the panel gives one.
 */
public final class MovieActivity extends Activity {
    private static final String EXTRA_ID = "id", EXTRA_NAME = "name", EXTRA_IMAGE = "image", EXTRA_EXT = "ext",
            EXTRA_CATEGORY = "category";

    private Xtream api;
    private Item film;
    private TextView meta, plot, people, play, restart, trailer;
    private ImageView blur, poster;
    private View plotSkeleton, posterSkeleton;
    private String trailerId = "";

    static Intent intent(Context c, Item film) {
        return new Intent(c, MovieActivity.class).putExtra(EXTRA_ID, film.id).putExtra(EXTRA_NAME, film.name)
                .putExtra(EXTRA_IMAGE, film.image).putExtra(EXTRA_EXT, film.ext).putExtra(EXTRA_CATEGORY, film.categoryId);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_movie);
        Account a = AccountStore.load(this);
        if (a == null) {
            finish();
            return;
        }
        api = new Xtream(a);
        Intent in = getIntent();
        film = Library.of(Xtream.Section.MOVIES).find(in.getStringExtra(EXTRA_ID));
        if (film == null) {
            film = new Item(Item.Kind.MOVIE, in.getStringExtra(EXTRA_ID), in.getStringExtra(EXTRA_NAME),
                    in.getStringExtra(EXTRA_IMAGE), in.getStringExtra(EXTRA_EXT), 0, "", in.getStringExtra(EXTRA_CATEGORY),
                    0, 0f, "", "", 0, 0);
        }
        blur = findViewById(R.id.blur);
        poster = findViewById(R.id.poster);
        posterSkeleton = findViewById(R.id.poster_skeleton);
        plotSkeleton = findViewById(R.id.plot_skeleton);
        trailer = findViewById(R.id.trailer);
        Ui.roundClip(findViewById(R.id.poster_frame));
        ((TextView) findViewById(R.id.title)).setText(film.title);
        meta = findViewById(R.id.meta);
        plot = findViewById(R.id.plot);
        people = findViewById(R.id.people);
        play = findViewById(R.id.play);
        restart = findViewById(R.id.restart);
        ((TextView) findViewById(R.id.label)).setText(groupName());
        fill(Details.cached(film.id));
        Details.get(api, film.id, info -> {
            if (!isDestroyed()) fill(info);
        });
        play.setOnClickListener(v -> start(position()));
        restart.setOnClickListener(v -> start(0));
        trailer.setOnClickListener(v -> openTrailer());
        Ui.lift(play, 1.05f);
        Ui.lift(restart, 1.05f);
        Ui.lift(trailer, 1.05f);
        play.requestFocus();
    }

    private String groupName() {
        Library lib = Library.of(Xtream.Section.MOVIES);
        Groups g = Groups.get(this);
        if (lib.categories() != null) {
            for (Xtream.Category c : lib.categories()) {
                if (c.id.equals(film.categoryId)) return g == null ? Groups.panelName(Xtream.Section.MOVIES, c) : g.name(Xtream.Section.MOVIES, c);
            }
        }
        return getString(R.string.nav_movies);
    }

    private void fill(Xtream.VodInfo vi) {
        String genre = vi != null ? vi.genre : "";
        int year = film.year != 0 ? film.year : vi != null ? vi.year : 0;
        float rating = film.rating > 0 ? film.rating : vi != null ? vi.rating : 0;
        String length = vi != null && vi.seconds > 0 ? Players.duration(vi.seconds * 1000L) : "";
        meta.setText(Ui.meta(year > 0 ? String.valueOf(year) : "", length, genre, Ui.rating(rating), film.tag));
        String summary = vi != null ? vi.plot : "";
        plot.setText(summary);
        plot.setVisibility(summary.isEmpty() ? View.GONE : View.VISIBLE);
        // The sheet is still on its way: a skeleton holds the summary's place.
        plotSkeleton.setVisibility(vi == null ? View.VISIBLE : View.GONE);
        StringBuilder p = new StringBuilder();
        if (vi != null && !vi.director.isEmpty()) p.append(getString(R.string.detail_director, vi.director));
        if (vi != null && !vi.cast.isEmpty()) {
            if (p.length() > 0) p.append('\n');
            p.append(getString(R.string.detail_cast, vi.cast));
        }
        people.setText(p);
        people.setVisibility(p.length() == 0 ? View.GONE : View.VISIBLE);
        String picture = vi != null && !vi.poster.isEmpty() ? vi.poster : film.image;
        if (!picture.isEmpty() && !picture.equals(poster.getTag(R.id.image_url))) {
            Images.load(poster, picture, Ui.dp(this, 140), null, posterSkeleton);
            Blur.into(blur, picture, 0.85f);
        } else if (picture.isEmpty() && vi != null) {
            posterSkeleton.setVisibility(View.GONE);
        }
        trailerId = vi == null ? "" : vi.trailer;
        trailer.setVisibility(trailerId.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** The trailer opens in YouTube (the panel gives a YouTube id, sometimes a whole address). */
    private void openTrailer() {
        if (trailerId.isEmpty()) return;
        String url = trailerId.matches("(?i)^https?://.*") ? trailerId
                : "https://www.youtube.com/watch?v=" + Uri.encode(trailerId);
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            GlassToast.show(this, getString(R.string.detail_trailer_none), false);
        }
    }

    private long position() {
        History h = History.get(this);
        return h == null ? 0 : h.position(Item.Kind.MOVIE, film.id);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (film == null) return;
        long at = position();
        play.setText(at > 0 ? getString(R.string.detail_resume, Players.duration(at)) : getString(R.string.detail_play));
        restart.setVisibility(at > 0 ? View.VISIBLE : View.GONE);
    }

    private void start(long at) {
        startActivity(PlayerActivity.film(this, api, film, at));
    }
}
