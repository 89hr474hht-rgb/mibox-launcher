package com.gathin.launchos.tv;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Which trailer address the app agrees to open. The panel sends a YouTube video id, sometimes a whole address; only a
 * YouTube video is accepted, opened as https://www.youtube.com/watch?v=ID, so a panel cannot send the family to any
 * other site. Pure, tested on the JVM.
 */
final class Trailers {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    private Trailers() {
    }

    /** The address to open for what the panel sent, or null when it is not a YouTube video. */
    static String youtube(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (ID.matcher(s).matches()) return watch(s);
        URI u;
        try {
            u = new URI(s);
        } catch (java.net.URISyntaxException e) {
            return null;
        }
        if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null || u.getPort() != -1
                || u.getRawUserInfo() != null) {
            return null;
        }
        String host = u.getHost().toLowerCase(Locale.ROOT);
        String path = u.getPath() == null ? "" : u.getPath();
        String id = null;
        if (host.equals("youtu.be")) {
            id = path.startsWith("/") ? path.substring(1) : path;
        } else if (host.equals("youtube.com") || host.equals("www.youtube.com") || host.equals("m.youtube.com")) {
            if (path.equals("/watch")) id = param(u.getRawQuery(), "v");
            else if (path.startsWith("/embed/")) id = path.substring("/embed/".length());
        }
        return id != null && ID.matcher(id).matches() ? watch(id) : null;
    }

    private static String watch(String id) {
        return "https://www.youtube.com/watch?v=" + id;
    }

    private static String param(String query, String name) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(name)) return part.substring(eq + 1);
        }
        return null;
    }
}
