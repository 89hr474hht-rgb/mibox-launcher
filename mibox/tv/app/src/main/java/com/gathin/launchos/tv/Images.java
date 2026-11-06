package com.gathin.launchos.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Logos and posters: fetched once, scaled down to the size they are shown at, kept in memory (an eighth of the
 * app's heap) and on disk in the app's cache, which Android may clear when space runs low.
 */
final class Images {
    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> MEMORY =
            new LruCache<String, Bitmap>((int) Math.min(Runtime.getRuntime().maxMemory() / 8, 48L * 1024 * 1024)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private Images() {
    }

    /** Shows the image at url in view, sized for widthPx; clears the view when url is empty or fails. */
    static void load(ImageView view, String url, int widthPx) {
        view.setTag(R.id.image_url, url);
        if (url == null || url.isEmpty() || !url.matches("(?i)^https?://.*")) {
            view.setImageDrawable(null);
            return;
        }
        String key = url + "@" + widthPx;
        Bitmap hit = MEMORY.get(key);
        if (hit != null) {
            view.setImageBitmap(hit);
            return;
        }
        view.setImageDrawable(null);
        Context app = view.getContext().getApplicationContext();
        POOL.execute(() -> {
            Bitmap b = fetch(app, url, widthPx);
            if (b != null) MEMORY.put(key, b);
            Bg.MAIN.post(() -> {
                if (url.equals(view.getTag(R.id.image_url)) && b != null) {
                    view.setImageBitmap(b);
                    view.setAlpha(0f);
                    view.animate().alpha(1f).setDuration(180).start();
                }
            });
        });
    }

    static void clearMemory() {
        MEMORY.evictAll();
    }

    /** Background thread. */
    static long diskBytes(Context context) {
        long total = 0;
        File[] files = new File(context.getCacheDir(), "img").listFiles();
        if (files != null) for (File f : files) total += f.length();
        return total;
    }

    /** Background thread. */
    static void clearDisk(Context context) {
        File[] files = new File(context.getCacheDir(), "img").listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.delete()) f.deleteOnExit();
        }
    }

    private static Bitmap fetch(Context app, String url, int widthPx) {
        File dir = new File(app.getCacheDir(), "img");
        File file = new File(dir, sha1(url));
        byte[] data = null;
        if (file.exists()) {
            try {
                data = java.nio.file.Files.readAllBytes(file.toPath());
            } catch (java.io.IOException ignored) {
            }
        }
        if (data == null) {
            try {
                data = Http.get(url, MAX_IMAGE_BYTES);
            } catch (XtreamException e) {
                return null;
            }
            if (dir.isDirectory() || dir.mkdirs()) {
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(data);
                } catch (java.io.IOException ignored) {
                }
            }
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= widthPx) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        try {
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format(java.util.Locale.ROOT, "%02x", x));
            return b.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
