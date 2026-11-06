package com.gathin.tvhome.mibox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class A11yPauseTest {
    private static final String HOME = "com.gathin.tvhome/com.gathin.tvhome.HomeKeyService";
    private static final String KEYS = "com.gathin.tvhome/com.gathin.tvhome.mibox.RemoteKeysService";
    private static final String INPUT = "com.gathin.tvhome/com.gathin.tvhome.InputKeyService";
    private static final String OTHER = "com.example.reader/.ReaderService";

    @Test
    public void pauseTakesOnlyThisAppsServices() {
        List<String> removed = new ArrayList<>();
        String kept = A11yPause.without(HOME + ":" + OTHER + ":" + KEYS + ":" + INPUT, "com.gathin.tvhome", removed);
        assertEquals(OTHER, kept);
        assertEquals(Arrays.asList(HOME, KEYS, INPUT), removed);
    }

    @Test
    public void aPackageThatStartsTheSameIsNotTaken() {
        List<String> removed = new ArrayList<>();
        assertEquals("com.gathin.tvhomex/.S", A11yPause.without("com.gathin.tvhomex/.S", "com.gathin.tvhome", removed));
        assertTrue(removed.isEmpty());
    }

    @Test
    public void emptyOrNullListsGiveNothing() {
        List<String> removed = new ArrayList<>();
        assertEquals("", A11yPause.without(null, "com.gathin.tvhome", removed));
        assertEquals("", A11yPause.without("null", "com.gathin.tvhome", removed));
        assertEquals("", A11yPause.without("", "com.gathin.tvhome", removed));
        assertTrue(removed.isEmpty());
    }

    @Test
    public void resumeAppendsWhatIsMissingOnly() {
        assertEquals(OTHER + ":" + HOME + ":" + KEYS, A11yPause.with(OTHER, Arrays.asList(HOME, KEYS)));
        assertEquals(HOME + ":" + KEYS, A11yPause.with("", Arrays.asList(HOME, KEYS)));
        assertEquals(HOME + ":" + KEYS, A11yPause.with(null, Arrays.asList(HOME, KEYS)));
    }

    @Test
    public void resumeSeesBothNameForms() {
        String shortName = "com.gathin.tvhome/.HomeKeyService";
        assertEquals(shortName, A11yPause.with(shortName, Arrays.asList(HOME)));
        assertEquals(HOME, A11yPause.with(HOME, Arrays.asList(shortName)));
        assertEquals(HOME, A11yPause.full(shortName));
        assertEquals(HOME, A11yPause.full(HOME));
    }

    @Test
    public void onlyListedAppsPause() {
        String[] list = {"com.sfr.androidtv.launcher"};
        assertTrue(A11yPause.pausesFor(list, "com.sfr.androidtv.launcher"));
        assertFalse(A11yPause.pausesFor(list, "com.sfr.androidtv.selfcare"));
        assertFalse(A11yPause.pausesFor(list, null));
        assertFalse(A11yPause.pausesFor(new String[0], "com.sfr.androidtv.launcher"));
    }
}
