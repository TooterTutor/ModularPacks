package io.github.tootertutor.ModularPacks.config;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Named substitutions are performed once, so user values cannot expand other
 * tokens.
 */
public final class LangTemplates {
    private static final Pattern TOKEN = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)\\}");

    private LangTemplates() {
    }

    public static String format(String template, Object... replacements) {
        if (replacements.length % 2 != 0) {
            throw new IllegalArgumentException("Expected name/value pairs");
        }
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < replacements.length; i += 2) {
            values.put(String.valueOf(replacements[i]), String.valueOf(replacements[i + 1]));
        }
        return TOKEN.matcher(template).replaceAll(match -> Matcher.quoteReplacement(
                values.getOrDefault(match.group(1), match.group())));
    }
}
