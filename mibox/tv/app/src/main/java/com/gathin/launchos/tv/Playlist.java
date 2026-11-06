package com.gathin.launchos.tv;

import java.util.Collections;
import java.util.List;

/** The channels of the category the player was opened from, so CH+ and CH− can zap inside it. */
final class Playlist {
    static List<Item> channels = Collections.emptyList();
    static String category = "";

    private Playlist() {
    }
}
