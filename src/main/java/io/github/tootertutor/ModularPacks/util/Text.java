package io.github.tootertutor.ModularPacks.util;

import java.util.List;

import net.kyori.adventure.text.Component;

public final class Text {

    private Text() {
    }

    public static Component c(String input) {
        return io.github.tootertutor.ModularCore.util.Text.c(input);
    }

    public static List<Component> lore(List<String> lines) {
        return io.github.tootertutor.ModularCore.util.Text.lore(lines);
    }

    public static String ampersandToMiniMessage(String input) {
        return io.github.tootertutor.ModularCore.util.Text.ampersandToMiniMessage(input);
    }
}
