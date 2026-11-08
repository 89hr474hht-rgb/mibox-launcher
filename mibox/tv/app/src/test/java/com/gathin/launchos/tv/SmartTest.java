package com.gathin.launchos.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Smart sorting: themes, countries, adults, TNT numbers and doubles. */
public class SmartTest {
    private static Xtream.Category cat(String id, String name) {
        return new Xtream.Category(id, name);
    }

    private static Item ch(String id, String name) {
        return new Item(Item.Kind.LIVE, id, name, "", "ts", 0, "");
    }

    private static List<String> names(List<Xtream.Category> l) {
        List<String> out = new ArrayList<>();
        for (Xtream.Category c : l) out.add(c.name);
        return out;
    }

    private static List<String> titles(List<Item> l) {
        List<String> out = new ArrayList<>();
        for (Item i : l) out.add(i.title + (i.tag.isEmpty() ? "" : " " + i.tag));
        return out;
    }

    @Test
    public void themes() {
        assertEquals(Smart.Theme.SPORT, Smart.theme("|FR| SPORTS ★"));
        assertEquals(Smart.Theme.NEWS, Smart.theme("FR: Information"));
        assertEquals(Smart.Theme.KIDS, Smart.theme("Films enfants"));
        assertEquals(Smart.Theme.MOVIES, Smart.theme("Cinéma"));
        assertEquals(Smart.Theme.DOCS, Smart.theme("Documentaires"));
        assertEquals(Smart.Theme.GENERAL, Smart.theme("FR | Généralistes"));
        assertEquals(Smart.Theme.OTHER, Smart.theme("Divers"));
        assertEquals(Smart.Theme.ADULT, Smart.theme("FR | XXX"));
    }

    @Test
    public void adults() {
        assertTrue(Smart.adult("Adultes +18"));
        assertTrue(Smart.adult("FOR ADULTS"));
        assertTrue(Smart.adult("XXX Movies"));
        assertFalse(Smart.adult("Sport"));
        assertFalse(Smart.adult("Ligue 1 2018"));
        assertFalse(Smart.adult("Le XXe siècle"));
    }

    @Test
    public void countries() {
        assertEquals("fr", Smart.country("FR | Sport"));
        assertEquals("fr", Smart.country("|FR| Cinéma"));
        assertEquals("uk", Smart.country("UK: News"));
        assertEquals("be", Smart.country("[BE] RTBF"));
        assertEquals("", Smart.country("Sport"));
        assertEquals("", Smart.country("Ma série préférée"));
        assertEquals(0, Smart.countryRank("Sport"));
        assertEquals(1, Smart.countryRank("CH | Info"));
        assertEquals(2, Smart.countryRank("US | Movies"));
    }

    @Test
    public void smartGroupsHomeFirstThenThemesThenAToZ() {
        List<Xtream.Category> cats = Arrays.asList(cat("1", "UK | Sport"), cat("2", "FR | Cinéma"), cat("3", "FR | Sport"),
                cat("4", "BE | Info"), cat("5", "FR | Généralistes"), cat("6", "FR | Zoo"), cat("7", "FR | Information"));
        List<Xtream.Category> l = Smart.orderGroups(cats, Smart.GroupOrder.SMART, c -> c.name, c -> 0L);
        assertEquals(Arrays.asList("FR | Généralistes", "FR | Information", "FR | Sport", "FR | Cinéma", "FR | Zoo",
                "BE | Info", "UK | Sport"), names(l));
    }

    @Test
    public void pinnedStayFirstAndPanelKeepsItsOrder() {
        List<Xtream.Category> cats = Arrays.asList(cat("1", "B"), cat("2", "C"), cat("3", "A"));
        List<Xtream.Category> panel = Smart.orderGroups(cats, Smart.GroupOrder.PANEL, c -> c.name,
                c -> c.id.equals("2") ? 5L : 0L);
        assertEquals(Arrays.asList("C", "B", "A"), names(panel));
        List<Xtream.Category> az = Smart.orderGroups(cats, Smart.GroupOrder.AZ, c -> c.name, c -> 0L);
        assertEquals(Arrays.asList("A", "B", "C"), names(az));
    }

    @Test
    public void tntNumbers() {
        assertEquals(1, Smart.tnt("TF1"));
        assertEquals(2, Smart.tnt("France 2"));
        assertEquals(4, Smart.tnt("Canal+"));
        assertEquals(3, Smart.tnt("France 3 Paris"));
        assertEquals(21, Smart.tnt("L'Équipe"));
        assertEquals(27, Smart.tnt("franceinfo:"));
        assertEquals(0, Smart.tnt("Eurosport 1"));
    }

    @Test
    public void tntOrderThenAToZWithoutDoubles() {
        List<Item> items = Arrays.asList(ch("a", "FR: Eurosport 1 HD"), ch("b", "FR: M6 HD"), ch("c", "FR: TF1 SD"),
                ch("d", "FR: TF1 FHD"), ch("e", "FR: Arte"), ch("f", "FR: TF1 HD"), ch("g", "FR: Ciné+ Frisson"));
        assertEquals(Arrays.asList("TF1 FHD", "M6 HD", "Arte", "Ciné+ Frisson", "Eurosport 1 HD"),
                titles(Smart.arrange(items, Smart.ChannelOrder.TNT, true)));
        assertEquals(7, Smart.arrange(items, Smart.ChannelOrder.TNT, false).size());
        List<Item> panel = Smart.arrange(items, Smart.ChannelOrder.PANEL, true);
        assertEquals(Arrays.asList("Eurosport 1 HD", "M6 HD", "TF1 FHD", "Arte", "Ciné+ Frisson"), titles(panel));
    }
}
