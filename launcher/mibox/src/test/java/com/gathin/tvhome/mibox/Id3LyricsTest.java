package com.gathin.tvhome.mibox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Lyrics inside an MP3's ID3v2 tag. */
public class Id3LyricsTest {
    private static byte[] synchsafe(int n) {
        return new byte[]{(byte) ((n >> 21) & 0x7F), (byte) ((n >> 14) & 0x7F), (byte) ((n >> 7) & 0x7F), (byte) (n & 0x7F)};
    }

    private static byte[] be32(int n) {
        return new byte[]{(byte) (n >> 24), (byte) (n >> 16), (byte) (n >> 8), (byte) n};
    }

    private static byte[] frame(String id, byte[] body, int version) {
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        f.writeBytes(id.getBytes(StandardCharsets.ISO_8859_1));
        f.writeBytes(version == 4 ? synchsafe(body.length) : be32(body.length));
        f.writeBytes(new byte[]{0, 0});
        f.writeBytes(body);
        return f.toByteArray();
    }

    private static byte[] tag(int version, byte[]... frames) {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (byte[] f : frames) all.writeBytes(f);
        all.writeBytes(new byte[64]);   // padding
        ByteArrayOutputStream t = new ByteArrayOutputStream();
        t.writeBytes(new byte[]{'I', 'D', '3', (byte) version, 0, 0});
        t.writeBytes(synchsafe(all.size()));
        t.writeBytes(all.toByteArray());
        t.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xFB, 0, 0});   // the audio that follows
        return t.toByteArray();
    }

    private static byte[] usltUtf16(String text) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(1);
        b.writeBytes("ara".getBytes(StandardCharsets.ISO_8859_1));
        b.writeBytes("".getBytes(StandardCharsets.UTF_16));
        b.writeBytes(new byte[]{0, 0});
        b.writeBytes(text.getBytes(StandardCharsets.UTF_16));
        return b.toByteArray();
    }

    private static byte[] usltUtf8(String text) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(3);
        b.writeBytes("fra".getBytes(StandardCharsets.ISO_8859_1));
        b.writeBytes("desc".getBytes(StandardCharsets.UTF_8));
        b.write(0);
        b.writeBytes(text.getBytes(StandardCharsets.UTF_8));
        b.write(0);
        return b.toByteArray();
    }

    @Test
    public void readsUtf16LyricsFromAVersion3Tag() throws Exception {
        String lrc = "[00:00.00]مرحبا بالعالم\n[00:00.00]Bonjour : « le monde »\n";
        byte[] title = {1, (byte) 0xFF, (byte) 0xFE, 'A', 0, 0, 0};
        byte[] mp3 = tag(3, frame("TIT2", title, 3), frame("USLT", usltUtf16(lrc), 3));
        assertEquals(lrc, Id3Lyrics.read(new ByteArrayInputStream(mp3)));
    }

    @Test
    public void readsUtf8LyricsFromAVersion4Tag() throws Exception {
        String lrc = "[00:01.00]Bonjour\n";
        byte[] mp3 = tag(4, frame("USLT", usltUtf8(lrc), 4));
        assertEquals(lrc, Id3Lyrics.read(new ByteArrayInputStream(mp3)));
    }

    @Test
    public void noTagOrNoLyricsGivesNothing() throws Exception {
        assertNull(Id3Lyrics.read(new ByteArrayInputStream(new byte[]{(byte) 0xFF, (byte) 0xFB, 0, 0, 0, 0, 0, 0, 0, 0})));
        byte[] title = {3, 'A', 0};
        assertNull(Id3Lyrics.read(new ByteArrayInputStream(tag(3, frame("TIT2", title, 3)))));
    }

    @Test
    public void aTruncatedFileIsNotAnError() throws Exception {
        byte[] mp3 = tag(3, frame("USLT", usltUtf16("[00:00.00]x"), 3));
        byte[] cut = java.util.Arrays.copyOf(mp3, 30);
        assertNull(Id3Lyrics.read(new ByteArrayInputStream(cut)));
    }
}
