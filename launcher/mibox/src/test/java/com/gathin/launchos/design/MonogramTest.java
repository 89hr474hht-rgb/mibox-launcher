package com.gathin.launchos.design;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The "lo" mark exists as Java (the start-up screens draw it being written) and as vectors (icons, banners, logos of
 * both apps). They must stay one shape: every vector carries the line Monogram draws. Runs from launcher/mibox.
 */
public class MonogramTest {
    private static final Path REPO = Paths.get("../..");

    private static String f(float v) {
        return v == (int) v ? String.valueOf((int) v) : String.valueOf(v);
    }

    /** The path Monogram builds, written the way the vectors write it. */
    static String line() {
        float l = Monogram.LEFT, b = Monogram.BASE, e = Monogram.ELBOW, cx = Monogram.CX, r = Monogram.R;
        return "M" + f(l) + "," + f(Monogram.TOP) + " V" + f(b - e) + " Q" + f(l) + "," + f(b) + " " + f(l + e) + ","
                + f(b) + " H" + f(cx) + " A" + f(r) + "," + f(r) + " 0 1,0 " + f(cx) + "," + f(Monogram.CY - r) + " A"
                + f(r) + "," + f(r) + " 0 1,0 " + f(cx) + "," + f(b);
    }

    @Test
    public void everyVectorOfTheMarkIsTheSameLine() throws IOException {
        String[] files = {
                "mibox/design/res/drawable/lo_mark.xml",
                "launcher/mibox/src/main/res/drawable/mibox_icon_fg.xml",
                "launcher/mibox/src/main/res/drawable/mibox_logo.xml",
                "launcher/mibox/app-res/drawable-nodpi/banner.xml",
                "mibox/tv/app/src/main/res/drawable/ic_launcher_fg.xml",
                "mibox/tv/app/src/main/res/drawable/logo_mark.xml",
                "mibox/tv/app/src/main/res/drawable/banner.xml",
        };
        String line = line();
        for (String name : files) {
            String s = new String(Files.readAllBytes(REPO.resolve(name)), StandardCharsets.UTF_8);
            assertTrue(name + " does not draw the mark's line " + line, s.contains(line));
        }
    }
}
