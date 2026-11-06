package com.gathin.launchos.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Group order, favourites, resume and search ranking. */
public class OrderTest {
    private static List<String> names(List<Xtream.Category> l) {
        List<String> out = new ArrayList<>();
        for (Xtream.Category c : l) out.add(c.name);
        return out;
    }

    @Test
    public void groupsPinnedFirstThenAToZ() {
        List<Xtream.Category> cats = Arrays.asList(new Xtream.Category("1", "Sport"), new Xtream.Category("2", "02 - Action"),
                new Xtream.Category("3", "écoles"), new Xtream.Category("4", "Zoo"), new Xtream.Category("5", "Cinéma"));
        Map<String, Long> pinned = new HashMap<>();
        pinned.put("4", 20L);
        pinned.put("1", 10L);
        List<Xtream.Category> l = Groups.order(cats, c -> c.name, c -> pinned.getOrDefault(c.id, 0L));
        assertEquals(Arrays.asList("Sport", "Zoo", "02 - Action", "Cinéma", "écoles"), names(l));
    }

    @Test
    public void favouritesNeedAMinute() {
        List<Map.Entry<String, Long>> t = new ArrayList<>();
        t.add(new AbstractMap.SimpleEntry<>("a", 59_000L));
        t.add(new AbstractMap.SimpleEntry<>("b", 600_000L));
        t.add(new AbstractMap.SimpleEntry<>("c", 120_000L));
        t.add(new AbstractMap.SimpleEntry<>("d", 900_000L));
        assertEquals(Arrays.asList("d", "b", "c"), History.top(t, 5, History.FAVOURITE_MS));
        assertEquals(Arrays.asList("d", "b"), History.top(t, 2, History.FAVOURITE_MS));
    }

    @Test
    public void resumeOnlyInTheMiddle() {
        long h = 3_600_000L;
        assertFalse(History.worthResuming(30_000, h));
        assertTrue(History.worthResuming(120_000, h));
        assertTrue(History.worthResuming(h - 4 * 60_000, h));
        assertFalse(History.worthResuming(h - 2 * 60_000, h));
        // A 20 min episode: the margin is 2 min (10 %).
        assertTrue(History.worthResuming(17 * 60_000, 20 * 60_000));
        assertFalse(History.worthResuming(19 * 60_000, 20 * 60_000));
        assertTrue(History.worthResuming(120_000, 0));
    }

    private static Item film(String id, String name, long added) {
        return new Item(Item.Kind.MOVIE, id, name, "", "mkv", 0, "", "1", added, 0f, "", "", 0, 0);
    }

    @Test
    public void searchRanksStartsFirst() {
        List<Item> all = Arrays.asList(film("1", "Le Retour du roi", 1), film("2", "Retour vers le futur", 2),
                film("3", "FR: Retour", 3), film("4", "Le grand retour", 9), film("5", "Rien", 4),
                film("2", "Retour vers le futur", 2));
        List<Item> l = SearchActivity.match(all, Names.fold("retour"), 10);
        List<String> ids = new ArrayList<>();
        for (Item i : l) ids.add(i.id);
        assertEquals(Arrays.asList("3", "2", "4", "1"), ids);
        assertEquals(2, SearchActivity.match(all, "retour", 2).size());
        assertEquals(1, SearchActivity.match(all, Names.fold("roi le"), 10).size());
    }
}
