package com.gathin.launchos.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * What was watched, on the box only and for this account: the time spent on each channel (the favourites at the top
 * of Chaînes), where a film or an episode was stopped (Reprendre), and the last episode of each series. Nothing
 * leaves the box. Main thread only.
 *
 * <p>Stored in the "history" preferences as three JSON objects per account (watch, resume, series), keyed by id,
 * each entry a named object (see the W_, R_ and S_ field names). Versions up to 0.7 stored positional arrays;
 * {@link #migrate} turns them into named objects the first time they are read, and the result is written back.
 */
final class History {
    /** A channel joins the favourites after this much viewing. */
    static final long FAVOURITE_MS = 60_000;
    /** A film or an episode is "started" after this, and "finished" this close to its end. */
    static final long RESUME_MIN_MS = 60_000;
    private static final long END_MARGIN_MS = 3 * 60_000;
    private static final int MAX_CHANNELS = 300, MAX_RESUME = 200, MAX_SERIES = 200;
    private static final String PREFS = "history";
    static final String WATCH = "watch", RESUME = "resume", SERIES = "series";

    // Field names of a channel (watch), a film or episode (resume) and a series.
    static final String W_MS = "ms", W_AT = "at", W_NAME = "name", W_IMAGE = "image", W_NUM = "num", W_CAT = "cat";
    static final String R_POS = "pos", R_DUR = "dur", R_AT = "at", R_NAME = "name", R_IMAGE = "image", R_EXT = "ext",
            R_SERIES = "series";
    static final String S_EPISODE = "episode", S_SEASON = "season", S_AT = "at", S_NAME = "name", S_COVER = "cover",
            S_NUM = "num", S_DONE = "done";
    /** The old positional order of each kind, for {@link #migrate}. */
    private static final String[] OLD_WATCH = {W_MS, W_AT, W_NAME, W_IMAGE, W_NUM, W_CAT};
    private static final String[] OLD_RESUME = {R_POS, R_DUR, R_AT, R_NAME, R_IMAGE, R_EXT, R_SERIES};
    private static final String[] OLD_SERIES = {S_EPISODE, S_SEASON, S_AT, S_NAME, S_COVER, S_NUM, S_DONE};

    private static History cached;
    private static int version;

    private final SharedPreferences prefs;
    private final String account;
    private JSONObject watch, resume, series;

    /** One remembered film, episode or series. */
    static final class Entry {
        final String id;
        final long position, duration, at;
        final String name, image, ext, extra;

        Entry(String id, long position, long duration, long at, String name, String image, String ext, String extra) {
            this.id = id;
            this.position = position;
            this.duration = duration;
            this.at = at;
            this.name = name;
            this.image = image;
            this.ext = ext;
            this.extra = extra;
        }
    }

    private History(Context c, String account) {
        prefs = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.account = account;
        watch = read(WATCH);
        resume = read(RESUME);
        series = read(SERIES);
    }

    /** Null when no account is saved. */
    static History get(Context c) {
        Account a = AccountStore.load(c);
        if (a == null) return null;
        String key = AccountStore.key(a);
        if (cached == null || !cached.account.equals(key)) cached = new History(c, key);
        return cached;
    }

    /** Forgets everything watched, for every account (the account was deleted). */
    static void forgetAll(Context c) {
        c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        cached = null;
        version++;
    }

    /** Changes after every change, so pages know to show it again. */
    static int version() {
        return version;
    }

    private JSONObject read(String what) {
        JSONObject o;
        try {
            o = new JSONObject(prefs.getString(account + "|" + what, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
        if (migrate(what, o)) prefs.edit().putString(account + "|" + what, o.toString()).apply();
        return o;
    }

    private void save(String what, JSONObject o) {
        prefs.edit().putString(account + "|" + what, o.toString()).apply();
        version++;
    }

    /**
     * Turns the old positional arrays of one kind (watch, resume or series) into named objects, in place. Entries that
     * are already objects are left alone. True when something changed. Pure, tested on the JVM.
     */
    static boolean migrate(String what, JSONObject o) {
        String[] fields = WATCH.equals(what) ? OLD_WATCH : RESUME.equals(what) ? OLD_RESUME : OLD_SERIES;
        boolean changed = false;
        List<String> keys = new ArrayList<>();
        for (Iterator<String> it = o.keys(); it.hasNext(); ) keys.add(it.next());
        for (String k : keys) {
            JSONArray a = o.optJSONArray(k);
            if (a == null) continue;
            JSONObject named = new JSONObject();
            try {
                for (int i = 0; i < fields.length && i < a.length(); i++) {
                    Object v = a.opt(i);
                    if (v != null && v != JSONObject.NULL) named.put(fields[i], v);
                }
                o.put(k, named);
            } catch (JSONException e) {
                o.remove(k);
            }
            changed = true;
        }
        return changed;
    }

    // ---- channels ----

    /** Adds viewing time to a channel. */
    void watched(Item channel, long ms) {
        if (ms <= 0) return;
        JSONObject old = watch.optJSONObject(channel.id);
        long total = (old != null ? old.optLong(W_MS) : 0) + ms;
        try {
            watch.put(channel.id, new JSONObject().put(W_MS, total).put(W_AT, System.currentTimeMillis())
                    .put(W_NAME, channel.name).put(W_IMAGE, channel.image).put(W_NUM, channel.num)
                    .put(W_CAT, channel.categoryId));
        } catch (JSONException ignored) {
            return;
        }
        trim(watch, MAX_CHANNELS, W_AT);
        save(WATCH, watch);
    }

    long watchedMs(String channelId) {
        JSONObject o = watch.optJSONObject(channelId);
        return o == null ? 0 : o.optLong(W_MS);
    }

    /** The most watched channels, most first, as items that can be played even before the list is read. */
    List<Item> favourites(int max) {
        List<Map.Entry<String, Long>> times = new ArrayList<>();
        for (Iterator<String> it = watch.keys(); it.hasNext(); ) {
            String id = it.next();
            times.add(new java.util.AbstractMap.SimpleEntry<>(id, watchedMs(id)));
        }
        List<Item> out = new ArrayList<>();
        for (String id : top(times, max, FAVOURITE_MS)) out.add(channel(id, watch.optJSONObject(id)));
        return out;
    }

    /** The channels watched lately, the most recent first, as items that can be played before the list is read. */
    List<Item> recentChannels(int max) {
        List<Map.Entry<String, Long>> at = new ArrayList<>();
        for (Iterator<String> it = watch.keys(); it.hasNext(); ) {
            String id = it.next();
            JSONObject o = watch.optJSONObject(id);
            if (o != null) at.add(new java.util.AbstractMap.SimpleEntry<>(id, o.optLong(W_AT)));
        }
        List<Item> out = new ArrayList<>();
        for (String id : top(at, max, 1)) out.add(channel(id, watch.optJSONObject(id)));
        return out;
    }

    /** A remembered channel as an item. Pure. */
    static Item channel(String id, JSONObject o) {
        return Item.live(id, o.optString(W_NAME), o.optString(W_IMAGE), o.optInt(W_NUM), o.optString(W_CAT));
    }

    /** The ids with the most time, at least min, most first. */
    static List<String> top(List<Map.Entry<String, Long>> times, int max, long min) {
        List<Map.Entry<String, Long>> l = new ArrayList<>();
        for (Map.Entry<String, Long> e : times) if (e.getValue() >= min) l.add(e);
        l.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < l.size() && i < max; i++) out.add(l.get(i).getKey());
        return out;
    }

    void clearWatched() {
        watch = new JSONObject();
        save(WATCH, watch);
    }

    // ---- films and episodes ----

    private static String resumeKey(Item.Kind kind, String id) {
        return (kind == Item.Kind.EPISODE ? "e:" : "m:") + id;
    }

    /** Remembers where a film or an episode was stopped; forgets it when it was barely started or is finished. */
    void position(Item item, long positionMs, long durationMs, String seriesId) {
        String k = resumeKey(item.kind, item.id);
        if (!worthResuming(positionMs, durationMs)) {
            if (resume.has(k)) {
                resume.remove(k);
                save(RESUME, resume);
            }
            return;
        }
        try {
            resume.put(k, new JSONObject().put(R_POS, positionMs).put(R_DUR, durationMs)
                    .put(R_AT, System.currentTimeMillis()).put(R_NAME, item.name).put(R_IMAGE, item.image)
                    .put(R_EXT, item.ext).put(R_SERIES, seriesId == null ? "" : seriesId));
        } catch (JSONException ignored) {
            return;
        }
        trim(resume, MAX_RESUME, R_AT);
        save(RESUME, resume);
    }

    /** Watched to the end: nothing to resume. */
    void finished(Item item) {
        if (resume.remove(resumeKey(item.kind, item.id)) != null) save(RESUME, resume);
    }

    static boolean worthResuming(long positionMs, long durationMs) {
        if (positionMs < RESUME_MIN_MS) return false;
        return durationMs <= 0 || positionMs < durationMs - Math.min(END_MARGIN_MS, durationMs / 10);
    }

    /** Where to start again, 0 when from the beginning. */
    long position(Item.Kind kind, String id) {
        JSONObject o = resume.optJSONObject(resumeKey(kind, id));
        return o == null ? 0 : o.optLong(R_POS);
    }

    Entry resumeEntry(Item.Kind kind, String id) {
        JSONObject o = resume.optJSONObject(resumeKey(kind, id));
        return o == null ? null : entry(id, o);
    }

    /** Films stopped part way, the most recent first. */
    List<Entry> films(int max) {
        List<Entry> out = new ArrayList<>();
        for (Iterator<String> it = resume.keys(); it.hasNext(); ) {
            String k = it.next();
            if (!k.startsWith("m:")) continue;
            JSONObject o = resume.optJSONObject(k);
            if (o != null) out.add(entry(k.substring(2), o));
        }
        out.sort((x, y) -> Long.compare(y.at, x.at));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** A remembered film or episode. Pure. */
    static Entry entry(String id, JSONObject o) {
        return new Entry(id, o.optLong(R_POS), o.optLong(R_DUR), o.optLong(R_AT), o.optString(R_NAME),
                o.optString(R_IMAGE), o.optString(R_EXT), o.optString(R_SERIES));
    }

    // ---- series ----

    /** The episode last played in a series, with what is needed to show the series in Reprendre. */
    void episode(String seriesId, String seriesName, String cover, Item episode, int season) {
        try {
            series.put(seriesId, new JSONObject().put(S_EPISODE, episode.id).put(S_SEASON, season)
                    .put(S_AT, System.currentTimeMillis()).put(S_NAME, seriesName).put(S_COVER, cover == null ? "" : cover)
                    .put(S_NUM, episode.num));
        } catch (JSONException ignored) {
            return;
        }
        trim(series, MAX_SERIES, S_AT);
        save(SERIES, series);
    }

    /** The last episode played in a series: its id, season, number and "1" when it was seen to the end. */
    String[] lastEpisode(String seriesId) {
        JSONObject o = series.optJSONObject(seriesId);
        return o == null ? null : last(o);
    }

    /** The same from a stored series. Pure. */
    static String[] last(JSONObject o) {
        return new String[]{o.optString(S_EPISODE), o.optString(S_SEASON), o.optString(S_NUM),
                o.optBoolean(S_DONE) ? "1" : ""};
    }

    /** The episode last played in this series was seen to the end: Reprendre offers the next one. */
    void episodeDone(String seriesId, String episodeId) {
        JSONObject o = series.optJSONObject(seriesId);
        if (o == null || !episodeId.equals(o.optString(S_EPISODE)) || o.optBoolean(S_DONE)) return;
        try {
            o.put(S_DONE, true);
        } catch (JSONException ignored) {
            return;
        }
        save(SERIES, series);
    }

    /** Series watched lately, the most recent first, as series items. */
    List<Item> recentSeries(int max) {
        List<String> ids = new ArrayList<>();
        for (Iterator<String> it = series.keys(); it.hasNext(); ) {
            String id = it.next();
            if (series.optJSONObject(id) != null) ids.add(id);
        }
        ids.sort((x, y) -> Long.compare(series.optJSONObject(y).optLong(S_AT), series.optJSONObject(x).optLong(S_AT)));
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < ids.size() && i < max; i++) out.add(seriesItem(ids.get(i), series.optJSONObject(ids.get(i))));
        return out;
    }

    /** A remembered series as an item (its "added" is when it was last watched, in seconds). Pure. */
    static Item seriesItem(String id, JSONObject o) {
        return Item.series(id, o.optString(S_NAME), o.optString(S_COVER), o.optLong(S_AT) / 1000);
    }

    void clearPlayback() {
        resume = new JSONObject();
        series = new JSONObject();
        save(RESUME, resume);
        save(SERIES, series);
    }

    /** Keeps the newest entries when there are too many (at: the field that holds each entry's time). */
    private static void trim(JSONObject o, int max, String at) {
        if (o.length() <= max) return;
        String oldest = null;
        long oldestAt = Long.MAX_VALUE;
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject e = o.optJSONObject(k);
            long when = e == null ? 0 : e.optLong(at);
            if (when < oldestAt) {
                oldestAt = when;
                oldest = k;
            }
        }
        if (oldest != null) o.remove(oldest);
    }
}
