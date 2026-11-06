package com.gathin.launchos.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * One series: its picture full screen, its summary, Regarder (or Reprendre where it was left), the seasons, and the
 * chosen season's episodes side by side. Moving along the seasons shows each one's episodes.
 */
public final class SeriesActivity extends Activity {
    private static final String EXTRA_ID = "series_id", EXTRA_NAME = "series_name", EXTRA_COVER = "series_cover",
            EXTRA_PLAY = "play";

    private Xtream api;
    private Item series;
    private List<Xtream.Season> seasons = new ArrayList<>();
    private int season;
    private RecyclerView seasonsView, episodesView;
    private TextView status, play, meta, plot;
    private Backdrop backdrop;
    private final SeasonAdapter seasonAdapter = new SeasonAdapter();
    private final EpisodeAdapter episodeAdapter = new EpisodeAdapter();
    private Xtream.SeriesInfo info;

    static Intent intent(Context c, Item s, boolean play) {
        return new Intent(c, SeriesActivity.class).putExtra(EXTRA_ID, s.id).putExtra(EXTRA_NAME, s.name)
                .putExtra(EXTRA_COVER, s.image).putExtra(EXTRA_PLAY, play);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_series);
        Account a = AccountStore.load(this);
        if (a == null) {
            finish();
            return;
        }
        api = new Xtream(a);
        Intent in = getIntent();
        String id = in.getStringExtra(EXTRA_ID);
        series = Library.of(Xtream.Section.SERIES).find(id);
        if (series == null) {
            series = new Item(Item.Kind.SERIES, id, in.getStringExtra(EXTRA_NAME), in.getStringExtra(EXTRA_COVER), "", 0, "");
        }
        backdrop = new Backdrop(findViewById(R.id.backdrop_a), findViewById(R.id.backdrop_b));
        backdrop.show(series.backdrop, series.image);
        ((TextView) findViewById(R.id.title)).setText(series.title);
        ((TextView) findViewById(R.id.label)).setText(series.genre.isEmpty() ? getString(R.string.nav_series) : series.genre);
        meta = findViewById(R.id.meta);
        plot = findViewById(R.id.plot);
        plot.setText(series.plot);
        meta.setText(Ui.meta(series.year > 0 ? String.valueOf(series.year) : "", Ui.rating(series.rating)));
        status = findViewById(R.id.status);
        play = findViewById(R.id.play);
        seasonsView = findViewById(R.id.seasons);
        episodesView = findViewById(R.id.episodes);
        seasonsView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        seasonsView.setAdapter(seasonAdapter);
        seasonsView.setItemAnimator(null);
        episodesView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        episodesView.setAdapter(episodeAdapter);
        episodesView.setItemAnimator(null);
        play.setOnClickListener(v -> playNext());

        status.setText(R.string.loading);
        status.setVisibility(View.VISIBLE);
        boolean autoplay = in.getBooleanExtra(EXTRA_PLAY, false);
        Bg.run(() -> api.seriesInfo(id), (i, error) -> {
            if (isDestroyed()) return;
            if (error != null) {
                status.setText(error.french());
                return;
            }
            info = i;
            if (!i.plot.isEmpty()) plot.setText(i.plot);
            backdrop.show(i.backdrop, series.backdrop, i.cover, series.image);
            seasons = i.seasons;
            int year = series.year > 0 ? series.year : i.year;
            float rating = series.rating > 0 ? series.rating : i.rating;
            meta.setText(Ui.meta(year > 0 ? String.valueOf(year) : "",
                    seasons.size() == 1 ? "1 saison" : seasons.size() > 1 ? seasons.size() + " saisons" : "",
                    Ui.rating(rating), i.genre.isEmpty() ? series.genre : i.genre));
            if (seasons.isEmpty()) {
                status.setText(R.string.empty);
                return;
            }
            status.setVisibility(View.GONE);
            int[] last = last();
            seasonAdapter.notifyItemRangeInserted(0, seasons.size());
            showSeason(last[0]);
            episodesView.scrollToPosition(last[1]);
            updatePlay();
            play.setVisibility(View.VISIBLE);
            play.requestFocus();
            if (autoplay) playNext();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!seasons.isEmpty()) {
            updatePlay();
            episodeAdapter.notifyItemRangeChanged(0, episodeAdapter.getItemCount());
        }
    }

    /** The season and episode index where the series was left, or the first one. */
    private int[] last() {
        History h = History.get(this);
        String[] l = h == null ? null : h.lastEpisode(series.id);
        if (l != null) {
            for (int s = 0; s < seasons.size(); s++) {
                List<Item> eps = seasons.get(s).episodes;
                for (int e = 0; e < eps.size(); e++) {
                    if (eps.get(e).id.equals(l[0])) {
                        // Seen to the end: the next one.
                        if (l[3].equals("1")) {
                            if (e + 1 < eps.size()) return new int[]{s, e + 1};
                            if (s + 1 < seasons.size()) return new int[]{s + 1, 0};
                        }
                        return new int[]{s, e};
                    }
                }
            }
        }
        return new int[]{0, 0};
    }

    private void updatePlay() {
        History h = History.get(this);
        int[] at = last();
        Xtream.Season s = seasons.get(at[0]);
        Item e = s.episodes.isEmpty() ? null : s.episodes.get(Math.min(at[1], s.episodes.size() - 1));
        boolean resume = e != null && h != null && h.position(Item.Kind.EPISODE, e.id) > 0;
        int number = e == null ? 1 : e.num > 0 ? e.num : at[1] + 1;
        play.setText(getString(resume ? R.string.series_resume : R.string.series_watch, s.number, number));
    }

    private void playNext() {
        if (seasons.isEmpty()) return;
        int[] at = last();
        play(at[0], at[1]);
    }

    private void play(int s, int e) {
        Xtream.Season sea = seasons.get(s);
        if (sea.episodes.isEmpty()) return;
        Item ep = sea.episodes.get(Math.min(e, sea.episodes.size() - 1));
        History h = History.get(this);
        long pos = h == null ? 0 : h.position(Item.Kind.EPISODE, ep.id);
        String cover = info != null && !info.cover.isEmpty() ? info.cover : series.image;
        startActivity(PlayerActivity.episode(this, api, series.id, series.name, cover, sea.number, sea.episodes,
                sea.episodes.indexOf(ep), pos));
    }

    private void showSeason(int index) {
        season = index;
        for (int i = 0; i < seasonsView.getChildCount(); i++) {
            View chip = seasonsView.getChildAt(i);
            chip.setSelected(seasonsView.getChildAdapterPosition(chip) == index);
        }
        episodeAdapter.set(seasons.get(index).episodes);
        episodesView.scrollToPosition(0);
    }

    private final class SeasonAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_season, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int position) {
            TextView t = (TextView) h.itemView;
            t.setText(seasons.get(position).name);
            t.setSelected(position == season);
            t.setOnFocusChangeListener((v, focused) -> {
                int p = h.getBindingAdapterPosition();
                if (focused && p != season && p != RecyclerView.NO_POSITION) showSeason(p);
            });
            t.setOnClickListener(v -> episodesView.requestFocus());
        }

        @Override
        public int getItemCount() {
            return seasons.size();
        }
    }

    private final class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.Holder> {
        private List<Item> items = new ArrayList<>();

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView number, title, plot, badge;
            final View track, bar;

            Holder(View v) {
                super(v);
                image = v.findViewById(R.id.image);
                number = v.findViewById(R.id.number);
                title = v.findViewById(R.id.title);
                plot = v.findViewById(R.id.plot);
                badge = v.findViewById(R.id.badge);
                track = v.findViewById(R.id.track);
                bar = v.findViewById(R.id.bar);
                Ui.roundClip(v.findViewById(R.id.clip));
                v.setOnFocusChangeListener((view, focused) -> {
                    View frame = view.findViewById(R.id.frame);
                    float s = focused ? 1.06f : 1f;
                    frame.setPivotY(frame.getHeight());
                    frame.animate().scaleX(s).scaleY(s).setDuration(170).start();
                });
            }
        }

        @SuppressWarnings("NotifyDataSetChanged") // the whole list changes with the season
        void set(List<Item> list) {
            items = list;
            notifyDataSetChanged();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int type) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_episode_card, parent, false));
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            Item e = items.get(position);
            int n = e.num > 0 ? e.num : position + 1;
            h.number.setText(String.valueOf(n));
            h.title.setText(getString(R.string.episode_title, n, Names.episode(series.title, e.title)));
            String length = e.seconds > 0 ? Players.duration(e.seconds * 1000L) : "";
            h.plot.setText(Ui.meta(length, e.plot));
            Images.load(h.image, e.image, Ui.dp(h.itemView.getContext(), 224));
            History hist = History.get(SeriesActivity.this);
            History.Entry r = hist == null ? null : hist.resumeEntry(Item.Kind.EPISODE, e.id);
            boolean started = r != null && r.duration > 0;
            h.track.setVisibility(started ? View.VISIBLE : View.GONE);
            if (started) {
                h.bar.setPivotX(0);
                h.bar.setScaleX(Math.max(0.04f, Math.min(1f, r.position / (float) r.duration)));
            }
            h.badge.setVisibility(View.GONE);
            h.itemView.setOnClickListener(v -> play(season, h.getBindingAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
