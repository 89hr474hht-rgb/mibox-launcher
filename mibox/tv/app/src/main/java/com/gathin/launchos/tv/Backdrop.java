package com.gathin.launchos.tv;

import android.graphics.Bitmap;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * The picture behind a page, crossfading from one title to the next. A wide picture fills the screen; a poster
 * (taller than wide) sits on the right two thirds, the scrims fading it into the page.
 */
final class Backdrop {
    private static final long FADE_MS = 650;

    private final ImageView a, b;
    private boolean aOnTop;
    private String shown;
    private int request;

    Backdrop(ImageView a, ImageView b) {
        this.a = a;
        this.b = b;
    }

    /** Shows the first picture that loads among urls (a wide one first, then the poster). */
    void show(String... urls) {
        String url = null;
        for (String u : urls) {
            if (u != null && !u.isEmpty()) {
                url = u;
                break;
            }
        }
        if (url == null) {
            clear();
            return;
        }
        if (url.equals(shown)) return;
        int r = ++request;
        String first = url;
        int width = a.getResources().getDisplayMetrics().widthPixels;
        Images.get(a.getContext(), first, width / 2, bitmap -> {
            if (r != request) return;
            if (bitmap == null) {
                // The wide picture failed: try the next one.
                String[] rest = rest(urls, first);
                if (rest.length > 0) show(rest);
                else clear();
                return;
            }
            put(first, bitmap);
        });
    }

    private static String[] rest(String[] urls, String failed) {
        java.util.List<String> l = new java.util.ArrayList<>();
        boolean after = false;
        for (String u : urls) {
            if (after && u != null && !u.isEmpty() && !u.equals(failed)) l.add(u);
            if (failed.equals(u)) after = true;
        }
        return l.toArray(new String[0]);
    }

    void clear() {
        request++;
        shown = null;
        a.animate().alpha(0f).setDuration(FADE_MS).start();
        b.animate().alpha(0f).setDuration(FADE_MS).start();
    }

    private void put(String url, Bitmap bitmap) {
        shown = url;
        ImageView in = aOnTop ? b : a, out = aOnTop ? a : b;
        aOnTop = !aOnTop;
        android.view.ViewGroup parent = (android.view.ViewGroup) in.getParent();
        // The two pictures stay at the bottom of the page, the new one above the old; the scrims stay above both.
        in.animate().cancel();
        out.animate().cancel();
        parent.removeView(in);
        parent.addView(in, 1);
        boolean wide = bitmap.getWidth() >= bitmap.getHeight();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) in.getLayoutParams();
        // Before the first layout the page has no width yet: the screen's is the same.
        int pageWidth = parent.getWidth() > 0 ? parent.getWidth() : parent.getResources().getDisplayMetrics().widthPixels;
        lp.width = wide ? FrameLayout.LayoutParams.MATCH_PARENT : Math.round(pageWidth * 0.62f);
        in.setLayoutParams(lp);
        in.setImageBitmap(wide ? bitmap : fadeLeft(bitmap));
        in.setAlpha(0f);
        in.setScaleX(1.04f);
        in.setScaleY(1.04f);
        in.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(FADE_MS).start();
        out.animate().alpha(0f).setDuration(FADE_MS).start();
    }

    /** A poster's copy whose left part fades out, so it melts into the page instead of ending on an edge. */
    private static Bitmap fadeLeft(Bitmap src) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        c.drawBitmap(src, 0, 0, null);
        android.graphics.Paint mask = new android.graphics.Paint();
        mask.setShader(new android.graphics.LinearGradient(0, 0, src.getWidth() * 0.55f, 0,
                new int[]{0x00000000, 0x80000000, 0xFF000000}, new float[]{0f, 0.45f, 1f},
                android.graphics.Shader.TileMode.CLAMP));
        mask.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        c.drawRect(0, 0, src.getWidth(), src.getHeight(), mask);
        return out;
    }
}
