package io.github.tootertutor.SophiBackpacks.config;

import java.util.ArrayList;
import java.util.List;

import io.github.tootertutor.SophiBackpacks.SophiBackpacksPlugin;

public final class Placeholders {
    private Placeholders() {
    }

    public static List<String> expandLore(SophiBackpacksPlugin plugin, List<String> lore) {
        if (lore == null || lore.isEmpty())
            return List.of();

        List<String> out = new ArrayList<>();
        for (String line : lore) {
            if (line == null)
                continue;

            if (line.contains("{moduleActions}")) {
                // If the line is just the placeholder, replace it with the list.
                if (line.trim().equals("{moduleActions}")) {
                    out.addAll(plugin.lang().moduleActions());
                } else {
                    // inline replace: insert each action line with the line wrapper
                    for (String a : plugin.lang().moduleActions()) {
                        out.add(line.replace("{moduleActions}", a));
                    }
                }
                continue;
            }

            out.add(line);
        }
        return out;
    }
}
