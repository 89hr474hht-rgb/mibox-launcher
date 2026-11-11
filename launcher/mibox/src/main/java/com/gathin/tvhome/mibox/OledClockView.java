package com.gathin.tvhome.mibox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.format.DateFormat;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

/**
 * The sleep screen's content, drawn on true black so an OLED panel keeps every other pixel off: the time, and the
 * date or the sleep timer when asked. It never stays in one place: each minute it fades out, moves somewhere else on
 * the screen and fades back in, so nothing marks the panel. Dimmer still at night. One frame a minute, nothing more.
 * While LaunchOS plays music, the line of lyrics being sung (and its translation) takes the date's place, changing with
 * the sound; the block still moves each minute.
 */
final class OledClockView extends View {
    private static final long MOVE_FADE_MS = 900;

    private final Paint time = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final float density;
    private DreamSettings.Style style;
    private boolean nightDim;
    /** Where the block's centre is, as a fraction of the free room, so a new size keeps a valid place. */
    private float fx = 0.5f, fy = 0.45f;
    private final Runnable tick = this::minute;
    private final TextPaint lyricPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    /** The lyrics line shown, so a new one is drawn when the sound moves on (checked every half second). */
    private int lyricIndex = -2;
    private String trackTitle;
    private final Runnable music = new Runnable() {
        @Override
        public void run() {
            int now = musicIndex();
            String t = musicTitle();
            if (now != lyricIndex || !java.util.Objects.equals(t, trackTitle)) {
                lyricIndex = now;
                trackTitle = t;
                invalidate();
            }
            postDelayed(this, 500);
        }
    };

    OledClockView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().scaledDensity;
        Typeface light = UiFonts.light(c);
        Typeface regular = UiFonts.regular(c);
        if (light != null) time.setTypeface(light);
        if (regular != null) small.setTypeface(regular);
        time.setTextAlign(Paint.Align.CENTER);
        small.setTextAlign(Paint.Align.CENTER);
        time.setColor(Color.WHITE);
        small.setColor(Color.WHITE);
        lyricPaint.setColor(Color.WHITE);
        subPaint.setColor(Color.WHITE);
        Typeface medium = UiFonts.medium(c);
        if (medium != null) lyricPaint.setTypeface(medium);
        if (regular != null) subPaint.setTypeface(regular);
        setBackgroundColor(Color.BLACK);
        reload();
    }

    /** Reads the Veille page's choices again. */
    void reload() {
        style = DreamSettings.style(getContext());
        nightDim = DreamSettings.nightDim(getContext());
        boolean discreet = style == DreamSettings.Style.SMALL;
        time.setTextSize((discreet ? 64 : style == DreamSettings.Style.CLOCK ? 150 : 128) * density);
        time.setLetterSpacing(-0.02f);
        small.setTextSize((discreet ? 18 : 26) * density);
        small.setLetterSpacing(0.02f);
        lyricPaint.setTextSize((discreet ? 20 : 28) * density);
        subPaint.setTextSize((discreet ? 14 : 18) * density);
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        fx = 0.2f + random.nextFloat() * 0.6f;
        fy = 0.2f + random.nextFloat() * 0.6f;
        scheduleTick();
        post(music);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(music);
        removeCallbacks(tick);
        animate().cancel();
        super.onDetachedFromWindow();
    }

    private void scheduleTick() {
        removeCallbacks(tick);
        long now = System.currentTimeMillis();
        postDelayed(tick, 60_000L - now % 60_000L + 50);
    }

    /** A new minute: fade out, move, show the new time, fade in. */
    private void minute() {
        animate().alpha(0f).setDuration(MOVE_FADE_MS).setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    fx = 0.15f + random.nextFloat() * 0.7f;
                    fy = 0.15f + random.nextFloat() * 0.7f;
                    invalidate();
                    animate().alpha(1f).setDuration(MOVE_FADE_MS).start();
                }).start();
        scheduleTick();
    }

    /** Lower at night (22 h to 7 h) when asked, and lower again for the discreet look. */
    private int alpha() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean night = nightDim && (hour >= 22 || hour < 7);
        float a = style == DreamSettings.Style.SMALL ? 0.55f : 0.82f;
        if (night) a *= 0.5f;
        return Math.round(a * 255);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (style == DreamSettings.Style.BLACK) return;
        Context c = getContext();
        Calendar now = Calendar.getInstance();
        String clock = DateFormat.format(is24(c) ? "HH:mm" : "h:mm", now).toString();
        String line = null;
        long left = SleepTimer.remaining(c);
        StaticLayout[] lyric = style == DreamSettings.Style.SMALL || getWidth() == 0 ? null : lyricLayouts();
        if (lyric != null) {
            drawWithLyrics(canvas, clock, lyric);
            return;
        }
        if (left > 0) {
            line = c.getString(R.string.mibox_dream_timer, (int) Math.max(1, (left + 59_999) / 60_000));
        } else if (style != DreamSettings.Style.CLOCK) {
            line = DateFormat.format("EEEE d MMMM", now).toString();
            if (!line.isEmpty()) line = line.substring(0, 1).toUpperCase(Locale.getDefault()) + line.substring(1);
        }

        Paint.FontMetrics tm = time.getFontMetrics();
        Paint.FontMetrics sm = small.getFontMetrics();
        float timeH = tm.descent - tm.ascent;
        float gap = 10 * density;
        float smallH = line == null ? 0 : sm.descent - sm.ascent + gap;
        float blockH = timeH + smallH;
        float blockW = Math.max(time.measureText(clock), line == null ? 0 : small.measureText(line));
        float freeW = Math.max(0, getWidth() - blockW), freeH = Math.max(0, getHeight() - blockH);
        float cx = blockW / 2 + freeW * fx;
        float top = freeH * fy;

        int a = alpha();
        time.setAlpha(a);
        canvas.drawText(clock, cx, top - tm.ascent, time);
        if (line != null) {
            small.setAlpha(Math.round(a * 0.62f));
            canvas.drawText(line, cx, top + timeH + gap - sm.ascent, small);
        }
    }

    // ---- music ----

    private static MusicPlayer playing() {
        MusicPlayer p = MusicPlayer.peek();
        return p != null && p.isPlaying() ? p : null;
    }

    private static int musicIndex() {
        MusicPlayer p = playing();
        if (p == null) return -2;
        Lrc l = p.lyrics();
        return l == null ? -1 : l.indexAt(p.position());
    }

    private static String musicTitle() {
        MusicPlayer p = playing();
        MusicLibrary.Track t = p == null ? null : p.track();
        return t == null ? null : t.title;
    }

    /** The lines under the time while music plays: the lyrics line and its translation, or the track's title. */
    private StaticLayout[] lyricLayouts() {
        MusicPlayer p = playing();
        if (p == null || p.track() == null) return null;
        String main = "\u266A  " + p.track().title, sub = "";
        Lrc l = p.lyrics();
        int i = l == null ? -1 : l.indexAt(p.position());
        if (i >= 0) {
            Lrc.Line ln = l.lines.get(i);
            main = ln.text;
            sub = ln.sub;
        }
        int width = Math.round(getWidth() * 0.62f);
        return new StaticLayout[]{main.isEmpty() ? null : layout(main, lyricPaint, width),
                sub.isEmpty() ? null : layout(sub, subPaint, width)};
    }

    private static StaticLayout layout(String text, TextPaint paint, int width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setMaxLines(4)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build();
    }

    private void drawWithLyrics(Canvas canvas, String clock, StaticLayout[] lyric) {
        Paint.FontMetrics tm = time.getFontMetrics();
        float timeH = tm.descent - tm.ascent, gap = 14 * density, between = 8 * density;
        float lyricH = (lyric[0] == null ? 0 : lyric[0].getHeight())
                + (lyric[1] == null ? 0 : lyric[1].getHeight() + (lyric[0] == null ? 0 : between));
        float blockW = Math.max(time.measureText(clock), getWidth() * 0.62f);
        float blockH = timeH + gap + lyricH;
        float freeW = Math.max(0, getWidth() - blockW), freeH = Math.max(0, getHeight() - blockH);
        float cx = blockW / 2 + freeW * fx;
        float top = freeH * fy;
        int a = alpha();
        time.setAlpha(a);
        canvas.drawText(clock, cx, top - tm.ascent, time);
        canvas.save();
        canvas.translate(cx - getWidth() * 0.31f, top + timeH + gap);
        if (lyric[0] != null) {
            lyricPaint.setAlpha(Math.round(a * 0.9f));
            lyric[0].draw(canvas);
            canvas.translate(0, lyric[0].getHeight() + between);
        }
        if (lyric[1] != null) {
            subPaint.setAlpha(Math.round(a * 0.6f));
            lyric[1].draw(canvas);
        }
        canvas.restore();
    }

    /** The launcher's own choice in its settings (12, 24 or Android's), as the top bar shows it. */
    private static boolean is24(Context c) {
        String f = c.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE).getString("clock_format", "system");
        if ("12".equals(f)) return false;
        if ("24".equals(f)) return true;
        return DateFormat.is24HourFormat(c);
    }
}
