package com.gathin.launchos.tv;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.ui.PlayerView;

import java.util.List;

/**
 * Full-screen playback with Media3. A live channel shows its name for a few seconds and zaps with CH+/CH− or
 * Up/Down inside its category; a film or an episode has the usual controls (OK, then Left/Right to seek).
 * A live channel is asked for as MPEG-TS first, then as HLS if that fails.
 */
public final class PlayerActivity extends Activity {
    static final String EXTRA_URL = "url";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_LIVE_INDEX = "live_index";
    private static final long INFO_MS = 4000;

    private ExoPlayer player;
    private PlayerView view;
    private View info, error;
    private TextView infoNumber, infoTitle, infoSub, errorText;
    private Xtream api;

    private boolean live;
    private List<Item> channels;
    private int index;
    private boolean triedHls;
    private int liveRetries;
    private final Runnable hideInfo = () -> info.animate().alpha(0f).setDuration(250)
            .withEndAction(() -> info.setVisibility(View.GONE)).start();

    @OptIn(markerClass = UnstableApi.class)
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_player);
        view = findViewById(R.id.player);
        info = findViewById(R.id.info);
        error = findViewById(R.id.error);
        infoNumber = findViewById(R.id.info_number);
        infoTitle = findViewById(R.id.info_title);
        infoSub = findViewById(R.id.info_sub);
        errorText = findViewById(R.id.error_text);
        Account a = AccountStore.load(this);
        if (a == null) {
            finish();
            return;
        }
        api = new Xtream(a);
        index = getIntent().getIntExtra(EXTRA_LIVE_INDEX, -1);
        channels = Playlist.channels;
        live = index >= 0 && index < channels.size();
        view.setUseController(!live);
        // One title at a time: no previous/next. Seeking steps are 10 s back and 30 s forward.
        view.setShowPreviousButton(false);
        view.setShowNextButton(false);
        if (state != null) index = state.getInt(EXTRA_LIVE_INDEX, index);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(EXTRA_LIVE_INDEX, index);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (api == null) return;
        player = build();
        view.setPlayer(player);
        if (live) playChannel(index);
        else play(getIntent().getStringExtra(EXTRA_URL), null);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            view.setPlayer(null);
            player.release();
            player = null;
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private ExoPlayer build() {
        HttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(Http.USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(12_000)
                .setReadTimeoutMs(20_000);
        // IPTV streams often lack clean key frames or access-unit markers; these flags make MPEG-TS start anyway.
        DefaultExtractorsFactory extractors = new DefaultExtractorsFactory()
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
                        | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
                .setConstantBitrateSeekingEnabled(true);
        DefaultMediaSourceFactory sources = new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, http), extractors);
        ExoPlayer p = new ExoPlayer.Builder(this, new DefaultRenderersFactory(this).setEnableDecoderFallback(true))
                .setMediaSourceFactory(sources)
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(30_000)
                .build();
        p.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException e) {
                failed(e);
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && live) {
                    liveRetries = 0;
                    infoSub.setText(Playlist.category);
                }
            }
        });
        p.setPlayWhenReady(true);
        return p;
    }

    private void play(String url, String mime) {
        if (player == null || url == null) return;
        error.setVisibility(View.GONE);
        MediaItem.Builder b = new MediaItem.Builder().setUri(Uri.parse(url));
        if (mime != null) b.setMimeType(mime);
        if (live) b.setLiveConfiguration(new MediaItem.LiveConfiguration.Builder().build());
        player.setMediaItem(b.build());
        player.prepare();
    }

    private void playChannel(int i) {
        index = i;
        triedHls = false;
        liveRetries = 0;
        Item c = channels.get(i);
        play(api.liveUrl(c, false), MimeTypes.VIDEO_MP2T);
        infoNumber.setText(c.num > 0 ? String.valueOf(c.num) : "");
        infoTitle.setText(c.name);
        infoSub.setText(Playlist.category);
        showInfo();
    }

    private void showInfo() {
        info.removeCallbacks(hideInfo);
        info.animate().cancel();
        info.setAlpha(1f);
        info.setVisibility(View.VISIBLE);
        info.postDelayed(hideInfo, INFO_MS);
    }

    private void failed(PlaybackException e) {
        if (live) {
            // A live stream that drops is retried twice, then asked for as HLS once.
            if (liveRetries < 2) {
                liveRetries++;
                infoSub.setText(R.string.retrying);
                showInfo();
                view.postDelayed(() -> {
                    if (player != null) {
                        player.seekToDefaultPosition();
                        player.prepare();
                    }
                }, 1200);
                return;
            }
            if (!triedHls) {
                triedHls = true;
                liveRetries = 0;
                infoSub.setText(R.string.retrying_hls);
                showInfo();
                play(api.liveUrl(channels.get(index), true), MimeTypes.APPLICATION_M3U8);
                return;
            }
        }
        errorText.setText(describe(e));
        error.setVisibility(View.VISIBLE);
    }

    /** What went wrong, without the address (it holds the account). */
    private static String describe(PlaybackException e) {
        Throwable c = e.getCause();
        if (c instanceof HttpDataSource.InvalidResponseCodeException) {
            int code = ((HttpDataSource.InvalidResponseCodeException) c).responseCode;
            if (code == 401 || code == 403) {
                return "Le serveur refuse ce flux (code " + code + "). Une autre lecture est peut-être en cours sur ce compte.";
            }
            if (code == 404) return "Ce contenu n'existe plus sur le serveur (code 404).";
            return "Le serveur a répondu avec une erreur (code " + code + ").";
        }
        switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
                return "Impossible de joindre le serveur.";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
                return "Le serveur ne répond pas (délai dépassé).";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED:
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
                return "Format du flux non reconnu.";
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES:
                return "La box ne sait pas décoder ce format vidéo ou audio.";
            default:
                return "Erreur de lecture (" + e.getErrorCodeName() + ").";
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int k = event.getKeyCode();
            if (error.getVisibility() == View.VISIBLE
                    && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
                if (live) playChannel(index);
                else {
                    error.setVisibility(View.GONE);
                    if (player != null) player.prepare();
                }
                return true;
            }
            if (live) {
                int n = channels.size();
                if (k == KeyEvent.KEYCODE_CHANNEL_UP || k == KeyEvent.KEYCODE_DPAD_UP) {
                    playChannel((index + 1) % n);
                    return true;
                }
                if (k == KeyEvent.KEYCODE_CHANNEL_DOWN || k == KeyEvent.KEYCODE_DPAD_DOWN) {
                    playChannel((index - 1 + n) % n);
                    return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_INFO) {
                    showInfo();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
