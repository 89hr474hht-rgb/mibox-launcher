package com.gathin.tvhome.mibox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * The lyrics stored inside an MP3's ID3v2 tag (the USLT frame), which many music tools fill with LRC text. Android
 * lets an app read audio files but not the text files beside them on a USB drive, so this is how a track brings its
 * synced lyrics along. Reads ID3v2.2, 2.3 and 2.4, only the tag at the start of the file, at most {@link #MAX_TAG}.
 * Plain Java, tested on the JVM.
 */
final class Id3Lyrics {
    static final int MAX_TAG = 4 * 1024 * 1024;

    private Id3Lyrics() {
    }

    /** The first USLT text in the stream's ID3v2 tag, or null. The stream is read, not closed. */
    static String read(InputStream in) throws IOException {
        byte[] head = readFully(in, 10);
        if (head == null || head[0] != 'I' || head[1] != 'D' || head[2] != '3') return null;
        int version = head[3];
        int flags = head[5] & 0xFF;
        int size = synchsafe(head, 6);
        if (version < 2 || version > 4 || size <= 0 || size > MAX_TAG) return null;
        byte[] tag = readFully(in, size);
        if (tag == null) return null;
        if ((flags & 0x80) != 0 && version < 4) tag = unsync(tag);   // whole-tag unsynchronisation (2.2/2.3)
        return find(tag, version, flags);
    }

    static String find(byte[] tag, int version, int flags) {
        int pos = 0;
        if ((flags & 0x40) != 0 && version >= 3) {   // extended header: skip it
            if (tag.length < 4) return null;
            int ext = version == 4 ? synchsafe(tag, 0) : be32(tag, 0) + 4;
            pos = Math.max(0, ext);
        }
        int idLen = version == 2 ? 3 : 4, headLen = version == 2 ? 6 : 10;
        while (pos + headLen <= tag.length) {
            if (tag[pos] == 0) break;   // padding
            String id = new String(tag, pos, idLen, StandardCharsets.ISO_8859_1);
            int size = version == 2 ? be24(tag, pos + 3) : version == 4 ? synchsafe(tag, pos + 4) : be32(tag, pos + 4);
            int frameFlags = version == 2 ? 0 : ((tag[pos + 8] & 0xFF) << 8) | (tag[pos + 9] & 0xFF);
            int start = pos + headLen;
            if (size <= 0 || start + size > tag.length) break;
            if (id.equals(version == 2 ? "ULT" : "USLT")) {
                byte[] body = new byte[size];
                System.arraycopy(tag, start, body, 0, size);
                // 2.4 per-frame unsynchronisation; compressed or encrypted frames are skipped.
                boolean skip = version == 4 && (frameFlags & 0x000C) != 0;
                if (version == 4 && (frameFlags & 0x0002) != 0) body = unsync(body);
                if (!skip) {
                    String text = uslt(body);
                    if (text != null && !text.trim().isEmpty()) return text;
                }
            }
            pos = start + size;
        }
        return null;
    }

    /** USLT body: encoding, language (3 bytes), a descriptor ended by a null, then the text. */
    static String uslt(byte[] b) {
        if (b.length < 5) return null;
        int enc = b[0];
        Charset cs = enc == 1 ? StandardCharsets.UTF_16 : enc == 2 ? StandardCharsets.UTF_16BE
                : enc == 3 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1;
        boolean wide = enc == 1 || enc == 2;
        int p = 4, end = terminator(b, p, wide);
        if (end < 0) return null;
        int textStart = end + (wide ? 2 : 1);
        if (textStart > b.length) return null;
        int textEnd = b.length;
        // A trailing null is not part of the text.
        if (wide) {
            if ((textEnd - textStart) % 2 == 1) textEnd--;
            while (textEnd - textStart >= 2 && b[textEnd - 1] == 0 && b[textEnd - 2] == 0) textEnd -= 2;
        } else {
            while (textEnd > textStart && b[textEnd - 1] == 0) textEnd--;
        }
        if (enc == 1 && textEnd - textStart >= 2) {
            // Each UTF-16 string in a v2.3 frame has its own byte order mark.
            return new String(b, textStart, textEnd - textStart, StandardCharsets.UTF_16);
        }
        return new String(b, textStart, textEnd - textStart, cs);
    }

    private static int terminator(byte[] b, int from, boolean wide) {
        if (!wide) {
            for (int i = from; i < b.length; i++) if (b[i] == 0) return i;
            return -1;
        }
        for (int i = from; i + 1 < b.length; i += 2) if (b[i] == 0 && b[i + 1] == 0) return i;
        return -1;
    }

    private static byte[] unsync(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        for (int i = 0; i < in.length; i++) {
            out.write(in[i]);
            if ((in[i] & 0xFF) == 0xFF && i + 1 < in.length && in[i + 1] == 0) i++;
        }
        return out.toByteArray();
    }

    static int synchsafe(byte[] b, int at) {
        return ((b[at] & 0x7F) << 21) | ((b[at + 1] & 0x7F) << 14) | ((b[at + 2] & 0x7F) << 7) | (b[at + 3] & 0x7F);
    }

    private static int be32(byte[] b, int at) {
        return ((b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }

    private static int be24(byte[] b, int at) {
        return ((b[at] & 0xFF) << 16) | ((b[at + 1] & 0xFF) << 8) | (b[at + 2] & 0xFF);
    }

    private static byte[] readFully(InputStream in, int n) throws IOException {
        byte[] b = new byte[n];
        int got = 0;
        while (got < n) {
            int r = in.read(b, got, n - got);
            if (r < 0) return null;
            got += r;
        }
        return b;
    }
}
