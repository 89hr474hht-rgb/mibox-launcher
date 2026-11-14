package com.gathin.tvhome.mibox;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Keeps this library's hooks into the launcher honest. The library finds the launcher's views and styles by name and
 * replaces one of its layouts; an upstream merge that renames or adds something there would otherwise only show as a
 * feature quietly gone, or a crash in the Mi Box build alone. Runs from the module folder (launcher/mibox).
 */
public class UpstreamHooksTest {
    private static final Path APP_RES = Paths.get("../app/src/main/res");
    private static final Path APP_JAVA = Paths.get("../app/src/main/java/com/gathin/tvhome");
    private static final Path FORK_RES = Paths.get("app-res");
    private static final Path OWN_RES = Paths.get("src/main/res");
    private static final Path OWN_JAVA = Paths.get("src/main/java/com/gathin/tvhome/mibox");

    private static final Pattern ID = Pattern.compile("@\\+id/(\\w+)");
    private static final Pattern STYLE = Pattern.compile("<style\\s+name=\"([\\w.]+)\"");
    private static final Pattern LOOKUP = Pattern.compile("UpstreamViews\\.(find|id|style)\\(\\s*\\w+,\\s*\"(\\w+)\"");
    private static final Pattern FORK_ID_LITERAL = Pattern.compile("\"(mibox[A-Z]\\w+)\"");

    @Test
    public void theSettingsCopyHasEveryIdOfTheLaunchersOwn() throws IOException {
        Set<String> upstream = ids(APP_RES.resolve("layout/activity_launcher_settings.xml"));
        Set<String> copy = ids(FORK_RES.resolve("layout/activity_launcher_settings.xml"));
        List<String> lost = new ArrayList<>();
        for (String id : upstream) if (!copy.contains(id)) lost.add(id);
        assertTrue("The Mi Box copy of activity_launcher_settings.xml lacks upstream ids " + lost
                + ": copy them in from launcher/app", lost.isEmpty());
    }

    @Test
    public void everyNameLookedUpExists() throws IOException {
        Set<String> ids = new HashSet<>(), styles = new HashSet<>();
        for (Path res : new Path[]{APP_RES, FORK_RES, OWN_RES}) {
            for (Path f : xml(res)) {
                String s = read(f);
                collect(ID, s, ids);
                collect(STYLE, s, styles);
            }
        }
        List<String> missing = new ArrayList<>();
        for (Path f : java(OWN_JAVA)) {
            String s = read(f);
            Matcher m = LOOKUP.matcher(s);
            while (m.find()) {
                boolean style = "style".equals(m.group(1));
                if (!(style ? styles : ids).contains(m.group(2))) missing.add(f.getFileName() + ": " + m.group(2));
            }
            Matcher fork = FORK_ID_LITERAL.matcher(s);
            while (fork.find()) {
                String name = fork.group(1);
                if (!ids.contains(name) && !styles.contains(name)) missing.add(f.getFileName() + ": " + name);
            }
        }
        assertTrue("Names looked up by this library but not in the launcher's resources: " + missing, missing.isEmpty());
    }

    @Test
    public void theLauncherClassesAndSettingsNamedInStringsExist() throws IOException {
        assertTrue(Files.exists(APP_JAVA.resolve("MainActivity.java")));
        assertTrue(Files.exists(APP_JAVA.resolve("LauncherSettingsActivity.java")));
        String init = read(OWN_JAVA.resolve("MiboxInit.java"));
        assertTrue(init.contains("\"com.gathin.tvhome.MainActivity\""));
        assertTrue(init.contains("\"com.gathin.tvhome.LauncherSettingsActivity\""));
        // The sleep screen follows the launcher's clock format, read from its preferences.
        StringBuilder app = new StringBuilder();
        for (Path f : java(APP_JAVA)) app.append(read(f));
        assertTrue("launcher_settings preferences", app.indexOf("\"launcher_settings\"") >= 0);
        assertTrue("clock_format key", app.indexOf("\"clock_format\"") >= 0);
    }

    private static Set<String> ids(Path file) throws IOException {
        Set<String> out = new HashSet<>();
        collect(ID, read(file), out);
        return out;
    }

    private static void collect(Pattern p, String s, Set<String> out) {
        Matcher m = p.matcher(s);
        while (m.find()) out.add(m.group(1));
    }

    private static String read(Path f) throws IOException {
        return new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
    }

    private static List<Path> xml(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            List<Path> out = new ArrayList<>();
            s.filter(p -> p.toString().endsWith(".xml")).forEach(out::add);
            return out;
        }
    }

    private static List<Path> java(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            List<Path> out = new ArrayList<>();
            s.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
            return out;
        }
    }
}
