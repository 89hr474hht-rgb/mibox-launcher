package com.gathin.launchos.tv;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;

/**
 * A small pull reader for JSON, so a panel's whole film list (tens of MB on a big panel) is read item by item
 * instead of being held as text and as a tree. It reads what panels send; anything else is {@link Malformed}.
 * Scalars are read as text: a number, true/false or a string all come back as a String, null as "".
 */
final class JsonStream implements Closeable {
    enum Kind { ARRAY, OBJECT, STRING, LITERAL, END }

    static final class Malformed extends IOException {
        Malformed(String what) {
            super(what);
        }
    }

    private final Reader in;
    private final char[] buf = new char[16 * 1024];
    private int pos, limit;
    /** One flag per open array or object (and the document): whether a comma must come before the next value. */
    private boolean[] comma = new boolean[32];
    private int depth;

    JsonStream(Reader in) {
        this.in = in;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    // ---- structure ----

    Kind peek() throws IOException {
        int c = separated();
        switch (c) {
            case -1:
                return Kind.END;
            case '[':
                return Kind.ARRAY;
            case '{':
                return Kind.OBJECT;
            case '"':
                return Kind.STRING;
            default:
                return Kind.LITERAL;
        }
    }

    void beginArray() throws IOException {
        separated();
        expect('[');
        push();
    }

    void endArray() throws IOException {
        expect(']');
        pop();
    }

    void beginObject() throws IOException {
        separated();
        expect('{');
        push();
    }

    void endObject() throws IOException {
        expect('}');
        pop();
    }

    /** True when the array or object open now has another value (or name), after eating its comma. */
    boolean hasNext() throws IOException {
        int c = peekChar();
        if (c == ']' || c == '}' || c == -1) return false;
        if (comma[depth]) {
            if (c != ',') throw new Malformed("comma");
            pos++;
            comma[depth] = false;
            c = peekChar();
            if (c == ']' || c == '}') throw new Malformed("trailing comma");
        }
        return true;
    }

    String nextName() throws IOException {
        if (separated() != '"') throw new Malformed("name");
        pos++;
        String name = readString();
        expect(':');
        return name;
    }

    // ---- values ----

    /** Any scalar as text; "" for null; an array or object is skipped and gives "". */
    String nextString() throws IOException {
        int c = separated();
        String s;
        if (c == '"') {
            pos++;
            s = readString();
        } else if (c == '[' || c == '{') {
            skipValue();
            return "";
        } else {
            s = readLiteral();
            if (s.equals("null")) s = "";
        }
        valueDone();
        return s;
    }

    /** The first string of an array (["a","b"] gives "a"), or the value itself when it is a scalar. */
    String nextFirstString() throws IOException {
        if (peek() != Kind.ARRAY) return nextString();
        beginArray();
        String first = "";
        boolean seen = false;
        while (hasNext()) {
            if (!seen && peek() != Kind.ARRAY && peek() != Kind.OBJECT) {
                first = nextString();
                seen = true;
            } else {
                skipValue();
            }
        }
        endArray();
        return first;
    }

    void skipValue() throws IOException {
        int c = separated();
        if (c == '[' || c == '{') {
            boolean array = c == '[';
            if (array) beginArray();
            else beginObject();
            while (hasNext()) {
                if (!array) nextName();
                skipValue();
            }
            if (array) endArray();
            else endObject();
        } else if (c == '"') {
            pos++;
            skipString();
            valueDone();
        } else if (c == -1) {
            throw new Malformed("end");
        } else {
            readLiteral();
            valueDone();
        }
    }

    // ---- characters ----

    /** The next character, after the comma owed before this value or name (when hasNext was not asked). */
    private int separated() throws IOException {
        int c = peekChar();
        if (comma[depth] && c == ',') {
            pos++;
            comma[depth] = false;
            c = peekChar();
        }
        return c;
    }

    private void push() {
        if (++depth == comma.length) comma = java.util.Arrays.copyOf(comma, depth * 2);
        comma[depth] = false;
    }

    private void pop() throws Malformed {
        if (depth == 0) throw new Malformed("close");
        depth--;
        valueDone();
    }

    private void valueDone() {
        comma[depth] = true;
    }

    private void expect(char c) throws IOException {
        if (peekChar() != c) throw new Malformed("expected " + c);
        pos++;
    }

    private boolean fill() throws IOException {
        int n = in.read(buf, 0, buf.length);
        if (n <= 0) return false;
        pos = 0;
        limit = n;
        return true;
    }

    /** The next character that is not white space, without eating it; -1 at the end. */
    private int peekChar() throws IOException {
        while (true) {
            if (pos >= limit && !fill()) return -1;
            char c = buf[pos];
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\uFEFF') pos++;
            else return c;
        }
    }

    private int read() throws IOException {
        if (pos >= limit && !fill()) return -1;
        return buf[pos++];
    }

    private String readString() throws IOException {
        StringBuilder b = new StringBuilder();
        while (true) {
            int c = read();
            if (c == -1) throw new Malformed("string");
            if (c == '"') return b.toString();
            if (c != '\\') {
                b.append((char) c);
                continue;
            }
            int e = read();
            switch (e) {
                case 'n':
                    b.append('\n');
                    break;
                case 't':
                    b.append('\t');
                    break;
                case 'r':
                    b.append('\r');
                    break;
                case 'b':
                    b.append('\b');
                    break;
                case 'f':
                    b.append('\f');
                    break;
                case 'u':
                    int v = 0;
                    for (int i = 0; i < 4; i++) {
                        int h = Character.digit(read(), 16);
                        if (h < 0) throw new Malformed("unicode escape");
                        v = v * 16 + h;
                    }
                    b.append((char) v);
                    break;
                case -1:
                    throw new Malformed("escape");
                default:
                    b.append((char) e);   // \" \\ \/ and anything a lax panel escapes
            }
        }
    }

    private void skipString() throws IOException {
        while (true) {
            int c = read();
            if (c == -1) throw new Malformed("string");
            if (c == '"') return;
            if (c == '\\' && read() == -1) throw new Malformed("escape");
        }
    }

    private String readLiteral() throws IOException {
        StringBuilder b = new StringBuilder();
        while (true) {
            if (pos >= limit && !fill()) break;
            char c = buf[pos];
            if (c == ',' || c == ':' || c == ']' || c == '}' || c == ' ' || c == '\n' || c == '\r' || c == '\t') break;
            b.append(c);
            pos++;
        }
        if (b.length() == 0) throw new Malformed("value");
        return b.toString();
    }
}
