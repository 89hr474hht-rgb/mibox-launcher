package com.gathin.tvhome.mibox;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SleepTimerTest {
    @Test
    public void durationsReadAsTheMenuSaysThem() {
        assertEquals("15 min", SleepTimer.duration(15 * 60_000L));
        assertEquals("1 h", SleepTimer.duration(60 * 60_000L));
        assertEquals("1 h 30", SleepTimer.duration(90 * 60_000L));
        assertEquals("2 h", SleepTimer.duration(120 * 60_000L));
        assertEquals("1 h 05", SleepTimer.duration(65 * 60_000L));
    }

    @Test
    public void durationsRoundUpToTheMinute() {
        assertEquals("42 min", SleepTimer.duration(41 * 60_000L + 1));
        assertEquals("1 min", SleepTimer.duration(10_000L));
        assertEquals("1 min", SleepTimer.duration(0));
    }

    @Test
    public void countdownShowsTheLastMinute() {
        assertEquals("1:00", SleepTimer.countdown(60_000L));
        assertEquals("0:59", SleepTimer.countdown(58_200L));
        assertEquals("0:01", SleepTimer.countdown(1L));
        assertEquals("0:00", SleepTimer.countdown(0L));
        assertEquals("0:00", SleepTimer.countdown(-500L));
    }
}
