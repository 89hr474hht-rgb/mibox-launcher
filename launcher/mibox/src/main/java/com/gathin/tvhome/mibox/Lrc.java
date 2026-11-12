package com.gathin.tvhome.mibox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Synced lyrics in the LRC format: "[mm:ss.xx]text" lines, a line may carry several time tags, and "[offset:±ms]"
 * shifts them all. Two lines with the same time are a pair: the first is shown large, the second small under it (a
 * translation), as music apps do with translated lyrics. Lines are kept sorted by time.
 *
 * <p>Word timing, the "enhanced LRC" way, is read too: "&lt;mm:ss.xx&gt;" (or "[mm:ss.xx]") inside a line marks
 * when the text after it starts being sung, and a tag at the very end of a word marks when it stops. A line without
 * them is taken as sung evenly from its time to the next line's. {@link Line#sung} turns a position into how much of
 * the line has been sung, for the word-by-word highlight. Plain Java, tested on the JVM.
 */
final class Lrc {
    /** One line on screen: when it starts, its text, and the smaller line under it ("" when none). */
    static final class Line {
        final long atMs;
        final String text;
        final String sub;
        /** Character positions in text and the times they are reached, both rising; at least two points. */
        private final int[] pos;
        private final long[] time;
        final boolean wordTimed;

        Line(long atMs, String text, String sub, int[] pos, long[] time, boolean wordTimed) {
            this.atMs = atMs;
            this.text = text;
            this.sub = sub;
            this.pos = pos;
            this.time = time;
            this.wordTimed = wordTimed;
        }

        /** When the last of the line has been sung. */
        long endMs() {
            return time[time.length - 1];
        }

        /**
         * How much of the text has been sung at ms, in characters: 0 before the line starts, text.length() once it
         * is over, and a fraction in between, moving through each word while it is sung.
         */
        float sung(long ms) {
            if (text.isEmpty()) return 0;
            if (ms <= time[0]) return pos[0];
            int last = time.length - 1;
            if (ms >= time[last]) return text.length();
            for (int k = 0; k < last; k++) {
                if (ms < time[k + 1]) {
                    long span = time[k + 1] - time[k];
                    float f = span <= 0 ? 1f : (ms - time[k]) / (float) span;
                    return pos[k] + (pos[k + 1] - pos[k]) * f;
                }
            }
            return text.length();
        }
    }

    private static final char BOM = (char) 0xFEFF;
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    /** A time inside a line: enhanced LRC's "<mm:ss.xx>", or a bracketed one after the text has begun. */
    private static final Pattern WORD = Pattern.compile("[<\\[](\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?[>\\]]");
    private static final Pattern TAG = Pattern.compile("^\\[([a-zA-Z]+):(.*)]\\s*$");
    /** A line without word times is taken as sung at this pace at most, so a long pause after it is not filled. */
    private static final long MS_PER_CHAR = 85, MIN_LINE_MS = 1500;

    final List<Line> lines;
    final String title, artist, album;

    private Lrc(List<Line> lines, String title, String artist, String album) {
        this.lines = lines;
        this.title = title;
        this.artist = artist;
        this.album = album;
    }

    /** Null when the text has no timed line (plain, unsynced lyrics are not shown). */
    static Lrc parse(String text) {
        if (text == null) return null;
        if (!text.isEmpty() && text.charAt(0) == BOM) text = text.substring(1);
        List<long[]> times = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        long offset = 0;
        String title = "", artist = "", album = "";
        for (String raw : text.split("\r\n|\r|\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            Matcher m = TIME.matcher(line);
            int end = 0;
            List<Long> at = new ArrayList<>();
            while (m.find() && m.start() == end) {
                at.add(ms(m));
                end = m.end();
            }
            if (at.isEmpty()) {
                Matcher t = TAG.matcher(line);
                if (!t.matches()) continue;
                String key = t.group(1).toLowerCase(java.util.Locale.ROOT), value = t.group(2).trim();
                switch (key) {
                    case "offset":
                        try {
                            offset = Long.parseLong(value.replace("+", ""));
                        } catch (NumberFormatException ignored) {
                            // A broken offset is ignored, as players do.
                        }
                        break;
                    case "ti":
                        title = value;
                        break;
                    case "ar":
                        artist = value;
                        break;
                    case "al":
                        album = value;
                        break;
                    default:
                        break;
                }
                continue;
            }
            String body = line.substring(end).trim();
            for (long a : at) {
                times.add(new long[]{a, texts.size()});
                texts.add(body);
            }
        }
        if (times.isEmpty()) return null;
        // Stable by time: lines given in the same order keep it, so a translation stays after its line.
        Collections.sort(times, (x, y) -> x[0] != y[0] ? Long.compare(x[0], y[0]) : Long.compare(x[1], y[1]));
        List<long[]> starts = new ArrayList<>();
        List<String[]> bodies = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            long at = Math.max(0, times.get(i)[0] - offset);
            String main = texts.get((int) times.get(i)[1]);
            StringBuilder sub = new StringBuilder();
            // The lines at the same time after the first are its translation; a third is joined to the second.
            while (i + 1 < times.size() && times.get(i + 1)[0] == times.get(i)[0]) {
                if (sub.length() > 0) sub.append('\n');
                sub.append(texts.get((int) times.get(i + 1)[1]));
                i++;
            }
            starts.add(new long[]{at});
            bodies.add(new String[]{main, sub.toString()});
        }
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            long at = starts.get(i)[0];
            long next = i + 1 < starts.size() ? starts.get(i + 1)[0] : Long.MAX_VALUE;
            out.add(line(at, next, bodies.get(i)[0], stripTimes(bodies.get(i)[1]), offset));
        }
        return new Lrc(Collections.unmodifiableList(out), title, artist, album);
    }

    /** A line with its word times read out of the text; an empty one is a pause between lines. */
    private static Line line(long at, long next, String body, String sub, long offset) {
        StringBuilder text = new StringBuilder();
        List<Integer> pos = new ArrayList<>();
        List<Long> time = new ArrayList<>();
        Matcher m = WORD.matcher(body);
        int from = 0;
        while (m.find()) {
            text.append(body, from, m.start());
            pos.add(text.length());
            time.add(Math.max(0, ms(m) - offset));
            from = m.end();
        }
        text.append(body.substring(from));
        // Spaces around the text are dropped: the positions move with it.
        int lead = 0;
        while (lead < text.length() && Character.isWhitespace(text.charAt(lead))) lead++;
        String clean = text.toString().trim();
        boolean wordTimed = !pos.isEmpty();
        List<Integer> p = new ArrayList<>();
        List<Long> t = new ArrayList<>();
        long latest = at;
        for (int k = 0; k < pos.size(); k++) {
            int c = Math.max(0, Math.min(clean.length(), pos.get(k) - lead));
            long w = Math.max(latest, time.get(k));   // times never go back
            if (!p.isEmpty() && c < p.get(p.size() - 1)) continue;
            p.add(c);
            t.add(w);
            latest = w;
        }
        if (p.isEmpty() || p.get(0) > 0) {
            // The text before the first word time is sung from the line's own time.
            p.add(0, 0);
            t.add(0, Math.min(at, t.isEmpty() ? at : t.get(0)));
        }
        if (p.get(p.size() - 1) < clean.length()) {
            long end;
            if (wordTimed && p.size() > 1) {
                // After the last word time, its word takes as long as a word of its length at the line's pace.
                long done = t.get(t.size() - 1);
                int left = clean.length() - p.get(p.size() - 1);
                end = Math.min(next, done + Math.max(400, left * MS_PER_CHAR));
            } else {
                end = Math.min(next, t.get(0) + Math.max(MIN_LINE_MS, clean.length() * MS_PER_CHAR));
            }
            p.add(clean.length());
            t.add(Math.max(t.get(t.size() - 1), end));
        }
        int[] pa = new int[p.size()];
        long[] ta = new long[t.size()];
        for (int k = 0; k < pa.length; k++) {
            pa[k] = p.get(k);
            ta[k] = t.get(k);
        }
        return new Line(at, clean, sub, pa, ta, wordTimed);
    }

    /** A translation keeps its text only. */
    private static String stripTimes(String s) {
        return s.isEmpty() ? s : WORD.matcher(s).replaceAll("").replaceAll("[ \\t]{2,}", " ").trim();
    }

    private static long ms(Matcher m) {
        long min = Long.parseLong(m.group(1)), sec = Long.parseLong(m.group(2));
        String frac = m.group(3);
        long f = 0;
        if (frac != null) {
            f = Long.parseLong(frac);
            if (frac.length() == 1) f *= 100;
            else if (frac.length() == 2) f *= 10;
        }
        return (min * 60 + sec) * 1000 + f;
    }

    /** The line playing at position ms: the last one started, or -1 before the first. */
    int indexAt(long ms) {
        int lo = 0, hi = lines.size() - 1, found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).atMs <= ms) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }
}
