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

    /** Puts the choices the app-wide caches read (lists' age, the guide's shift) into effect. Main thread. */
    static void applyGlobals(Context c) {
        Library.keepHours(listHours(c));
        Epg.shiftHours(guideShiftHours(c));
    }

    // ---- added in 0.10: each one's default is how the app behaved before, so nothing changes until chosen ----

    /** Where the app opens when started from the home screen. */
    enum StartPage { HOME, LIVE, LAST_CHANNEL, FAVOURITES }

    static StartPage startPage(Context c) {
        try {
            return StartPage.valueOf(p(c).getString("start_page", StartPage.HOME.name()));
        } catch (IllegalArgumentException e) {
            return StartPage.HOME;
        }
    }

    static void setStartPage(Context c, StartPage s) {
        p(c).edit().putString("start_page", s.name()).apply();
    }

    /** The hours the guide is moved by, for a panel whose programme times are off by a time zone (-2 to +2). */
    static final int[] GUIDE_SHIFTS = {-2, -1, 0, 1, 2};

    static int guideShiftHours(Context c) {
        int h = p(c).getInt("guide_shift_hours", 0);
        return h < -2 || h > 2 ? 0 : h;
    }

    static void setGuideShiftHours(Context c, int hours) {
        p(c).edit().putInt("guide_shift_hours", Math.max(-2, Math.min(2, hours))).apply();
    }

    /** Up on the remote while watching goes to the channel before rather than the next one. */
    static boolean zapInverted(Context c) {
        return p(c).getBoolean("zap_inverted", false);
    }

    static void setZapInverted(Context c, boolean on) {
        p(c).edit().putBoolean("zap_inverted", on).apply();
    }

    /** How the picture fills the screen: whole picture, zoomed to fill (edges cut), or stretched. */
    enum Picture { FIT, ZOOM, STRETCH }

    static Picture picture(Context c) {
        try {
            return Picture.valueOf(p(c).getString("picture", Picture.FIT.name()));
        } catch (IllegalArgumentException e) {
            return Picture.FIT;
        }
    }

    static void setPicture(Context c, Picture pic) {
        p(c).edit().putString("picture", pic.name()).apply();
    }

    /** How long the provider's lists are kept before they are read again by themselves, in hours. */
    static final int[] LIST_HOURS = {6, 12, 24};

    static int listHours(Context c) {
        int h = p(c).getInt("list_hours", 6);
        for (int x : LIST_HOURS) if (x == h) return h;
        return 6;
    }

    static void setListHours(Context c, int hours) {
        p(c).edit().putInt("list_hours", hours).apply();
    }
}
