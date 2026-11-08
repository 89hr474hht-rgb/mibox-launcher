package com.gathin.launchos.tv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The box filter behind the blurred posters, and the genre filters' names. */
public class BlurTest {
    @Test
    public void aFlatPictureStaysTheSame() {
        int[] in = new int[5 * 3];
        java.util.Arrays.fill(in, 0xFF336699);
        int[] out = new int[in.length];
        Blur.boxBlur(in, out, 5, 3, 2, true);
        for (int c : out) assertEquals(0xFF336699, c);
    }

    @Test
    public void aBrightPixelSpreadsToItsNeighbours() {
        int[] in = new int[5];
        java.util.Arrays.fill(in, 0xFF000000);
        in[2] = 0xFFFFFFFF;
        int[] out = new int[5];
        Blur.boxBlur(in, out, 5, 1, 1, true);
        assertEquals(0xFF555555, out[1]);
        assertEquals(0xFF555555, out[2]);
        assertEquals(0xFF555555, out[3]);
        assertEquals(0xFF000000, out[0]);
    }

    @Test
    public void verticalPassesWorkOnColumns() {
        int[] in = {0xFF000000, 0xFFFFFFFF, 0xFF000000};
        int[] out = new int[3];
        Blur.boxBlur(in, out, 1, 3, 1, false);
        assertEquals(0xFF555555, out[1]);
    }

    @Test
    public void theFirstGenreIsTrimmedAndCapitalised() {
        assertEquals("Action", Smart.firstGenre("action, Aventure"));
        assertEquals("Drame", Smart.firstGenre(" Drame / Romance"));
        assertEquals("", Smart.firstGenre(""));
        assertEquals("", Smart.firstGenre(null));
    }
}
