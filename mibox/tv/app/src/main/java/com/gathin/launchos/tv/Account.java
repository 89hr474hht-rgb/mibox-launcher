package com.gathin.launchos.tv;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** The Xtream account: the server's base address (scheme, host, port), the user name and the password. */
final class Account {
    final String server;
    final String user;
    final String password;

    Account(String server, String user, String password) {
        this.server = server;
        this.user = user;
        this.password = password;
    }

    /**
     * The base address from what the owner typed: adds http:// when missing, and drops a path such as
     * /player_api.php or /get.php. Null when it is not an address.
     */
    static String normalizeServer(String typed) {
        if (typed == null) return null;
        String s = typed.trim();
        if (s.isEmpty()) return null;
        if (!s.matches("(?i)^https?://.*")) s = "http://" + s;
        try {
            URI u = new URI(s);
            if (u.getHost() == null || u.getHost().isEmpty()) return null;
            String scheme = u.getScheme().toLowerCase(java.util.Locale.ROOT);
            return scheme + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
        } catch (java.net.URISyntaxException e) {
            return null;
        }
    }

    /**
     * The user name and password from a pasted M3U link (http://host:port/get.php?username=…&password=…),
     * or null when the text has none.
     */
    static String[] credentialsFromLink(String typed) {
        if (typed == null) return null;
        int q = typed.indexOf('?');
        if (q < 0) return null;
        String user = null, pass = null;
        for (String part : typed.substring(q + 1).split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String key = part.substring(0, eq), value;
            try {
                value = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8.name());
            } catch (java.io.UnsupportedEncodingException | IllegalArgumentException e) {
                continue;
            }
            if (key.equals("username")) user = value;
            else if (key.equals("password")) pass = value;
        }
        return user != null && pass != null ? new String[]{user, pass} : null;
    }
}
