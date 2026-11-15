package com.gathin.launchos.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The owner's choices about single channels, on the box only and for this account: channels added to Favoris by
 * hand (in the order they were added), and channels hidden from every list. Each keeps its name, logo and number,
 * so Favoris and the list of hidden channels can be shown before the panel's lists are read.
 */
final class Channels {
    private static final String PREFS = "channels";
    private static final String STAR = "s|", HIDE = "h|";
    private static int version;

    private final SharedPreferences prefs;
    private final String account;

    private Channels(Context c, String account) {
        prefs = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.account = account;
    }

    /** Null when no account is saved. */
    static Channels get(Context c) {
        Account a = AccountStore.load(c);
        return a == null ? null : new Channels(c, AccountStore.key(a));
    }

    /** Forgets every starred and hidden channel, for every account (the account was deleted). */
    static void forgetAll(Context c) {
        c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        version++;
    }

    /** Changes after every edit, so Chaînes knows to show its lists again. */
    static int version() {
        return version;
    }

    private String key(String what, String id) {
        return account + "|" + what + id;
    }

    boolean starred(String id) {
        return prefs.contains(key(STAR, id));
    }

    boolean hidden(String id) {
        return prefs.contains(key(HIDE, id));
    }

    void setStarred(Item i, boolean on) {
        set(STAR, i, on);
    }

    void setHidden(Item i, boolean on) {
        set(HIDE, i, on);
        if (on) set(STAR, i, false);
    }

    private void set(String what, Item i, boolean on) {
        SharedPreferences.Editor e = prefs.edit();
        if (on) {
            JSONArray a = new JSONArray();
            a.put(System.currentTimeMillis()).put(i.name).put(i.image).put(i.num).put(i.categoryId);
            e.putString(key(what, i.id), a.toString());
        } else {
            e.remove(key(what, i.id));
        }
        e.apply();
        version++;
    }

    /**
     * Moves a channel of Favoris to another place among those added by hand (0 is the first); the others keep their
     * order. Nothing happens for a channel that was not added by hand.
     */
    void moveStarred(String id, int to) {
        List<Item> order = starredItems();
        int from = -1;
        for (int i = 0; i < order.size(); i++) if (order.get(i).id.equals(id)) from = i;
        if (from < 0) return;
        to = Math.max(0, Math.min(order.size() - 1, to));
        if (to == from) return;
        order.add(to, order.remove(from));
        SharedPreferences.Editor e = prefs.edit();
        for (int i = 0; i < order.size(); i++) {
            String k = key(STAR, order.get(i).id);
            try {
                JSONArray a = new JSONArray(prefs.getString(k, "[]"));
                a.put(0, i + 1L);   // the place is the first field: small numbers keep every later addition after them
                e.putString(k, a.toString());
            } catch (JSONException ignored) {
                // A damaged entry keeps its place.
            }
        }
        e.apply();
        version++;
    }

    /** Where a channel stands among those added to Favoris by hand, -1 when it is not one of them. */
    int starredIndex(String id) {
        List<Item> order = starredItems();
        for (int i = 0; i < order.size(); i++) if (order.get(i).id.equals(id)) return i;
        return -1;
    }

    /** Channels added to Favoris, first added first. */
    List<Item> starredItems() {
        return items(STAR);
    }

    /** Hidden channels, first hidden first. */
    List<Item> hiddenItems() {
        return items(HIDE);
    }

    private List<Item> items(String what) {
        String prefix = account + "|" + what;
        List<long[]> order = new ArrayList<>();
        List<Item> found = new ArrayList<>();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (!e.getKey().startsWith(prefix) || !(e.getValue() instanceof String)) continue;
            try {
                JSONArray a = new JSONArray((String) e.getValue());
                String id = e.getKey().substring(prefix.length());
                found.add(Item.live(id, a.optString(1), a.optString(2), a.optInt(3), a.optString(4)));
                order.add(new long[]{a.optLong(0), found.size() - 1});
            } catch (JSONException ignored) {
                // A damaged entry is left out.
            }
        }
        order.sort((x, y) -> Long.compare(x[0], y[0]));
        List<Item> out = new ArrayList<>();
        for (long[] o : order) out.add(found.get((int) o[1]));
        return out;
    }

    /** The list without its hidden channels. */
    List<Item> visible(List<Item> items) {
        if (items == null) return null;
        List<Item> out = new ArrayList<>(items.size());
        for (Item i : items) if (!hidden(i.id)) out.add(i);
        return out;
    }

    /** Shows every hidden channel again. */
    void showAll() {
        SharedPreferences.Editor e = prefs.edit();
        String prefix = account + "|" + HIDE;
        for (String k : prefs.getAll().keySet()) if (k.startsWith(prefix)) e.remove(k);
        e.apply();
        version++;
    }
}
