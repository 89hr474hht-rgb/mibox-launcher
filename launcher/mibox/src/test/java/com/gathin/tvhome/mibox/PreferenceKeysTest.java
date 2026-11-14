package com.gathin.tvhome.mibox;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Every preference file and key the launcher and this library read or write, against a list kept in
 * src/test/resources/preference-keys.txt. A screen can be reorganised freely; a key that disappears or is renamed
 * would lose a saved setting on the box, so it has to be changed here on purpose. Runs from launcher/mibox.
 */
public class PreferenceKeysTest {
    private static final Path[] SOURCES = {Paths.get("../app/src/main/java"), Paths.get("src/main/java")};
    private static final Pattern CALL = Pattern.compile(
            "\\.(?:get|put)(?:Boolean|Int|Long|Float|String|StringSet)\\(\\s*\"([\\w.\\-]+)\""
                    + "|\\.(?:remove|contains)\\(\\s*\"([\\w.\\-]+)\""
                    + "|getSharedPreferences\\(\\s*\"([\\w.\\-]+)\"");
    private static final Pattern CONSTANT = Pattern.compile("static\\s+final\\s+String\\s+\\w+\\s*=\\s*\"([\\w.\\-]+)\"");

    @Test
    public void noSavedSettingChangesItsKey() throws IOException {
        Set<String> found = new TreeSet<>();
        for (Path root : SOURCES) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                    String text = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
                    if (!text.contains("SharedPreferences")) continue;
                    Matcher m = CALL.matcher(text);
                    while (m.find()) {
                        for (int g = 1; g <= m.groupCount(); g++) if (m.group(g) != null) found.add(m.group(g));
                    }
                    Matcher c = CONSTANT.matcher(text);
                    while (c.find()) found.add(c.group(1));
                }
            }
        }
        List<String> lines = Files.readAllLines(Paths.get("src/test/resources/preference-keys.txt"), StandardCharsets.UTF_8);
        Set<String> kept = new TreeSet<>();
        for (String l : lines) if (!l.isEmpty() && !l.startsWith("#")) kept.add(l.trim());
        assertEquals("Preference files and keys", kept, found);
    }
}
