package com.gathin.tvhome.mibox;

/** MAJOR.MINOR.PATCH, with an optional leading "v". Pre-releases ("1.2.0-beta") are not offered. */
public final class SemVer implements Comparable<SemVer> {
    public final int major, minor, patch;

    private SemVer(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    /** The version, or null when the text is not a plain release version. */
    public static SemVer parse(String text) {
        if (text == null) return null;
        String s = text.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        String[] parts = s.split("\\.", -1);
        if (parts.length != 3) return null;
        int[] n = new int[3];
        for (int i = 0; i < 3; i++) {
            String p = parts[i];
            if (p.isEmpty() || p.length() > 5 || (p.length() > 1 && p.charAt(0) == '0')) return null;
            // ASCII only: Character.isDigit and parseInt also accept other scripts' digits.
            for (int c = 0; c < p.length(); c++) if (p.charAt(c) < '0' || p.charAt(c) > '9') return null;
            n[i] = Integer.parseInt(p);
        }
        return new SemVer(n[0], n[1], n[2]);
    }

    @Override
    public int compareTo(SemVer o) {
        if (major != o.major) return Integer.compare(major, o.major);
        if (minor != o.minor) return Integer.compare(minor, o.minor);
        return Integer.compare(patch, o.patch);
    }

    public boolean isNewerThan(SemVer o) {
        return o == null || compareTo(o) > 0;
    }

    /** Same formula as app/build.gradle, so a release's versionCode can be predicted from its tag. */
    public long versionCode() {
        return major * 10000L + minor * 100L + patch;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SemVer && compareTo((SemVer) o) == 0;
    }

    @Override
    public int hashCode() {
        return (int) versionCode();
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
