package com.gathin.tvhome.mibox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The picture of the home screen in Réglages uses the launcher's own tile sums (AppAdapter), copied since this
 * library cannot see them. Read from the launcher's source, they must still be the same, or the picture would lie.
 */
public class HomePreviewTest {
    private static String adapter() throws IOException {
        return new String(Files.readAllBytes(Paths.get("../app/src/main/java/com/gathin/tvhome/AppAdapter.java")),
                StandardCharsets.UTF_8);
    }

    /** The value of a float constant, summing "6 + 12" as the source writes it. */
    private static float constant(String src, String name) {
        Matcher m = Pattern.compile("\\b" + name + "\\s*=\\s*([0-9.+ f]+)[,;]").matcher(src);
        assertTrue(name + " not found in AppAdapter", m.find());
        float sum = 0;
        for (String part : m.group(1).split("\\+")) sum += Float.parseFloat(part.trim().replace("f", ""));
        return sum;
    }

    @Test
    public void theTileSumsAreTheLaunchers() throws IOException {
        String src = adapter();
        assertEquals(constant(src, "DESIGN_W_DP"), HomePreview.DESIGN_W, 0.001f);
        assertEquals(constant(src, "DESIGN_H_DP"), HomePreview.DESIGN_H, 0.001f);
        assertEquals(constant(src, "FOCUS_SCALE"), HomePreview.FOCUS_SCALE, 0.001f);
        assertEquals(constant(src, "MIN_HEIGHT_FIT"), HomePreview.MIN_HEIGHT_FIT, 0.001f);
        assertEquals(constant(src, "ITEM_EXTRA_DP"), HomePreview.ITEM_EXTRA, 0.001f);
        assertEquals(constant(src, "DOCK_EXTRA_DP"), HomePreview.DOCK_EXTRA, 0.001f);
        assertEquals(constant(src, "BOTTOM_ROOM_DP"), HomePreview.BOTTOM_ROOM, 0.001f);
        assertEquals(constant(src, "TITLE_ROOM_DP"), HomePreview.TITLE_ROOM, 0.001f);
    }
}
