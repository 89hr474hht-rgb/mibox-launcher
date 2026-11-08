package com.gathin.launchos.tv;

import android.app.Activity;
import android.app.Dialog;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The player's on-screen display. At the top, the title and the time; at the bottom, a thin seek bar, the controls
 * in the middle (10 s back, play or pause, 30 s on, next episode) and audio, subtitles and picture-in-picture at the
 * far right. It hides after {@link #HIDE_MS} without a key. Hidden, Left and Right seek at once and show it, any
 * other arrow or OK only shows it, and Back leaves; shown, Back hides it. Media keys work either way.
 */
final class Osd {
    static final long HIDE_MS = 3000;
    private static final long BACK_MS = 10_000, FORWARD_MS = 30_000;

    interface Host {
        boolean hasNext();

        void next();

        /** Picture-in-picture, when the box has it; false hides the button. */
        boolean canPip();

        void pip();
    }

    private final Activity activity;
    private final Host host;
    private final View root, seek, fill, buffered, thumb, track;
    private final TextView title, sub, clock, position, duration;
    private final ImageView play, next, pip;
    private final SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.FRANCE);
    private Player player;
    private boolean shown = true;
    /** A key whose press was used here: its release is swallowed too, or a hidden button would be clicked. */
    private int swallowUp = -1;
    private final Runnable hide = this::hide;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            root.postDelayed(this, 500);
        }
    };
    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onEvents(Player p, Player.Events events) {
            update();
        }
    };

    Osd(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
        root = activity.findViewById(R.id.osd);
        seek = activity.findViewById(R.id.osd_seek);
        track = activity.findViewById(R.id.osd_track);
        fill = activity.findViewById(R.id.osd_fill);
        buffered = activity.findViewById(R.id.osd_buffered);
        thumb = activity.findViewById(R.id.osd_thumb);
        title = activity.findViewById(R.id.osd_title);
        sub = activity.findViewById(R.id.osd_sub);
        clock = activity.findViewById(R.id.osd_clock);
        position = activity.findViewById(R.id.osd_position);
        duration = activity.findViewById(R.id.osd_duration);
        play = activity.findViewById(R.id.osd_play);
        next = activity.findViewById(R.id.osd_next);
        pip = activity.findViewById(R.id.osd_pip);
        fill.setPivotX(0);
        buffered.setPivotX(0);
        play.setOnClickListener(v -> toggle());
        activity.findViewById(R.id.osd_back).setOnClickListener(v -> seekBy(-BACK_MS));
        activity.findViewById(R.id.osd_forward).setOnClickListener(v -> seekBy(FORWARD_MS));
        next.setOnClickListener(v -> host.next());
        activity.findViewById(R.id.osd_audio).setOnClickListener(v -> tracks(C.TRACK_TYPE_AUDIO));
        activity.findViewById(R.id.osd_subs).setOnClickListener(v -> tracks(C.TRACK_TYPE_TEXT));
        pip.setOnClickListener(v -> host.pip());
        pip.setVisibility(host.canPip() ? View.VISIBLE : View.GONE);
        seek.setOnClickListener(v -> toggle());
        seek.setOnFocusChangeListener((v, focused) -> {
            float s = focused ? 1.8f : 1f;
            for (View x : new View[]{track, fill, buffered}) x.animate().scaleY(s).setDuration(140).start();
            thumb.animate().scaleX(focused ? 1.3f : 1f).scaleY(focused ? 1.3f : 1f).setDuration(140).start();
        });
    }

    void setTitle(CharSequence t, CharSequence s) {
        title.setText(t);
        sub.setText(s);
        sub.setVisibility(s == null || s.length() == 0 ? View.GONE : View.VISIBLE);
        next.setVisibility(host.hasNext() ? View.VISIBLE : View.GONE);
    }

    void attach(Player p) {
        player = p;
        p.addListener(listener);
        show(true);
    }

    void detach() {
        if (player != null) player.removeListener(listener);
        player = null;
        root.removeCallbacks(tick);
        root.removeCallbacks(hide);
    }

    boolean shown() {
        return shown;
    }

    /** Shows the display and starts the 3 s count again; focus goes to play/pause unless it is already inside. */
    void show(boolean focusPlay) {
        root.removeCallbacks(hide);
        root.postDelayed(hide, HIDE_MS);
        if (!shown) {
            shown = true;
            root.setVisibility(View.VISIBLE);
            root.animate().alpha(1f).setDuration(180).start();
            root.removeCallbacks(tick);
            root.post(tick);
        } else if (root.getVisibility() == View.VISIBLE) {
            root.removeCallbacks(tick);
            root.post(tick);
        }
        View f = activity.getCurrentFocus();
        if (focusPlay || f == null || !Root.inside(f, root)) play.requestFocus();
    }

    void hide() {
        root.removeCallbacks(hide);
        if (!shown) return;
        shown = false;
        root.removeCallbacks(tick);
        root.animate().alpha(0f).setDuration(260).withEndAction(() -> {
            if (!shown) root.setVisibility(View.INVISIBLE);
        }).start();
    }

    /** The keys, before the views get them; true when used here. */
    boolean key(KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_UP) {
            if (k == swallowUp) {
                swallowUp = -1;
                return true;
            }
            return false;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN || player == null) return false;
        switch (k) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (k == KeyEvent.KEYCODE_MEDIA_PLAY) player.play();
                else if (k == KeyEvent.KEYCODE_MEDIA_PAUSE) player.pause();
                else toggle();
                return used(k, false);
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekBy(FORWARD_MS);
                return used(k, false);
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekBy(-BACK_MS);
                return used(k, false);
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                if (host.hasNext()) host.next();
                return used(k, false);
            default:
                break;
        }
        if (!shown) {
            if (k == KeyEvent.KEYCODE_BACK) return false;
            if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                seekBy(k == KeyEvent.KEYCODE_DPAD_LEFT ? -BACK_MS : FORWARD_MS);
                swallowUp = k;
                show(false);
                seek.requestFocus();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_DPAD_UP
                    || k == KeyEvent.KEYCODE_DPAD_DOWN) {
                return used(k, true);
            }
            return false;
        }
        if (k == KeyEvent.KEYCODE_BACK) {
            hide();
            swallowUp = k;
            return true;
        }
        show(false);
        if (seek.isFocused() && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            seekBy(k == KeyEvent.KEYCODE_DPAD_LEFT ? -BACK_MS : FORWARD_MS);
            return true;
        }
        return false;
    }

    private boolean used(int k, boolean focusPlay) {
        swallowUp = k;
        show(focusPlay);
        return true;
    }

    private void toggle() {
        if (player == null) return;
        if (player.isPlaying()) player.pause();
        else player.play();
        update();
    }

    private void seekBy(long ms) {
        if (player == null) return;
        long d = player.getDuration();
        long to = Math.max(0, player.getCurrentPosition() + ms);
        if (d != C.TIME_UNSET && d > 0) to = Math.min(to, d - 1000);
        player.seekTo(to);
        update();
    }

    /** The time, the seek bar and the play/pause icon, from the player now. */
    private void update() {
        clock.setText(hm.format(new Date()));
        if (player == null) return;
        boolean playing = player.isPlaying() || (player.getPlayWhenReady() && player.getPlaybackState() == Player.STATE_BUFFERING);
        play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        play.setContentDescription(activity.getString(playing ? R.string.osd_pause : R.string.osd_play));
        long d = player.getDuration(), p = player.getCurrentPosition(), b = player.getBufferedPosition();
        boolean known = d != C.TIME_UNSET && d > 0;
        seek.setVisibility(known ? View.VISIBLE : View.INVISIBLE);
        position.setText(Players.elapsed(p));
        duration.setText(known ? Players.elapsed(d) : "");
        if (!known) return;
        float f = Math.max(0f, Math.min(1f, p / (float) d));
        fill.setScaleX(f);
        buffered.setScaleX(Math.max(f, Math.min(1f, b / (float) d)));
        thumb.setTranslationX(f * Math.max(0, seek.getWidth() - thumb.getWidth()));
    }

    // ---- audio and subtitles ----

    private void tracks(int type) {
        if (player == null) return;
        boolean text = type == C.TRACK_TYPE_TEXT;
        Dialog d = GroupOptions.dialog(activity, R.layout.dialog_group);
        ((TextView) d.findViewById(R.id.dialog_title)).setText(text ? R.string.osd_subs : R.string.osd_audio);
        d.findViewById(R.id.dialog_sub).setVisibility(View.GONE);
        LinearLayout body = d.findViewById(R.id.dialog_body);
        TrackSelectionParameters params = player.getTrackSelectionParameters();
        View focus = null;
        int count = 0;
        if (text) {
            boolean off = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
            TextView none = GroupOptions.action(activity, body, off ? R.drawable.ic_check : R.drawable.ic_subtitles,
                    activity.getString(R.string.osd_subs_off), () -> {
                        player.setTrackSelectionParameters(params.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build());
                        d.dismiss();
                    });
            if (off) focus = none;
        }
        for (Tracks.Group g : player.getCurrentTracks().getGroups()) {
            if (g.getType() != type) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) continue;
                count++;
                int index = i;
                boolean selected = g.isTrackSelected(i) && !params.disabledTrackTypes.contains(type);
                TextView t = GroupOptions.action(activity, body, selected ? R.drawable.ic_check
                        : text ? R.drawable.ic_subtitles : R.drawable.ic_audio, label(g.getTrackFormat(i), count), () -> {
                    player.setTrackSelectionParameters(params.buildUpon().setTrackTypeDisabled(type, false)
                            .setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), index)).build());
                    d.dismiss();
                });
                if (selected) focus = t;
            }
        }
        if (count == 0) {
            // Nothing to choose: say so, without a menu.
            GlassToast.show(activity, activity.getString(text ? R.string.osd_no_subs : R.string.osd_no_audio), false);
            show(false);
            return;
        }
        root.removeCallbacks(hide);
        d.setOnDismissListener(x -> show(false));
        d.show();
        if (focus == null && body.getChildCount() > 0) focus = body.getChildAt(0);
        if (focus != null) focus.requestFocus();
    }

    /** "Français · 5.1", from the track's label or language. */
    static String label(Format f, int n) {
        String name = f.label;
        if (name == null || name.isEmpty()) {
            if (f.language != null && !f.language.isEmpty() && !f.language.equals("und")) {
                String l = new Locale(f.language).getDisplayLanguage(Locale.FRANCE);
                name = l.isEmpty() ? f.language : Character.toUpperCase(l.charAt(0)) + l.substring(1);
            } else {
                name = "Piste " + n;
            }
        }
        if (f.channelCount >= 6) name += " · 5.1";
        else if (f.channelCount == 2) name += " · stéréo";
        return name;
    }
}
