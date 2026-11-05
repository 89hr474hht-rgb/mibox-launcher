package com.gathin.tvhome.mibox;

import java.util.Locale;

/** Parsing and comparing SHA-256 values; plain Java so the unit tests cover it. */
final class Checksums {
    private Checksums() {
    }

    /** The hex digest from a sha256sum-style line ("<hex>  file.apk"), or null when there is none. */
    static String parseShaFile(String text) {
        if (text == null) return null;
        String t = text.trim();
        int end = 0;
        while (end < t.length() && !Character.isWhitespace(t.charAt(end))) end++;
        String hex = t.substring(0, end).toLowerCase(Locale.ROOT);
        return isSha256(hex) ? hex : null;
    }

    /** GitHub's asset digest ("sha256:<hex>") as bare hex, or null for another algorithm. */
    static String fromGitHubDigest(String digest) {
        if (digest == null || !digest.startsWith("sha256:")) return null;
        String hex = digest.substring(7).toLowerCase(Locale.ROOT);
        return isSha256(hex) ? hex : null;
    }

    static boolean isSha256(String hex) {
        if (hex == null || hex.length() != 64) return false;
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return sb.toString();
    }
}
