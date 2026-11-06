package com.gathin.launchos.tv;

/** When to check and when to show the prompt; plain Java so the unit tests cover it. */
final class UpdatePolicy {
    static final long DAY_MS = 24L * 60 * 60 * 1000;
    /** A check at process start is skipped when one ran this recently (a crash loop must not spend the API quota). */
    static final long START_CHECK_MIN_MS = 60L * 60 * 1000;
    /** "Later" hides the prompt for this long, until after the next daily check. */
    static final long SNOOZE_MS = DAY_MS;

    private UpdatePolicy() {
    }

    static boolean checkAtStart(long lastCheck, long now) {
        return lastCheck <= 0 || now - lastCheck >= START_CHECK_MIN_MS || now < lastCheck;
    }

    /** True when the home screen should offer this release now. */
    static boolean shouldPrompt(SemVer available, SemVer installed, String ignored,
                                String snoozedVersion, long snoozedUntil, long now) {
        if (available == null || !available.isNewerThan(installed)) return false;
        String v = available.toString();
        if (v.equals(ignored)) return false;
        return !(v.equals(snoozedVersion) && now < snoozedUntil && snoozedUntil - now <= SNOOZE_MS);
    }
}
