package com.gathin.launchos.tv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Tidying the names panels send. */
public class NamesTest {
    private static Names.Clean c(String s) {
        return Names.clean(s);
    }

    @Test
    public void languagePrefixAndQualityGo() {
        assertEquals("TF1", c("FR: TF1 FHD").title);
        assertEquals("FHD", c("FR: TF1 FHD").tag);
        assertEquals("Canal+ Sport", c("|FR| Canal+ Sport HD").title);
        assertEquals("HD", c("|FR| Canal+ Sport HD").tag);
        assertEquals("BBC One", c("UK - BBC One").title);
        assertEquals("M6", c("FR: M6 ᴴᴰ").title);
        assertEquals("HD", c("FR: M6 ᴴᴰ").tag);
        assertEquals("4K", c("Film [UHD]").tag);
        assertEquals("Dossier 13", c("Dossier 13 (VOSTFR)").title);
        assertEquals("VOSTFR", c("Dossier 13 (VOSTFR)").tag);
        assertEquals("4K", c("Film VF 4K").tag);
        assertEquals("Film", c("Film VF 4K").title);
    }

    @Test
    public void unknownPrefixStays() {
        assertEquals("FBI: Most Wanted", c("FBI: Most Wanted").title);
        assertEquals("NCIS: Los Angeles", c("NCIS: Los Angeles").title);
    }

    @Test
    public void yearIsKeptApart() {
        Names.Clean x = c("VOSTFR - Dune (2021)");
        assertEquals("Dune", x.title);
        assertEquals(2021, x.year);
        assertEquals(1999, c("Matrix - 1999").year);
        assertEquals("Matrix", c("Matrix - 1999").title);
        assertEquals("2012", c("2012").title);
    }

    @Test
    public void nothingLeftGivesTheOriginal() {
        assertEquals("FR", c("FR:").title);
        assertEquals("HD", c("HD").title);
        assertEquals("", c(null).title);
    }

    @Test
    public void groupsAndSortKeys() {
        assertEquals("SPORT", Names.tidy("★★ SPORT ★★"));
        assertEquals("FR | Cinéma", Names.tidy("FR | Cinéma"));
        assertEquals("action", Names.groupKey("01 - Action"));
        assertEquals("action", Names.groupKey("01 Action"));
        assertEquals("cine ma eclair", Names.fold("Ciné-MA  Éclair!"));
        assertEquals("24", Names.groupKey("24"));
    }

    @Test
    public void matching() {
        assertEquals(0, Names.match("le seigneur des anneaux", "le sei"));
        assertEquals(1, Names.match("le seigneur des anneaux", "anneaux"));
        assertEquals(2, Names.match("le seigneur des anneaux", "eigneur nneaux"));
        assertEquals(-1, Names.match("le seigneur des anneaux", "hobbit"));
        assertEquals(-1, Names.match("x", ""));
    }

    @Test
    public void episodeTitles() {
        assertEquals("Le départ", Names.episode("Lupin", "Lupin S01E02 - Le départ"));
        assertEquals("Le départ", Names.episode("Lupin", "S01E02 - Le départ"));
        assertEquals("S01E02", Names.episode("Lupin", "S01E02"));
        assertEquals("Lupin", Names.episode("Lupin", "Lupin"));
    }

    @Test
    public void sharedGroupPrefixGoes() {
        java.util.List<String> raw = java.util.Arrays.asList("|FR| SPORT ★", "FR | Généralistes", "FR | Cinéma",
                "★ Information ★", "FR | Vide", "01 - Jeunesse", "UK | Sport");
        String p = Names.groupPrefix(raw);
        assertEquals("FR", p);
        assertEquals("Sport", Names.group("|FR| SPORT ★", p));
        assertEquals("Cinéma", Names.group("FR | Cinéma", p));
        assertEquals("UK | Sport", Names.group("UK | Sport", p));
        assertEquals("Jeunesse", Names.group("01 - Jeunesse", p));
        assertEquals("24h Info", Names.group("24h Info", p));
        assertEquals("Information", Names.group("★ Information ★", p));
        // No word shared by half of the groups: nothing is taken off.
        assertEquals(null, Names.groupPrefix(java.util.Arrays.asList("FR | A", "UK | B", "US | C", "D")));
        assertEquals("FR | A", Names.group("FR | A", null));
    }

    @Test
    public void capitals() {
        assertEquals("Sport France", Names.uncap("SPORT FRANCE"));
        assertEquals("Canal+ TV", Names.uncap("CANAL+ TV"));
        assertEquals("Cinéma Français", Names.uncap("CINÉMA FRANÇAIS"));
        assertEquals("F1", Names.uncap("F1"));
        assertEquals("beIN Sports", Names.uncap("beIN Sports"));
    }
}
