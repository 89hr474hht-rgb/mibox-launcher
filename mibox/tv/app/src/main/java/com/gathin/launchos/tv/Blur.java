package com.gathin.launchos.tv;

import android.graphics.Bitmap;
import android.widget.ImageView;

/**
 * A blurred copy of a picture, for the background of a title's page. The Mi Box (Android 11) has no live blur
 * (RenderEffect needs Android 12), so the picture is shrunk to a few dozen pixels, blurred three times with a box
 * filter (close to a gaussian) off the main thread, and stretched back by the ImageView: soft, and nearly free.
 */
final class Blur {
    private static final int SMALL_WIDTH = 48;
    private static final int RADIUS = 3;

    private Blur() {
    }

    /** Loads url, blurs it and fades it into view (at alpha); nothing happens when there is no picture. */
    static void into(ImageView view, String url, float alpha) {
        if (url == null || url.isEmpty()) return;
        view.setTag(R.id.image_url, url);
        Images.get(view.getContext(), url, SMALL_WIDTH * 4, bitmap -> {
            if (bitmap == null || !url.equals(view.getTag(R.id.image_url))) return;
            Bg.run(() -> blur(bitmap), (blurred, error) -> {
                if (blurred == null || !url.equals(view.getTag(R.id.image_url))) return;
                view.setImageBitmap(blurred);
                view.setAlpha(0f);
                view.animate().alpha(alpha).setDuration(500).start();
            });
        });
    }

    /** Background thread. */
    static Bitmap blur(Bitmap src) {
        int w = SMALL_WIDTH;
        int h = Math.max(1, Math.round(src.getHeight() * (w / (float) src.getWidth())));
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        int[] tmp = new int[px.length];
        for (int pass = 0; pass < 3; pass++) {
            boxBlur(px, tmp, w, h, RADIUS, true);
            boxBlur(tmp, px, w, h, RADIUS, false);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }

    /** One box-filter pass along rows (or columns), edges clamped. Plain arithmetic, tested on the JVM. */
    static void boxBlur(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h, div = 2 * r + 1;
        for (int line = 0; line < lines; line++) {
            int a = 0, red = 0, g = 0, b = 0;
            for (int k = -r; k <= r; k++) {
                int c = in[index(line, clamp(k, len), w, horizontal)];
                a += c >>> 24;
                red += (c >> 16) & 0xFF;
                g += (c >> 8) & 0xFF;
                b += c & 0xFF;
            }
            for (int i = 0; i < len; i++) {
                out[index(line, i, w, horizontal)] = ((a / div) << 24) | ((red / div) << 16) | ((g / div) << 8) | (b / div);
                int add = in[index(line, clamp(i + r + 1, len), w, horizontal)];
                int drop = in[index(line, clamp(i - r, len), w, horizontal)];
                a += (add >>> 24) - (drop >>> 24);
                red += ((add >> 16) & 0xFF) - ((drop >> 16) & 0xFF);
                g += ((add >> 8) & 0xFF) - ((drop >> 8) & 0xFF);
                b += (add & 0xFF) - (drop & 0xFF);
            }
        }
    }

    private static int clamp(int i, int len) {
        return i < 0 ? 0 : i >= len ? len - 1 : i;
    }

    private static int index(int line, int i, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }
}
