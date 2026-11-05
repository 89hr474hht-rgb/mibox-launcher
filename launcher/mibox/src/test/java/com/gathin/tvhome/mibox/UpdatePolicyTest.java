package com.gathin.tvhome.mibox;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdatePolicyTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final SemVer V1 = SemVer.parse("1.0.0");
    private static final SemVer V2 = SemVer.parse("1.1.0");

    @Test
    public void promptsForANewerVersion() {
        assertTrue(UpdatePolicy.shouldPrompt(V2, V1, null, null, 0, NOW));
        assertFalse(UpdatePolicy.shouldPrompt(V1, V1, null, null, 0, NOW));
        assertFalse(UpdatePolicy.shouldPrompt(V1, V2, null, null, 0, NOW));
        assertFalse(UpdatePolicy.shouldPrompt(null, V1, null, null, 0, NOW));
    }

    @Test
    public void anIgnoredVersionStaysQuiet() {
        assertFalse(UpdatePolicy.shouldPrompt(V2, V1, "1.1.0", null, 0, NOW));
        assertTrue(UpdatePolicy.shouldPrompt(SemVer.parse("1.2.0"), V1, "1.1.0", null, 0, NOW));
    }

    @Test
    public void laterLastsADay() {
        long until = NOW + UpdatePolicy.SNOOZE_MS;
        assertFalse(UpdatePolicy.shouldPrompt(V2, V1, null, "1.1.0", until, NOW));
        assertTrue(UpdatePolicy.shouldPrompt(V2, V1, null, "1.1.0", until, until));
        // A newer release is offered even while the previous one is snoozed.
        assertTrue(UpdatePolicy.shouldPrompt(SemVer.parse("1.2.0"), V1, null, "1.1.0", until, NOW));
        // A clock set back must not hide the prompt for longer than a day.
        assertTrue(UpdatePolicy.shouldPrompt(V2, V1, null, "1.1.0", NOW + 10 * UpdatePolicy.DAY_MS, NOW));
    }

    @Test
    public void startCheckIsRateLimited() {
        assertTrue(UpdatePolicy.checkAtStart(0, NOW));
        assertFalse(UpdatePolicy.checkAtStart(NOW - 60_000, NOW));
        assertTrue(UpdatePolicy.checkAtStart(NOW - UpdatePolicy.START_CHECK_MIN_MS, NOW));
        assertTrue(UpdatePolicy.checkAtStart(NOW + 60_000, NOW));
    }
}
