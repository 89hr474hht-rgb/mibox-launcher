package com.gathin.tvhome.mibox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RemoteRemapTest {
    @Test
    public void onlyTheThreeAppButtonsAreHandled() {
        assertTrue(RemoteRemap.isAppButton(190));
        assertTrue(RemoteRemap.isAppButton(193));
        assertTrue(RemoteRemap.isAppButton(194));
        assertFalse(RemoteRemap.isAppButton(24));    // volume up
        assertFalse(RemoteRemap.isAppButton(3));     // home
        assertFalse(RemoteRemap.isAppButton(192));   // BUTTON_5, Xiaomi TV+
    }

    @Test
    public void youtubeOpensYoutubeAndTheOthersNothingByDefault() {
        assertEquals("com.google.android.youtube.tv", RemoteRemap.defaultFor(RemoteRemap.YOUTUBE));
        assertEquals(RemoteRemap.NONE, RemoteRemap.defaultFor(RemoteRemap.NETFLIX));
        assertEquals(RemoteRemap.NONE, RemoteRemap.defaultFor(RemoteRemap.PRIME));
    }

    @Test
    public void keysAreListedNetflixYoutubePrime() {
        assertEquals(3, RemoteRemap.KEYS.length);
        assertEquals(193, RemoteRemap.KEYS[0]);
        assertEquals(190, RemoteRemap.KEYS[1]);
        assertEquals(194, RemoteRemap.KEYS[2]);
    }
}
