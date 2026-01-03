package io.github.tootertutor.ModularPacks.commands.sub;

import java.util.List;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.commands.CommandContext;
import io.github.tootertutor.ModularPacks.commands.Subcommand;
import net.kyori.adventure.text.Component;

public final class ReloadSubcommand implements Subcommand {

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
        return "Reload config, lang, and recipes";
    }

    @Override
    public String permission() {
        return "modularpacks.reload";
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!ctx.sender().hasPermission("modularpacks.reload")) {
            ctx.sender().sendMessage(Component.text("You do not have permission."));
            return;
        }

        plugin.reloadAll();
        ctx.sender().sendMessage(Component.text("modularpacks reloaded."));
    }

    @Override
    public List<String> tabComplete(CommandContext ctx) {
        return List.of();
    }
}
