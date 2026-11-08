package com.gathin.launchos.tv;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Rational;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Films and episodes, full screen, with LaunchTV OS's own display ({@link Osd}): title and time at the top, a thin
 * seek bar, the controls and audio, subtitles and picture-in-picture at the bottom, hidden after 3 s. Left and
 * Right seek 10 s back or 30 s on.
 * Where it was stopped is remembered for Reprendre; at the end of an episode the next one starts (Réglages).
 * Live channels play in the main screen instead ({@link LivePlayer}).
 */
public final class PlayerActivity extends Activity {
    private static final String EXTRA_URL = "url";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_START = "start_ms";
    private static final String EXTRA_KIND = "kind";
    private static final String EXTRA_ID = "id";
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_IMAGE = "image";
    private static final String EXTRA_EXT = "ext";
    private static final String EXTRA_SERIES = "series_id";
    private static final String EXTRA_SERIES_NAME = "series_name";
    private static final String EXTRA_COVER = "cover";
    private static final String EXTRA_SEASON = "season";
    private static final String EXTRA_EPISODE_INDEX = "episode_index";
    private static final long SAVE_MS = 15_000;

    /** The season the episode was started from, so the next one can follow. */
    static final class Episodes {
        static List<Item> list = new ArrayList<>();

        private Episodes() {
        }
    }

    private ExoPlayer player;
    private PlayerView view;
    private View error;
    private TextView errorText, toast;
    private Xtream api;
    private Osd osd;
    private Item item;
    private String seriesId, seriesName, cover;
    private int season, episodeIndex = -1;
    private long startAt;
    private final Runnable save = new Runnable() {
        @Override
        public void run() {
            savePosition();
            view.postDelayed(this, SAVE_MS);
        }
    };

    static Intent film(Context c, Xtream api, Item film, long startMs) {
        return base(c, film, api.movieUrl(film), film.title, startMs);
    }

    static Intent episode(Context c, Xtream api, String seriesId, String seriesName, String cover, int season,
                          List<Item> episodes, int index, long startMs) {
        Item e = episodes.get(index);
        Episodes.list = new ArrayList<>(episodes);
        return base(c, e, api.episodeUrl(e), seriesName + " – " + e.title, startMs)
                .putExtra(EXTRA_SERIES, seriesId).putExtra(EXTRA_SERIES_NAME, seriesName).putExtra(EXTRA_COVER, cover)
                .putExtra(EXTRA_SEASON, season).putExtra(EXTRA_EPISODE_INDEX, index);
    }

    private static Intent base(Context c, Item i, String url, String title, long startMs) {
        return new Intent(c, PlayerActivity.class).putExtra(EXTRA_URL, url).putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_START, startMs).putExtra(EXTRA_KIND, i.kind.name()).putExtra(EXTRA_ID, i.id)
                .putExtra(EXTRA_NAME, i.name).putExtra(EXTRA_IMAGE, i.image).putExtra(EXTRA_EXT, i.ext);
    }

    @Override
    @OptIn(markerClass = UnstableApi.class)
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_player);
        view = findViewById(R.id.player);
        error = findViewById(R.id.error);
        errorText = findViewById(R.id.error_text);
        toast = findViewById(R.id.toast);
        Account a = AccountStore.load(this);
        Intent in = getIntent();
        if (a == null || in.getStringExtra(EXTRA_URL) == null) {
            finish();
            return;
        }
        api = new Xtream(a);
        Item.Kind kind = Item.Kind.valueOf(in.getStringExtra(EXTRA_KIND));
        item = new Item(kind, in.getStringExtra(EXTRA_ID), in.getStringExtra(EXTRA_NAME), in.getStringExtra(EXTRA_IMAGE),
                in.getStringExtra(EXTRA_EXT), 0, "");
        seriesId = in.getStringExtra(EXTRA_SERIES);
        seriesName = in.getStringExtra(EXTRA_SERIES_NAME);
        cover = in.getStringExtra(EXTRA_COVER);
        season = in.getIntExtra(EXTRA_SEASON, 0);
        episodeIndex = in.getIntExtra(EXTRA_EPISODE_INDEX, -1);
        startAt = state != null ? state.getLong(EXTRA_START) : in.getLongExtra(EXTRA_START, 0);
        if (seriesId != null) remember();
        osd = new Osd(this, new Osd.Host() {
            @Override
            public boolean hasNext() {
                return seriesId != null && episodeIndex >= 0 && episodeIndex + 1 < Episodes.list.size();
            }

            @Override
            public void next() {
                if (hasNext()) nextEpisode();
            }

            @Override
            public boolean canPip() {
                return getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
            }

            @Override
            public void pip() {
                try {
                    enterPictureInPictureMode(new PictureInPictureParams.Builder()
                            .setAspectRatio(new Rational(16, 9)).build());
                } catch (IllegalStateException | IllegalArgumentException e) {
                    GlassToast.show(PlayerActivity.this, getString(R.string.osd_pip_none), false);
                }
            }
        });
        titles();
    }

    /** The display's title: the film, or the series with the episode under it. */
    private void titles() {
        if (seriesId == null) {
            osd.setTitle(item.title, "");
        } else {
            osd.setTitle(seriesName, getString(R.string.osd_episode, season, item.title));
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPip, newConfig);
        if (inPip) osd.hide();
        else osd.show(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong(EXTRA_START, player != null ? player.getCurrentPosition() : startAt);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (api == null) return;
        player = Players.build(this);
        view.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException e) {
                errorText.setText(Players.describe(e));
                error.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) error.setVisibility(View.GONE);
                if (state == Player.STATE_ENDED) ended();
            }
        });
        play(getIntent().getStringExtra(EXTRA_URL), startAt);
        osd.attach(player);
        view.postDelayed(save, SAVE_MS);
    }

    @Override
    protected void onStop() {
        super.onStop();
        view.removeCallbacks(save);
        if (osd != null) osd.detach();
        if (player != null) {
            savePosition();
            startAt = player.getCurrentPosition();
            view.setPlayer(null);
            player.release();
            player = null;
        }
    }

    private void play(String url, long at) {
        error.setVisibility(View.GONE);
        player.setMediaItem(new MediaItem.Builder().setUri(Uri.parse(url)).build(), Math.max(0, at));
        player.prepare();
    }

    private void savePosition() {
        if (player == null || item == null) return;
        long pos = player.getCurrentPosition(), dur = player.getDuration();
        if (pos <= 0) return;
        History h = History.get(this);
        if (h == null) return;
        h.position(item, pos, dur > 0 ? dur : 0, seriesId);
        if (seriesId != null && dur > 0 && pos >= History.RESUME_MIN_MS && !History.worthResuming(pos, dur)) {
            h.episodeDone(seriesId, item.id);
        }
    }

    /** The series remembers this episode, for Reprendre. */
    private void remember() {
        History h = History.get(this);
        if (h != null) h.episode(seriesId, seriesName, cover, item, season);
    }

    private void ended() {
        History h = History.get(this);
        if (h != null) {
            h.finished(item);   // no Reprendre for it
            if (seriesId != null) h.episodeDone(seriesId, item.id);
        }
        List<Item> eps = Episodes.list;
        if (seriesId == null || !Options.nextEpisode(this) || episodeIndex < 0 || episodeIndex + 1 >= eps.size()) {
            finish();
            return;
        }
        nextEpisode();
    }

    /** The next episode of the season, at the end of one or from the display's button. */
    private void nextEpisode() {
        List<Item> eps = Episodes.list;
        savePosition();
        episodeIndex++;
        Item next = eps.get(episodeIndex);
        item = next;
        remember();
        toast.setText(getString(R.string.next_episode, next.title));
        toast.setVisibility(View.VISIBLE);
        toast.setAlpha(0f);
        toast.animate().alpha(1f).setDuration(250).start();
        toast.postDelayed(() -> toast.animate().alpha(0f).setDuration(400)
                .withEndAction(() -> toast.setVisibility(View.GONE)).start(), 4000);
        play(api.episodeUrl(next), 0);
        titles();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && error.getVisibility() == View.VISIBLE) {
            int k = event.getKeyCode();
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
                error.setVisibility(View.GONE);
                if (player != null) player.prepare();
                return true;
            }
        }
        if (osd != null && error.getVisibility() != View.VISIBLE && osd.key(event)) return true;
        return super.dispatchKeyEvent(event);
    }
}
