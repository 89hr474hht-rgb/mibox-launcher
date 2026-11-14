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

    @Test
    public void shortProgrammesKeepAReadableWidthWithoutOverlapping() {
        // 10 px a minute, 4 px gaps, at least 70 px: a 3-minute programme still gets 70 px and pushes the next.
        int[][] at = Timeline.place(new float[][]{{0, 30}, {30, 33}, {33, 60}}, 10f, 4, 70, 1200);
        org.junit.Assert.assertArrayEquals(new int[]{0, 296}, at[0]);
        org.junit.Assert.assertArrayEquals(new int[]{300, 70}, at[1]);
        org.junit.Assert.assertEquals(374, at[2][0]);
        org.junit.Assert.assertEquals(596 - 374, at[2][1]);
    }

    @Test
    public void aProgrammePushedPastTheEndIsLeftOut() {
        int[][] at = Timeline.place(new float[][]{{0, 118}, {118, 120}}, 10f, 4, 70, 1200);
        org.junit.Assert.assertNotNull(at[0]);
        org.junit.Assert.assertNull(at[1]);
    }
}
