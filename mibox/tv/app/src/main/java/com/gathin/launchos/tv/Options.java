package com.gathin.launchos.tv;

import android.content.Context;
import android.content.SharedPreferences;

/** The owner's choices in Réglages that are not about groups. */
final class Options {
    private static final String PREFS = "options";

    private Options() {
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Back from a channel keeps it playing in a small window at the bottom right of Chaînes. */
    static boolean miniPlayer(Context c) {
        return p(c).getBoolean("mini_player", true);
    }

    static void setMiniPlayer(Context c, boolean on) {
        p(c).edit().putBoolean("mini_player", on).apply();
    }

    /** At the end of an episode, the next one starts. */
    static boolean nextEpisode(Context c) {
        return p(c).getBoolean("next_episode", true);
    }

    static void setNextEpisode(Context c, boolean on) {
        p(c).edit().putBoolean("next_episode", on).apply();
    }

    /** Channel names without their language prefix, with the quality as a small tag. */
    static boolean tidyNames(Context c) {
        return p(c).getBoolean("tidy_names", true);
    }

    static void setTidyNames(Context c, boolean on) {
        p(c).edit().putBoolean("tidy_names", on).apply();
    }

    /** How groups are put in order: smart (home country, then themes), A to Z, or as the panel sends them. */
    static Smart.GroupOrder groupOrder(Context c) {
        try {
            return Smart.GroupOrder.valueOf(p(c).getString("group_order", Smart.GroupOrder.SMART.name()));
        } catch (IllegalArgumentException e) {
            return Smart.GroupOrder.SMART;
        }
    }

    static void setGroupOrder(Context c, Smart.GroupOrder o) {
        p(c).edit().putString("group_order", o.name()).apply();
    }

    /** How channels are put in order: TNT numbers then A to Z, as the panel sends them, or A to Z. */
    static Smart.ChannelOrder channelOrder(Context c) {
        try {
            return Smart.ChannelOrder.valueOf(p(c).getString("channel_order", Smart.ChannelOrder.TNT.name()));
        } catch (IllegalArgumentException e) {
            return Smart.ChannelOrder.TNT;
        }
    }

    static void setChannelOrder(Context c, Smart.ChannelOrder o) {
        p(c).edit().putString("channel_order", o.name()).apply();
    }

    /** The same channel in several qualities is shown once, in the best one. */
    static boolean dedupe(Context c) {
        return p(c).getBoolean("dedupe", true);
    }

    static void setDedupe(Context c, boolean on) {
        p(c).edit().putBoolean("dedupe", on).apply();
    }

    /**
     * Whether live channels of this account played last time as HLS rather than MPEG-TS, so the next channel asks for
     * that format first. Keyed by AccountStore.key (a hash), never by the address.
     */
    static boolean liveHls(Context c, String accountKey) {
        return p(c).getBoolean("live_hls_" + accountKey, false);
    }

    static void setLiveHls(Context c, String accountKey, boolean hls) {
        if (liveHls(c, accountKey) == hls) return;
        p(c).edit().putBoolean("live_hls_" + accountKey, hls).apply();
    }

    /** Forgets what was learnt about accounts (the account was deleted); the owner's choices stay. */
    static void forgetAccounts(Context c) {
        SharedPreferences.Editor e = p(c).edit();
        for (String k : p(c).getAll().keySet()) if (k.startsWith("live_hls_")) e.remove(k);
        e.apply();
    }

    /** Groups for adults stay out of sight unless shown one by one. */
    static boolean hideAdult(Context c) {
        return p(c).getBoolean("hide_adult", true);
    }

    static void setHideAdult(Context c, boolean on) {
        p(c).edit().putBoolean("hide_adult", on).apply();
    }
}
