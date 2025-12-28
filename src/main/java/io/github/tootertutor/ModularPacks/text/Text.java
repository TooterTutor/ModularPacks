package io.github.tootertutor.ModularPacks.text;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    /** Deserialize a config/lang string that may contain legacy & codes. */
    public static Component c(String input) {
        if (input == null || input.isEmpty())
            return Component.empty();

        // If you later decide to allow raw minimessage in config, you can detect it
        // here.
        // For now we always convert '&' -> minimessage.
        String mm = ampersandToMiniMessage(input);

        // IMPORTANT: if your config might contain '<' characters that are NOT
        // minimessage tags,
        // you may want to escape them. For most MC configs this is uncommon.
        return MM.deserialize(mm);
    }

    public static List<Component> lore(List<String> lines) {
        if (lines == null || lines.isEmpty())
            return List.of();
        List<Component> out = new ArrayList<>(lines.size());
        for (String s : lines)
            out.add(c(s));
        return out;
    }

    /**
     * Converts legacy color codes (&a, &7, &l, &r, etc.) into MiniMessage tags.
     * Supports hex in the form &#RRGGBB.
     */
    public static String ampersandToMiniMessage(String s) {
        if (s == null)
            return "";

        StringBuilder out = new StringBuilder(s.length() + 16);

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);

            // hex: &#RRGGBB -> <#RRGGBB>
            if (ch == '&' && i + 1 < s.length() && s.charAt(i + 1) == '#') {
                if (i + 7 < s.length()) {
                    String hex = s.substring(i + 2, i + 8);
                    if (hex.matches("[0-9a-fA-F]{6}")) {
                        out.append("<#").append(hex).append(">");
                        i += 7; // consumed & # RRGGBB
                        continue;
                    }
                }
            }

            // standard &x codes
            if (ch == '&' && i + 1 < s.length()) {
                char code = Character.toLowerCase(s.charAt(i + 1));
                String tag = legacyCodeToMiniTag(code);
                if (tag != null) {
                    out.append(tag);
                    i++; // skip code char
                    continue;
                }
            }

            out.append(ch);
        }

        return out.toString();
    }

    private static String legacyCodeToMiniTag(char code) {
        return switch (code) {
            // colors
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";

            // formats
            case 'l' -> "<bold>";
            case 'o' -> "<italic>";
            case 'n' -> "<underlined>";
            case 'm' -> "<strikethrough>";
            case 'k' -> "<obfuscated>";
            case 'r' -> "<reset>";

            default -> null;
        };
    }
}
