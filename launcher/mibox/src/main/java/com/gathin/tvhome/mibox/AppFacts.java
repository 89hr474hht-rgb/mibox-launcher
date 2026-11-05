package com.gathin.tvhome.mibox;

import java.util.Locale;

/** Plain wording for App info, apart from Android so it is tested on the JVM. */
final class AppFacts {
    static final int SOURCE_PLAY = 1, SOURCE_PREINSTALLED = 2, SOURCE_MANUAL = 3, SOURCE_OTHER = 4;

    private AppFacts() {
    }

    /** "Android 11 (API 30)" for an SDK level; the bare level for one this list does not know yet. */
    static String android(int sdk) {
        String name;
        switch (sdk) {
            case 21: name = "5.0"; break;
            case 22: name = "5.1"; break;
            case 23: name = "6"; break;
            case 24: name = "7.0"; break;
            case 25: name = "7.1"; break;
            case 26: name = "8.0"; break;
            case 27: name = "8.1"; break;
            case 28: name = "9"; break;
            case 29: name = "10"; break;
            case 30: name = "11"; break;
            case 31: name = "12"; break;
            case 32: name = "12L"; break;
            case 33: name = "13"; break;
            case 34: name = "14"; break;
            case 35: name = "15"; break;
            case 36: name = "16"; break;
            default: return "API " + sdk;
        }
        return "Android " + name + " (API " + sdk + ")";
    }

    /** "48,2 Mo" in French, "48.2 MB" in English: one decimal under 100, none above. */
    static String size(long bytes, Locale locale) {
        boolean fr = "fr".equals(locale.getLanguage());
        String[] units = fr ? new String[]{"o", "Ko", "Mo", "Go"} : new String[]{"B", "KB", "MB", "GB"};
        double v = bytes;
        int u = 0;
        while (v >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        String n = u == 0 || v >= 100 ? String.format(locale, "%.0f", v) : String.format(locale, "%.1f", v);
        return n + " " + units[u];
    }

    /** Where the app came from: Google Play, the TV's own system, an install from a computer, or another store. */
    static int source(String installer, boolean system, boolean updatedSystem) {
        if ("com.android.vending".equals(installer)) return SOURCE_PLAY;
        if (system && (installer == null || !updatedSystem)) return SOURCE_PREINSTALLED;
        if (installer == null || installer.isEmpty() || "com.android.shell".equals(installer)
                || "com.google.android.packageinstaller".equals(installer)
                || "com.android.packageinstaller".equals(installer)) return SOURCE_MANUAL;
        return SOURCE_OTHER;
    }

    /** "1.4.2 (1402)", or just the code when an app has no version name. */
    static String version(String name, long code) {
        return name == null || name.isEmpty() ? String.valueOf(code) : name + " (" + code + ")";
    }
}
