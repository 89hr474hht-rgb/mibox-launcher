package com.gathin.launchos.tv;

import android.content.Context;

import androidx.annotation.OptIn;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;

/** The Media3 player both screens use, and what a playback error means in French. */
final class Players {
    private Players() {
    }

    @OptIn(markerClass = UnstableApi.class)
    static ExoPlayer build(Context c) {
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
        DefaultMediaSourceFactory sources = new DefaultMediaSourceFactory(new DefaultDataSource.Factory(c, http), extractors);
        ExoPlayer p = new ExoPlayer.Builder(c, new DefaultRenderersFactory(c).setEnableDecoderFallback(true))
                .setMediaSourceFactory(sources)
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(30_000)
                .build();
        p.setPlayWhenReady(true);
        return p;
    }

    /** "1:52:07" or "52:07": a position on the player's seek bar. */
    static String elapsed(long ms) {
        long t = Math.max(0, ms) / 1000;
        long h = t / 3600, m = (t % 3600) / 60, sec = t % 60;
        return h > 0 ? String.format(java.util.Locale.FRANCE, "%d:%02d:%02d", h, m, sec)
                : String.format(java.util.Locale.FRANCE, "%d:%02d", m, sec);
    }

    /** What went wrong, without the address (it holds the account). */
    static String describe(PlaybackException e) {
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
