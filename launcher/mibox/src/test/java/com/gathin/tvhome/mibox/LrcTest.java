package com.gathin.tvhome.mibox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Synced lyrics: times, pairs of lines, offset, tags. */
public class LrcTest {
    @Test
    public void readsTimesAndText() {
        Lrc l = Lrc.parse("[ti:Song]\n[ar:Someone]\n[00:01.50]One\n[00:03.2]Two\n[01:00]Three\n");
        assertEquals("Song", l.title);
        assertEquals("Someone", l.artist);
        assertEquals(3, l.lines.size());
        assertEquals(1500, l.lines.get(0).atMs);
        assertEquals(3200, l.lines.get(1).atMs);
        assertEquals(60_000, l.lines.get(2).atMs);
        assertEquals("Three", l.lines.get(2).text);
    }

    @Test
    public void twoLinesAtTheSameTimeAreAPair() {
        Lrc l = Lrc.parse("[00:00.00]Main\n[00:00.00]Translation\n[00:04.00]Next\n[00:04.00]Suivant\n");
        assertEquals(2, l.lines.size());
        assertEquals("Main", l.lines.get(0).text);
        assertEquals("Translation", l.lines.get(0).sub);
        assertEquals("Suivant", l.lines.get(1).sub);
    }

    @Test
    public void severalTimesOnOneLineAndUnsortedInput() {
        Lrc l = Lrc.parse("[00:10.00]Later\n[00:02.00][00:20.00]Chorus\n");
        assertEquals(3, l.lines.size());
        assertEquals("Chorus", l.lines.get(0).text);
        assertEquals("Later", l.lines.get(1).text);
        assertEquals(20_000, l.lines.get(2).atMs);
    }

    @Test
    public void offsetShiftsEveryLine() {
        Lrc l = Lrc.parse("[offset:+500]\n[00:02.00]A\n");
        assertEquals(1500, l.lines.get(0).atMs);
    }

    @Test
    public void longTracksPastAnHour() {
        Lrc l = Lrc.parse("[121:04.12]End\n");
        assertEquals((121 * 60 + 4) * 1000 + 120, l.lines.get(0).atMs);
    }

    @Test
    public void indexFollowsThePosition() {
        Lrc l = Lrc.parse("[00:01.00]A\n[00:05.00]B\n[00:09.00]C\n");
        assertEquals(-1, l.indexAt(500));
        assertEquals(0, l.indexAt(1000));
        assertEquals(1, l.indexAt(8999));
        assertEquals(2, l.indexAt(60_000));
    }

    @Test
    public void plainTextIsNotSyncedLyrics() {
        assertNull(Lrc.parse("Just words\nNo times\n"));
        assertNull(Lrc.parse(null));
    }

    @Test
    public void rightToLeftTextIsKept() {
        Lrc l = Lrc.parse("\uFEFF[00:00.00]مرحبا بالعالم\n[00:00.00]Bonjour\n");
        assertEquals("مرحبا بالعالم", l.lines.get(0).text);
        assertEquals("Bonjour", l.lines.get(0).sub);
    }
}
