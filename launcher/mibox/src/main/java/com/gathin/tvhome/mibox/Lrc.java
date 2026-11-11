package com.gathin.tvhome.mibox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Synced lyrics in the LRC format: "[mm:ss.xx]text" lines, a line may carry several time tags, and "[offset:±ms]"
 * shifts them all. Two lines with the same time are a pair: the first is shown large, the second small under it (a
 * translation), as music apps do with translated lyrics. Lines are kept sorted by time. Plain Java, tested on the JVM.
 */
final class Lrc {
    /** One line on screen: when it starts, its text, and the smaller line under it ("" when none). */
    static final class Line {
        final long atMs;
        final String text;
        final String sub;

        Line(long atMs, String text, String sub) {
            this.atMs = atMs;
            this.text = text;
            this.sub = sub;
        }
    }

    private static final char BOM = (char) 0xFEFF;
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern TAG = Pattern.compile("^\\[([a-zA-Z]+):(.*)]\\s*$");

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
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            long at = Math.max(0, times.get(i)[0] - offset);
            String main = texts.get((int) times.get(i)[1]);
            String sub = "";
            if (i + 1 < times.size() && times.get(i + 1)[0] == times.get(i)[0]) {
                sub = texts.get((int) times.get(i + 1)[1]);
                i++;
                // A third line at the same time is joined to the second, not lost.
                while (i + 1 < times.size() && times.get(i + 1)[0] == times.get(i)[0]) {
                    sub = sub + "\n" + texts.get((int) times.get(i + 1)[1]);
                    i++;
                }
            }
            if (main.isEmpty() && sub.isEmpty()) {
                out.add(new Line(at, "", ""));   // an empty timed line is a pause between lines
            } else {
                out.add(new Line(at, main, sub));
            }
        }
        return new Lrc(Collections.unmodifiableList(out), title, artist, album);
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
