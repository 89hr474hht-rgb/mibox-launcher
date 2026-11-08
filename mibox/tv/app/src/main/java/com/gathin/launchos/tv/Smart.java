package com.gathin.launchos.tv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.regex.Pattern;

/**
 * Smart sorting, from the names alone (nothing is sent anywhere): what a group is about, which country it is for,
 * groups for adults, the French TNT numbers of channels, and the same channel offered in several qualities.
 */
final class Smart {
    /** What a group is about, in the order smart sorting shows them. */
    enum Theme { GENERAL, NEWS, SPORT, MOVIES, SERIES, KIDS, DOCS, ENTERTAINMENT, MUSIC, REGIONAL, OTHER, ADULT }

    /** How groups are put in order. */
    enum GroupOrder { SMART, AZ, PANEL }

    /** How the channels of a group are put in order. */
    enum ChannelOrder { TNT, PANEL, AZ }

    static final String HOME = "fr";

    private static final Pattern ADULT = Pattern.compile(
            "(?i)(?:\\bxxx\\b|\\badult[eo]?s?\\b|\\bporn\\w*|\\+\\s?18\\b|\\b18\\s?\\+|\\bfor adults\\b|\\bcharme\\b)");

    /** Words (folded) that tell the theme; the first theme with a word in the name wins, in this order. */
    private static final Object[][] THEMES = {
            {Theme.KIDS, "enfant", "enfants", "jeunesse", "kids", "kid", "junior", "juniors", "cartoon", "cartoons",
                    "dessin", "dessins", "disney", "gulli", "toons", "nickelodeon", "nick", "piwi", "tiji", "boomerang"},
            {Theme.SPORT, "sport", "sports", "foot", "football", "soccer", "ligue", "rugby", "tennis", "f1", "formule",
                    "formula", "nba", "nfl", "nhl", "ufc", "boxe", "boxing", "bein", "eurosport", "golf", "cyclisme",
                    "moto", "motogp", "handball", "basket", "dazn", "equipe", "match", "matchs"},
            {Theme.NEWS, "info", "infos", "information", "informations", "news", "actualite", "actualites", "journal",
                    "bfm", "bfmtv", "cnews", "lci", "franceinfo", "euronews"},
            {Theme.DOCS, "doc", "docs", "documentaire", "documentaires", "documentary", "decouverte", "decouvertes",
                    "nature", "histoire", "history", "science", "sciences", "geographic", "animaux", "planete",
                    "ushuaia", "discovery"},
            {Theme.MUSIC, "musique", "musiques", "music", "clip", "clips", "radio", "radios", "mtv", "mezzo", "concert",
                    "concerts"},
            {Theme.MOVIES, "cinema", "cine", "film", "films", "movie", "movies", "ocs", "box office"},
            {Theme.SERIES, "serie", "series", "show", "shows", "feuilleton", "feuilletons"},
            {Theme.REGIONAL, "region", "regions", "regional", "regionales", "local", "locales", "locale"},
            {Theme.ENTERTAINMENT, "divertissement", "divertissements", "variete", "varietes", "entertainment",
                    "telerealite", "realite", "culture", "lifestyle", "cuisine", "jeux", "humour"},
            {Theme.GENERAL, "generaliste", "generalistes", "tnt", "nationale", "nationales", "principales",
                    "principal", "general", "generales", "hertzien"},
    };

    /** Country words at the start of a panel's group name, folded, and the code they stand for. */
    private static final Map<String, String> COUNTRIES = new HashMap<>();
    /** Countries close to home for a French household: after home, before the rest. */
    private static final List<String> NEAR = java.util.Arrays.asList("be", "ch", "ca", "lu", "mc");

    static {
        String[][] c = {
                {"fr", "fr", "france", "french", "francais", "vf", "vff"},
                {"be", "be", "belgique", "belgium", "belge"},
                {"ch", "ch", "suisse", "swiss"},
                {"ca", "ca", "qc", "quebec", "canada"},
                {"lu", "lu", "luxembourg"},
                {"mc", "mc", "monaco"},
                {"uk", "uk", "gb", "england", "british"},
                {"us", "us", "usa", "america"},
                {"de", "de", "ger", "germany", "deutschland", "allemagne"},
                {"it", "it", "ita", "italia", "italie", "italy"},
                {"es", "es", "esp", "espana", "espagne", "spain"},
                {"pt", "pt", "portugal"},
                {"nl", "nl", "netherlands", "pays bas"},
                {"ar", "ar", "arab", "arabic", "arabe"},
                {"tr", "tr", "turk", "turkey", "turquie"},
                {"ma", "maroc", "morocco"},
                {"dz", "dz", "algerie", "algeria"},
                {"tn", "tn", "tunisie", "tunisia"},
                {"af", "af", "afrique", "africa"},
                {"pl", "pl", "polska", "pologne"},
                {"ro", "ro", "romania", "roumanie"},
                {"in", "india", "inde"},
                {"exyu", "exyu", "ex yu"},
        };
        for (String[] row : c) for (int i = 1; i < row.length; i++) COUNTRIES.put(row[i], row[0]);
    }

    /** French TNT channels and their numbers, by folded name. */
    private static final Map<String, Integer> TNT = new HashMap<>();

    static {
        Object[][] t = {
                {1, "tf1"}, {2, "france 2"}, {3, "france 3"}, {4, "canal", "canal plus"}, {5, "france 5"}, {6, "m6"},
                {7, "arte"}, {8, "c8"}, {9, "w9"}, {10, "tmc"}, {11, "tfx"}, {12, "nrj 12", "nrj12"},
                {13, "lcp", "lcp an", "public senat", "lcp public senat"}, {14, "france 4"},
                {15, "bfm tv", "bfmtv"}, {16, "cnews"}, {17, "cstar"}, {18, "gulli"},
                {20, "tf1 series films"}, {21, "l equipe", "lequipe"}, {22, "6ter"}, {23, "rmc story"},
                {24, "rmc decouverte"}, {25, "cherie 25"}, {26, "lci"}, {27, "franceinfo", "france info"},
        };
        for (Object[] row : t) for (int i = 1; i < row.length; i++) TNT.put((String) row[i], (Integer) row[0]);
    }

    private Smart() {
    }

    /** Whether a group or channel name is for adults: kept out of sight in smart mode. */
    static boolean adult(String raw) {
        return raw != null && ADULT.matcher(raw).find();
    }

    static Theme theme(String raw) {
        if (adult(raw)) return Theme.ADULT;
        String f = " " + Names.fold(raw) + " ";
        for (Object[] row : THEMES) {
            for (int i = 1; i < row.length; i++) {
                if (f.contains(" " + row[i] + " ")) return (Theme) row[0];
            }
        }
        return Theme.OTHER;
    }

    /** The country a panel's group is for ("fr", "uk"…), from its first word; "" when it says none. */
    static String country(String raw) {
        String f = Names.fold(raw);
        if (f.isEmpty()) return "";
        String[] w = f.split(" ");
        String one = COUNTRIES.get(w[0]);
        if (one != null && (w.length > 1 || !w[0].equals("ca"))) return one;
        if (w.length > 1) {
            String two = COUNTRIES.get(w[0] + " " + w[1]);
            if (two != null) return two;
        }
        return "";
    }

    /** 0 for home (or no country said), 1 for countries close to home, 2 for the rest. */
    static int countryRank(String raw) {
        String c = country(raw);
        if (c.isEmpty() || c.equals(HOME)) return 0;
        return NEAR.contains(c) ? 1 : 2;
    }

    /**
     * Groups in order. Pinned groups come first, in the order they were pinned, whatever the mode. Smart: home
     * country first, then the countries close to it, then the rest; inside each, by theme (general, news, sport,
     * films…), then A to Z. AZ: A to Z. PANEL: as the panel sends them.
     */
    static List<Xtream.Category> orderGroups(List<Xtream.Category> cats, GroupOrder mode,
                                             Function<Xtream.Category, String> shown, ToLongFunction<Xtream.Category> pinnedAt) {
        List<Xtream.Category> out = new ArrayList<>(cats);
        Map<Xtream.Category, Integer> panel = new HashMap<>();
        Map<Xtream.Category, String> keys = new HashMap<>();
        for (int i = 0; i < out.size(); i++) {
            Xtream.Category c = out.get(i);
            panel.put(c, i);
            keys.put(c, Names.groupKey(shown.apply(c)));
        }
        out.sort((a, b) -> {
            long pa = pinnedAt.applyAsLong(a), pb = pinnedAt.applyAsLong(b);
            if ((pa > 0) != (pb > 0)) return pa > 0 ? -1 : 1;
            if (pa > 0) return Long.compare(pa, pb);
            if (mode == GroupOrder.PANEL) return Integer.compare(panel.get(a), panel.get(b));
            if (mode == GroupOrder.SMART) {
                int r = Integer.compare(countryRank(a.name), countryRank(b.name));
                if (r != 0) return r;
                r = theme(a.name).compareTo(theme(b.name));
                if (r != 0) return r;
            }
            int k = keys.get(a).compareTo(keys.get(b));
            return k != 0 ? k : shown.apply(a).compareTo(shown.apply(b));
        });
        return out;
    }

    /** The channel's French TNT number, or 0. */
    static int tnt(String title) {
        return tntFolded(Names.fold(title));
    }

    /** Same, from a name already folded (an item's key): no work per channel on long lists. */
    static int tntFolded(String f) {
        Integer n = TNT.get(f);
        if (n != null) return n;
        if (f.startsWith("france 3 ")) return 3;   // France 3's regional editions
        return 0;
    }

    /** Lower is better: the quality kept when the same channel comes several times. Full HD first, it is steady. */
    static int qualityRank(String tag) {
        switch (tag == null ? "" : tag.toUpperCase(Locale.ROOT)) {
            case "FHD":
            case "1080P":
                return 0;
            case "HD":
            case "720P":
                return 1;
            case "":
                return 2;
            case "4K":
            case "UHD":
                return 3;
            case "HEVC":
            case "H265":
                return 4;
            case "SD":
                return 5;
            default:
                return 2;
        }
    }

    /** One entry per channel name: the best quality is kept, at the place of the first one. */
    static List<Item> dedupe(List<Item> items) {
        LinkedHashMap<String, Item> best = new LinkedHashMap<>();
        for (Item i : items) {
            String k = i.key.isEmpty() ? i.id : i.key;
            Item have = best.get(k);
            if (have == null || qualityRank(i.tag) < qualityRank(have.tag)) {
                if (have == null) best.put(k, i);
                else best.replace(k, i);
            }
        }
        return new ArrayList<>(best.values());
    }

    /** Channels in order, without their doubles when asked. TNT: numbered channels by number, then A to Z. */
    static List<Item> arrange(List<Item> items, ChannelOrder mode, boolean dedupe) {
        List<Item> out = dedupe ? dedupe(items) : new ArrayList<>(items);
        if (mode == ChannelOrder.PANEL) return out;
        Map<Item, Integer> numbers = new HashMap<>();
        for (Item i : out) numbers.put(i, mode == ChannelOrder.TNT ? tntFolded(i.key) : 0);
        out.sort((a, b) -> {
            int na = numbers.get(a), nb = numbers.get(b);
            if ((na > 0) != (nb > 0)) return na > 0 ? -1 : 1;
            if (na != nb) return Integer.compare(na, nb);
            int k = a.key.compareTo(b.key);
            return k != 0 ? k : Integer.compare(qualityRank(a.tag), qualityRank(b.tag));
        });
        return out;
    }
}
