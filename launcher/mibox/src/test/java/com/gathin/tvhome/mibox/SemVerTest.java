package com.gathin.tvhome.mibox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SemVerTest {
    @Test
    public void parsesPlainAndTaggedVersions() {
        assertEquals("1.2.3", String.valueOf(SemVer.parse("1.2.3")));
        assertEquals("1.2.3", String.valueOf(SemVer.parse("v1.2.3")));
        assertEquals("10.0.0", String.valueOf(SemVer.parse(" V10.0.0 ")));
    }

    @Test
    public void refusesAnythingElse() {
        for (String s : new String[]{null, "", "1.2", "1.2.3.4", "1.2.x", "1.2.3-beta", "01.2.3", "1..3",
                "v", "1.2.-3", "123456.0.0", "+1.2.3", "１.2.3"}) {
            assertNull(s, SemVer.parse(s));
        }
    }

    @Test
    public void comparesNumerically() {
        assertTrue(SemVer.parse("1.10.0").isNewerThan(SemVer.parse("1.9.9")));
        assertTrue(SemVer.parse("2.0.0").isNewerThan(SemVer.parse("1.99.99")));
        assertFalse(SemVer.parse("1.0.0").isNewerThan(SemVer.parse("1.0.0")));
        assertFalse(SemVer.parse("1.0.0").isNewerThan(SemVer.parse("1.0.1")));
        assertTrue(SemVer.parse("1.0.0").isNewerThan(null));
    }

    @Test
    public void versionCodeMatchesTheBuildFormula() {
        assertEquals(10000, SemVer.parse("1.0.0").versionCode());
        assertEquals(10203, SemVer.parse("1.2.3").versionCode());
        assertTrue(SemVer.parse("1.10.0").versionCode() > SemVer.parse("1.9.99").versionCode());
    }
}
