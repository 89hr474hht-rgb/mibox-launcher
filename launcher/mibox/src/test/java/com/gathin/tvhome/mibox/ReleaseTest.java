package com.gathin.tvhome.mibox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class ReleaseTest {
    private static final String BASE = "https://github.com/" + BuildConfig.RELEASES_REPO + "/releases/download/";
    private static final String HEX = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    static JSONObject release(String tag) throws Exception {
        String v = tag.substring(1);
        JSONObject o = new JSONObject();
        o.put("tag_name", tag);
        o.put("draft", false);
        o.put("prerelease", false);
        o.put("body", "## What's new\n- First line\n- Second line\n\nSHA-256: " + HEX);
        o.put("published_at", "2026-11-05T20:00:00Z");
        JSONArray assets = new JSONArray();
        assets.put(asset("mibox-launcher-" + v + ".apk", BASE + tag + "/mibox-launcher-" + v + ".apk", 600_000,
                "sha256:" + HEX.toUpperCase()));
        assets.put(asset("mibox-launcher-" + v + ".apk.sha256", BASE + tag + "/mibox-launcher-" + v + ".apk.sha256", 100, null));
        o.put("assets", assets);
        return o;
    }

    private static JSONObject asset(String name, String url, long size, String digest) throws Exception {
        JSONObject a = new JSONObject();
        a.put("name", name);
        a.put("browser_download_url", url);
        a.put("size", size);
        if (digest != null) a.put("digest", digest);
        return a;
    }

    @Test
    public void readsAPublishedRelease() throws Exception {
        Release r = Release.fromJson(release("v1.2.0"));
        assertNotNull(r);
        assertEquals("1.2.0", r.version.toString());
        assertEquals(600_000, r.apkSize);
        assertEquals("sha256:" + HEX, r.apkDigest);
        assertTrue(r.apkUrl.endsWith("/v1.2.0/mibox-launcher-1.2.0.apk"));
        assertTrue(r.sha256Url.endsWith(".apk.sha256"));
    }

    @Test
    public void refusesDraftsPrereleasesAndOddTags() throws Exception {
        assertNull(Release.fromJson(release("v1.2.0").put("draft", true)));
        assertNull(Release.fromJson(release("v1.2.0").put("prerelease", true)));
        assertNull(Release.fromJson(release("v1.2.0").put("tag_name", "v1.2.0-rc1")));
        assertNull(Release.fromJson(release("v1.2.0").put("tag_name", "latest")));
    }

    @Test
    public void refusesMissingOrForeignAssets() throws Exception {
        JSONObject noSha = release("v1.2.0");
        noSha.getJSONArray("assets").remove(1);
        assertNull(Release.fromJson(noSha));

        JSONObject foreign = release("v1.2.0");
        foreign.getJSONArray("assets").getJSONObject(0)
                .put("browser_download_url", "https://example.com/mibox-launcher-1.2.0.apk");
        assertNull(Release.fromJson(foreign));

        JSONObject otherVersion = release("v1.2.0");
        otherVersion.getJSONArray("assets").getJSONObject(0).put("name", "mibox-launcher-1.1.0.apk");
        assertNull(Release.fromJson(otherVersion));

        JSONObject empty = release("v1.2.0");
        empty.getJSONArray("assets").getJSONObject(0).put("size", 0);
        assertNull(Release.fromJson(empty));
    }

    @Test
    public void acceptsOnlyThisRepositorysDownloads() {
        assertTrue(Release.isGitHubDownload(BASE + "v1.0.0/x.apk"));
        assertFalse(Release.isGitHubDownload("http://github.com/" + BuildConfig.RELEASES_REPO + "/releases/download/v1/x.apk"));
        assertFalse(Release.isGitHubDownload("https://github.com/someone/else/releases/download/v1/x.apk"));
        assertFalse(Release.isGitHubDownload("https://github.com.evil.example/" + BuildConfig.RELEASES_REPO + "/releases/download/v1/x.apk"));
        assertFalse(Release.isGitHubDownload("https://evil.example/?u=https://github.com/" + BuildConfig.RELEASES_REPO + "/releases/download/"));
        assertFalse(Release.isGitHubDownload(null));
        assertFalse(Release.isGitHubDownload("not a url"));
    }

    @Test
    public void listIsNewestFirstAndSkipsBadEntries() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(release("v1.0.0"));
        arr.put(release("v1.10.0"));
        arr.put(release("v1.2.0").put("draft", true));
        arr.put(release("v1.9.0"));
        List<Release> list = Release.listFromJson(arr.toString());
        assertEquals(3, list.size());
        assertEquals("1.10.0", list.get(0).version.toString());
        assertEquals("1.9.0", list.get(1).version.toString());
        assertEquals("1.0.0", list.get(2).version.toString());
    }

    @Test
    public void survivesItsOwnStorage() throws Exception {
        Release r = Release.fromJson(release("v1.3.1"));
        Release back = Release.fromJson(new JSONObject(r.toJson().toString()));
        assertNotNull(back);
        assertEquals(r.version, back.version);
        assertEquals(r.apkUrl, back.apkUrl);
        assertEquals(r.sha256Url, back.sha256Url);
        assertEquals(r.apkSize, back.apkSize);
        assertEquals(r.apkDigest, back.apkDigest);
        assertEquals(r.notes, back.notes);
    }

    @Test
    public void summaryKeepsTheFirstListLines() throws Exception {
        Release r = Release.fromJson(release("v1.2.0"));
        assertEquals("• First line\n• Second line", r.summary(3));
        assertEquals("• First line", r.summary(1));
    }

    @Test
    public void plainNotesDropMarkdownAndTheChecksum() {
        assertEquals("What's new\n• Faster start\n• Fixed the clock",
                UpdatesActivity.plainNotes("## What's new\n- **Faster** start\n* Fixed the `clock`\nSHA-256: abc"));
    }
}
