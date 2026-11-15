package com.gathin.launchos.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * How TV's channels are put on shelves for the screen: Favoris, Récentes, then one shelf per theme for the home
 * country (Généralistes, Info, Sport…), then one per other country. Presentation only: the panel's channels, ids,
 * names and groups are read, never changed, and the panel's own groups stay available as they are.
 *
 * <p>One channel offered in several versions (HD, SD, FHD, 4K, or the same one in two groups) is one entry on a
 * shelf, its best version first and every version kept and playable. Two items are versions of one channel only when
 * their names are the same once the country prefix and the quality mark are taken off ({@link Item#key}), and they
 * are for the same country: "France 2" and "France 24", or "TF1" and "TF1 Séries Films", stay apart. Each item keeps
 * its id, which favourites, hidden channels, history, the chosen stream format and the guide all use. Pure: no
 * Android, so it is tested on the computer.
 */
final class TvShelves {
    enum Kind { FAVOURITES, RECENT, THEME, COUNTRY }

    /** One channel on a shelf: the version shown, and all its versions (the shown one included), best first. */
    static final class Entry {
        final Item main;
        final List<Item> versions;

        Entry(Item main, List<Item> versions) {
            this.main = main;
            this.versions = Collections.unmodifiableList(versions);
        }

        boolean has(String id) {
            for (Item i : versions) if (i.id.equals(id)) return true;
            return false;
        }
    }

    static final class Shelf {
        final Kind kind;
        /** For a THEME shelf. */
        final Smart.Theme theme;
        /** For a COUNTRY shelf: the country's code ("be", "uk"…). */
        final String country;
        final List<Entry> entries;

        Shelf(Kind kind, Smart.Theme theme, String country, List<Entry> entries) {
            this.kind = kind;
            this.theme = theme;
            this.country = country;
            this.entries = Collections.unmodifiableList(entries);
        }

        /** A key that stays the same across rebuilds, to keep the shelf chosen. */
        String key() {
            return kind == Kind.THEME ? "t:" + theme : kind == Kind.COUNTRY ? "c:" + country : kind.name();
        }
    }

    /** What the shelves are built from; the context-bound checks come in as plain functions. */
    static final class Input {
        /** Every channel of the panel. */
        List<Item> all = new ArrayList<>();
        /** The panel's group names, by group id, as the panel sent them. */
        Map<String, String> groupNames = new HashMap<>();
        /** A channel the owner hid, or in a group that is hidden (by hand, or for adults while those are hidden). */
        Predicate<Item> hidden = i -> false;
        /** Whether groups for adults are shown at all (Réglages). */
        boolean showAdult;
        /** Favoris and Récentes, in their order, as remembered (ids may be gone from the panel). */
        List<Item> favourites = new ArrayList<>();
        List<Item> recent = new ArrayList<>();
    }

    private TvShelves() {
    }

    static List<Shelf> build(Input in) {
        Map<String, String> countryOf = new HashMap<>();
        Map<String, Smart.Theme> themeOf = new HashMap<>();
        List<Item> visible = new ArrayList<>();
        for (Item i : in.all) if (!in.hidden.test(i)) visible.add(i);

        // Versions of one channel, by country and name; the panel's order is kept for the first one seen.
        LinkedHashMap<String, List<Item>> byChannel = new LinkedHashMap<>();
        for (Item i : visible) byChannel.computeIfAbsent(versionKey(i, country(i, in, countryOf)), k -> new ArrayList<>()).add(i);
        Map<String, Entry> entryOf = new HashMap<>();
        List<Entry> entries = new ArrayList<>();
        for (List<Item> versions : byChannel.values()) {
            List<Item> sorted = new ArrayList<>(versions);
            sorted.sort((a, b) -> Integer.compare(Smart.qualityRank(a.tag), Smart.qualityRank(b.tag)));
            Entry e = new Entry(sorted.get(0), sorted);
            entries.add(e);
            for (Item v : sorted) entryOf.put(v.id, e);
        }

        List<Shelf> out = new ArrayList<>();
        List<Entry> fav = remembered(in.favourites, entryOf, in);
        if (!fav.isEmpty()) out.add(new Shelf(Kind.FAVOURITES, null, "", fav));
        List<Entry> recent = remembered(in.recent, entryOf, in);
        if (!recent.isEmpty()) out.add(new Shelf(Kind.RECENT, null, "", recent));

        EnumMap<Smart.Theme, List<Entry>> themes = new EnumMap<>(Smart.Theme.class);
        TreeMap<String, List<Entry>> countries = new TreeMap<>((a, b) -> {
            int r = Integer.compare(nearRank(a), nearRank(b));
            return r != 0 ? r : a.compareTo(b);
        });
        for (Entry e : entries) {
            String c = country(e.main, in, countryOf);
            if (c.isEmpty() || c.equals(Smart.HOME)) {
                Smart.Theme t = theme(e.main, in, themeOf);
                if (t == Smart.Theme.ADULT && !in.showAdult) continue;
                themes.computeIfAbsent(t, k -> new ArrayList<>()).add(e);
            } else {
                if (theme(e.main, in, themeOf) == Smart.Theme.ADULT && !in.showAdult) continue;
                countries.computeIfAbsent(c, k -> new ArrayList<>()).add(e);
            }
        }
        for (Map.Entry<Smart.Theme, List<Entry>> t : themes.entrySet()) {
            out.add(new Shelf(Kind.THEME, t.getKey(), "", ordered(t.getValue())));
        }
        for (Map.Entry<String, List<Entry>> c : countries.entrySet()) {
            out.add(new Shelf(Kind.COUNTRY, null, c.getKey(), ordered(c.getValue())));
        }
        return out;
    }

    /**
     * The key that makes two items versions of one channel: their country and their name without prefix nor quality.
     * An item whose name says nothing once cleaned keeps its own id, so it is never merged.
     */
    static String versionKey(Item i, String country) {
        return i.key == null || i.key.isEmpty() ? "id:" + i.id : country + "|" + i.key;
    }

    /**
     * Favoris and Récentes keep the exact channel remembered as the one shown (a favourite set on the SD version stays
     * that version), with the other versions of that channel next to it. A remembered channel the panel no longer
     * has, or that is hidden now, is left out.
     */
    private static List<Entry> remembered(List<Item> items, Map<String, Entry> entryOf, Input in) {
        List<Entry> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Item r : items) {
            Entry e = entryOf.get(r.id);
            if (e == null || !seen.add(r.id)) continue;
            Item exact = r;
            for (Item v : e.versions) if (v.id.equals(r.id)) exact = v;
            List<Item> versions = new ArrayList<>(e.versions);
            versions.remove(exact);
            versions.add(0, exact);
            out.add(new Entry(exact, versions));
        }
        return out;
    }

    /** The French TNT channels by number first, then A to Z, as the screen lists them. */
    private static List<Entry> ordered(List<Entry> l) {
        List<Entry> out = new ArrayList<>(l);
        out.sort((a, b) -> {
            int na = Smart.tntFolded(a.main.key), nb = Smart.tntFolded(b.main.key);
            if ((na > 0) != (nb > 0)) return na > 0 ? -1 : 1;
            if (na != nb) return Integer.compare(na, nb);
            return a.main.key.compareTo(b.main.key);
        });
        return out;
    }

    /** The country a channel is for: its group's, else a country written in front of its own name; "" for none. */
    static String country(Item i, Input in, Map<String, String> cache) {
        String group = i.categoryId == null ? "" : i.categoryId;
        String c = cache.get(group);
        if (c == null) {
            c = Smart.country(in.groupNames.get(group));
            cache.put(group, c);
        }
        if (!c.isEmpty()) return c;
        // "UK: BBC One": the panel put the country in front of the name, and Names took it off the title.
        return i.name != null && !i.name.equals(i.title) ? Smart.country(i.name) : "";
    }

    /**
     * A channel's theme: its group's; a group that says nothing leaves it to the channel's own name; a channel with
     * a French TNT number is a Généraliste when nothing else is said.
     */
    static Smart.Theme theme(Item i, Input in, Map<String, Smart.Theme> cache) {
        String group = i.categoryId == null ? "" : i.categoryId;
        Smart.Theme t = cache.get(group);
        if (t == null) {
            t = Smart.theme(in.groupNames.get(group));
            cache.put(group, t);
        }
        if (t != Smart.Theme.OTHER) return t;
        Smart.Theme own = Smart.theme(i.title);
        if (own != Smart.Theme.OTHER) return own;
        return Smart.tntFolded(i.key) > 0 ? Smart.Theme.GENERAL : Smart.Theme.OTHER;
    }

    /** Countries close to home come first among the other countries. */
    private static int nearRank(String c) {
        int n = NEAR.indexOf(c);
        return n >= 0 ? n : NEAR.size();
    }

    private static final List<String> NEAR = java.util.Arrays.asList("be", "ch", "ca", "lu", "mc");
}
