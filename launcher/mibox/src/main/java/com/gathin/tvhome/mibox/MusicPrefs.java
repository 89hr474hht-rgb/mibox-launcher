package com.gathin.tvhome.mibox;

import android.content.Context;
import android.content.SharedPreferences;

/** The music player's settings and memory, in one preferences file, with every key in one place. */
final class MusicPrefs {
    private static final String FILE = "mibox_music";

    // Settings → Musique
    static final String HOME_CARD = "home_card";
    static final String BUBBLE = "bubble";
    // Lyrics options
    static final String LYRICS_SIZE = "lyrics_size";
    static final String LYRICS_TRANSLATION = "lyrics_translation";
    static final String LYRICS_WORDS = "lyrics_words";
    static final String LYRICS_OFFSET = "lyrics_offset";
    // Favourites: "folder/file" keys
    static final String FAVORITES = "favorites";
    // The last track, for Resume
    static final String LAST_COLLECTION = "collection";
    static final String LAST_COLLECTION_NAME = "collection_name";
    static final String LAST_URI = "uri";
    static final String LAST_TITLE = "title";
    static final String LAST_POSITION = "position";

    private MusicPrefs() {
    }

    static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
