package com.gathin.launchos.tv;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Reads the repository's releases (LaunchOS's and LaunchOS TV's; only tv-v tags are kept) from GitHub's public REST API and downloads their assets. HTTPS only, no
 * token, no account: the API allows 60 requests an hour per address, and the app makes about one a day.
 */
final class ReleaseClient {
    static final String API = "https://api.github.com/repos/" + BuildConfig.RELEASES_REPO + "/releases?per_page=30";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int MAX_JSON = 1 << 20;
    private static final int MAX_SHA_FILE = 4096;
    static final long MAX_APK = 32L << 20;

    interface Progress {
        void onProgress(long done, long total);
    }

    private ReleaseClient() {
    }

    /** Published releases, newest first. */
    static List<Release> fetch(String userAgent) throws UpdateException {
        byte[] body = get(API, MAX_JSON, userAgent, "application/vnd.github+json");
        try {
            return Release.listFromJson(new String(body, StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new UpdateException(R.string.upd_err_format);
        }
    }

    /** The hex SHA-256 published next to the APK. */
    static String fetchSha(Release r, String userAgent) throws UpdateException {
        String hex = Checksums.parseShaFile(new String(get(r.sha256Url, MAX_SHA_FILE, userAgent, "*/*"),
                StandardCharsets.UTF_8));
        if (hex == null) throw new UpdateException(R.string.upd_err_format);
        return hex;
    }

    /** Streams the APK into out and returns its SHA-256 in hex; the size must be the one the API announced. */
    static String download(Release r, OutputStream out, String userAgent, Progress progress) throws UpdateException {
        HttpURLConnection c = open(r.apkUrl, userAgent, "application/octet-stream");
        try {
            check(c);
            long total = r.apkSize;
            long announced = c.getContentLengthLong();
            if (announced >= 0 && announced != total) throw new UpdateException(R.string.upd_err_size);
            MessageDigest sha = sha256();
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            try (InputStream in = c.getInputStream()) {
                int n;
                while ((n = in.read(buf)) > 0) {
                    done += n;
                    if (done > total) throw new UpdateException(R.string.upd_err_size);
                    sha.update(buf, 0, n);
                    out.write(buf, 0, n);
                    progress.onProgress(done, total);
                }
            }
            if (done != total) throw new UpdateException(R.string.upd_err_size);
            return Checksums.hex(sha.digest());
        } catch (IOException e) {
            throw network(e);
        } finally {
            c.disconnect();
        }
    }

    private static byte[] get(String url, int max, String userAgent, String accept) throws UpdateException {
        HttpURLConnection c = open(url, userAgent, accept);
        try {
            check(c);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            try (InputStream in = c.getInputStream()) {
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (out.size() + n > max) throw new UpdateException(R.string.upd_err_format);
                    out.write(buf, 0, n);
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw network(e);
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String userAgent, String accept) throws UpdateException {
        if (url == null || !url.startsWith("https://")) throw new UpdateException(R.string.upd_err_format);
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(CONNECT_TIMEOUT_MS);
            c.setReadTimeout(READ_TIMEOUT_MS);
            // HttpURLConnection follows https -> https redirects (GitHub's asset CDN) but never https -> http.
            c.setInstanceFollowRedirects(true);
            c.setUseCaches(false);
            c.setRequestProperty("User-Agent", userAgent);
            c.setRequestProperty("Accept", accept);
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            return c;
        } catch (IOException e) {
            throw network(e);
        }
    }

    private static void check(HttpURLConnection c) throws IOException, UpdateException {
        int code = c.getResponseCode();
        if (!"https".equals(c.getURL().getProtocol())) throw new UpdateException(R.string.upd_err_format);
        if (code == 403 || code == 429) throw new UpdateException(R.string.upd_err_rate);
        if (code != 200) throw new UpdateException(R.string.upd_err_http, code);
    }

    private static UpdateException network(IOException e) {
        return new UpdateException(R.string.upd_err_network, e.getClass().getSimpleName());
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
