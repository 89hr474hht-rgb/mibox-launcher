package com.gathin.tvhome.mibox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void wordTimesAreReadAndTakenOutOfTheText() {
        Lrc l = Lrc.parse("[00:01.00]<00:01.00>Hello <00:02.00>big <00:03.00>world<00:04.00>\n"
                + "[00:01.00]<00:01.00>Bonjour le monde\n[00:06.00]Next\n");
        Lrc.Line a = l.lines.get(0);
        assertEquals("Hello big world", a.text);
        assertEquals("Bonjour le monde", a.sub);
        assertTrue(a.wordTimed);
        assertEquals(0f, a.sung(500), 0.001f);
        assertEquals(0f, a.sung(1000), 0.001f);
        assertEquals(3f, a.sung(1500), 0.001f);     // half of "Hello " (six characters)
        assertEquals(6f, a.sung(2000), 0.001f);     // "big" starts
        assertEquals(12.5f, a.sung(3500), 0.001f);  // half of "world"
        assertEquals(15f, a.sung(4000), 0.001f);
        assertEquals(15f, a.sung(5000), 0.001f);
        assertEquals(4000, a.endMs());
    }

    @Test
    public void aTimeAfterAWordHoldsThroughThePause() {
        Lrc.Line a = Lrc.parse("[00:00.00]<00:00.00>One<00:01.00> <00:03.00>two<00:04.00>\n").lines.get(0);
        assertEquals(3f, a.sung(1000), 0.001f);
        assertEquals(3.5f, a.sung(2000), 0.001f);   // only the space moves during the pause
        assertEquals(4f, a.sung(3000), 0.001f);
    }

    @Test
    public void aLineWithoutWordTimesIsSungEvenly() {
        Lrc l = Lrc.parse("[00:00.00]abcdefghij\n[00:01.00]Next line\n[00:30.00]Last\n");
        Lrc.Line a = l.lines.get(0);
        assertFalse(a.wordTimed);
        assertEquals(1000, a.endMs());              // up to the next line
        assertEquals(5f, a.sung(500), 0.001f);
        Lrc.Line b = l.lines.get(1);
        // A long pause after it is not filled slowly: the line is sung at a singing pace.
        assertEquals(1000 + 1500, b.endMs());
        assertEquals(9f, b.sung(10_000), 0.001f);
    }

    @Test
    public void bracketedWordTimesAndOffsetToo() {
        Lrc.Line a = Lrc.parse("[offset:1000]\n[00:02.00]Ab [00:03.00]cd\n").lines.get(0);
        assertEquals("Ab cd", a.text);
        assertEquals(1000, a.atMs);
        assertEquals(3f, a.sung(2000), 0.001f);     // "cd" starts at 3 s less the offset
    }
}
