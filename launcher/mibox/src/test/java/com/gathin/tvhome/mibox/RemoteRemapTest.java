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

    private static final int V = 0x2717, P = 0x32b9;

    @Test
    public void theBoxTellsWhichRemoteItIs() {
        assertEquals(RemoteRemap.Model.MIBOX_S2, RemoteRemap.modelFor("jaws"));
        assertEquals(RemoteRemap.Model.MIBOX_S, RemoteRemap.modelFor("oneday"));
        assertEquals(null, RemoteRemap.modelFor("sabrina"));
        assertEquals(null, RemoteRemap.modelFor(null));
    }

    @Test
    public void the2ndGenRemoteKeepsItsThreeButtons() {
        RemoteRemap.Model m = RemoteRemap.Model.MIBOX_S2;
        assertEquals(193, RemoteRemap.button(m, V, P, 193));
        assertEquals(190, RemoteRemap.button(m, V, P, 190));
        assertEquals(194, RemoteRemap.button(m, V, P, 194));
        assertEquals(-1, RemoteRemap.button(m, V, P, 1001));  // the Mi Box S Netflix code is not one of its
        assertEquals(-1, RemoteRemap.button(m, V, P, 24));    // volume up
        assertEquals(3, RemoteRemap.keys(m).length);
    }

    @Test
    public void theMiBoxSRemoteHasNetflixAndLive() {
        RemoteRemap.Model m = RemoteRemap.Model.MIBOX_S;
        assertEquals(RemoteRemap.S_NETFLIX, RemoteRemap.button(m, V, P, 1001));
        assertEquals(RemoteRemap.S_LIVE, RemoteRemap.button(m, V, P, 191));
        assertEquals(RemoteRemap.S_LIVE, RemoteRemap.button(m, V, P, 189));
        assertEquals(RemoteRemap.S_LIVE, RemoteRemap.button(m, V, P, 190));
        assertEquals(-1, RemoteRemap.button(m, V, P, 193));
        assertEquals(-1, RemoteRemap.button(m, V, P, 219));   // the assistant key (ASSIST)
        assertEquals("com.netflix.ninja", RemoteRemap.defaultFor(RemoteRemap.S_NETFLIX));
        assertEquals("com.mitv.tvhome.mitvplus", RemoteRemap.defaultFor(RemoteRemap.S_LIVE));
        assertEquals(2, RemoteRemap.keys(m).length);
    }

    @Test
    public void anUnknownBoxOrAnotherDeviceIsLeftAlone() {
        assertEquals(-1, RemoteRemap.button(null, V, P, 193));
        assertEquals(-1, RemoteRemap.button(RemoteRemap.Model.MIBOX_S2, 0x1234, P, 193));
        assertEquals(-1, RemoteRemap.button(RemoteRemap.Model.MIBOX_S, V, 0x0001, 1001));
        assertEquals(0, RemoteRemap.keys(null).length);
    }
}
