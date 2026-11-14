package com.gathin.launchos.tv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Which redirects Http follows: never from https down to http, never to another scheme. */
public class HttpTest {
    @Test
    public void plainAndUpgradeRedirectsAreFollowed() {
        assertTrue(Http.redirectAllowed("http://a.example:8080/x", "http://b.example/y"));
        assertTrue(Http.redirectAllowed("http://a.example/x", "https://b.example/y"));
        assertTrue(Http.redirectAllowed("https://a.example/x", "https://cdn.example/y"));
        assertTrue(Http.redirectAllowed("HTTP://a.example/x", "HTTPS://b.example/y"));
    }

    @Test
    public void downgradeIsRefused() {
        assertFalse(Http.redirectAllowed("https://a.example/x", "http://a.example/x"));
        assertFalse(Http.redirectAllowed("HTTPS://a.example/x", "Http://b.example/y"));
    }

    @Test
    public void otherSchemesAreRefused() {
        assertFalse(Http.redirectAllowed("http://a.example/x", "file:///data/data"));
        assertFalse(Http.redirectAllowed("http://a.example/x", "ftp://b.example/y"));
        assertFalse(Http.redirectAllowed("http://a.example/x", "nothing"));
        assertFalse(Http.redirectAllowed("http://a.example/x", null));
    }
}
