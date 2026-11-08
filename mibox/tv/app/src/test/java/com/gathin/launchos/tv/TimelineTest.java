package com.gathin.launchos.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** The guide's window of hours and where programmes fall in it. */
public class TimelineTest {
    private static final long T = 1_700_000_000L - 1_700_000_000L % 1800; // a half hour

    @Test
    public void theWindowStartsOnTheHalfHour() {
        assertEquals(T, Timeline.windowStart(T));
        assertEquals(T, Timeline.windowStart(T + 1799));
        assertEquals(T + 1800, Timeline.windowStart(T + 1800));
    }

    @Test
    public void aProgrammeIsCutToTheWindow() {
        assertArrayEquals(new float[]{0f, 30f}, Timeline.span(T - 600, T + 1800, T, 120), 0.001f);
        assertArrayEquals(new float[]{90f, 120f}, Timeline.span(T + 5400, T + 9000, T, 120), 0.001f);
        assertArrayEquals(new float[]{15f, 45f}, Timeline.span(T + 900, T + 2700, T, 120), 0.001f);
    }

    @Test
    public void programmesOutsideTheWindowAreLeftOut() {
        assertNull(Timeline.span(T - 3600, T, T, 120));
        assertNull(Timeline.span(T + 7200, T + 9000, T, 120));
        assertNull(Timeline.span(T + 600, T + 600, T, 120));
    }

    @Test
    public void theNowLineIsOnlyInsideTheWindow() {
        assertEquals(10f, Timeline.nowAt(T + 600, T, 120), 0.001f);
        assertEquals(-1f, Timeline.nowAt(T - 60, T, 120), 0.001f);
        assertEquals(-1f, Timeline.nowAt(T + 7300, T, 120), 0.001f);
    }
}
