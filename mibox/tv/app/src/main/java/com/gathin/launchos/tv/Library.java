package com.gathin.launchos.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One section's catalogue (channels, films or series), kept for the whole app: the categories, then every item
 * at once, read as it arrives, so the home pages can show the newest films, rows by group and search across
 * everything. A panel that refuses the whole list is read one category at a time instead ({@link #partial}).
 * Everything is kept for {@link #MAX_AGE_MS}; the Actualiser screen drops it at once. Main thread only.
 */
final class Library {
    static final long MAX_AGE_MS = 6 * 60 * 60 * 1000L;
    /** How many of the newest items the home page and its hero use. */
    private static final int RECENT = 60;

    private static final Map<Xtream.Section, Library> ALL = new HashMap<>();
    private static int accountVersion = -1;
    private static int generation;

    final Xtream.Section section;
    private List<Xtream.Category> categories;
    private List<Xtream.Category> prefixFor;
    private String prefix;
    private List<Item> items;
    private Map<String, List<Item>> byCategory = new HashMap<>();
    private Map<String, Item> byId = new HashMap<>();
    private List<Item> recent = Collections.emptyList();
    private boolean partial;
    private XtreamException error;
    private long loadedAt;
    private boolean loading;
    private int epoch;
    private final List<Runnable> waiting = new ArrayList<>();
    private final Map<String, List<Runnable>> categoryWaiting = new HashMap<>();

    private Library(Xtream.Section section) {
        this.section = section;
    }

    static Library of(Xtream.Section s) {
        Library l = ALL.get(s);
        if (l == null) {
            l = new Library(s);
            ALL.put(s, l);
        }
        return l;
    }

    /** Forgets everything when the account changed since the last call. */
    static void forAccount(int version) {
        if (version == accountVersion) return;
        accountVersion = version;
        for (Library l : ALL.values()) l.forgetQuietly();
        generation++;
    }

    /** Changes whenever something is dropped, so a screen knows to show it again. */
    static int generation() {
        return generation;
    }

    // ---- state ----

    boolean ready() {
        return categories != null && fresh();
    }

    boolean loading() {
        return loading;
    }

    /** The last failure, when nothing could be read. */
    XtreamException error() {
        return error;
    }

    /** The prefix most of this section's groups share, taken off their names (null when none). */
    String groupPrefix() {
        List<Xtream.Category> cats = categories;
        if (cats == null) return null;
        if (prefixFor != cats) {
            List<String> names = new java.util.ArrayList<>();
            for (Xtream.Category c : cats) names.add(c.name);
            prefix = Names.groupPrefix(names);
            prefixFor = cats;
        }
        return prefix;
    }

    /** The panel's categories in its own order (null before the first load). */
    List<Xtream.Category> categories() {
        return categories;
    }

    /** Every item, or null when only some categories could be read. */
    List<Item> all() {
        return items;
    }

    boolean partial() {
        return partial;
    }

    /** A category's items in the panel's order, or null when not read yet. */
    List<Item> items(String categoryId) {
        return byCategory.get(categoryId);
    }

    Item find(String id) {
        return byId.get(id);
    }

    /** The newest items, newest first (from every category read so far). */
    List<Item> recent() {
        return recent;
    }

    long loadedAt() {
        return loadedAt;
    }

    // ---- loading ----

    /** Calls done on the main thread once the section is read (or failed); at once when it already is. */
    void load(Xtream api, Runnable done) {
        if (ready()) {
            done.run();
            return;
        }
        if (done != null) waiting.add(done);
        if (loading || api == null) {
            if (api == null) flush();
            return;
        }
        loading = true;
        int e = epoch;
        Xtream.Section s = section;
        Bg.run(() -> {
            List<Xtream.Category> cats = api.categories(s);
            List<Item> list = null;
            try {
                list = api.items(s, null);
            } catch (XtreamException failed) {
                // Too big, or a panel that wants a category: read the categories one by one when they are shown.
            }
            return new Object[]{cats, list, list == null ? null : index(list)};
        }, (result, failure) -> {
            if (e != epoch) return;
            loading = false;
            if (failure != null) {
                error = failure;
            } else {
                @SuppressWarnings("unchecked") List<Xtream.Category> cats = (List<Xtream.Category>) result[0];
                @SuppressWarnings("unchecked") List<Item> list = (List<Item>) result[1];
                error = null;
                categories = cats;
                loadedAt = System.currentTimeMillis();
                if (list != null) {
                    Index index = (Index) result[2];
                    items = list;
                    partial = false;
                    byCategory = index.byCategory;
                    byId = index.byId;
                    recent = index.recent;
                } else {
                    items = null;
                    partial = true;
                    byCategory = new HashMap<>();
                    byId = new HashMap<>();
                    recent = Collections.emptyList();
                }
            }
            flush();
        });
    }

    /** When the whole list could not be read: reads one category, then calls done (main thread). */
    void loadCategory(Xtream api, String categoryId, Runnable done) {
        if (byCategory.containsKey(categoryId) || api == null) {
            done.run();
            return;
        }
        List<Runnable> w = categoryWaiting.get(categoryId);
        if (w != null) {
            w.add(done);
            return;
        }
        w = new ArrayList<>();
        w.add(done);
        categoryWaiting.put(categoryId, w);
        int e = epoch;
        Xtream.Section s = section;
        Bg.run(() -> api.items(s, categoryId), (list, failure) -> {
            if (e != epoch) return;
            List<Runnable> all = categoryWaiting.remove(categoryId);
            if (list != null) {
                byCategory.put(categoryId, list);
                for (Item i : list) byId.put(i.id, i);
                List<Item> merged = new ArrayList<>(recent);
                merged.addAll(list);
                recent = newest(merged);
            }
            if (all != null) for (Runnable r : all) r.run();
        });
    }

    /** Drops everything; the next screen that shows the section reads it again. */
    void forget() {
        forgetQuietly();
        generation++;
    }

    private void forgetQuietly() {
        epoch++;
        categories = null;
        items = null;
        byCategory = new HashMap<>();
        byId = new HashMap<>();
        recent = Collections.emptyList();
        partial = false;
        error = null;
        loadedAt = 0;
        loading = false;
        categoryWaiting.clear();
        flush();
    }

    private void flush() {
        List<Runnable> all = new ArrayList<>(waiting);
        waiting.clear();
        for (Runnable r : all) r.run();
    }

    private boolean fresh() {
        long age = System.currentTimeMillis() - loadedAt;
        return age >= 0 && age < MAX_AGE_MS;
    }

    // ---- indexing (background thread) ----

    private static final class Index {
        final Map<String, List<Item>> byCategory = new LinkedHashMap<>();
        final Map<String, Item> byId = new HashMap<>();
        List<Item> recent;
    }

    private static Index index(List<Item> list) {
        Index x = new Index();
        for (Item i : list) {
            List<Item> c = x.byCategory.get(i.categoryId);
            if (c == null) {
                c = new ArrayList<>();
                x.byCategory.put(i.categoryId, c);
            }
            c.add(i);
            x.byId.put(i.id, i);
        }
        x.recent = newest(list);
        return x;
    }

    /** The newest items, newest first; items without a date are left out. */
    static List<Item> newest(List<Item> list) {
        List<Item> dated = new ArrayList<>();
        for (Item i : list) if (i.added > 0) dated.add(i);
        dated.sort((a, b) -> Long.compare(b.added, a.added));
        return new ArrayList<>(dated.subList(0, Math.min(RECENT, dated.size())));
    }
}
