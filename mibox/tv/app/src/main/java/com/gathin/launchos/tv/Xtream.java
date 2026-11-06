package com.gathin.launchos.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The Xtream Codes API: player_api.php for the lists, and the stream addresses. Panels differ in small ways
 * (numbers sent as strings, "null", episodes as a map or a list), and the parsers accept all of them.
 * Nothing here logs: every address carries the user name and password.
 */
final class Xtream {
    /** A list can be several MB on a big panel; more than this is refused rather than filling the memory. */
    private static final int MAX_LIST_BYTES = 48 * 1024 * 1024;

    static final class Category {
        final String id;
        final String name;

        Category(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static final class AccountInfo {
        final String status;
        /** Seconds since 1970, or 0 when the subscription does not expire. */
        final long expires;
        final int maxConnections;
        final int activeConnections;
        final boolean m3u8;
        final boolean ts;

        AccountInfo(String status, long expires, int maxConnections, int activeConnections, boolean m3u8, boolean ts) {
            this.status = status;
            this.expires = expires;
            this.maxConnections = maxConnections;
            this.activeConnections = activeConnections;
            this.m3u8 = m3u8;
            this.ts = ts;
        }
    }

    static final class Season {
        final int number;
        final String name;
        final List<Item> episodes;

        Season(int number, String name, List<Item> episodes) {
            this.number = number;
            this.name = name;
            this.episodes = episodes;
        }
    }

    static final class SeriesInfo {
        final String plot;
        final String cover;
        final List<Season> seasons;

        SeriesInfo(String plot, String cover, List<Season> seasons) {
            this.plot = plot;
            this.cover = cover;
            this.seasons = seasons;
        }
    }

    enum Section {
        LIVE("get_live_categories", "get_live_streams"),
        MOVIES("get_vod_categories", "get_vod_streams"),
        SERIES("get_series_categories", "get_series");

        final String categoriesAction;
        final String itemsAction;

        Section(String categoriesAction, String itemsAction) {
            this.categoriesAction = categoriesAction;
            this.itemsAction = itemsAction;
        }
    }

    private final Account account;

    Xtream(Account account) {
        this.account = account;
    }

    // ---- calls (background thread only) ----

    AccountInfo login() throws XtreamException {
        return parseLogin(fetch(null, null));
    }

    List<Category> categories(Section section) throws XtreamException {
        return parseCategories(fetch(section.categoriesAction, null));
    }

    List<Item> items(Section section, String categoryId) throws XtreamException {
        Object json = fetch(section.itemsAction, "&category_id=" + enc(categoryId));
        Item.Kind kind = section == Section.LIVE ? Item.Kind.LIVE
                : section == Section.MOVIES ? Item.Kind.MOVIE : Item.Kind.SERIES;
        return parseItems(json, kind);
    }

    SeriesInfo seriesInfo(String seriesId) throws XtreamException {
        return parseSeriesInfo(fetch("get_series_info", "&series_id=" + enc(seriesId)));
    }

    private Object fetch(String action, String extra) throws XtreamException {
        String url = account.server + "/player_api.php?username=" + enc(account.user) + "&password=" + enc(account.password)
                + (action != null ? "&action=" + action : "") + (extra != null ? extra : "");
        String body = new String(Http.get(url, MAX_LIST_BYTES), StandardCharsets.UTF_8).trim();
        if (body.isEmpty() || body.equals("null")) return new JSONArray();
        try {
            return new JSONTokener(body).nextValue();
        } catch (JSONException e) {
            throw new XtreamException(XtreamException.Kind.BAD_ANSWER, 0);
        }
    }

    // ---- stream addresses ----

    String liveUrl(Item channel, boolean hls) {
        return account.server + "/live/" + path(account.user) + "/" + path(account.password) + "/" + channel.id
                + (hls ? ".m3u8" : ".ts");
    }

    String movieUrl(Item film) {
        return account.server + "/movie/" + path(account.user) + "/" + path(account.password) + "/" + film.id + "."
                + (film.ext.isEmpty() ? "mp4" : film.ext);
    }

    String episodeUrl(Item episode) {
        return account.server + "/series/" + path(account.user) + "/" + path(account.password) + "/" + episode.id + "."
                + (episode.ext.isEmpty() ? "mp4" : episode.ext);
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    /** A path segment: like a query value, but a space is %20, not +. */
    private static String path(String s) {
        return enc(s).replace("+", "%20");
    }

    // ---- parsers (static, so plain JVM tests can feed them panel answers) ----

    static AccountInfo parseLogin(Object json) throws XtreamException {
        if (!(json instanceof JSONObject)) throw new XtreamException(XtreamException.Kind.BAD_ANSWER, 0);
        JSONObject u = ((JSONObject) json).optJSONObject("user_info");
        if (u == null) throw new XtreamException(XtreamException.Kind.BAD_ANSWER, 0);
        if (num(u, "auth") != 1) throw new XtreamException(XtreamException.Kind.AUTH, 0);
        String status = str(u, "status");
        if (!status.isEmpty() && !status.equalsIgnoreCase("Active")) {
            throw new XtreamException(XtreamException.Kind.ACCOUNT_STATUS, 0, status);
        }
        boolean m3u8 = false, ts = false;
        JSONArray formats = u.optJSONArray("allowed_output_formats");
        if (formats == null || formats.length() == 0) {
            ts = true;
        } else {
            for (int i = 0; i < formats.length(); i++) {
                String f = formats.optString(i).toLowerCase(Locale.ROOT);
                if (f.equals("m3u8")) m3u8 = true;
                if (f.equals("ts")) ts = true;
            }
        }
        return new AccountInfo(status.isEmpty() ? "Active" : status, num(u, "exp_date"),
                (int) num(u, "max_connections"), (int) num(u, "active_cons"), m3u8, ts);
    }

    static List<Category> parseCategories(Object json) {
        List<Category> out = new ArrayList<>();
        if (!(json instanceof JSONArray)) return out;
        JSONArray a = (JSONArray) json;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String id = str(o, "category_id");
            if (!id.isEmpty()) out.add(new Category(id, str(o, "category_name")));
        }
        return out;
    }

    static List<Item> parseItems(Object json, Item.Kind kind) {
        List<Item> out = new ArrayList<>();
        JSONArray a = asArray(json);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String id = str(o, kind == Item.Kind.SERIES ? "series_id" : "stream_id");
            if (id.isEmpty()) continue;
            String image = str(o, kind == Item.Kind.SERIES ? "cover" : "stream_icon");
            out.add(new Item(kind, id, str(o, "name"), image, str(o, "container_extension"),
                    (int) num(o, "num"), str(o, "plot")));
        }
        return out;
    }

    static SeriesInfo parseSeriesInfo(Object json) {
        if (!(json instanceof JSONObject)) return new SeriesInfo("", "", Collections.emptyList());
        JSONObject root = (JSONObject) json;
        JSONObject info = root.optJSONObject("info");
        String plot = info != null ? str(info, "plot") : "";
        String cover = info != null ? str(info, "cover") : "";

        // Season names and covers, when the panel gives them.
        java.util.Map<Integer, String> names = new java.util.HashMap<>();
        JSONArray seasons = root.optJSONArray("seasons");
        if (seasons != null) {
            for (int i = 0; i < seasons.length(); i++) {
                JSONObject s = seasons.optJSONObject(i);
                if (s != null) names.put((int) num(s, "season_number"), str(s, "name"));
            }
        }

        // Episodes: a map {"1": [...], "2": [...]} on most panels, a flat list with a "season" field on some.
        java.util.TreeMap<Integer, List<Item>> bySeason = new java.util.TreeMap<>();
        Object eps = root.opt("episodes");
        if (eps instanceof JSONObject) {
            JSONObject map = (JSONObject) eps;
            for (java.util.Iterator<String> it = map.keys(); it.hasNext(); ) {
                String key = it.next();
                int season = (int) parseLong(key);
                for (Item e : episodes(map.opt(key))) bySeason.computeIfAbsent(season, k -> new ArrayList<>()).add(e);
            }
        } else if (eps instanceof JSONArray) {
            JSONArray list = (JSONArray) eps;
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.optJSONObject(i);
                if (o == null) continue;
                Item e = episode(o);
                if (e != null) bySeason.computeIfAbsent((int) num(o, "season"), k -> new ArrayList<>()).add(e);
            }
        }
        List<Season> out = new ArrayList<>();
        for (java.util.Map.Entry<Integer, List<Item>> s : bySeason.entrySet()) {
            List<Item> list = s.getValue();
            list.sort((x, y) -> Integer.compare(x.num, y.num));
            String name = names.get(s.getKey());
            out.add(new Season(s.getKey(), name == null || name.isEmpty() ? "Saison " + s.getKey() : name, list));
        }
        return new SeriesInfo(plot, cover, out);
    }

    private static List<Item> episodes(Object json) {
        List<Item> out = new ArrayList<>();
        JSONArray a = asArray(json);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            Item e = o == null ? null : episode(o);
            if (e != null) out.add(e);
        }
        return out;
    }

    private static Item episode(JSONObject o) {
        String id = str(o, "id");
        if (id.isEmpty()) return null;
        JSONObject info = o.optJSONObject("info");
        int n = (int) num(o, "episode_num");
        String title = str(o, "title");
        return new Item(Item.Kind.EPISODE, id, title.isEmpty() ? "Épisode " + n : title,
                info != null ? str(info, "movie_image") : "", str(o, "container_extension"), n,
                info != null ? str(info, "plot") : "");
    }

    /** Some panels send an object keyed by index instead of a list. */
    private static JSONArray asArray(Object json) {
        if (json instanceof JSONArray) return (JSONArray) json;
        JSONArray a = new JSONArray();
        if (json instanceof JSONObject) {
            JSONObject o = (JSONObject) json;
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                Object v = o.opt(it.next());
                if (v instanceof JSONObject) a.put(v);
            }
        }
        return a;
    }

    /** A text field: "" for missing or JSON null. */
    static String str(JSONObject o, String key) {
        Object v = o.opt(key);
        if (v == null || v == JSONObject.NULL) return "";
        String s = String.valueOf(v).trim();
        return s.equals("null") ? "" : s;
    }

    /** A number sent as a number or a string; 0 when missing or not a number. */
    static long num(JSONObject o, String key) {
        Object v = o.opt(key);
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof Boolean) return (Boolean) v ? 1 : 0;
        return v == null ? 0 : parseLong(String.valueOf(v));
    }

    private static long parseLong(String s) {
        try {
            return (long) Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
