package com.gathin.launchos.tv;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Rect;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Live TV inside the main screen, so a channel can shrink into the small window at the bottom right of Chaînes and
 * grow back without stopping. Full screen: Up/Down (or CH+/CH−) zap inside the list it was started from, OK shows
 * what is on, Back shrinks it (or stops it when the mini player is off). A stream that drops is tried again, then
 * as HLS. The time spent on each channel feeds the favourites.
 */
final class LivePlayer {
    enum Mode { HIDDEN, FULL, MINI }

    interface Host {
        /** Where the small window goes, in the root's coordinates; null when the page is not shown. */
        Rect miniBounds();

        void modeChanged(Mode mode);

        void channelChanged(Item channel);
    }

    private static final long INFO_MS = 4500;
    private static final long FLUSH_MS = 30_000;

    private final Activity activity;
    private final FrameLayout layer;
    private final PlayerView video;
    private final View info, error, frame;
    private final TextView infoNumber, infoTitle, infoSub, infoNow, infoNext, errorText, caption, infoInitials, infoKeys;
    private final ImageView infoLogo;
    private final View infoGuide, infoBar, infoTrack;
    private final Host host;

    private ExoPlayer player;
    private Xtream api;
    private Mode mode = Mode.HIDDEN;
    private List<Item> channels = new ArrayList<>();
    private int index;
    private String group = "";
    private boolean triedHls;
    private int retries;
    private Epg.Listing guide;
    private String guideFor;
    private ValueAnimator move;
    /** Viewing time not yet counted: since when the picture has been playing (0 when it is not). */
    private long playingSince;
    private final Runnable hideInfo;
    private final Runnable flush = new Runnable() {
        @Override
        public void run() {
            countViewing();
            if (playingSince > 0) playingSince = System.currentTimeMillis();
            layer.postDelayed(this, FLUSH_MS);
        }
    };

    LivePlayer(Activity activity, FrameLayout layer, Host host) {
        this.activity = activity;
        this.layer = layer;
        this.host = host;
        video = layer.findViewById(R.id.live_video);
        info = layer.findViewById(R.id.info);
        error = layer.findViewById(R.id.error);
        frame = layer.findViewById(R.id.live_frame);
        infoNumber = layer.findViewById(R.id.info_number);
        infoTitle = layer.findViewById(R.id.info_title);
        infoSub = layer.findViewById(R.id.info_sub);
        infoNow = layer.findViewById(R.id.info_now);
        infoNext = layer.findViewById(R.id.info_next);
        infoGuide = layer.findViewById(R.id.info_guide);
        infoBar = layer.findViewById(R.id.info_bar);
        infoTrack = layer.findViewById(R.id.info_track);
        infoLogo = layer.findViewById(R.id.info_logo);
        infoInitials = layer.findViewById(R.id.info_initials);
        infoKeys = layer.findViewById(R.id.info_keys);
        errorText = layer.findViewById(R.id.error_text);
        caption = layer.findViewById(R.id.pip_caption);
        hideInfo = () -> info.animate().alpha(0f).setDuration(250)
                .withEndAction(() -> info.setVisibility(View.GONE)).start();
        layer.setOnClickListener(v -> {
            if (mode == Mode.MINI) full();
        });
    }

    Mode mode() {
        return mode;
    }

    boolean isFull() {
        return mode == Mode.FULL;
    }

    /** The channel playing (or kept for the window), null when none. */
    Item current() {
        return mode == Mode.HIDDEN || channels.isEmpty() ? null : channels.get(index);
    }

    void setApi(Xtream api) {
        this.api = api;
        if (api == null) stop();
    }

    // ---- what the page asks ----

    /** Plays channels.get(index) full screen; the same channel already in the window just grows back. */
    void play(List<Item> list, int i, String groupName) {
        if (api == null || list.isEmpty()) return;
        Item wanted = list.get(i);
        Item now = current();
        channels = new ArrayList<>(list);
        group = groupName;
        index = i;
        if (now != null && now.id.equals(wanted.id) && player != null) {
            full();
            return;
        }
        ensurePlayer();
        playChannel(i);
        setMode(Mode.FULL, true);
    }

    void full() {
        if (mode == Mode.HIDDEN) return;
        setMode(Mode.FULL, true);
        showInfo();
    }

    /** Back while full screen: into the window, or off. */
    void back() {
        if (mode != Mode.FULL) return;
        if (Options.miniPlayer(activity) && host.miniBounds() != null) setMode(Mode.MINI, true);
        else stop();
    }

    void stop() {
        if (mode == Mode.HIDDEN && player == null) return;
        countViewing();
        release();
        setMode(Mode.HIDDEN, false);
    }

    /** The activity is no longer visible: the stream stops; {@link #resume()} starts it again where it was. */
    void pause() {
        countViewing();
        release();
    }

    void resume() {
        if (mode == Mode.HIDDEN || channels.isEmpty() || api == null) return;
        if (mode == Mode.MINI && host.miniBounds() == null) {
            setMode(Mode.HIDDEN, false);
            return;
        }
        ensurePlayer();
        playChannel(index);
        setMode(mode, false);
    }

    /** Keys while full screen. */
    boolean key(KeyEvent e) {
        if (mode != Mode.FULL) return false;
        int k = e.getKeyCode();
        if (e.getAction() != KeyEvent.ACTION_DOWN) return handles(k);
        if (error.getVisibility() == View.VISIBLE && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
            playChannel(index);
            return true;
        }
        int n = channels.size();
        switch (k) {
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_DPAD_UP:
                if (n > 1) playChannel((index + 1) % n);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (n > 1) playChannel((index - 1 + n) % n);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_INFO:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                showInfo();
                return true;
            case KeyEvent.KEYCODE_BACK:
                back();
                return true;
            default:
                return false;   // volume, Home and the like go to the system
        }
    }

    private static boolean handles(int k) {
        switch (k) {
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_INFO:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BACK:
                return true;
            default:
                return false;
        }
    }

    // ---- playback ----

    private void ensurePlayer() {
        if (player != null) return;
        player = Players.build(activity);
        video.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException e) {
                failed(e);
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    // The tries left come back only after a while of steady playback, so a stream that keeps
                    // failing a few seconds in ends on a message instead of trying forever.
                    layer.removeCallbacks(steady);
                    layer.postDelayed(steady, STEADY_MS);
                    infoSub.setText(group);
                    error.setVisibility(View.GONE);
                    caption();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean playing) {
                if (playing) {
                    if (playingSince == 0) playingSince = System.currentTimeMillis();
                } else {
                    countViewing();
                }
            }
        });
        layer.removeCallbacks(flush);
        layer.postDelayed(flush, FLUSH_MS);
    }

    private void release() {
        layer.removeCallbacks(flush);
        layer.removeCallbacks(steady);
        if (player != null) {
            video.setPlayer(null);
            player.release();
            player = null;
        }
        playingSince = 0;
    }

    private static final long STEADY_MS = 10_000;

    private void playChannel(int i) {
        if (player == null || channels.isEmpty()) return;
        countViewing();
        index = i;
        triedHls = false;
        retries = 0;
        Item c = channels.get(i);
        start(api.liveUrl(c, false), MimeTypes.VIDEO_MP2T);
        infoNumber.setText(c.num > 0 ? String.valueOf(c.num) : "");
        infoTitle.setText(LivePage.display(activity, c));
        infoSub.setText(group);
        infoInitials.setText(Ui.initials(c.title));
        Images.load(infoLogo, c.image, Ui.dp(activity, 120), infoInitials);
        if (!c.id.equals(guideFor)) {
            guide = null;
            guideFor = c.id;
        }
        caption();
        if (mode == Mode.FULL) showInfo();
        Epg.get(api, c.id, listing -> {
            if (!c.id.equals(guideFor) || activity.isDestroyed()) return;
            guide = listing;
            showGuide();
            caption();
        });
        host.channelChanged(c);
    }

    private void start(String url, String mime) {
        error.setVisibility(View.GONE);
        MediaItem.Builder b = new MediaItem.Builder().setUri(Uri.parse(url)).setMimeType(mime)
                .setLiveConfiguration(new MediaItem.LiveConfiguration.Builder().build());
        player.setMediaItem(b.build());
        player.prepare();
    }

    private final Runnable steady = () -> retries = 0;

    private void failed(PlaybackException e) {
        layer.removeCallbacks(steady);
        // A live stream that drops is tried twice again, then asked for as HLS once.
        if (retries < 2) {
            retries++;
            infoSub.setText(R.string.retrying);
            if (mode == Mode.FULL) showInfo();
            layer.postDelayed(() -> {
                if (player != null) {
                    player.seekToDefaultPosition();
                    player.prepare();
                }
            }, 1200);
            return;
        }
        if (!triedHls) {
            triedHls = true;
            retries = 0;
            infoSub.setText(R.string.retrying_hls);
            if (mode == Mode.FULL) showInfo();
            start(api.liveUrl(channels.get(index), true), MimeTypes.APPLICATION_M3U8);
            return;
        }
        errorText.setText(Players.describe(e));
        error.setVisibility(mode == Mode.FULL ? View.VISIBLE : View.GONE);
        caption.setText(activity.getString(R.string.play_failed));
    }

    private void countViewing() {
        if (playingSince <= 0 || channels.isEmpty()) return;
        long ms = System.currentTimeMillis() - playingSince;
        playingSince = player != null && player.isPlaying() ? System.currentTimeMillis() : 0;
        History h = History.get(activity);
        if (h != null && ms > 0 && ms < 6 * 60 * 60 * 1000L) h.watched(channels.get(index), ms);
    }

    // ---- overlays ----

    private void showInfo() {
        showGuide();
        infoKeys.setText(Options.miniPlayer(activity) ? R.string.live_keys : R.string.live_keys_no_pip);
        info.removeCallbacks(hideInfo);
        info.animate().cancel();
        info.setAlpha(1f);
        info.setVisibility(View.VISIBLE);
        info.postDelayed(hideInfo, INFO_MS);
    }

    private void showGuide() {
        long sec = System.currentTimeMillis() / 1000;
        Epg.Programme now = guide == null ? null : guide.now(sec);
        Epg.Programme next = guide == null ? null : guide.next(sec);
        if (now == null && next == null) {
            infoGuide.setVisibility(View.GONE);
            return;
        }
        infoGuide.setVisibility(View.VISIBLE);
        infoNow.setVisibility(now == null ? View.GONE : View.VISIBLE);
        infoTrack.setVisibility(now == null ? View.GONE : View.VISIBLE);
        if (now != null) {
            infoNow.setText(activity.getString(R.string.guide_now, Players.clock(now.start), Players.clock(now.end), now.title));
            infoBar.setPivotX(0);
            infoBar.setScaleX(now.progress(sec));
        }
        infoNext.setVisibility(next == null ? View.GONE : View.VISIBLE);
        if (next != null) infoNext.setText(activity.getString(R.string.guide_next, Players.clock(next.start), next.title));
    }

    /** The line at the bottom of the small window: the channel and what is on. */
    private void caption() {
        Item c = channels.isEmpty() ? null : channels.get(index);
        if (c == null) return;
        Epg.Programme now = guide == null ? null : guide.now(System.currentTimeMillis() / 1000);
        caption.setText(now == null ? LivePage.display(activity, c) : LivePage.display(activity, c) + "  ·  " + now.title);
    }

    // ---- size and place ----

    private void setMode(Mode m, boolean animate) {
        Mode old = mode;
        mode = m;
        if (m == Mode.HIDDEN) {
            if (move != null) move.cancel();
            layer.setVisibility(View.GONE);
            info.setVisibility(View.GONE);
            host.modeChanged(m);
            return;
        }
        boolean mini = m == Mode.MINI;
        Rect target = mini ? host.miniBounds() : new Rect(0, 0, ((View) layer.getParent()).getWidth(),
                ((View) layer.getParent()).getHeight());
        if (target == null) {
            stop();
            return;
        }
        info.setVisibility(View.GONE);
        if (mini) error.setVisibility(View.GONE);
        caption.setVisibility(mini ? View.VISIBLE : View.GONE);
        frame.setVisibility(mini ? View.VISIBLE : View.GONE);
        layer.setFocusable(mini);
        layer.setVisibility(View.VISIBLE);
        Rect from = current(layer);
        if (!animate || old == Mode.HIDDEN || from.isEmpty()) {
            if (old == Mode.HIDDEN) {
                layer.setAlpha(0f);
                layer.animate().alpha(1f).setDuration(220).start();
            }
            place(target);
        } else {
            if (move != null) move.cancel();
            move = ValueAnimator.ofFloat(0f, 1f);
            move.setDuration(260);
            move.setInterpolator(new DecelerateInterpolator(1.6f));
            move.addUpdateListener(a -> {
                float f = (float) a.getAnimatedValue();
                place(new Rect(lerp(from.left, target.left, f), lerp(from.top, target.top, f),
                        lerp(from.right, target.right, f), lerp(from.bottom, target.bottom, f)));
            });
            move.start();
        }
        host.modeChanged(m);
        if (m == Mode.FULL) layer.requestFocus();
    }

    private static int lerp(int a, int b, float f) {
        return Math.round(a + (b - a) * f);
    }

    private static Rect current(View v) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int w = v.getWidth(), h = v.getHeight();
        return w <= 0 ? new Rect() : new Rect(lp.leftMargin, lp.topMargin, lp.leftMargin + w, lp.topMargin + h);
    }

    private void place(Rect r) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) layer.getLayoutParams();
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        lp.width = r.width();
        lp.height = r.height();
        lp.leftMargin = r.left;
        lp.topMargin = r.top;
        layer.setLayoutParams(lp);
    }
}
