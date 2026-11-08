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
    /** A whole film list can be tens of MB on a big panel. It is read item by item, but not without end. */
    private static final long MAX_LIST_BYTES = 96L * 1024 * 1024;
    /** Categories and a series' episodes are held as text first. */
    private static final int MAX_TEXT_BYTES = 24 * 1024 * 1024;
    private static final int MAX_EPG_BYTES = 512 * 1024;
    private static final int MAX_INFO_BYTES = 2 * 1024 * 1024;

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
        final String backdrop;
        final String genre;
        final String cast;
        final float rating;
        final int year;

        SeriesInfo(String plot, String cover, List<Season> seasons) {
            this(plot, cover, seasons, "", "", "", 0f, 0);
        }

        SeriesInfo(String plot, String cover, List<Season> seasons, String backdrop, String genre, String cast,
                   float rating, int year) {
            this.plot = plot;
            this.cover = cover;
            this.seasons = seasons;
            this.backdrop = backdrop;
            this.genre = genre;
            this.cast = cast;
            this.rating = rating;
            this.year = year;
        }
    }

    /** A film's sheet from get_vod_info. */
    static final class VodInfo {
        final String plot;
        final String backdrop;
        final String poster;
        final String genre;
        final String director;
        final String cast;
        final float rating;
        final int year;
        final int seconds;

        VodInfo(String plot, String backdrop, String poster, String genre, String director, String cast, float rating,
                int year, int seconds) {
            this.plot = plot;
            this.backdrop = backdrop;
            this.poster = poster;
            this.genre = genre;
            this.director = director;
            this.cast = cast;
            this.rating = rating;
            this.year = year;
            this.seconds = seconds;
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

    /** The section's items in one category, or in all of them when categoryId is null. Read as they arrive. */
    List<Item> items(Section section, String categoryId) throws XtreamException {
        return items(section, categoryId, null);
    }

    /** The same, telling progress how much of the list has arrived. */
    List<Item> items(Section section, String categoryId, Http.Progress progress) throws XtreamException {
        Item.Kind kind = section == Section.LIVE ? Item.Kind.LIVE
                : section == Section.MOVIES ? Item.Kind.MOVIE : Item.Kind.SERIES;
        String url = address(section.itemsAction, categoryId == null ? null : "&category_id=" + enc(categoryId));
        return Http.get(url, MAX_LIST_BYTES, in -> {
            try {
                return readItems(new java.io.InputStreamReader(in, StandardCharsets.UTF_8), kind);
            } catch (JsonStream.Malformed e) {
                throw new XtreamException(XtreamException.Kind.BAD_ANSWER, 0);
            }
        }, progress);
    }

    VodInfo vodInfo(String streamId) throws XtreamException {
        return parseVodInfo(fetch("get_vod_info", "&vod_id=" + enc(streamId), MAX_INFO_BYTES));
    }

    SeriesInfo seriesInfo(String seriesId) throws XtreamException {
        return parseSeriesInfo(fetch("get_series_info", "&series_id=" + enc(seriesId)));
    }

    /** Now and next for one channel: a few programmes from get_short_epg. */
    List<Epg.Programme> shortEpg(String streamId) throws XtreamException {
        return Epg.parse(fetch("get_short_epg", "&stream_id=" + enc(streamId) + "&limit=4", MAX_EPG_BYTES));
    }

    private Object fetch(String action, String extra) throws XtreamException {
        return fetch(action, extra, MAX_TEXT_BYTES);
    }

    private String address(String action, String extra) {
        return account.server + "/player_api.php?username=" + enc(account.user) + "&password=" + enc(account.password)
                + (action != null ? "&action=" + action : "") + (extra != null ? extra : "");
    }

    private Object fetch(String action, String extra, int maxBytes) throws XtreamException {
        String url = address(action, extra);
        String body = new String(Http.get(url, maxBytes), StandardCharsets.UTF_8).trim();
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

    /** A list of channels, films or series, read one item at a time. Some panels send an object keyed by index. */
    static List<Item> readItems(java.io.Reader reader, Item.Kind kind) throws java.io.IOException {
        List<Item> out = new ArrayList<>();
        JsonStream r = new JsonStream(reader);
        JsonStream.Kind top = r.peek();
        if (top == JsonStream.Kind.END) return out;
        boolean keyed = top == JsonStream.Kind.OBJECT;
        if (!keyed && top != JsonStream.Kind.ARRAY) {
            r.skipValue();   // "null" or another lone value: an empty list
            return out;
        }
        if (keyed) r.beginObject();
        else r.beginArray();
        while (r.hasNext()) {
            if (keyed) r.nextName();
            if (r.peek() != JsonStream.Kind.OBJECT) {
                r.skipValue();
                continue;
            }
            Item item = readItem(r, kind);
            if (item != null) out.add(item);
        }
        if (keyed) r.endObject();
        else r.endArray();
        return out;
    }

    private static Item readItem(JsonStream r, Item.Kind kind) throws java.io.IOException {
        String id = "", name = "", image = "", ext = "", plot = "", cat = "", cats = "", genre = "", backdrop = "";
        String rating = "", rating5 = "", added = "", modified = "", release = "", num = "";
        boolean series = kind == Item.Kind.SERIES;
        r.beginObject();
        while (r.hasNext()) {
            String k = r.nextName();
            switch (k) {
                case "stream_id":
                    if (!series) id = r.nextString();
                    else r.skipValue();
                    break;
                case "series_id":
                    if (series) id = r.nextString();
                    else r.skipValue();
                    break;
                case "name":
                    name = r.nextString();
                    break;
                case "stream_icon":
                    if (!series) image = r.nextString();
                    else r.skipValue();
                    break;
                case "cover":
                    if (series) image = r.nextString();
                    else r.skipValue();
                    break;
                case "container_extension":
                    ext = r.nextString();
                    break;
                case "num":
                    num = r.nextString();
                    break;
                case "plot":
                    plot = r.nextString();
                    break;
                case "category_id":
                    cat = r.nextString();
                    break;
                case "category_ids":
                    cats = r.nextFirstString();
                    break;
                case "added":
                    added = r.nextString();
                    break;
                case "last_modified":
                    modified = r.nextString();
                    break;
                case "rating":
                    rating = r.nextString();
                    break;
                case "rating_5based":
                    rating5 = r.nextString();
                    break;
                case "genre":
                    genre = r.nextString();
                    break;
                case "backdrop_path":
                    backdrop = r.nextFirstString();
                    break;
                case "releaseDate":
                case "release_date":
                case "year":
                    if (release.isEmpty()) release = r.nextString();
                    else r.skipValue();
                    break;
                default:
                    r.skipValue();
            }
        }
        r.endObject();
        if (id.isEmpty() || id.equals("null")) return null;
        long when = parseLong(series && !modified.isEmpty() ? modified : added);
        return new Item(kind, id, name, image, ext, (int) parseLong(num), plot, cat.isEmpty() ? cats : cat, when,
                rating(rating, rating5), genre, backdrop, year(release), 0);
    }

    /** A rating out of 10 from "7.2" or, failing that, from rating_5based. */
    static float rating(String ten, String five) {
        float r = (float) parseDouble(ten);
        if (r <= 0) r = (float) parseDouble(five) * 2;
        return r > 0 && r <= 10 ? r : 0f;
    }

    /** The year in "2019-05-01", "2019" or "01/05/2019"; 0 when none. */
    static int year(String date) {
        if (date == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?<!\\d)(19\\d\\d|20\\d\\d)(?!\\d)").matcher(date);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    static VodInfo parseVodInfo(Object json) {
        JSONObject root = json instanceof JSONObject ? (JSONObject) json : new JSONObject();
        JSONObject info = root.optJSONObject("info");
        if (info == null) info = new JSONObject();
        String backdrop = "";
        Object b = info.opt("backdrop_path");
        if (b instanceof JSONArray && ((JSONArray) b).length() > 0) backdrop = ((JSONArray) b).optString(0, "");
        else if (b instanceof String) backdrop = (String) b;
        if (backdrop.equals("null")) backdrop = "";
        String cast = str(info, "cast");
        if (cast.isEmpty()) cast = str(info, "actors");
        String release = str(info, "releasedate");
        if (release.isEmpty()) release = str(info, "release_date");
        if (release.isEmpty()) release = str(info, "year");
        int seconds = (int) num(info, "duration_secs");
        if (seconds <= 0) seconds = clockSeconds(str(info, "duration"));
        if (seconds <= 0) seconds = (int) num(info, "episode_run_time") * 60;
        String plot = str(info, "plot");
        if (plot.isEmpty()) plot = str(info, "description");
        return new VodInfo(plot, backdrop, str(info, "movie_image"), str(info, "genre"), str(info, "director"), cast,
                rating(str(info, "rating"), str(info, "rating_5based")), year(release), seconds);
    }

    /** "01:52:00" as seconds. */
    static int clockSeconds(String s) {
        if (s == null || !s.matches("\\d{1,3}:\\d{2}(:\\d{2})?")) return 0;
        String[] p = s.split(":");
        int total = 0;
        for (String x : p) total = total * 60 + Integer.parseInt(x);
        return p.length == 2 ? total * 60 : total;
    }

    static SeriesInfo parseSeriesInfo(Object json) {
        if (!(json instanceof JSONObject)) return new SeriesInfo("", "", Collections.emptyList());
        JSONObject root = (JSONObject) json;
        JSONObject info = root.optJSONObject("info");
        if (info == null) info = new JSONObject();
        String plot = str(info, "plot");
        String cover = str(info, "cover");
        String backdrop = "";
        Object b = info.opt("backdrop_path");
        if (b instanceof JSONArray && ((JSONArray) b).length() > 0) backdrop = ((JSONArray) b).optString(0, "");
        else if (b instanceof String) backdrop = (String) b;
        if (backdrop.equals("null")) backdrop = "";
        String release = str(info, "releaseDate");
        if (release.isEmpty()) release = str(info, "release_date");

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
        return new SeriesInfo(plot, cover, out, backdrop, str(info, "genre"), str(info, "cast"),
                rating(str(info, "rating"), str(info, "rating_5based")), year(release));
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
        int seconds = 0;
        if (info != null) {
            seconds = (int) num(info, "duration_secs");
            if (seconds <= 0) seconds = clockSeconds(str(info, "duration"));
        }
        return new Item(Item.Kind.EPISODE, id, title.isEmpty() ? "Épisode " + n : title,
                info != null ? str(info, "movie_image") : "", str(o, "container_extension"), n,
                info != null ? str(info, "plot") : "", "", 0, 0f, "", "", 0, seconds);
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

    private static double parseDouble(String s) {
        try {
            double d = Double.parseDouble(s.trim());
            return Double.isNaN(d) ? 0 : d;
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        }
    }

    private static long parseLong(String s) {
        try {
            return (long) Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
