package com.gathin.launchos.tv;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tidy names for the screen. Panels write "FR: Channel FHD", "|FR| Film (2019)" or "Film - 2019": the language
 * prefix goes, the quality becomes a small tag, the year is kept apart. The name the panel sent is never changed
 * anywhere else. Also the keys used to sort A to Z and to search without accents or case.
 */
final class Names {
    /** Language and country prefixes panels put in front of names. Only these are removed, so "FBI: …" stays. */
    private static final Set<String> PREFIXES = new HashSet<>(java.util.Arrays.asList(
            "FR", "FRA", "FRENCH", "VF", "VFF", "VFQ", "VO", "VOST", "VOSTFR", "MULTI", "EN", "ENG", "UK", "US", "USA",
            "BE", "CH", "CA", "QC", "LU", "DE", "ES", "IT", "PT", "NL", "AR", "TR", "MA", "DZ", "TN", "AF", "AFR",
            "EU", "INT", "4K", "UHD", "FHD", "HD"));
    private static final Pattern PREFIX = Pattern.compile(
            "^\\s*[|\\[(]?\\s*([A-Z0-9]{2,7})\\s*(?:[|\\])]\\s*[:\\-]?|:|\\s-|\\s\\|)\\s*");
    /** A quality written at the end, alone or in brackets. */
    private static final Pattern QUALITY = Pattern.compile(
            "(?i)(?:[\\s\\-|]+[\\[(]?|[\\[(])\\s*(UHD|4K|FHD|HD\\+?|SD|HEVC|H\\.?265|1080P|720P|50FPS|HDR|RAW|LQ|HQ|VOSTFR|VOST|TRUEFRENCH|FRENCH|MULTI|VFF|VFQ|VF|VO)\\s*[\\])]?\\s*$");
    private static final Pattern YEAR = Pattern.compile("\\s*(?:[(\\[]\\s*((?:19|20)\\d\\d)\\s*[)\\]]|-\\s*((?:19|20)\\d\\d))\\s*$");
    private static final Pattern MARKS = Pattern.compile("^[\\s\\p{So}\\p{Sk}*#|\\-_.:~•·]+|[\\s\\p{So}\\p{Sk}*#|\\-_.:~•·]+$");
    private static final Pattern EPISODE_CODE = Pattern.compile("^[\\s\\-–:|]*(?i:S\\d{1,2}\\s*E\\d{1,3})[\\s\\-–:|]*");
    private static final Pattern NUMBERING = Pattern.compile("^\\d{1,3} (?=\\p{L})");
    /** A word in front of a group's name, set apart by "|", ":" or " -" ("FR | Sport", "|VOD| Action"). */
    private static final Pattern GROUP_PREFIX = Pattern.compile(
            "^\\s*[|\\[(]?\\s*([\\p{L}0-9]{2,8})\\s*(?:[|\\])]\\s*[:\\-|]?|:|\\s-|\\s\\|)\\s*");
    /** "01 - Action", "3. Sport": a panel's numbering in front of a group, set apart by a sign. */
    private static final Pattern SHOWN_NUMBERING = Pattern.compile("^\\s*\\d{1,3}\\s*[-.|:)]\\s*(?=\\p{L})");
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    /** A cleaned name: what to show, the quality tag ("" when none) and the year (0 when none). */
    static final class Clean {
        final String title;
        final String tag;
        final int year;

        Clean(String title, String tag, int year) {
            this.title = title;
            this.tag = tag;
            this.year = year;
        }
    }

    private Names() {
    }

    static Clean clean(String raw) {
        String s = superscripts(raw == null ? "" : raw).trim();
        String original = s;
        // The language prefix, once or twice ("|FR| VOSTFR - Film").
        for (int i = 0; i < 2; i++) {
            Matcher m = PREFIX.matcher(s);
            if (!m.find() || !PREFIXES.contains(m.group(1))) break;
            String rest = s.substring(m.end());
            if (rest.trim().isEmpty()) break;
            s = rest;
        }
        String tag = "";
        int year = 0;
        for (int i = 0; i < 3; i++) {
            Matcher q = QUALITY.matcher(s);
            if (q.find() && q.start() > 0) {
                String t = q.group(1).toUpperCase(Locale.ROOT).replace(".", "");
                if (tag.isEmpty()) tag = t.equals("UHD") ? "4K" : t.equals("H265") ? "HEVC" : t;
                s = s.substring(0, q.start());
                continue;
            }
            Matcher y = YEAR.matcher(s);
            if (year == 0 && y.find() && y.start() > 0) {
                year = Integer.parseInt(y.group(1) != null ? y.group(1) : y.group(2));
                s = s.substring(0, y.start());
                continue;
            }
            break;
        }
        s = MARKS.matcher(s).replaceAll("").trim();
        if (s.isEmpty()) s = original;
        return new Clean(s, tag, year);
    }

    /** An episode's own title: without the series' name and the "S01E02" panels often put in front. */
    static String episode(String seriesTitle, String title) {
        String t = title == null ? "" : title.trim();
        if (seriesTitle != null && !seriesTitle.isEmpty()
                && fold(t).startsWith(fold(seriesTitle) + " ") && t.length() > seriesTitle.length()) {
            t = t.substring(seriesTitle.length());
        }
        t = EPISODE_CODE.matcher(t).replaceFirst("");
        t = MARKS.matcher(t).replaceAll("").trim();
        return t.isEmpty() ? (title == null ? "" : title.trim()) : t;
    }

    /** A group's name without the decorations around it ("★★ SPORT ★★" is "SPORT"); the prefix is kept. */
    static String tidy(String raw) {
        String s = MARKS.matcher(superscripts(raw == null ? "" : raw)).replaceAll("").trim();
        return s.isEmpty() ? (raw == null ? "" : raw.trim()) : s;
    }

    /**
     * The word most of a section's groups start with ("FR" in "FR | Sport", "FR | Cinéma", …), or null when no word
     * is shared by at least half of them. Only that word is taken off, so "UK | Sport" stays apart from "Sport".
     */
    static String groupPrefix(java.util.Collection<String> rawNames) {
        java.util.Map<String, Integer> count = new java.util.HashMap<>();
        String best = null;
        int max = 0;
        for (String raw : rawNames) {
            String p = groupPrefixOf(superscripts(raw == null ? "" : raw));
            if (p == null) continue;
            int n = count.merge(p, 1, Integer::sum);
            if (n > max) {
                max = n;
                best = p;
            }
        }
        return max >= 2 && max * 2 >= rawNames.size() ? best : null;
    }

    private static String groupPrefixOf(String s) {
        Matcher m = GROUP_PREFIX.matcher(s);
        if (!m.find() || s.substring(m.end()).trim().isEmpty()) return null;
        String p = m.group(1).toUpperCase(Locale.ROOT);
        return p.chars().allMatch(Character::isDigit) ? null : p;   // "01 - Action" is numbering, not a prefix
    }

    /** A group's name as shown: without the section's shared prefix and decorations, and not in capitals. */
    static String group(String raw, String prefix) {
        String s = superscripts(raw == null ? "" : raw);
        if (prefix != null && prefix.equals(groupPrefixOf(s))) {
            Matcher m = GROUP_PREFIX.matcher(s);
            if (m.find()) s = s.substring(m.end());
        }
        Matcher n = SHOWN_NUMBERING.matcher(s);
        if (n.find() && n.end() < s.length()) s = s.substring(n.end());
        return uncap(tidy(s));
    }

    /** "SPORT FRANCE" becomes "Sport France"; short words stay as they are ("TV", "USA", "F1"). */
    static String uncap(String s) {
        boolean letters = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLowerCase(c)) return s;
            if (Character.isLetter(c)) letters = true;
        }
        if (!letters) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (String w : s.split(" ", -1)) {
            if (b.length() > 0) b.append(' ');
            b.append(w.length() >= 4 ? w.charAt(0) + w.substring(1).toLowerCase(Locale.FRENCH) : w);
        }
        return b.toString();
    }

    /** The key to sort names A to Z: no accents, no case, no leading symbols. */
    static String sortKey(String name) {
        return fold(name);
    }

    /** The key to sort groups: like {@link #sortKey}, and a numbering in front is ignored ("01 - Action" is A). */
    static String groupKey(String name) {
        String f = fold(name);
        Matcher m = NUMBERING.matcher(f);
        return m.find() && m.end() < f.length() ? f.substring(m.end()) : f;
    }

    /** Lower case, no accents, words separated by single spaces. */
    static String fold(String s) {
        if (s == null) return "";
        String n = ACCENTS.matcher(Normalizer.normalize(superscripts(s), Normalizer.Form.NFD)).replaceAll("");
        return NOT_WORD.matcher(n.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    /**
     * How well a folded name matches a folded query: 0 starts with it, 1 a word starts with it, 2 every word of the
     * query is in it somewhere, -1 no match.
     */
    static int match(String foldedName, String foldedQuery) {
        if (foldedQuery.isEmpty()) return -1;
        if (foldedName.startsWith(foldedQuery)) return 0;
        if (foldedName.contains(" " + foldedQuery)) return 1;
        for (String w : foldedQuery.split(" ")) {
            if (!foldedName.contains(w)) return -1;
        }
        return 2;
    }

    /** IPTV lists love small capitals and superscripts (ᴴᴰ, ᶠᴴᴰ, ᵁᴴᴰ): read them as plain letters. */
    private static String superscripts(String s) {
        StringBuilder b = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char r = plain(c);
            if (r != c) {
                if (b == null) b = new StringBuilder(s.substring(0, i));
                b.append(r);
            } else if (b != null) {
                b.append(c);
            }
        }
        return b == null ? s : b.toString();
    }

    private static char plain(char c) {
        switch (c) {
            case 'ᴬ': return 'A';
            case 'ᴮ': return 'B';
            case 'ᴰ': return 'D';
            case 'ᴱ': return 'E';
            case 'ᴳ': return 'G';
            case 'ᴴ': return 'H';
            case 'ᴵ': return 'I';
            case 'ᴶ': return 'J';
            case 'ᴷ': return 'K';
            case 'ᴸ': return 'L';
            case 'ᴹ': return 'M';
            case 'ᴺ': return 'N';
            case 'ᴼ': return 'O';
            case 'ᴾ': return 'P';
            case 'ᴿ': return 'R';
            case 'ᵀ': return 'T';
            case 'ᵁ': return 'U';
            case 'ᵂ': return 'W';
            case 'ᶠ': return 'F';
            case 'ˢ': return 'S';
            case '⁴': return '4';
            case 'ᵏ': return 'K';
            default: return c;
        }
    }
}
