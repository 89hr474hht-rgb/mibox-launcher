package com.gathin.launchos.tv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One GitHub release of LaunchOS TV, read from the REST API's JSON. LaunchOS TV shares the launcher's repository:
 * its tags are tv-vX.Y.Z and its APK is launchos-tv-X.Y.Z.apk, so each app's updater ignores the other's releases.
 */
public final class Release {
    static final String TAG_PREFIX = "tv-v";
    static final String ASSET_PREFIX = "launchos-tv-";

    public final SemVer version;
    public final String notes;
    public final String publishedAt;
    public final String apkUrl;
    public final long apkSize;
    /** "sha256:<hex>" as GitHub reports it for the asset, or null on an API that does not. */
    public final String apkDigest;
    public final String sha256Url;

    private Release(SemVer version, String notes, String publishedAt, String apkUrl, long apkSize,
                    String apkDigest, String sha256Url) {
        this.version = version;
        this.notes = notes;
        this.publishedAt = publishedAt;
        this.apkUrl = apkUrl;
        this.apkSize = apkSize;
        this.apkDigest = apkDigest;
        this.sha256Url = sha256Url;
    }

    /**
     * The release, or null when it is not one this updater may offer: a draft or pre-release, a tag that
     * is not tv-vX.Y.Z, or a release without exactly the expected APK and checksum assets on GitHub's hosts.
     */
    public static Release fromJson(JSONObject o) {
        if (o == null || o.optBoolean("draft") || o.optBoolean("prerelease")) return null;
        String tag = o.optString("tag_name", "");
        if (!tag.startsWith(TAG_PREFIX)) return null;
        SemVer v = SemVer.parse(tag.substring(TAG_PREFIX.length() - 1));
        if (v == null) return null;
        String apkName = ASSET_PREFIX + v + ".apk";
        String apkUrl = null, digest = null, shaUrl = null;
        long size = -1;
        JSONArray assets = o.optJSONArray("assets");
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null) continue;
            String name = a.optString("name");
            String url = a.optString("browser_download_url", null);
            if (!isGitHubDownload(url)) continue;
            if (name.equals(apkName)) {
                apkUrl = url;
                size = a.optLong("size", -1);
                String d = a.optString("digest", null);
                digest = d == null || d.isEmpty() || "null".equals(d) ? null : d.toLowerCase(Locale.ROOT);
            } else if (name.equals(apkName + ".sha256")) {
                shaUrl = url;
            }
        }
        if (apkUrl == null || shaUrl == null || size <= 0) return null;
        return new Release(v, o.optString("body", ""), o.optString("published_at", ""), apkUrl, size, digest, shaUrl);
    }

    /** Releases newest first, skipping the ones fromJson refuses. */
    public static List<Release> listFromJson(String json) throws JSONException {
        JSONArray arr = new JSONArray(json);
        List<Release> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            Release r = fromJson(arr.optJSONObject(i));
            if (r != null) out.add(r);
        }
        out.sort((a, b) -> b.version.compareTo(a.version));
        return out;
    }

    /** Only HTTPS links to this repository's release downloads on github.com. */
    static boolean isGitHubDownload(String url) {
        if (url == null) return false;
        try {
            URI u = URI.create(url);
            return "https".equals(u.getScheme()) && "github.com".equals(u.getHost())
                    && u.getPath() != null
                    && u.getPath().startsWith("/" + BuildConfig.RELEASES_REPO + "/releases/download/");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** The first lines of the notes, for the small top-right prompt. */
    public String summary(int maxLines) {
        StringBuilder sb = new StringBuilder();
        int lines = 0;
        for (String line : notes.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("SHA-256")) continue;
            if (t.startsWith("- ") || t.startsWith("* ")) t = "• " + t.substring(2);
            if (sb.length() > 0) sb.append('\n');
            sb.append(t);
            if (++lines >= maxLines) break;
        }
        return sb.toString();
    }

    /** Stored in SharedPreferences between the check and the install. */
    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("tag_name", TAG_PREFIX + version);
            o.put("body", notes);
            o.put("published_at", publishedAt);
            JSONArray assets = new JSONArray();
            JSONObject apk = new JSONObject();
            apk.put("name", ASSET_PREFIX + version + ".apk");
            apk.put("browser_download_url", apkUrl);
            apk.put("size", apkSize);
            if (apkDigest != null) apk.put("digest", apkDigest);
            assets.put(apk);
            JSONObject sha = new JSONObject();
            sha.put("name", ASSET_PREFIX + version + ".apk.sha256");
            sha.put("browser_download_url", sha256Url);
            assets.put(sha);
            o.put("assets", assets);
            return o;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}
