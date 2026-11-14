package com.gathin.launchos.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** History's stored entries: the old positional arrays are read once into named objects, with the same values. */
public class HistoryTest {
    @Test
    public void oldWatchArraysBecomeTheSameChannels() throws Exception {
        JSONObject watch = new JSONObject().put("101",
                new JSONArray().put(185_000L).put(1_700_000_000_000L).put("FR: TF1 HD").put("http://l/1.png").put(1).put("7"));
        assertTrue(History.migrate(History.WATCH, watch));
        JSONObject o = watch.getJSONObject("101");
        assertEquals(185_000L, o.getLong(History.W_MS));
        Item c = History.channel("101", o);
        assertEquals(Item.Kind.LIVE, c.kind);
        assertEquals("101", c.id);
        assertEquals("FR: TF1 HD", c.name);
        assertEquals("http://l/1.png", c.image);
        assertEquals(1, c.num);
        assertEquals("7", c.categoryId);
        // Read again: already named, nothing to do.
        assertFalse(History.migrate(History.WATCH, watch));
    }

    @Test
    public void oldResumeArraysBecomeTheSameEntries() throws Exception {
        JSONObject resume = new JSONObject()
                .put("m:9", new JSONArray().put(600_000L).put(6_000_000L).put(1_700_000_000_000L).put("Film").put("p.jpg")
                        .put("mkv").put(""))
                .put("e:31", new JSONArray().put(120_000L).put(2_400_000L).put(1_700_000_100_000L).put("Ep").put("")
                        .put("mp4").put("4"));
        assertTrue(History.migrate(History.RESUME, resume));
        History.Entry film = History.entry("9", resume.getJSONObject("m:9"));
        assertEquals(600_000L, film.position);
        assertEquals(6_000_000L, film.duration);
        assertEquals(1_700_000_000_000L, film.at);
        assertEquals("Film", film.name);
        assertEquals("p.jpg", film.image);
        assertEquals("mkv", film.ext);
        assertEquals("", film.extra);
        History.Entry ep = History.entry("31", resume.getJSONObject("e:31"));
        assertEquals(120_000L, ep.position);
        assertEquals("mp4", ep.ext);
        assertEquals("4", ep.extra);
    }

    @Test
    public void oldSeriesArraysKeepTheirLastEpisodeAndDoneMark() throws Exception {
        JSONObject series = new JSONObject()
                .put("4", new JSONArray().put("41").put(2).put(1_700_000_000_000L).put("Show").put("c.jpg").put(3).put(true))
                .put("5", new JSONArray().put("51").put(1).put(1_600_000_000_000L).put("Other").put("").put(1));
        assertTrue(History.migrate(History.SERIES, series));
        assertArrayEquals(new String[]{"41", "2", "3", "1"}, History.last(series.getJSONObject("4")));
        assertArrayEquals(new String[]{"51", "1", "1", ""}, History.last(series.getJSONObject("5")));
        Item s = History.seriesItem("4", series.getJSONObject("4"));
        assertEquals(Item.Kind.SERIES, s.kind);
        assertEquals("Show", s.name);
        assertEquals("c.jpg", s.image);
        assertEquals(1_700_000_000L, s.added);
    }

    @Test
    public void namedEntriesAreLeftAlone() throws Exception {
        JSONObject named = new JSONObject().put("m:1", new JSONObject().put(History.R_POS, 5L));
        String before = named.toString();
        assertFalse(History.migrate(History.RESUME, named));
        assertEquals(before, named.toString());
    }
}
