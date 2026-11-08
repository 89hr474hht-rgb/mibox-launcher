package com.gathin.launchos.tv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Plain GET requests. Follows redirects itself, as panels often send http to https or to another host. */
final class Http {
    static final String USER_AGENT = "LaunchOS-TV/" + BuildConfig.VERSION_NAME;
    private static final int MAX_REDIRECTS = 5;

    private Http() {
    }

    /** Reads a 200 answer's body as it arrives. */
    interface Body<T> {
        T read(InputStream in) throws IOException, XtreamException;
    }

    /** How much of a body has arrived (background thread); total is -1 when the server did not say. */
    interface Progress {
        void bytes(long read, long total);
    }

    /** The body of a 200 answer, or an {@link XtreamException} that never quotes the address (it holds the account). */
    static byte[] get(String address, int maxBytes) throws XtreamException {
        return get(address, maxBytes, in -> readAll(in, maxBytes));
    }

    /** A 200 answer handed to body as a stream, cut off after maxBytes. */
    static <T> T get(String address, long maxBytes, Body<T> body) throws XtreamException {
        return get(address, maxBytes, body, null);
    }

    /** The same, telling progress how much has arrived as it is read (about every 64 KB). */
    static <T> T get(String address, long maxBytes, Body<T> body, Progress progress) throws XtreamException {
        String url = address;
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(10_000);
                c.setReadTimeout(30_000);
                c.setRequestProperty("User-Agent", USER_AGENT);
                c.setRequestProperty("Accept-Encoding", "identity");
                try {
                    int code = c.getResponseCode();
                    if (code >= 300 && code < 400) {
                        String next = c.getHeaderField("Location");
                        if (next == null) throw new XtreamException(XtreamException.Kind.SERVER, code);
                        url = new URL(new URL(url), next).toString();
                        continue;
                    }
                    if (code != 200) {
                        throw new XtreamException(code == 401 || code == 403
                                ? XtreamException.Kind.REFUSED : XtreamException.Kind.SERVER, code);
                    }
                    try (InputStream in = new Capped(c.getInputStream(), maxBytes, progress, c.getContentLengthLong())) {
                        return body.read(in);
                    }
                } finally {
                    c.disconnect();
                }
            }
            throw new XtreamException(XtreamException.Kind.SERVER, 310);
        } catch (java.net.UnknownHostException e) {
            throw new XtreamException(XtreamException.Kind.UNKNOWN_HOST, 0);
        } catch (java.net.SocketTimeoutException e) {
            throw new XtreamException(XtreamException.Kind.TIMEOUT, 0);
        } catch (java.net.MalformedURLException | IllegalArgumentException e) {
            throw new XtreamException(XtreamException.Kind.BAD_ADDRESS, 0);
        } catch (IOException e) {
            throw new XtreamException(XtreamException.Kind.NETWORK, 0);
        }
    }

    /** Stops a body that grows past its limit, rather than filling the box's memory; tells progress as it goes. */
    private static final class Capped extends java.io.FilterInputStream {
        private static final long STEP = 64 * 1024;
        private long left;
        private final Progress progress;
        private final long total;
        private long read, told;

        Capped(InputStream in, long max, Progress progress, long total) {
            super(in);
            left = max;
            this.progress = progress;
            this.total = total;
            if (progress != null) progress.bytes(0, total);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) counted(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) counted(n);
            else if (n < 0 && progress != null && told != read) progress.bytes(told = read, total);
            return n;
        }

        private void counted(int n) throws IOException {
            if ((left -= n) < 0) throw new IOException("too large");
            read += n;
            if (progress != null && read - told >= STEP) progress.bytes(told = read, total);
        }
    }

    private static byte[] readAll(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        byte[] buf = new byte[32 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > maxBytes) throw new IOException("too large");
        }
        return out.toByteArray();
    }
}
