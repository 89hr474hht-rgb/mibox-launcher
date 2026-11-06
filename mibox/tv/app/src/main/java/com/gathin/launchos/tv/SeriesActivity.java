package com.gathin.launchos.tv;

import android.app.Activity;
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

/** One series: its cover and summary, the seasons across the top, and the chosen season's episodes. */
public final class SeriesActivity extends Activity {
    static final String EXTRA_ID = "series_id";
    static final String EXTRA_NAME = "series_name";
    static final String EXTRA_COVER = "series_cover";

    private Xtream api;
    private String seriesName;
    private List<Xtream.Season> seasons = new ArrayList<>();
    private int season;
    private RecyclerView seasonsView, episodesView;
    private TextView status;
    private final SeasonAdapter seasonAdapter = new SeasonAdapter();
    private final EpisodeAdapter episodeAdapter = new EpisodeAdapter();

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
        seriesName = getIntent().getStringExtra(EXTRA_NAME);
        ((TextView) findViewById(R.id.title)).setText(seriesName);
        ImageView cover = findViewById(R.id.cover);
        Ui.roundClip(findViewById(R.id.cover_frame));
        Images.load(cover, getIntent().getStringExtra(EXTRA_COVER), Ui.dp(this, 170));
        status = findViewById(R.id.status);
        seasonsView = findViewById(R.id.seasons);
        episodesView = findViewById(R.id.episodes);
        seasonsView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        seasonsView.setAdapter(seasonAdapter);
        seasonsView.setItemAnimator(null);
        episodesView.setLayoutManager(new LinearLayoutManager(this));
        episodesView.setAdapter(episodeAdapter);
        episodesView.setItemAnimator(null);

        status.setText(R.string.loading);
        status.setVisibility(View.VISIBLE);
        String id = getIntent().getStringExtra(EXTRA_ID);
        Bg.run(() -> api.seriesInfo(id), (info, error) -> {
            if (isDestroyed()) return;
            if (error != null) {
                status.setText(error.french());
                return;
            }
            if (!info.plot.isEmpty()) ((TextView) findViewById(R.id.plot)).setText(info.plot);
            seasons = info.seasons;
            if (seasons.isEmpty()) {
                status.setText(R.string.empty);
                return;
            }
            status.setVisibility(View.GONE);
            seasonAdapter.notifyItemRangeInserted(0, seasons.size());
            showSeason(0);
            episodesView.post(() -> episodesView.requestFocus());
        });
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
            final TextView number, title, plot;

            Holder(View v) {
                super(v);
                image = v.findViewById(R.id.image);
                number = v.findViewById(R.id.number);
                title = v.findViewById(R.id.title);
                plot = v.findViewById(R.id.plot);
                Ui.roundClip(v.findViewById(R.id.frame));
                Ui.lift(v, 1.02f);
            }
        }

        @SuppressWarnings("NotifyDataSetChanged") // the whole list changes with the season
        void set(List<Item> list) {
            items = list;
            notifyDataSetChanged();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int type) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_episode, parent, false));
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            Item e = items.get(position);
            h.number.setText(String.valueOf(e.num > 0 ? e.num : position + 1));
            h.title.setText(e.name);
            h.plot.setText(e.plot);
            h.plot.setVisibility(e.plot.isEmpty() ? View.GONE : View.VISIBLE);
            Images.load(h.image, e.image, Ui.dp(h.itemView.getContext(), 112));
            h.itemView.setOnClickListener(v -> startActivity(new Intent(SeriesActivity.this, PlayerActivity.class)
                    .putExtra(PlayerActivity.EXTRA_URL, api.episodeUrl(e))
                    .putExtra(PlayerActivity.EXTRA_TITLE, seriesName + " – " + e.name)));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
