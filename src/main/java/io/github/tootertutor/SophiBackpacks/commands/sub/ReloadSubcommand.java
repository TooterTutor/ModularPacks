package io.github.tootertutor.SophiBackpacks.commands.sub;

import java.util.List;

import io.github.tootertutor.SophiBackpacks.SophiBackpacksPlugin;
import io.github.tootertutor.SophiBackpacks.commands.CommandContext;
import io.github.tootertutor.SophiBackpacks.commands.Subcommand;
import net.kyori.adventure.text.Component;

public final class ReloadSubcommand implements Subcommand {

    private final SophiBackpacksPlugin plugin;

    public ReloadSubcommand(SophiBackpacksPlugin plugin) {
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
    public void execute(CommandContext ctx) {
        if (!ctx.sender().hasPermission("sophibackpacks.reload")) {
            ctx.sender().sendMessage(Component.text("You do not have permission."));
            return;
        }

        plugin.reloadAll();
        ctx.sender().sendMessage(Component.text("SophiBackpacks reloaded."));
    }

    @Override
    public List<String> tabComplete(CommandContext ctx) {
        return List.of();
    }
}

