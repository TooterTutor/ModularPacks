package io.github.tootertutor.ModularPacks.commands.sub;

import java.util.List;
import java.util.Locale;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.commands.AbstractSubcommand;
import io.github.tootertutor.ModularPacks.commands.CommandContext;

/**
 * Reload config, language, and recipes from disk.
 */
public final class ReloadSubcommand extends AbstractSubcommand {

    private final ModularPacksPlugin plugin;

    public ReloadSubcommand(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String description() {
        return "Reload config, lang, and/or recipes";
    }

    @Override
    public String permission() {
        return "modularpacks.reload";
    }

    @Override
    public String getUsage() {
        return "backpack reload [config|lang|recipes|all]";
    }

    @Override
    public String getExtendedHelp() {
        return "Reload configuration files from disk.\n"
                + "  config  - Reload config.yml and backpack types/upgrades\n"
                + "  lang    - Reload language files\n"
                + "  recipes - Reload and re-register recipes\n"
                + "  all     - Reload everything (default if no args provided)";
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!checkPermission(ctx))
            return;

        boolean reloadConfig = false;
        boolean reloadLang = false;
        boolean reloadRecipes = false;

        if (ctx.args().isEmpty()) {
            // Default: reload all
            reloadConfig = reloadLang = reloadRecipes = true;
        } else {
            // Parse specific components
            for (String arg : ctx.args()) {
                String lower = arg.toLowerCase(Locale.ROOT);
                switch (lower) {
                    case "config", "cfg" -> reloadConfig = true;
                    case "lang", "language" -> reloadLang = true;
                    case "recipes", "recipe" -> reloadRecipes = true;
                    case "all" -> reloadConfig = reloadLang = reloadRecipes = true;
                    default -> {
                        ctx.sendError("Unknown reload component: " + arg);
                        ctx.sendError("Valid options: config, lang, recipes, all");
                        return;
                    }
                }
            }
        }

        StringBuilder reloaded = new StringBuilder();

        if (reloadConfig) {
            plugin.cfg().reload();
            reloaded.append("config");
        }

        if (reloadLang) {
            if (reloaded.length() > 0)
                reloaded.append(", ");
            plugin.lang().reload();
            reloaded.append("lang");
        }

        if (reloadRecipes) {
            if (reloaded.length() > 0)
                reloaded.append(", ");
            plugin.recipes().reload();
            reloaded.append("recipes");
        }

        ctx.sendInfo("Reloaded: " + reloaded.toString());
    }

    @Override
    public List<String> tabComplete(CommandContext ctx) {
        if (ctx.size() == 1) {
            String prefix = ctx.arg(0).toLowerCase(Locale.ROOT);
            return List.of("config", "lang", "recipes", "all").stream()
                    .filter(s -> s.startsWith(prefix))
                    .sorted()
                    .toList();
        }
        return List.of();
    }
}
