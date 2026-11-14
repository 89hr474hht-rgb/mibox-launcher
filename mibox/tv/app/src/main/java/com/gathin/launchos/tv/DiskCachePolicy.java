package com.gathin.launchos.tv;

import java.util.ArrayList;
import java.util.List;

/** Which pictures leave the disk cache (Images) when it grows past its cap: the least recently used. Pure, tested on the JVM. */
final class DiskCachePolicy {
    /** One file of the cache: its name, size and when it was last written or read. */
    static final class Entry {
        final String name;
        final long size, lastUsed;

        Entry(String name, long size, long lastUsed) {
            this.name = name;
            this.size = size;
            this.lastUsed = lastUsed;
        }
    }

    private DiskCachePolicy() {
    }

    /** The names to delete so the rest fits in cap bytes: the least recently used first, until it fits. */
    static List<String> evict(List<Entry> files, long cap) {
        long total = 0;
        for (Entry f : files) total += Math.max(0, f.size);
        List<String> out = new ArrayList<>();
        if (total <= cap) return out;
        List<Entry> byAge = new ArrayList<>(files);
        byAge.sort((a, b) -> a.lastUsed != b.lastUsed ? Long.compare(a.lastUsed, b.lastUsed) : a.name.compareTo(b.name));
        for (Entry f : byAge) {
            if (total <= cap) break;
            out.add(f.name);
            total -= Math.max(0, f.size);
        }
        return out;
    }
}
