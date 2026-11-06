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
 */
final class History {
    /** A channel joins the favourites after this much viewing. */
    static final long FAVOURITE_MS = 60_000;
    /** A film or an episode is "started" after this, and "finished" this close to its end. */
    static final long RESUME_MIN_MS = 60_000;
    private static final long END_MARGIN_MS = 3 * 60_000;
    private static final int MAX_CHANNELS = 300, MAX_RESUME = 200, MAX_SERIES = 200;
    private static final String PREFS = "history";

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
        watch = read("watch");
        resume = read("resume");
        series = read("series");
    }

    /** Null when no account is saved. */
    static History get(Context c) {
        Account a = AccountStore.load(c);
        if (a == null) return null;
        String key = AccountStore.key(a);
        if (cached == null || !cached.account.equals(key)) cached = new History(c, key);
        return cached;
    }

    /** Changes after every change, so pages know to show it again. */
    static int version() {
        return version;
    }

    private JSONObject read(String what) {
        try {
            return new JSONObject(prefs.getString(account + "|" + what, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private void save(String what, JSONObject o) {
        prefs.edit().putString(account + "|" + what, o.toString()).apply();
        version++;
    }

    // ---- channels ----

    /** Adds viewing time to a channel. */
    void watched(Item channel, long ms) {
        if (ms <= 0) return;
        JSONArray old = watch.optJSONArray(channel.id);
        long total = (old != null ? old.optLong(0) : 0) + ms;
        try {
            watch.put(channel.id, new JSONArray().put(total).put(System.currentTimeMillis()).put(channel.name)
                    .put(channel.image).put(channel.num).put(channel.categoryId));
        } catch (JSONException ignored) {
            return;
        }
        trim(watch, MAX_CHANNELS, 1);
        save("watch", watch);
    }

    long watchedMs(String channelId) {
        JSONArray a = watch.optJSONArray(channelId);
        return a == null ? 0 : a.optLong(0);
    }

    /** The most watched channels, most first, as items that can be played even before the list is read. */
    List<Item> favourites(int max) {
        List<Map.Entry<String, Long>> times = new ArrayList<>();
        for (Iterator<String> it = watch.keys(); it.hasNext(); ) {
            String id = it.next();
            times.add(new java.util.AbstractMap.SimpleEntry<>(id, watchedMs(id)));
        }
        List<Item> out = new ArrayList<>();
        for (String id : top(times, max, FAVOURITE_MS)) {
            JSONArray a = watch.optJSONArray(id);
            out.add(new Item(Item.Kind.LIVE, id, a.optString(2), a.optString(3), "", a.optInt(4), "",
                    a.optString(5), 0, 0f, "", "", 0, 0));
        }
        return out;
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
        save("watch", watch);
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
                save("resume", resume);
            }
            return;
        }
        try {
            resume.put(k, new JSONArray().put(positionMs).put(durationMs).put(System.currentTimeMillis())
                    .put(item.name).put(item.image).put(item.ext).put(seriesId == null ? "" : seriesId));
        } catch (JSONException ignored) {
            return;
        }
        trim(resume, MAX_RESUME, 2);
        save("resume", resume);
    }

    /** Watched to the end: nothing to resume. */
    void finished(Item item) {
        if (resume.remove(resumeKey(item.kind, item.id)) != null) save("resume", resume);
    }

    static boolean worthResuming(long positionMs, long durationMs) {
        if (positionMs < RESUME_MIN_MS) return false;
        return durationMs <= 0 || positionMs < durationMs - Math.min(END_MARGIN_MS, durationMs / 10);
    }

    /** Where to start again, 0 when from the beginning. */
    long position(Item.Kind kind, String id) {
        JSONArray a = resume.optJSONArray(resumeKey(kind, id));
        return a == null ? 0 : a.optLong(0);
    }

    Entry resumeEntry(Item.Kind kind, String id) {
        JSONArray a = resume.optJSONArray(resumeKey(kind, id));
        return a == null ? null : entry(id, a);
    }

    /** Films stopped part way, the most recent first. */
    List<Entry> films(int max) {
        List<Entry> out = new ArrayList<>();
        for (Iterator<String> it = resume.keys(); it.hasNext(); ) {
            String k = it.next();
            if (!k.startsWith("m:")) continue;
            JSONArray a = resume.optJSONArray(k);
            if (a != null) out.add(entry(k.substring(2), a));
        }
        out.sort((x, y) -> Long.compare(y.at, x.at));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    private static Entry entry(String id, JSONArray a) {
        return new Entry(id, a.optLong(0), a.optLong(1), a.optLong(2), a.optString(3), a.optString(4),
                a.optString(5), a.optString(6));
    }

    // ---- series ----

    /** The episode last played in a series, with what is needed to show the series in Reprendre. */
    void episode(String seriesId, String seriesName, String cover, Item episode, int season) {
        try {
            series.put(seriesId, new JSONArray().put(episode.id).put(season).put(System.currentTimeMillis())
                    .put(seriesName).put(cover).put(episode.num));
        } catch (JSONException ignored) {
            return;
        }
        trim(series, MAX_SERIES, 2);
        save("series", series);
    }

    /** The last episode played in a series: its id, season, number and "1" when it was seen to the end. */
    String[] lastEpisode(String seriesId) {
        JSONArray a = series.optJSONArray(seriesId);
        return a == null ? null : new String[]{a.optString(0), a.optString(1), a.optString(5),
                a.optBoolean(6) ? "1" : ""};
    }

    /** The episode last played in this series was seen to the end: Reprendre offers the next one. */
    void episodeDone(String seriesId, String episodeId) {
        JSONArray a = series.optJSONArray(seriesId);
        if (a == null || !episodeId.equals(a.optString(0)) || a.optBoolean(6)) return;
        try {
            a.put(6, true);
        } catch (JSONException ignored) {
            return;
        }
        save("series", series);
    }

    /** Series watched lately, the most recent first, as series items. */
    List<Item> recentSeries(int max) {
        List<Object[]> l = new ArrayList<>();
        for (Iterator<String> it = series.keys(); it.hasNext(); ) {
            String id = it.next();
            JSONArray a = series.optJSONArray(id);
            if (a != null) l.add(new Object[]{id, a});
        }
        l.sort((x, y) -> Long.compare(((JSONArray) y[1]).optLong(2), ((JSONArray) x[1]).optLong(2)));
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < l.size() && i < max; i++) {
            JSONArray a = (JSONArray) l.get(i)[1];
            out.add(new Item(Item.Kind.SERIES, (String) l.get(i)[0], a.optString(3), a.optString(4), "", 0, "",
                    "", a.optLong(2) / 1000, 0f, "", "", 0, 0));
        }
        return out;
    }

    void clearPlayback() {
        resume = new JSONObject();
        series = new JSONObject();
        save("resume", resume);
        save("series", series);
    }

    /** Keeps the newest entries when there are too many (atIndex: where each entry keeps its time). */
    private static void trim(JSONObject o, int max, int atIndex) {
        if (o.length() <= max) return;
        String oldest = null;
        long oldestAt = Long.MAX_VALUE;
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONArray a = o.optJSONArray(k);
            long at = a == null ? 0 : a.optLong(atIndex);
            if (at < oldestAt) {
                oldestAt = at;
                oldest = k;
            }
        }
        if (oldest != null) o.remove(oldest);
    }
}
