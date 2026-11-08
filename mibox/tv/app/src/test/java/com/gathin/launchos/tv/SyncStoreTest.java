package com.gathin.launchos.tv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** How far a list's refresh is, from the bytes received. */
public class SyncStoreTest {
    private static final float EPS = 0.0001f;

    @Test
    public void nothingReceivedYetShowsTheStart() {
        assertEquals(SyncStore.START, SyncStore.fraction(0, -1, 0), EPS);
        assertEquals(SyncStore.START, SyncStore.fraction(0, 1000, 0), EPS);
    }

    @Test
    public void theServersSizeGivesTheShare() {
        float half = SyncStore.START + (SyncStore.READ_END - SyncStore.START) * 0.5f;
        assertEquals(half, SyncStore.fraction(500, 1000, 0), EPS);
        assertEquals(SyncStore.READ_END, SyncStore.fraction(1000, 1000, 0), EPS);
    }

    @Test
    public void withoutASizeLastTimesSizeIsUsedAndNeverOvershoots() {
        float quarter = SyncStore.START + (SyncStore.READ_END - SyncStore.START) * 0.25f;
        assertEquals(quarter, SyncStore.fraction(250, -1, 1000), EPS);
        assertEquals(SyncStore.READ_END, SyncStore.fraction(5000, -1, 1000), EPS);
    }

    @Test
    public void withNothingToGoOnTheGaugeIsUnknown() {
        assertEquals(-1f, SyncStore.fraction(4096, -1, 0), EPS);
    }

    @Test
    public void theServersSizeWinsOverLastTime() {
        float tenth = SyncStore.START + (SyncStore.READ_END - SyncStore.START) * 0.1f;
        assertEquals(tenth, SyncStore.fraction(100, 1000, 50), EPS);
    }
}
