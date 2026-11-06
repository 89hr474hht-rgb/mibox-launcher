package com.gathin.launchos.tv;

import android.util.LruCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Films' sheets (summary, wide picture, length), fetched when a film is shown and kept for the session. */
final class Details {
    interface Got {
        void got(Xtream.VodInfo info);
    }

    private static final LruCache<String, Xtream.VodInfo> CACHE = new LruCache<>(300);
    private static final Map<String, List<Got>> LOADING = new HashMap<>();
    private static int accountVersion = -1;

    private Details() {
    }

    static void forAccount(int version) {
        if (version == accountVersion) return;
        accountVersion = version;
        CACHE.evictAll();
        LOADING.clear();
    }

    static Xtream.VodInfo cached(String id) {
        return CACHE.get(id);
    }

    /** Main thread; calls back at once from the cache, else later on the main thread (null when it failed). */
    static void get(Xtream api, String id, Got got) {
        Xtream.VodInfo hit = CACHE.get(id);
        if (hit != null) {
            got.got(hit);
            return;
        }
        if (api == null) {
            got.got(null);
            return;
        }
        List<Got> waiting = LOADING.get(id);
        if (waiting != null) {
            waiting.add(got);
            return;
        }
        List<Got> list = new ArrayList<>();
        list.add(got);
        LOADING.put(id, list);
        int v = accountVersion;
        Bg.run(() -> api.vodInfo(id), (info, error) -> {
            List<Got> all = LOADING.remove(id);
            if (v != accountVersion) return;
            if (info != null) CACHE.put(id, info);
            if (all != null) for (Got g : all) g.got(info);
        });
    }
}
