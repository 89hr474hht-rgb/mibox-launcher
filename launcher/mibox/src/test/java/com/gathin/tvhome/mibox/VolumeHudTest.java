package com.gathin.tvhome.mibox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VolumeHudTest {
    private static final float FRAME = 1 / 60f;

    @Test
    public void aSpringSettlesExactlyOnItsTarget() {
        Spring s = new Spring(420, 30, 0);
        s.target = 1;
        int frames = 0;
        while (s.step(FRAME)) {
            frames++;
            assertTrue("never settles", frames < 300);
        }
        assertEquals(1f, s.value, 0f);
        assertEquals(0f, s.velocity, 0f);
    }

    @Test
    public void anUnderdampedSpringOvershootsThenComesBack() {
        Spring s = new Spring(240, 18, 0);   // the bar opening: damping ratio about 0.58
        s.target = 1;
        float peak = 0;
        while (s.step(FRAME)) peak = Math.max(peak, s.value);
        assertTrue("no bounce: " + peak, peak > 1.05f);
        assertTrue("too much bounce: " + peak, peak < 1.3f);
    }

    @Test
    public void aSpringAtRestStaysPut() {
        Spring s = new Spring(600, 15, 1);
        assertFalse(s.step(FRAME));
        assertEquals(1f, s.value, 0f);
    }

    @Test
    public void aLongFrameDoesNotBlowUp() {
        Spring s = new Spring(700, 22, 0);   // the stiffest spring, after a 50 ms hitch
        s.target = 1;
        s.step(0.05f);
        assertTrue(Math.abs(s.value) < 2f);
    }

    @Test
    public void theBarFillsWithTheLevel() {
        assertEquals(0f, VolumeLevels.fraction(0, 15, false), 0f);
        assertEquals(1f, VolumeLevels.fraction(15, 15, false), 0f);
        assertEquals(0.2f, VolumeLevels.fraction(3, 15, false), 1e-6f);
        assertEquals(0f, VolumeLevels.fraction(9, 15, true), 0f);
        assertEquals(0f, VolumeLevels.fraction(5, 0, false), 0f);
    }

    @Test
    public void wavesFollowTheLevel() {
        assertEquals(0, VolumeLevels.waves(0, 15, false));
        assertEquals(0, VolumeLevels.waves(12, 15, true));
        assertEquals(1, VolumeLevels.waves(1, 15, false));
        assertEquals(1, VolumeLevels.waves(7, 15, false));
        assertEquals(2, VolumeLevels.waves(8, 15, false));
        assertEquals(2, VolumeLevels.waves(15, 15, false));
    }

    @Test
    public void pushingPastTheEndStretchesLessAndLess() {
        assertEquals(1f, VolumeLevels.stretch(0), 0f);
        float last = 1f, lastGain = Float.MAX_VALUE;
        for (int n = 1; n <= 12; n++) {
            float s = VolumeLevels.stretch(n);
            assertTrue(s > last);
            assertTrue(s < 1f + VolumeLevels.MAX_STRETCH);
            assertTrue(s - last < lastGain);
            lastGain = s - last;
            last = s;
        }
    }

    @Test
    public void onlyAPressWithNowhereToGoPushes() {
        assertEquals(1, VolumeLevels.push(true, 15, 15, 15));
        assertEquals(-1, VolumeLevels.push(false, 0, 0, 15));
        assertEquals(0, VolumeLevels.push(true, 14, 15, 15));
        assertEquals(0, VolumeLevels.push(false, 1, 0, 15));
        assertEquals(0, VolumeLevels.push(true, 0, 0, 15));   // a raise that did not move, but not at the top
    }
}
