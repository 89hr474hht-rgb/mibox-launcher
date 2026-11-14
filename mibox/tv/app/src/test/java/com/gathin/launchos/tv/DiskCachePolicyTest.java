package com.gathin.launchos.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** The pictures' disk cache keeps the most recently used files under its cap. */
public class DiskCachePolicyTest {
    private static DiskCachePolicy.Entry f(String name, long size, long used) {
        return new DiskCachePolicy.Entry(name, size, used);
    }

    @Test
    public void nothingLeavesUnderTheCap() {
        assertTrue(DiskCachePolicy.evict(Arrays.asList(f("a", 10, 1), f("b", 20, 2)), 30).isEmpty());
    }

    @Test
    public void leastRecentlyUsedLeaveFirstUntilItFits() {
        List<String> out = DiskCachePolicy.evict(Arrays.asList(
                f("new", 40, 300), f("old", 40, 100), f("mid", 40, 200), f("older", 10, 50)), 80);
        // 130 bytes for a cap of 80: "older" (10) then "old" (40) go, which leaves 80.
        assertEquals(Arrays.asList("older", "old"), out);
    }

    @Test
    public void capOfZeroEmptiesEverything() {
        assertEquals(3, DiskCachePolicy.evict(Arrays.asList(f("a", 1, 1), f("b", 1, 2), f("c", 1, 3)), 0).size());
    }

    @Test
    public void realCapIs64Mb() {
        assertEquals(64L * 1024 * 1024, Images.DISK_CAP);
    }
}
