package com.gathin.tvhome.mibox;

/** Colour arithmetic for the living glow, in plain Java so it is tested on the JVM. */
final class GlowColors {
    private GlowColors() {
    }

    /**
     * The colour turned around the colour wheel by degrees and its brightness scaled by value (clamped), as 0xRRGGBB.
     * A grey stays grey: with no hue to turn, only its brightness changes.
     */
    static int shift(int rgb, float degrees, float value) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), delta = max - min;
        float h;
        if (delta == 0) h = 0;
        else if (max == r) h = 60 * (((g - b) / delta) % 6);
        else if (max == g) h = 60 * ((b - r) / delta + 2);
        else h = 60 * ((r - g) / delta + 4);
        float s = max == 0 ? 0 : delta / max;
        float v = Math.min(1f, max * value);
        h = ((h + degrees) % 360 + 360) % 360;
        float c = v * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = v - c;
        float rr, gg, bb;
        if (h < 60) { rr = c; gg = x; bb = 0; }
        else if (h < 120) { rr = x; gg = c; bb = 0; }
        else if (h < 180) { rr = 0; gg = c; bb = x; }
        else if (h < 240) { rr = 0; gg = x; bb = c; }
        else if (h < 300) { rr = x; gg = 0; bb = c; }
        else { rr = c; gg = 0; bb = x; }
        return Math.round((rr + m) * 255) << 16 | Math.round((gg + m) * 255) << 8 | Math.round((bb + m) * 255);
    }
}
