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

    /** Back from a channel keeps it playing in a small window at the top right of Chaînes. */
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
}
