package com.gathin.launchos.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONTokener;
import org.junit.Test;

import java.util.List;

/** The parsers against the shapes real panels send. */
public class XtreamTest {
    private static Object json(String s) {
        try {
            return new JSONTokener(s).nextValue();
        } catch (org.json.JSONException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void loginReadsNumbersSentAsStrings() throws Exception {
        Xtream.AccountInfo i = Xtream.parseLogin(json("{\"user_info\":{\"auth\":1,\"status\":\"Active\","
                + "\"exp_date\":\"1804343746\",\"max_connections\":\"2\",\"active_cons\":\"1\","
                + "\"allowed_output_formats\":[\"m3u8\",\"ts\",\"rtmp\"]}}"));
        assertEquals(1804343746L, i.expires);
        assertEquals(2, i.maxConnections);
        assertEquals(1, i.activeConnections);
        assertTrue(i.m3u8);
        assertTrue(i.ts);
    }

    @Test
    public void loginWithoutExpiryOrFormats() throws Exception {
        Xtream.AccountInfo i = Xtream.parseLogin(json("{\"user_info\":{\"auth\":\"1\",\"status\":\"Active\",\"exp_date\":null}}"));
        assertEquals(0, i.expires);
        assertTrue(i.ts);
        assertFalse(i.m3u8);
    }

    @Test
    public void refusedLogin() {
        try {
            Xtream.parseLogin(json("{\"user_info\":{\"auth\":0}}"));
            fail();
        } catch (XtreamException e) {
            assertEquals(XtreamException.Kind.AUTH, e.kind);
        }
    }

    @Test
    public void expiredAccount() {
        try {
            Xtream.parseLogin(json("{\"user_info\":{\"auth\":1,\"status\":\"Expired\"}}"));
            fail();
        } catch (XtreamException e) {
            assertEquals(XtreamException.Kind.ACCOUNT_STATUS, e.kind);
            assertEquals("Expired", e.status);
        }
    }

    @Test
    public void itemsSkipEntriesWithoutId() {
        List<Item> l = Xtream.parseItems(json("[{\"stream_id\":7,\"name\":\"A\",\"stream_icon\":null,\"num\":\"3\"},"
                + "{\"name\":\"no id\"},{\"stream_id\":\"8\",\"name\":\"B\",\"container_extension\":\"mkv\"}]"), Item.Kind.MOVIE);
        assertEquals(2, l.size());
        assertEquals("7", l.get(0).id);
        assertEquals("", l.get(0).image);
        assertEquals(3, l.get(0).num);
        assertEquals("mkv", l.get(1).ext);
    }

    @Test
    public void emptyCategoryAsObject() {
        assertEquals(0, Xtream.parseItems(json("{}"), Item.Kind.LIVE).size());
        assertEquals(0, Xtream.parseCategories(json("{}")).size());
    }

    @Test
    public void episodesAsMapAreSortedBySeasonAndNumber() {
        Xtream.SeriesInfo s = Xtream.parseSeriesInfo(json("{\"seasons\":[],\"info\":{\"plot\":\"P\"},"
                + "\"episodes\":{\"2\":[{\"id\":\"21\",\"episode_num\":1,\"title\":\"x\"}],"
                + "\"1\":[{\"id\":\"12\",\"episode_num\":\"2\"},{\"id\":\"11\",\"episode_num\":1,\"title\":\"one\"}]}}"));
        assertEquals("P", s.plot);
        assertEquals(2, s.seasons.size());
        assertEquals(1, s.seasons.get(0).number);
        assertEquals("Saison 1", s.seasons.get(0).name);
        assertEquals("11", s.seasons.get(0).episodes.get(0).id);
        assertEquals("Épisode 2", s.seasons.get(0).episodes.get(1).name);
    }

    @Test
    public void episodesAsList() {
        Xtream.SeriesInfo s = Xtream.parseSeriesInfo(json("{\"seasons\":[{\"season_number\":3,\"name\":\"Finale\"}],"
                + "\"episodes\":[{\"id\":\"31\",\"episode_num\":1,\"season\":3},{\"id\":\"11\",\"episode_num\":1,\"season\":\"1\"}]}"));
        assertEquals(2, s.seasons.size());
        assertEquals("Finale", s.seasons.get(1).name);
        assertEquals("31", s.seasons.get(1).episodes.get(0).id);
    }

    @Test
    public void serverAddressIsNormalised() {
        assertEquals("http://line.example.org:8080", Account.normalizeServer(" line.example.org:8080/ "));
        assertEquals("http://h.example:8080", Account.normalizeServer("http://h.example:8080/player_api.php?username=a"));
        assertEquals("https://h.example", Account.normalizeServer("HTTPS://h.example/get.php"));
        assertNull(Account.normalizeServer(""));
        assertNull(Account.normalizeServer("http://"));
    }

    @Test
    public void credentialsFromM3uLink() {
        String[] c = Account.credentialsFromLink("http://h.example:8080/get.php?username=ab%40c&password=p%26q&type=m3u_plus");
        assertEquals("ab@c", c[0]);
        assertEquals("p&q", c[1]);
        assertNull(Account.credentialsFromLink("http://h.example:8080"));
    }

    @Test
    public void streamAddresses() {
        Xtream x = new Xtream(new Account("http://h.example:8080", "u", "p w"));
        Item ch = new Item(Item.Kind.LIVE, "5", "c", "", "", 1, "");
        assertEquals("http://h.example:8080/live/u/p%20w/5.ts", x.liveUrl(ch, false));
        assertEquals("http://h.example:8080/live/u/p%20w/5.m3u8", x.liveUrl(ch, true));
        Item film = new Item(Item.Kind.MOVIE, "9", "f", "", "mkv", 1, "");
        assertEquals("http://h.example:8080/movie/u/p%20w/9.mkv", x.movieUrl(film));
    }
}
