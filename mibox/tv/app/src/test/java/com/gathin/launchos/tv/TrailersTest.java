package com.gathin.launchos.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Only YouTube videos are opened as trailers, always as a plain https watch address. */
public class TrailersTest {
    private static final String WATCH = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @Test
    public void bareIdAndYouTubeAddresses() {
        assertEquals(WATCH, Trailers.youtube("dQw4w9WgXcQ"));
        assertEquals(WATCH, Trailers.youtube(" dQw4w9WgXcQ "));
        assertEquals(WATCH, Trailers.youtube("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertEquals(WATCH, Trailers.youtube("https://youtube.com/watch?feature=x&v=dQw4w9WgXcQ"));
        assertEquals(WATCH, Trailers.youtube("https://m.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertEquals(WATCH, Trailers.youtube("https://youtu.be/dQw4w9WgXcQ"));
        assertEquals(WATCH, Trailers.youtube("https://www.youtube.com/embed/dQw4w9WgXcQ"));
    }

    @Test
    public void anythingElseIsRefused() {
        assertNull(Trailers.youtube(null));
        assertNull(Trailers.youtube(""));
        assertNull(Trailers.youtube("http://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertNull(Trailers.youtube("https://evil.example/watch?v=dQw4w9WgXcQ"));
        assertNull(Trailers.youtube("https://www.youtube.com.evil.example/watch?v=dQw4w9WgXcQ"));
        assertNull(Trailers.youtube("https://user@www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertNull(Trailers.youtube("https://www.youtube.com:8443/watch?v=dQw4w9WgXcQ"));
        assertNull(Trailers.youtube("https://www.youtube.com/watch?v=short"));
        assertNull(Trailers.youtube("https://www.youtube.com/redirect?q=https://evil.example"));
        assertNull(Trailers.youtube("javascript:alert(1)"));
        assertNull(Trailers.youtube("intent://x#Intent;end"));
    }
}
