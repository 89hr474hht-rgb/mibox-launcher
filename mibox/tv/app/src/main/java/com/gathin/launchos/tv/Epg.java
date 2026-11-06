package com.gathin.launchos.tv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * The programme guide: what is on now and next, from the panel's get_short_epg, one channel at a time. A listing
 * is kept until its current programme ends (at most two hours; half an hour when the panel had nothing), and the
 * Actualiser screen drops them all. Its own small pool keeps guide calls from holding up the lists.
 */
final class Epg {
    private static final long MAX_KEEP_MS = 2 * 60 * 60 * 1000L;
    private static final long EMPTY_KEEP_MS = 30 * 60 * 1000L;
    private static final Pattern BASE64 = Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");

    static final class Programme {
        final String title;
        final String description;
        /** Seconds since 1970. */
        final long start, end;

        Programme(String title, String description, long start, long end) {
            this.title = title;
            this.description = description;
            this.start = start;
            this.end = end;
        }

        /** How far into the programme nowSec is, from 0 to 1. */
        float progress(long nowSec) {
            if (end <= start) return 0f;
            return Math.max(0f, Math.min(1f, (nowSec - start) / (float) (end - start)));
        }
    }

    static final class Listing {
        final List<Programme> programmes;
        final long fetchedAt;

        Listing(List<Programme> programmes, long fetchedAt) {
            this.programmes = programmes;
            this.fetchedAt = fetchedAt;
        }

        Programme now(long nowSec) {
            for (Programme p : programmes) if (p.start <= nowSec && nowSec < p.end) return p;
            return null;
        }

        Programme next(long nowSec) {
            for (Programme p : programmes) if (p.start > nowSec) return p;
            return null;
        }

        boolean fresh(long nowMs) {
            long age = nowMs - fetchedAt;
            if (age < 0) return false;
            if (programmes.isEmpty()) return age < EMPTY_KEEP_MS;
            Programme p = now(nowMs / 1000);
            return p != null && age < MAX_KEEP_MS && p.end * 1000 > nowMs;
        }
    }

    interface Callback {
        void done(Listing listing);

        /** Asked on the guide's thread before the call: false skips a card that scrolled away. */
        default boolean stillWanted() {
            return true;
        }
    }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Map<String, Listing> CACHE = new HashMap<>();
    /** Channels being fetched, with everyone waiting for them (the list is also read on the guide's thread). */
    private static final Map<String, List<Callback>> LOADING = new HashMap<>();
    private static int accountVersion = -1;
    private static int epoch;

    private Epg() {
    }

    static void forAccount(int version) {
        if (version == accountVersion) return;
        accountVersion = version;
        clear();
    }

    /** Main thread. Calls back at once from the cache, or later on the main thread; a failed call gives nothing. */
    static void get(Xtream api, String streamId, Callback cb) {
        Listing hit = CACHE.get(streamId);
        if (hit != null && hit.fresh(System.currentTimeMillis())) {
            cb.done(hit);
            return;
        }
        if (api == null) return;
        List<Callback> waiting = LOADING.get(streamId);
        if (waiting != null) {
            synchronized (waiting) {
                waiting.add(cb);
            }
            return;
        }
        List<Callback> waiters = new ArrayList<>();
        waiters.add(cb);
        LOADING.put(streamId, waiters);
        int e = epoch;
        POOL.execute(() -> {
            boolean wanted = false;
            synchronized (waiters) {
                for (Callback w : waiters) wanted |= w.stillWanted();
            }
            Listing l = null;
            if (wanted) {
                try {
                    l = new Listing(api.shortEpg(streamId), System.currentTimeMillis());
                } catch (XtreamException | RuntimeException | OutOfMemoryError ignored) {
                    // No guide for this channel this time: the card simply shows none.
                }
            }
            Listing result = l;
            Bg.MAIN.post(() -> {
                if (e != epoch) return;
                LOADING.remove(streamId);
                if (result == null) return;
                CACHE.put(streamId, result);
                List<Callback> all;
                synchronized (waiters) {
                    all = new ArrayList<>(waiters);
                }
                for (Callback w : all) w.done(result);
            });
        });
    }

    /** Main thread. Drops every listing; answers still on their way are thrown away. */
    static void clear() {
        CACHE.clear();
        LOADING.clear();
        epoch++;
    }

    /** How many channels have a guide in memory. */
    static int size() {
        return CACHE.size();
    }

    // ---- parser (static, for plain JVM tests) ----

    static List<Programme> parse(Object json) {
        List<Programme> out = new ArrayList<>();
        JSONArray a = null;
        if (json instanceof JSONObject) a = ((JSONObject) json).optJSONArray("epg_listings");
        else if (json instanceof JSONArray) a = (JSONArray) json;
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            long start = Xtream.num(o, "start_timestamp");
            long end = Xtream.num(o, "stop_timestamp");
            if (start <= 0) start = date(Xtream.str(o, "start"));
            if (end <= 0) end = date(Xtream.str(o, Xtream.str(o, "end").isEmpty() ? "stop" : "end"));
            String title = text(Xtream.str(o, "title"));
            if (start <= 0 || end <= start || title.isEmpty()) continue;
            out.add(new Programme(title, text(Xtream.str(o, "description")), start, end));
        }
        out.sort((x, y) -> Long.compare(x.start, y.start));
        return out;
    }

    /** Most panels send titles in base64; some send plain text. Plain text that happens to look like base64 but
     *  does not decode to valid UTF-8 is kept as it is. */
    static String text(String s) {
        String t = s.trim();
        if (t.isEmpty() || t.length() % 4 != 0 || !BASE64.matcher(t).matches()) return t;
        try {
            byte[] raw = Base64.getDecoder().decode(t);
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw)).toString().trim();
            for (int i = 0; i < decoded.length(); i++) {
                char c = decoded.charAt(i);
                if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') return t;
            }
            return decoded;
        } catch (IllegalArgumentException | CharacterCodingException e) {
            return t;
        }
    }

    /** "2026-11-06 20:00:00", read in the box's time zone, for panels without timestamps. */
    private static long date(String s) {
        if (s.isEmpty()) return 0;
        try {
            java.util.Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).parse(s);
            return d == null ? 0 : d.getTime() / 1000;
        } catch (ParseException e) {
            return 0;
        }
    }
}
