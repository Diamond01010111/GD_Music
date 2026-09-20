package com.diamond.gdmusic;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Character folding for metadata matching; original display text is never modified. */
final class ChineseText {
    private static final Map<Integer, String> SIMPLIFIED = load("TSCharacters.txt");
    private static final Map<Integer, String> TRADITIONAL = load("STCharacters.txt");
    static String simplified(String text) { return convert(text, SIMPLIFIED); }
    static String traditional(String text) { return convert(text, TRADITIONAL); }
    private static String convert(String text, Map<Integer, String> dictionary) {
        StringBuilder output = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int code = text.codePointAt(offset);
            String mapped = dictionary.get(code);
            if (mapped == null) output.appendCodePoint(code); else output.append(mapped);
            offset += Character.charCount(code);
        }
        return output.toString();
    }
    private static Map<Integer, String> load(String filename) {
        Map<Integer, String> result = new HashMap<>();
        try (InputStream input = ChineseText.class.getResourceAsStream("/opencc/" + filename)) {
            if (input == null) throw new IllegalStateException("Missing Chinese dictionary: " + filename);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#")) continue;
                    String[] entry = line.split("\t");
                    if (entry.length == 2 && entry[0].codePointCount(0, entry[0].length()) == 1)
                        result.put(entry[0].codePointAt(0), entry[1].split(" ")[0]);
                }
            }
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        return result;
    }
}
