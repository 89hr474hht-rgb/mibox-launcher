package com.gathin.launchos.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.json.JSONTokener;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class EpgTest {
    private static Object json(String s) {
        try {
            return new JSONTokener(s).nextValue();
        } catch (JSONException e) {
            throw new AssertionError(e);
        }
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void readsBase64ListingsWithTimestampsAsStrings() {
        String body = "{\"epg_listings\":["
                + "{\"title\":\"" + b64("Météo") + "\",\"description\":\"" + b64("Le temps de demain.") + "\","
                + "\"start_timestamp\":\"2000\",\"stop_timestamp\":\"3000\"},"
                + "{\"title\":\"" + b64("Le journal") + "\",\"start_timestamp\":\"1000\",\"stop_timestamp\":\"2000\"}]}";
        List<Epg.Programme> list = Epg.parse(json(body));
        assertEquals(2, list.size());
        assertEquals("Le journal", list.get(0).title);   // sorted by start
        assertEquals("Météo", list.get(1).title);
        assertEquals("Le temps de demain.", list.get(1).description);
        assertEquals(2000, list.get(1).start);
    }

    @Test
    public void keepsPlainTitlesAndSkipsBrokenEntries() {
        String body = "{\"epg_listings\":["
                + "{\"title\":\"Match en direct\",\"start_timestamp\":1000,\"stop_timestamp\":2000},"
                + "{\"title\":\"Test\",\"start_timestamp\":2000,\"stop_timestamp\":3000},"
                + "{\"title\":\"\",\"start_timestamp\":3000,\"stop_timestamp\":4000},"
                + "{\"title\":\"" + b64("Fin avant le début") + "\",\"start_timestamp\":5000,\"stop_timestamp\":4000},"
                + "null]}";
        List<Epg.Programme> list = Epg.parse(json(body));
        assertEquals(2, list.size());
        assertEquals("Match en direct", list.get(0).title);
        // Looks like base64 but does not decode to text: kept as it is.
        assertEquals("Test", list.get(1).title);
    }

    @Test
    public void emptyOrOddAnswersGiveNothing() {
        assertTrue(Epg.parse(json("{\"epg_listings\":[]}")).isEmpty());
        assertTrue(Epg.parse(json("[]")).isEmpty());
        assertTrue(Epg.parse(json("{}")).isEmpty());
        assertTrue(Epg.parse("not json").isEmpty());
    }

    @Test
    public void nowNextAndProgress() {
        List<Epg.Programme> list = Epg.parse(json("{\"epg_listings\":["
                + "{\"title\":\"A\",\"start_timestamp\":1000,\"stop_timestamp\":2000},"
                + "{\"title\":\"B\",\"start_timestamp\":2000,\"stop_timestamp\":3000}]}"));
        Epg.Listing l = new Epg.Listing(list, 1_500_000);
        assertEquals("A", l.now(1500).title);
        assertEquals("B", l.next(1500).title);
        assertEquals(0.5f, l.now(1500).progress(1500), 0.001f);
        assertEquals("B", l.now(2000).title);
        assertNull(l.next(2500));
        assertNull(l.now(3000));
    }

    @Test
    public void aListingIsKeptUntilItsProgrammeEnds() {
        List<Epg.Programme> list = Epg.parse(json("{\"epg_listings\":["
                + "{\"title\":\"A\",\"start_timestamp\":1000,\"stop_timestamp\":2000}]}"));
        Epg.Listing l = new Epg.Listing(list, 1_200_000);
        assertTrue(l.fresh(1_500_000));
        assertFalse(l.fresh(2_000_000));
        assertFalse(l.fresh(1_100_000));   // the clock went back
        Epg.Listing empty = new Epg.Listing(java.util.Collections.emptyList(), 1_000_000);
        assertTrue(empty.fresh(1_000_000 + 29 * 60_000));
        assertFalse(empty.fresh(1_000_000 + 31 * 60_000));
    }

    @Test
    public void shiftMovesEveryProgrammeAndKeepsTheRest() {
        List<Epg.Programme> in = java.util.Arrays.asList(new Epg.Programme("Journal", "Infos", 1000, 2800),
                new Epg.Programme("Film", "", 2800, 9000));
        List<Epg.Programme> out = Epg.shift(in, 3600);
        assertEquals(2, out.size());
        assertEquals(4600, out.get(0).start);
        assertEquals(6400, out.get(0).end);
        assertEquals("Journal", out.get(0).title);
        assertEquals("Infos", out.get(0).description);
        assertEquals(6400, out.get(1).start);
        assertEquals(1000, in.get(0).start);                 // the panel's own listing is left as it was
        assertTrue(Epg.shift(in, 0) == in);                  // no shift: nothing copied
        assertEquals(-6200, Epg.shift(in, -7200).get(0).start);
    }
}
