package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import com.gathin.launchos.design.Motion;

/**
 * The lyrics options panel over the player: text size, translation, word by word, timing. Left and Right change a
 * row's value, OK steps it (and sets the timing back to none), Up and Down stay inside the panel, Back closes it.
 * The values live in LyricsStyle and MusicPlayer; the panel only shows and changes them. Main thread only.
 */
final class LyricsOptionsPanel {
    private final Activity activity;
    private final MusicPlayer player;
    private final LyricsView lyrics;
    private final View panel, size, translation, words, offset;
    private final View[] rows;
    private Runnable onClose;

    LyricsOptionsPanel(Activity a, MusicPlayer player, LyricsView lyrics) {
        activity = a;
        this.player = player;
        this.lyrics = lyrics;
        panel = a.findViewById(R.id.music_panel);
        size = a.findViewById(R.id.music_opt_size);
        translation = a.findViewById(R.id.music_opt_translation);
        words = a.findViewById(R.id.music_opt_words);
        offset = a.findViewById(R.id.music_opt_offset);
        rows = new View[]{size, translation, words, offset};
        row(size, R.string.mibox_music_opt_size,
                () -> LyricsStyle.setSize(a, (LyricsStyle.size(a) + 2) % 3),
                () -> LyricsStyle.setSize(a, (LyricsStyle.size(a) + 1) % 3));
        Runnable flipTranslation = () -> LyricsStyle.setTranslation(a, !LyricsStyle.translation(a));
        row(translation, R.string.mibox_music_opt_translation, flipTranslation, flipTranslation);
        Runnable flipWords = () -> LyricsStyle.setWords(a, !LyricsStyle.words(a));
        row(words, R.string.mibox_music_opt_words, flipWords, flipWords);
        row(offset, R.string.mibox_music_opt_offset,
                () -> player.setLyricsOffset(player.lyricsOffset() - LyricsStyle.OFFSET_STEP),
                () -> player.setLyricsOffset(player.lyricsOffset() + LyricsStyle.OFFSET_STEP));
        // OK on the timing sets it back to none.
        offset.setOnClickListener(v -> {
            player.setLyricsOffset(0);
            apply();
        });
        apply();
    }

    private void row(View row, int name, Runnable left, Runnable right) {
        ((TextView) row.findViewById(R.id.option_name)).setText(name);
        row.setOnClickListener(v -> {
            right.run();
            apply();
        });
        row.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                (code == KeyEvent.KEYCODE_DPAD_LEFT ? left : right).run();
                apply();
                return true;
            }
            return false;
        });
    }

    /** Shows the current values and gives them to the lyrics view. */
    void apply() {
        int s = LyricsStyle.size(activity);
        boolean tr = LyricsStyle.translation(activity), w = LyricsStyle.words(activity);
        lyrics.setStyle(s, tr, w);
        value(size, activity.getString(s == LyricsStyle.SMALL ? R.string.mibox_music_size_small
                : s == LyricsStyle.LARGE ? R.string.mibox_music_size_large : R.string.mibox_music_size_medium));
        value(translation, activity.getString(tr ? R.string.mibox_music_shown : R.string.mibox_music_hidden));
        value(words, activity.getString(w ? R.string.mibox_music_on : R.string.mibox_music_off));
        value(offset, LyricsStyle.offsetText(player.lyricsOffset()));
    }

    private void value(View row, String text) {
        ((TextView) row.findViewById(R.id.option_value)).setText(activity.getString(R.string.mibox_music_option_value, text));
    }

    boolean isOpen() {
        return panel.getVisibility() == View.VISIBLE;
    }

    /** Opens the panel on its first row; onClose runs when it closes, to give the focus back. */
    void open(Runnable onClose) {
        this.onClose = onClose;
        apply();
        panel.setVisibility(View.VISIBLE);
        if (Motion.on()) {
            panel.setAlpha(0f);
            panel.setTranslationX(24 * activity.getResources().getDisplayMetrics().density);
            panel.animate().alpha(1f).translationX(0).setDuration(Motion.MEDIUM).setInterpolator(Motion.ENTER).start();
        }
        size.requestFocus();
    }

    /** Closes it if open; true when it was. */
    boolean close() {
        if (!isOpen()) return false;
        panel.animate().cancel();
        panel.setVisibility(View.GONE);
        if (onClose != null) onClose.run();
        return true;
    }

    /** While open, keeps the remote's Up and Down inside the panel. True when the key was used. */
    boolean onKeyDown(KeyEvent e, View focused) {
        if (!isOpen()) return false;
        if (focused == null || !inside(focused, panel)) {
            size.requestFocus();
            return true;
        }
        int code = e.getKeyCode();
        if (code != KeyEvent.KEYCODE_DPAD_UP && code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
        int at = 0;
        for (int i = 0; i < rows.length; i++) if (inside(focused, rows[i])) at = i;
        int to = Math.max(0, Math.min(rows.length - 1, at + (code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1)));
        rows[to].requestFocus();
        return true;
    }

    static boolean inside(View v, View parent) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == parent) return true;
        }
        return false;
    }
}
