package com.gathin.launchos.tv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * The owner's changes to the panel's groups (categories): a new name, hidden, or pinned to the top. Kept on the box
 * for this account only. Groups are shown pinned first (in the order they were pinned), then A to Z.
 */
final class Groups {
    private static final String PREFS = "groups";
    private static int version;

    private final SharedPreferences prefs;
    private final String account;

    private Groups(Context c, String account) {
        prefs = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.account = account;
    }

    /** Null when no account is saved. */
    static Groups get(Context c) {
        Account a = AccountStore.load(c);
        return a == null ? null : new Groups(c, AccountStore.key(a));
    }

    /** Changes after every edit, so pages know to show the groups again. */
    static int version() {
        return version;
    }

    /** Something that changes how groups and their channels are shown, outside the groups themselves. */
    static void touch() {
        version++;
    }

    private String key(Xtream.Section s, String id, String what) {
        return account + "|" + s.name() + "|" + id + "|" + what;
    }

    /** The name to show: the owner's, or the panel's without its decorations. */
    String name(Xtream.Section s, Xtream.Category c) {
        String custom = prefs.getString(key(s, c.id, "n"), null);
        return custom != null ? custom : panelName(s, c);
    }

    /** The panel's name, tidied: no shared "FR |" prefix, no stars, no capitals. */
    static String panelName(Xtream.Section s, Xtream.Category c) {
        return Names.group(c.name, Library.of(s).groupPrefix());
    }

    boolean renamed(Xtream.Section s, String id) {
        return prefs.contains(key(s, id, "n"));
    }

    boolean hidden(Xtream.Section s, String id) {
        return prefs.getBoolean(key(s, id, "h"), false);
    }

    boolean pinned(Xtream.Section s, String id) {
        return prefs.getLong(key(s, id, "p"), 0) > 0;
    }

    /** A blank name gives the panel's name back. */
    void rename(Xtream.Section s, String id, String name) {
        String n = name == null ? "" : name.trim();
        SharedPreferences.Editor e = prefs.edit();
        if (n.isEmpty()) e.remove(key(s, id, "n"));
        else e.putString(key(s, id, "n"), n);
        e.apply();
        version++;
    }

    void setHidden(Xtream.Section s, String id, boolean hidden) {
        SharedPreferences.Editor e = prefs.edit();
        if (hidden) e.putBoolean(key(s, id, "h"), true);
        else e.remove(key(s, id, "h"));
        e.apply();
        version++;
    }

    void setPinned(Xtream.Section s, String id, boolean pinned) {
        SharedPreferences.Editor e = prefs.edit();
        if (pinned) e.putLong(key(s, id, "p"), System.currentTimeMillis());
        else e.remove(key(s, id, "p"));
        e.apply();
        version++;
    }

    /** Back to the panel's groups for this section: names, hidden and pinned. */
    void reset(Xtream.Section s) {
        String prefix = account + "|" + s.name() + "|";
        SharedPreferences.Editor e = prefs.edit();
        for (String k : prefs.getAll().keySet()) if (k.startsWith(prefix)) e.remove(k);
        e.apply();
        version++;
    }

    /** The groups in the order they are shown, hidden ones left out unless asked for. */
    List<Xtream.Category> ordered(Xtream.Section s, List<Xtream.Category> cats, boolean withHidden) {
        List<Xtream.Category> shown = new ArrayList<>();
        for (Xtream.Category c : cats) if (withHidden || !hidden(s, c.id)) shown.add(c);
        return order(shown, c -> name(s, c), c -> prefs.getLong(key(s, c.id, "p"), 0));
    }

    /** Pinned first by when they were pinned, then A to Z (accents, case and numbering ignored). */
    static List<Xtream.Category> order(List<Xtream.Category> cats, Function<Xtream.Category, String> name,
                                       ToLongFunction<Xtream.Category> pinnedAt) {
        List<Xtream.Category> out = new ArrayList<>(cats);
        java.util.Map<Xtream.Category, String> keys = new java.util.HashMap<>();
        for (Xtream.Category c : out) keys.put(c, Names.groupKey(name.apply(c)));
        out.sort((a, b) -> {
            long pa = pinnedAt.applyAsLong(a), pb = pinnedAt.applyAsLong(b);
            if ((pa > 0) != (pb > 0)) return pa > 0 ? -1 : 1;
            if (pa > 0) return Long.compare(pa, pb);
            int k = keys.get(a).compareTo(keys.get(b));
            return k != 0 ? k : name.apply(a).compareTo(name.apply(b));
        });
        return out;
    }
}
