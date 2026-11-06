package com.gathin.launchos.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

/** The streaming reader used for the panels' full lists. */
public class JsonStreamTest {
    private static JsonStream r(String s) {
        return new JsonStream(new StringReader(s));
    }

    @Test
    public void readsObjectsArraysAndScalars() throws IOException {
        JsonStream r = r(" { \"a\" : \"x\\u00e9\\n\", \"b\": 12.5, \"c\": null, \"d\": true, \"e\": [1, 2] } ");
        r.beginObject();
        assertEquals("a", r.nextName());
        assertEquals("xé\n", r.nextString());
        assertEquals("b", r.nextName());
        assertEquals("12.5", r.nextString());
        assertEquals("c", r.nextName());
        assertEquals("", r.nextString());
        assertEquals("d", r.nextName());
        assertEquals("true", r.nextString());
        assertEquals("e", r.nextName());
        assertEquals("1", r.nextFirstString());
        assertFalse(r.hasNext());
        r.endObject();
        assertEquals(JsonStream.Kind.END, r.peek());
    }

    @Test
    public void skipsNestedValues() throws IOException {
        JsonStream r = r("[{\"x\":[[1,{\"y\":\"]}\"}],{}]},\"after\"]");
        r.beginArray();
        assertTrue(r.hasNext());
        r.skipValue();
        assertTrue(r.hasNext());
        assertEquals("after", r.nextString());
        assertFalse(r.hasNext());
        r.endArray();
    }

    @Test
    public void objectOrArrayReadAsStringIsEmpty() throws IOException {
        JsonStream r = r("[{\"a\":1},[2],\"s\"]");
        r.beginArray();
        assertEquals("", r.nextString());
        assertEquals("", r.nextString());
        assertEquals("s", r.nextString());
        r.endArray();
    }

    @Test
    public void emptyContainers() throws IOException {
        JsonStream r = r("[[],{}]");
        r.beginArray();
        r.beginArray();
        assertFalse(r.hasNext());
        r.endArray();
        r.beginObject();
        assertFalse(r.hasNext());
        r.endObject();
        assertFalse(r.hasNext());
        r.endArray();
    }

    @Test
    public void truncatedInputFails() {
        try {
            JsonStream r = r("[{\"a\":\"open");
            r.beginArray();
            r.beginObject();
            r.nextName();
            r.nextString();
            fail();
        } catch (IOException expected) {
            // a cut-off download is an error, not a short list
        }
    }
}
