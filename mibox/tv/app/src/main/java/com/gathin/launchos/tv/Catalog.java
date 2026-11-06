package com.gathin.launchos.tv;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The provider's lists, kept for the whole app (main thread only). A list older than {@link #MAX_AGE_MS} is
 * fetched again the next time it is shown; the Actualiser screen drops a section at once. {@link #generation()}
 * changes whenever something is dropped, so a screen knows to reload what it shows.
 */
final class Catalog {
    static final long MAX_AGE_MS = 6 * 60 * 60 * 1000L;

    private static final class Entry<T> {
        final T value;
        final long at;

        Entry(T value, long at) {
            this.value = value;
            this.at = at;
        }
    }

    private static final Map<Xtream.Section, Entry<List<Xtream.Category>>> CATEGORIES = new HashMap<>();
    private static final Map<String, Entry<List<Item>>> ITEMS = new HashMap<>();
    private static int accountVersion = -1;
    private static int generation;

    private Catalog() {
    }

    /** Forgets everything when the account changed since the last call. */
    static void forAccount(int version) {
        if (version == accountVersion) return;
        accountVersion = version;
        CATEGORIES.clear();
        ITEMS.clear();
        generation++;
    }

    static int generation() {
        return generation;
    }

    static List<Xtream.Category> categories(Xtream.Section s) {
        Entry<List<Xtream.Category>> e = CATEGORIES.get(s);
        return fresh(e) ? e.value : null;
    }

    static void putCategories(Xtream.Section s, List<Xtream.Category> list) {
        CATEGORIES.put(s, new Entry<>(list, System.currentTimeMillis()));
    }

    static List<Item> items(Xtream.Section s, String categoryId) {
        Entry<List<Item>> e = ITEMS.get(key(s, categoryId));
        return fresh(e) ? e.value : null;
    }

    static void putItems(Xtream.Section s, String categoryId, List<Item> list) {
        ITEMS.put(key(s, categoryId), new Entry<>(list, System.currentTimeMillis()));
    }

    /** When the section's categories were last fetched, or 0. */
    static long loadedAt(Xtream.Section s) {
        Entry<List<Xtream.Category>> e = CATEGORIES.get(s);
        return e == null ? 0 : e.at;
    }

    /** How many of the section's categories are known, or -1 when none were fetched. */
    static int categoryCount(Xtream.Section s) {
        Entry<List<Xtream.Category>> e = CATEGORIES.get(s);
        return e == null ? -1 : e.value.size();
    }

    /** Drops the section's categories and every list inside them. */
    static void forget(Xtream.Section s) {
        CATEGORIES.remove(s);
        String prefix = s.name() + "/";
        for (Iterator<String> it = ITEMS.keySet().iterator(); it.hasNext(); ) {
            if (it.next().startsWith(prefix)) it.remove();
        }
        generation++;
    }

    private static boolean fresh(Entry<?> e) {
        if (e == null) return false;
        long age = System.currentTimeMillis() - e.at;
        return age >= 0 && age < MAX_AGE_MS;
    }

    private static String key(Xtream.Section s, String categoryId) {
        return s.name() + "/" + categoryId;
    }
}
