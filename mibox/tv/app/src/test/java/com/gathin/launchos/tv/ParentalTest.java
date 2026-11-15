package com.gathin.launchos.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ParentalTest {
    @Test
    public void theCodeIsKeptAsASaltedSha256() {
        String h = Parental.hash("salt", "1234");
        assertEquals(64, h.length());
        assertTrue(h.matches("[0-9a-f]+"));
        assertEquals(h, Parental.hash("salt", "1234"));
        assertNotEquals(h, Parental.hash("salt", "1235"));
        assertNotEquals(h, Parental.hash("other", "1234"));
        assertNotEquals("1234", h);
    }
}
