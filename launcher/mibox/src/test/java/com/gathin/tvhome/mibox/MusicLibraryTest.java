package com.gathin.tvhome.mibox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The library's names, order and search. */
public class MusicLibraryTest {
    @Test
    public void filesSortAsPeopleExpect() {
        List<String> l = new ArrayList<>(Arrays.asList("10 - Ten.mp3", "2 - Two.mp3", "001 - One.mp3", "b.mp3", "A.mp3"));
        l.sort(MusicLibrary::naturalCompare);
        assertEquals(Arrays.asList("001 - One.mp3", "2 - Two.mp3", "10 - Ten.mp3", "A.mp3", "b.mp3"), l);
    }

    @Test
    public void accentsDoNotChangeTheOrder() {
        assertTrue(MusicLibrary.naturalCompare("Été", "Ete") == 0);
        assertTrue(MusicLibrary.naturalCompare("école", "Zèbre") < 0);
    }

    @Test
    public void titleFromTagsOrFileName() {
        assertEquals("A song", MusicLibrary.title("A song", "03 - Whatever.mp3"));
        assertEquals("Whatever", MusicLibrary.title("", "03 - Whatever.mp3"));
        assertEquals("Whatever", MusicLibrary.title("03 - Whatever", "03 - Whatever.mp3"));
        assertEquals("Song", MusicLibrary.title("<unknown>", "Song.flac"));
    }

    @Test
    public void searchFindsWordsAndNumbers() {
        assertTrue(MusicLibrary.matches("La Fête · Été indien", "112 - La Fete.mp3", "ete indien"));
        assertTrue(MusicLibrary.matches("La Fête · Été indien", "112 - La Fete.mp3", "112"));
        assertTrue(MusicLibrary.matches("Track", "002 - Track.mp3", "2"));
        assertFalse(MusicLibrary.matches("Track", "012 - Track.mp3", "2"));
        assertFalse(MusicLibrary.matches("Ouverture", "001 - Ouverture.mp3", "fete"));
        assertTrue(MusicLibrary.matches("Anything", "x.mp3", "  "));
    }

    @Test
    public void aFavouriteIsItsFolderAndFileName() {
        assertEquals("Albums/01 - Song.mp3", MusicLibrary.favoriteKey("/storage/1A2B-3C4D/Albums/01 - Song.mp3"));
        assertEquals("Albums/01 - Song.mp3", MusicLibrary.favoriteKey("/storage/emulated/0/Albums/01 - Song.mp3"));
        assertEquals("", MusicLibrary.favoriteKey(null));
    }
}
