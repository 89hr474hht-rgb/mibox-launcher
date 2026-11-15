package com.gathin.launchos.tv;

import android.content.Context;

import androidx.annotation.OptIn;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;

/**
 * The Media3 player both screens use (LivePlayer for channels, PlayerActivity for films and episodes), a playback
 * error as a sentence, and time formats. Main thread, like Media3.
 */
final class Players {
    static final int START_BUFFER_MS = 1500;
    static final int RESTART_BUFFER_MS = 3000;

    private Players() {
    }

    @OptIn(markerClass = UnstableApi.class)
    static ExoPlayer build(Context c) {
        HttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(Http.USER_AGENT)
                // Panels redirect streams to another host, often to another scheme. Media3 allows a change of scheme
                // in both directions or in none: from an http panel the only change is up to https, so it is allowed;
                // from an https panel it could only go down to http, so it is refused.
                .setAllowCrossProtocolRedirects(!secureServer(c))
                .setConnectTimeoutMs(12_000)
                .setReadTimeoutMs(20_000);
        // IPTV streams often lack clean key frames or access-unit markers; these flags make MPEG-TS start anyway.
        DefaultExtractorsFactory extractors = new DefaultExtractorsFactory()
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
                        | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
                .setConstantBitrateSeekingEnabled(true);
        DefaultMediaSourceFactory sources = new DefaultMediaSourceFactory(new DefaultDataSource.Factory(c, http), extractors);
        // The picture starts once 1.5 s is buffered (Media3's default waits for 2.5 s), and 3 s after a stall instead
        // of 5: a channel or a film shows sooner, and the 50 s kept ahead still rides out a slow panel.
        DefaultLoadControl load = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(DefaultLoadControl.DEFAULT_MIN_BUFFER_MS, DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                        START_BUFFER_MS, RESTART_BUFFER_MS)
                .build();
        ExoPlayer p = new ExoPlayer.Builder(c, new DefaultRenderersFactory(c).setEnableDecoderFallback(true))
                .setLoadControl(load)
                .setMediaSourceFactory(sources)
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(30_000)
                .build();
        p.setPlayWhenReady(true);
        return p;
    }

    /** How the picture fills the screen, as chosen in Réglages › Lecture. */
    @OptIn(markerClass = UnstableApi.class)
    static void picture(androidx.media3.ui.PlayerView v, Context c) {
        Options.Picture p = Options.picture(c);
        v.setResizeMode(p == Options.Picture.ZOOM ? androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                : p == Options.Picture.STRETCH ? androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                : androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
    }

    /** True when the saved account's server is reached over https. */
    private static boolean secureServer(Context c) {
        Account a = AccountStore.load(c);
        return a != null && a.server != null && a.server.regionMatches(true, 0, "https:", 0, 6);
    }

    /** "1:52:07" or "52:07": a position on the player's seek bar. */
    static String elapsed(long ms) {
        long t = Math.max(0, ms) / 1000;
        long h = t / 3600, m = (t % 3600) / 60, sec = t % 60;
        return h > 0 ? String.format(java.util.Locale.FRANCE, "%d:%02d:%02d", h, m, sec)
                : String.format(java.util.Locale.FRANCE, "%d:%02d", m, sec);
    }

    /**
     * What went wrong, as a plain sentence for the family, without the address (it holds the account). The error's
     * technical name goes to the log only.
     */
    static String describe(Context c, PlaybackException e) {
        Throwable cause = e.getCause();
        if (cause instanceof HttpDataSource.InvalidResponseCodeException) {
            int code = ((HttpDataSource.InvalidResponseCodeException) cause).responseCode;
            if (code == 401 || code == 403) return c.getString(R.string.play_err_refused, code);
            if (code == 404) return c.getString(R.string.play_err_gone);
            return c.getString(R.string.err_server, code);
        }
        switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
                return c.getString(R.string.err_network);
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
                return c.getString(R.string.err_timeout);
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED:
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
                return c.getString(R.string.play_err_format);
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES:
                return c.getString(R.string.play_err_decoder);
            default:
                Logs.failed("Players.describe", e);
                return c.getString(R.string.play_err_other);
        }
    }

    static String clock(long sec) {
        return new java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE).format(new java.util.Date(sec * 1000));
    }

    /** "1 h 52" or "42 min". */
    static String duration(long ms) {
        long min = Math.max(1, Math.round(ms / 60_000.0));
        if (min < 60) return min + " min";
        long m = min % 60;
        return (min / 60) + " h" + (m == 0 ? "" : " " + (m < 10 ? "0" : "") + m);
    }
}
