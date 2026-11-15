package com.gathin.launchos.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TV's shelves on hard lists: names written every which way, true doubles, foreign channels, channels without a
 * logo, HD/SD/FHD/4K versions, hidden channels and groups, and favourites set on one version.
 */
public class TvShelvesTest {
    private final TvShelves.Input in = new TvShelves.Input();

    private Item ch(String id, String name, String group) {
        Item i = Item.live(id, name, "", 0, group);
        in.all.add(i);
        return i;
    }

    private void group(String id, String name) {
        in.groupNames.put(id, name);
    }

    private static TvShelves.Shelf theme(List<TvShelves.Shelf> l, Smart.Theme t) {
        for (TvShelves.Shelf s : l) if (s.kind == TvShelves.Kind.THEME && s.theme == t) return s;
        return null;
    }

    private static TvShelves.Shelf country(List<TvShelves.Shelf> l, String c) {
        for (TvShelves.Shelf s : l) if (s.kind == TvShelves.Kind.COUNTRY && s.country.equals(c)) return s;
        return null;
    }

    private static TvShelves.Entry entry(TvShelves.Shelf s, String id) {
        for (TvShelves.Entry e : s.entries) if (e.has(id)) return e;
        return null;
    }

    private static Set<String> ids(List<Item> items) {
        Set<String> out = new HashSet<>();
        for (Item i : items) out.add(i.id);
        return out;
    }

    @Test
    public void qualityVersionsAreOneEntryWithEveryVersionKept() {
        group("1", "FR | Généralistes");
        ch("a", "FR: TF1 SD", "1");
        ch("b", "|FR| TF1 HD", "1");
        ch("c", "TF1 (FHD)", "1");
        ch("d", "TF1 4K", "1");
        TvShelves.Shelf g = theme(TvShelves.build(in), Smart.Theme.GENERAL);
        assertNotNull(g);
        assertEquals(1, g.entries.size());
        TvShelves.Entry e = g.entries.get(0);
        assertEquals("c", e.main.id);   // Full HD first: it is steady
        assertEquals(new HashSet<>(Arrays.asList("a", "b", "c", "d")), ids(e.versions));
    }

    @Test
    public void namesThatOnlyLookAlikeStayApart() {
        group("1", "FR | TNT");
        ch("a", "France 2 HD", "1");
        ch("b", "France 24", "1");
        ch("c", "France 3 Alpes", "1");
        ch("d", "France 3", "1");
        ch("e", "TF1", "1");
        ch("f", "TF1 Séries Films", "1");
        TvShelves.Shelf g = theme(TvShelves.build(in), Smart.Theme.GENERAL);
        assertEquals(6, g.entries.size());
        for (TvShelves.Entry e : g.entries) assertEquals(1, e.versions.size());
    }

    @Test
    public void trueDoublesInTwoGroupsAreOneEntryWithBothIds() {
        group("1", "FR | Sport");
        group("2", "FR | beIN");
        ch("a", "beIN Sports 1 HD", "1");
        ch("b", "beIN Sports 1 HD", "2");
        TvShelves.Shelf s = theme(TvShelves.build(in), Smart.Theme.SPORT);
        assertEquals(1, s.entries.size());
        assertEquals(new HashSet<>(Arrays.asList("a", "b")), ids(s.entries.get(0).versions));
    }

    @Test
    public void foreignChannelsGoToTheirCountryAndAreNotMergedWithHome() {
        group("1", "FR | Généralistes");
        group("2", "UK | Entertainment");
        group("3", "BE | Généralistes");
        ch("a", "BBC One", "1");
        ch("b", "BBC One HD", "2");
        ch("c", "La Une", "3");
        List<TvShelves.Shelf> l = TvShelves.build(in);
        assertNotNull(entry(theme(l, Smart.Theme.GENERAL), "a"));
        TvShelves.Entry uk = entry(country(l, "uk"), "b");
        assertNotNull(uk);
        assertEquals(1, uk.versions.size());   // same name, other country: another channel
        assertNotNull(entry(country(l, "be"), "c"));
        // Countries close to home come before the others.
        int be = -1, gb = -1;
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i) == country(l, "be")) be = i;
            if (l.get(i) == country(l, "uk")) gb = i;
        }
        assertTrue(be < gb);
    }

    @Test
    public void aCountryWrittenInFrontOfTheNameCountsWhenTheGroupSaysNone() {
        group("1", "Bouquet");
        ch("a", "UK: Sky News", "1");
        assertNotNull(country(TvShelves.build(in), "uk"));
    }

    @Test
    public void inconsistentNamesAndNoLogoStillShelve() {
        group("1", "FR | Bouquet");
        ch("a", "★ M6 HD ★", "1");
        ch("b", "M6", "1");
        ch("c", "***", "1");    // nothing left once cleaned: kept on its own, never merged
        ch("d", "***", "1");
        List<TvShelves.Shelf> l = TvShelves.build(in);
        TvShelves.Entry m6 = entry(theme(l, Smart.Theme.GENERAL), "b");   // a TNT number makes it a Généraliste
        assertNotNull(m6);
        assertEquals(new HashSet<>(Arrays.asList("a", "b")), ids(m6.versions));
        TvShelves.Shelf other = theme(l, Smart.Theme.OTHER);
        assertNotNull(entry(other, "c"));
        assertNotNull(entry(other, "d"));
        assertTrue(entry(other, "c") != entry(other, "d"));
    }

    @Test
    public void hiddenChannelsHiddenGroupsAndAdultsAreLeftOut() {
        group("1", "FR | Cinéma");
        group("2", "FR | Adultes XXX");
        group("3", "FR | Divers");
        ch("a", "Ciné Club", "1");
        ch("b", "Ciné Club HD", "1");
        ch("c", "Hot", "2");
        ch("d", "Chaîne cachée", "3");
        in.hidden = i -> i.id.equals("b") || i.categoryId.equals("3");
        List<TvShelves.Shelf> l = TvShelves.build(in);
        TvShelves.Entry e = entry(theme(l, Smart.Theme.MOVIES), "a");
        assertEquals(1, e.versions.size());                 // the hidden version is not offered
        assertNull(theme(l, Smart.Theme.ADULT));
        assertNull(theme(l, Smart.Theme.OTHER));
        in.showAdult = true;
        assertNotNull(entry(theme(TvShelves.build(in), Smart.Theme.ADULT), "c"));
    }

    @Test
    public void aFavouriteOnOneVersionStaysThatVersionWithTheOthersAtHand() {
        group("1", "FR | Info");
        Item sd = ch("a", "LCI SD", "1");
        ch("b", "LCI FHD", "1");
        in.favourites = new ArrayList<>(Arrays.asList(sd, Item.live("gone", "Ancienne", "", 0, "9")));
        List<TvShelves.Shelf> l = TvShelves.build(in);
        TvShelves.Shelf fav = l.get(0);
        assertEquals(TvShelves.Kind.FAVOURITES, fav.kind);
        assertEquals(1, fav.entries.size());                 // a channel the panel dropped is left out
        assertEquals("a", fav.entries.get(0).main.id);
        assertEquals(Arrays.asList("a", "b"), Arrays.asList(fav.entries.get(0).versions.get(0).id,
                fav.entries.get(0).versions.get(1).id));
        // On the theme shelf the same channel shows its best version, and still knows the favourite one.
        TvShelves.Entry info = entry(theme(l, Smart.Theme.NEWS), "a");
        assertEquals("b", info.main.id);
        assertTrue(info.has("a"));
    }

    @Test
    public void recentKeepsItsOrderAndShelvesComeInTheirOrder() {
        group("1", "FR | Sport");
        group("2", "FR | Généralistes");
        Item s = ch("a", "Eurosport 1", "1");
        Item t = ch("b", "TF1", "2");
        in.recent = new ArrayList<>(Arrays.asList(t, s));
        List<TvShelves.Shelf> l = TvShelves.build(in);
        assertEquals(TvShelves.Kind.RECENT, l.get(0).kind);
        assertEquals("b", l.get(0).entries.get(0).main.id);
        assertEquals(Smart.Theme.GENERAL, l.get(1).theme);
        assertEquals(Smart.Theme.SPORT, l.get(2).theme);
    }

    @Test
    public void generalistesAreInTntOrder() {
        group("1", "FR | Généralistes");
        ch("m6", "M6", "1");
        ch("tf1", "TF1", "1");
        ch("arte", "Arte", "1");
        ch("x", "Zeta TV", "1");
        TvShelves.Shelf g = theme(TvShelves.build(in), Smart.Theme.GENERAL);
        List<String> order = new ArrayList<>();
        for (TvShelves.Entry e : g.entries) order.add(e.main.id);
        assertEquals(Arrays.asList("tf1", "m6", "arte", "x"), order);
    }

    @Test
    public void theInputIsNeverChanged() {
        group("1", "FR | Généralistes");
        Item a = ch("a", "TF1 HD", "1");
        Item b = ch("b", "TF1 SD", "1");
        List<Item> before = new ArrayList<>(in.all);
        TvShelves.build(in);
        assertEquals(before, in.all);
        assertEquals("TF1 HD", a.name);
        assertEquals("1", b.categoryId);
        assertFalse(in.groupNames.isEmpty());
    }
}
