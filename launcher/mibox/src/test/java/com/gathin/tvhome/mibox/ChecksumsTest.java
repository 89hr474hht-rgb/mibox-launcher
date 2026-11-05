package com.gathin.tvhome.mibox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class ChecksumsTest {
    private static final String HEX = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    public void readsSha256sumOutput() {
        assertEquals(HEX, Checksums.parseShaFile(HEX + "  mibox-launcher-1.0.0.apk\n"));
        assertEquals(HEX, Checksums.parseShaFile(HEX.toUpperCase() + "\n"));
        assertEquals(HEX, Checksums.parseShaFile("\n  " + HEX));
    }

    @Test
    public void refusesWhatIsNotASha256() {
        assertNull(Checksums.parseShaFile(null));
        assertNull(Checksums.parseShaFile(""));
        assertNull(Checksums.parseShaFile(HEX.substring(1)));
        assertNull(Checksums.parseShaFile(HEX + "0"));
        assertNull(Checksums.parseShaFile("<html>" + HEX));
        assertFalse(Checksums.isSha256(HEX.replace('e', 'g')));
    }

    @Test
    public void readsGitHubDigests() {
        assertEquals(HEX, Checksums.fromGitHubDigest("sha256:" + HEX));
        assertNull(Checksums.fromGitHubDigest("sha512:" + HEX));
        assertNull(Checksums.fromGitHubDigest(null));
    }

    @Test
    public void hexOfTheEmptyInputIsTheKnownDigest() {
        assertEquals(HEX, Checksums.hex(ReleaseClient.sha256().digest("".getBytes(StandardCharsets.UTF_8))));
        assertTrue(Checksums.isSha256(HEX));
    }
}
